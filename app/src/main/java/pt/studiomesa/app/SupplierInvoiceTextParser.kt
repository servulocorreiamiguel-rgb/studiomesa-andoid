package pt.studiomesa.app

import java.math.BigDecimal
import java.security.MessageDigest
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

internal data class SupplierInvoiceLineDraft(
    val id: String,
    val productCode: String,
    val description: String,
    val quantity: BigDecimal,
    val unit: String,
    val unitPrice: BigDecimal,
    val netAmount: BigDecimal,
    val vatRate: BigDecimal?
)

internal data class SupplierInvoiceDraft(
    val supplierName: String = "",
    val supplierVatNumber: String = "",
    val invoiceNumber: String = "",
    val issueDate: String = "",
    val dueDate: String = "",
    val netAmount: BigDecimal? = null,
    val vatAmount: BigDecimal? = null,
    val totalAmount: BigDecimal? = null,
    val lines: List<SupplierInvoiceLineDraft> = emptyList(),
    val currency: String = "EUR",
    val source: String = "ocr",
    val rawText: String = "",
    val warnings: List<String> = emptyList()
) {
    val isReviewable: Boolean
        get() = supplierName.isNotBlank() && invoiceNumber.isNotBlank() &&
            issueDate.isNotBlank() && totalAmount != null && totalAmount > BigDecimal.ZERO

    fun arithmeticWarning(): String? {
        val net = netAmount ?: return null
        val vat = vatAmount ?: return null
        val total = totalAmount ?: return null
        val difference = net.add(vat).subtract(total).abs()
        return if (difference > BigDecimal("0.05")) {
            "A soma da base tributável e do IVA não coincide com o total (€${difference.setScale(2, RoundingMode.HALF_UP)} de diferença)."
        } else null
    }
}

/** Extração determinística. Todos os campos são candidatos e exigem revisão humana. */
internal object SupplierInvoiceTextParser {
    private val amountPattern = Regex("""(?<![\d])(?:€\s*)?(-?\d{1,3}(?:[ .]\d{3})*(?:[,.]\d{2,4})|-?\d+(?:[,.]\d{2,4})?)(?:\s*€|\s*EUR)?(?![\d])""", RegexOption.IGNORE_CASE)
    private val datePattern = Regex("""\b(\d{1,2}[./-]\d{1,2}[./-]\d{2,4}|\d{4}-\d{2}-\d{2})\b""")
    private val nifPattern = Regex("""\b(?:NIF|NIPC|CONTRIBUINTE|VAT\s*(?:NO|NUMBER|ID)?)\s*[:.]?\s*(\d{9})\b""", RegexOption.IGNORE_CASE)
    private val invoicePattern = Regex(
        """\b(?:FATURA|FACTURA|FATURA-RECIBO|FACTURA-RECIBO|INVOICE|DOCUMENTO)\s*(?:N[.º°]?\s*)?[:#-]?\s*([A-Z0-9][A-Z0-9./_-]{2,30})""",
        RegexOption.IGNORE_CASE
    )
    private val supplierLabelPattern = Regex(
        """(?im)^\s*(?:FORNECEDOR|EMITENTE|SUPPLIER|DENOMINAÇÃO SOCIAL)\s*[:\-]\s*(.{2,100})\s*$"""
    )
    // Capture only structured rows. Ambiguous OCR fragments are intentionally
    // excluded instead of being guessed into inventory movements.
    private val invoiceLinePattern = Regex(
        """^\s*(?:(?<lineNo>\d{1,4})[.)]?\s+)?(?:(?<productCode>(?=[A-Z0-9./_-]{4,24}\s)(?=[A-Z0-9./_-]*\d)[A-Z0-9][A-Z0-9./_-]{3,23})\s+)?(?<description>[\p{L}][\p{L}\p{N}() /.,'-]{2,79}?)\s+(?<quantity>\d{1,7}(?:[,.]\d{1,4})?)\s*(?<unit>UNIDADES?|UNID(?:ADE)?S?|UND|UN|KG|G|GR|L|LT|ML|CX|CAIXA|PCT|PACOTE|PACK|FD|FARDO|M|MT|M2|M3|DZ|DOZ)\s+(?:[x×]\s*)?(?<unitPrice>\d{1,7}(?:[,.]\d{2,4})?)\s+(?<netAmount>\d{1,7}(?:[,.]\d{2,4})?)(?:\s+(?<vatRate>\d{1,2}(?:[,.]\d{1,2})?)\s*%)?(?:\s+\d{1,7}(?:[,.]\d{2,4})?)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawText: String, source: String = "ocr"): SupplierInvoiceDraft {
        val text = rawText.replace('\u0000', ' ').replace('\r', '\n')
            .replace(Regex("[ \t]+"), " ").take(MAX_OCR_CHARS)
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val supplierName = lines.firstNotNullOfOrNull { line ->
            supplierLabelPattern.matchEntire(line)?.groupValues?.getOrNull(1)?.trim()
        }.orEmpty().ifBlank {
            lines.firstOrNull { line ->
                val n = normalize(line)
                line.length in 3..90 && !n.contains("fatura") && !n.contains("factura") &&
                    !n.contains("recibo") && !n.contains("contribuinte") && !n.contains("nif") &&
                    !n.contains("total") && !n.contains("data") && !n.contains("iva") &&
                    line.any { it.isLetter() } && line.any { it.isUpperCase() }
            }.orEmpty()
        }

        val nif = nifPattern.find(text)?.groupValues?.getOrNull(1)
            ?.takeIf(::isValidPortugueseVatNumber).orEmpty()
        val invoiceNumber = invoicePattern.find(text)?.groupValues?.getOrNull(1)
            ?.trimEnd('.', ':', ';', ',').orEmpty()
        val issueDate = extractDate(
            lines,
            labels = listOf("data da fatura", "data de emissao", "data emissao", "date", "data"),
            excludedLabels = listOf("vencimento", "due date", "data limite")
        )
        val dueDate = extractDate(lines, listOf("data de vencimento", "vencimento", "due date", "data limite"))
        val total = extractLabeledAmount(
            lines,
            labels = listOf(
                "total a pagar", "total da fatura", "total da factura",
                "total geral", "total documento", "valor total", "total com iva", "total c/ iva",
                "total incluindo iva", "valor com iva", "total eur", "total"
            ),
            excludedLabels = listOf(
                "total iva", "iva total", "valor iva", "valor do iva", "total vat", "vat total",
                "valor vat", "imposto", "base tributavel", "base tributável",
                "subtotal", "desconto", "retencao", "retenção"
            )
        )
        val net = extractLabeledAmount(lines, listOf(
            "base tributavel", "base tributável", "subtotal", "valor sem iva", "total liquido", "total líquido"
        ))
        val vat = extractLabeledAmount(
            lines,
            labels = listOf("total iva", "valor iva", "iva total", "imposto total", "iva", "vat")
        )

        val extractedLines = extractInvoiceLines(lines)
        val warnings = buildList {
            if (supplierName.isBlank()) add("Não foi possível identificar o fornecedor. Confirma manualmente.")
            if (nif.isBlank()) add("NIF/NIPC não identificado ou inválido. Confirma o número no documento.")
            if (invoiceNumber.isBlank()) add("Número da fatura não identificado. Confirma manualmente.")
            if (issueDate.isBlank()) add("Data de emissão não identificada. Confirma manualmente.")
            if (total == null) add("Total não identificado. Introduz o total exatamente como aparece na fatura.")
            if (net == null) add("Base tributável não identificada.")
            if (vat == null) add("IVA não identificado.")
            val normalized = normalize(text)
            if (normalized.contains("anulado") || normalized.contains("cancelado")) {
                add("O texto parece mencionar anulação/cancelamento. Confirma se o documento é válido.")
            }
        }

        val draft = SupplierInvoiceDraft(
            supplierName = supplierName.take(100),
            supplierVatNumber = nif,
            invoiceNumber = invoiceNumber.take(40),
            issueDate = issueDate,
            dueDate = dueDate,
            netAmount = net,
            vatAmount = vat,
            totalAmount = total,
            lines = extractedLines,
            rawText = text,
            source = source,
            warnings = warnings
        )
        return draft.copy(warnings = warnings + listOfNotNull(draft.arithmeticWarning()))
    }

    private fun extractInvoiceLines(lines: List<String>): List<SupplierInvoiceLineDraft> {
        val extracted = linkedMapOf<String, SupplierInvoiceLineDraft>()
        lines.forEachIndexed { index, line ->
            val normalized = normalize(line)
            if (normalized.isBlank() || listOf(
                    "subtotal", "total", "iva", "vat", "base tributavel", "desconto",
                    "transporte", "a pagar", "descricao quantidade", "preco unitario"
                ).any { normalized.startsWith(it) }) return@forEachIndexed
            val match = invoiceLinePattern.matchEntire(line) ?: return@forEachIndexed
            val description = match.groups["description"]?.value?.trim()
                ?.replace(Regex("\\s+"), " ").orEmpty()
            val quantity = match.groups["quantity"]?.value?.let(::parseFlexibleDecimal)
            val unitPrice = match.groups["unitPrice"]?.value?.let(::parseAmount)
            val netAmount = match.groups["netAmount"]?.value?.let(::parseAmount)
            if (description.length !in 3..80 || quantity == null || quantity.signum() <= 0 ||
                quantity > BigDecimal("999999") || unitPrice == null || unitPrice.signum() < 0 ||
                netAmount == null || netAmount.signum() < 0) return@forEachIndexed

            val unitRaw = match.groups["unit"]?.value.orEmpty().uppercase(Locale.ROOT)
            val unit = when (unitRaw) {
                "UNIDADE", "UNIDADES", "UNID", "UNIDS", "UND", "UN" -> "un"
                "GR", "G" -> "g"
                "LT", "L" -> "l"
                "MT", "M" -> "m"
                "CX", "CAIXA" -> "cx"
                "PCT", "PACOTE", "PACK" -> "pct"
                "FD", "FARDO" -> "fardo"
                "DZ", "DOZ" -> "dz"
                else -> unitRaw.lowercase(Locale.ROOT)
            }
            val vatRate = match.groups["vatRate"]?.value?.let(::parseFlexibleDecimal)
            if (vatRate != null && (vatRate.signum() < 0 || vatRate > BigDecimal("100"))) {
                return@forEachIndexed
            }
            val productCode = match.groups["productCode"]?.value.orEmpty().take(24)
            val stableSource = index.toString() + "|" + line.trim().lowercase(Locale.ROOT)
            val lineId = MessageDigest.getInstance("SHA-256")
                .digest(stableSource.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            extracted.putIfAbsent(
                lineId,
                SupplierInvoiceLineDraft(
                    id = lineId,
                    productCode = productCode,
                    description = description,
                    quantity = quantity,
                    unit = unit,
                    unitPrice = unitPrice,
                    netAmount = netAmount,
                    vatRate = vatRate
                )
            )
        }
        return extracted.values.take(MAX_INVOICE_LINES)
    }

    private fun parseFlexibleDecimal(raw: String): BigDecimal? {
        var value = raw.trim().replace(" ", "")
        if (value.isBlank()) return null
        val comma = value.lastIndexOf(',')
        val dot = value.lastIndexOf('.')
        value = when {
            comma >= 0 && dot >= 0 && comma > dot ->
                value.replace(".", "").replace(',', '.')
            comma >= 0 && dot >= 0 && dot > comma ->
                value.replace(",", "")
            comma >= 0 -> value.replace(',', '.')
            dot >= 0 && Regex("""^-?\d{1,3}(?:\.\d{3})+$""").matches(value) ->
                value.replace(".", "")
            else -> value
        }
        return runCatching {
            BigDecimal(value).takeIf {
                it.scale().coerceAtLeast(0) <= 4 && it.abs() <= BigDecimal("999999999")
            }
        }.getOrNull()
    }

    fun parseQuantity(raw: String): BigDecimal? {
        val value = raw.trim().replace(',', '.')
        if (!Regex("""^[0-9]+(?:\\.[0-9]{1,3})?$""").matches(value)) return null
        return runCatching {
            BigDecimal(value).takeIf {
                it.signum() > 0 && it <= BigDecimal("999999")
            }
        }.getOrNull()
    }

    fun isValidPortugueseVatNumber(raw: String): Boolean {
        val nif = raw.filter(Char::isDigit)
        if (nif.length != 9 || nif[0] !in "1235689") return false
        val sum = (0..7).sumOf { index -> nif[index].digitToInt() * (9 - index) }
        val remainder = sum % 11
        val checkDigit = if (remainder < 2) 0 else 11 - remainder
        return nif[8].digitToInt() == checkDigit
    }

    fun parseAmount(raw: String): BigDecimal? {
        var value = raw.trim().replace("€", "").replace(Regex("(?i)EUR"), "").replace(" ", "")
        if (value.isBlank()) return null
        if (value.startsWith("(") && value.endsWith(")")) value = "-${value.substring(1, value.length - 1)}"
        val comma = value.lastIndexOf(',')
        val dot = value.lastIndexOf('.')
        value = when {
            comma >= 0 && dot >= 0 -> {
                val decimalSeparator = if (comma > dot) ',' else '.'
                val thousandsSeparator = if (decimalSeparator == ',') '.' else ','
                value.replace(thousandsSeparator.toString(), "").replace(decimalSeparator, '.')
            }
            comma >= 0 -> value.replace(".", "").replace(',', '.')
            dot >= 0 && Regex("""^-?\d{1,3}(?:\.\d{3})+$""").matches(value) -> value.replace(".", "")
            else -> value
        }
        return runCatching { BigDecimal(value).setScale(2, RoundingMode.HALF_UP) }.getOrNull()
    }

    private fun extractLabeledAmount(
        lines: List<String>,
        labels: List<String>,
        excludedLabels: List<String> = emptyList()
    ): BigDecimal? {
        // Match specific labels first. A generic "total" must never match "Total IVA",
        // "Subtotal" or another tax/discount line.
        for (label in labels) {
            for (line in lines.asReversed()) {
                val normalized = normalize(line)
                if (excludedLabels.any { normalize(it) in normalized }) continue
                val normalizedLabel = normalize(label)
                val matchesLabel = when (normalizedLabel) {
                    "total" -> normalized == "total" ||
                        normalized.startsWith("total ") ||
                        normalized.startsWith("total:") ||
                        normalized.startsWith("total -") ||
                        normalized.startsWith("total #")
                    "iva", "vat" -> normalized == normalizedLabel ||
                        normalized.startsWith("$normalizedLabel:") ||
                        normalized.startsWith("$normalizedLabel ")
                    else -> normalizedLabel in normalized
                }
                if (!matchesLabel) continue
                amountPattern.findAll(line).mapNotNull { parseAmount(it.value) }
                    .lastOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun extractDate(
        lines: List<String>,
        labels: List<String>,
        excludedLabels: List<String> = emptyList()
    ): String {
        // Prefer explicit labels and exclude maturity/due-date rows from issue-date lookup.
        for (label in labels) {
            for (line in lines) {
                val normalized = normalize(line)
                if (excludedLabels.any { normalize(it) in normalized }) continue
                if (normalize(label) !in normalized) continue
                datePattern.find(line)?.groupValues?.getOrNull(1)
                    ?.let { parseDate(it)?.let { iso -> return iso } }
            }
        }
        return ""
    }

    private fun parseDate(raw: String): String? {
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("d/M/uu", Locale.ROOT),
            DateTimeFormatter.ofPattern("d-M-uu", Locale.ROOT)
        )
        for (format in formats) {
            val parsed = try { LocalDate.parse(raw.trim(), format) } catch (_: DateTimeParseException) { null }
            if (parsed != null && parsed.year in 2000..2100) return parsed.toString()
        }
        return null
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private const val MAX_OCR_CHARS = 50_000
    private const val MAX_INVOICE_LINES = 500
}
