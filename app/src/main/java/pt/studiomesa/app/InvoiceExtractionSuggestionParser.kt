package pt.studiomesa.app

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Produces editable candidates from locally recognized invoice text.
 * It never certifies a fiscal document or saves values by itself.
 */
object InvoiceExtractionSuggestionParser {
    data class Candidate(
        val value: String,
        val confidence: Int,
        val evidence: String
    )

    data class Suggestions(
        val supplier: Candidate? = null,
        val documentNumber: Candidate? = null,
        val invoiceDate: Candidate? = null,
        val invoiceType: Candidate? = null,
        val netAmount: Candidate? = null,
        val taxAmount: Candidate? = null,
        val totalAmount: Candidate? = null
    ) {
        fun isEmpty(): Boolean = listOf(
            supplier, documentNumber, invoiceDate, invoiceType,
            netAmount, taxAmount, totalAmount
        ).all { it == null }
    }

    fun parse(text: String): Suggestions {
        val lines = text.lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return Suggestions()

        val supplier = findText(lines, Regex(
            """(?i)^\s*(?:fornecedor|emitente|supplier|seller|nome do fornecedor)\s*[:\-]\s*(.+?)\s*$"""
        ), 92)
        val number = findText(lines, Regex(
            """(?i)^\s*(?:n[.º°o]?\s*(?:da\s*)?(?:fatura|factura|nota de crédito|nota de credito|documento)|(?:n[uú]mero|numero)\s*(?:da\s*)?(?:fatura|factura|documento)|invoice\s*no|document\s*no|invoiceno|documentno)\s*[:#\-]?\s*(\S.*)$"""
        ), 90)
        val date = findDate(lines)
        val type = lines.firstOrNull { line ->
            val normalized = normalize(line)
            normalized.contains("nota de credito") || normalized.contains("credit note")
        }?.let { Candidate("credit_note", 94, it.take(180)) }
            ?: lines.firstOrNull { normalize(it).contains("fatura") || normalize(it).contains("factura") }
                ?.let { Candidate("invoice", 82, it.take(180)) }

        val net = findAmount(lines, Regex(
            """(?i)^\s*(?:base\s*tribut[aá]vel|valor\s*tribut[aá]vel|subtotal(?:\s*sem\s*iva)?|total\s*l[ií]quido|net\s*total|invoicenet)\b(.*)$"""
        ), 90)
        val vat = findAmount(lines, Regex(
            """(?i)^\s*(?:total\s*iva|valor\s*do\s*iva|valor\s*iva|iva(?:\s*(?:total|a\s*pagar))?|vat(?:\s*amount)?|invoicetax)\b(.*)$"""
        ), 88)
        val total = findAmount(lines, Regex(
            """(?i)^\s*(?:total\s*(?:a\s*pagar|da\s*fatura|da\s*factura|do\s*documento|geral)|valor\s*total|grand\s*total|gross\s*total|invoicetotal)\b(.*)$"""
        ), 90)

        return Suggestions(
            supplier = supplier,
            documentNumber = number,
            invoiceDate = date,
            invoiceType = type,
            netAmount = net,
            taxAmount = vat,
            totalAmount = total
        )
    }

    private fun findText(lines: List<String>, regex: Regex, confidence: Int): Candidate? =
        lines.firstNotNullOfOrNull { line ->
            val value = regex.find(line)?.groupValues?.getOrNull(1)?.trim()
                ?.trimEnd('.', ';', ',')
                ?.takeIf { it.length in 2..120 }
                ?: return@firstNotNullOfOrNull null
            Candidate(value, confidence, line.take(180))
        }

    private fun findDate(lines: List<String>): Candidate? {
        val label = Regex(
            """(?i)^\s*(?:data(?:\s*(?:do\s*documento|da\s*fatura|de\s*emiss[aã]o|emiss[aã]o))?|date|invoicedate|docdate)\s*[:\-]?\s*(.+)$"""
        )
        for (line in lines) {
            val value = label.find(line)?.groupValues?.getOrNull(1)?.trim() ?: continue
            val match = Regex("""\b(\d{4}-\d{2}-\d{2}|\d{2}[./-]\d{2}[./-]\d{4})\b""")
                .find(value)?.value ?: continue
            val formats = if (match.contains('-') && match.startsWith("20")) {
                listOf("yyyy-MM-dd")
            } else {
                listOf("dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy")
            }
            for (pattern in formats) {
                val formatter = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }
                val parsed = runCatching { formatter.parse(match) }.getOrNull() ?: continue
                val iso = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(parsed)
                return Candidate(iso, 92, line.take(180))
            }
        }
        return null
    }

    private fun findAmount(lines: List<String>, label: Regex, confidence: Int): Candidate? {
        val trailingAmount = Regex("""(-?\d[\d\s.'’]*(?:[,.]\d{1,2})?)\s*(?:€|EUR)?\s*$""", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val remainder = label.find(line)?.groupValues?.getOrNull(1) ?: continue
            val match = trailingAmount.find(remainder) ?: continue
            val raw = match.groupValues[1].trim()
            val hasDecimal = raw.contains(',') || raw.contains('.') || remainder.contains('€') ||
                remainder.contains("EUR", ignoreCase = true)
            if (!hasDecimal) continue
            val normalized = normalizeAmount(raw) ?: continue
            return Candidate(normalized, confidence, line.take(180))
        }
        return null
    }

    private fun normalizeAmount(raw: String): String? {
        var value = raw.replace(" ", "").replace("'", "").replace("’", "")
        if (value.isBlank()) return null
        val comma = value.lastIndexOf(',')
        val dot = value.lastIndexOf('.')
        value = when {
            comma >= 0 && dot >= 0 -> {
                val decimalSeparator = if (comma > dot) ',' else '.'
                val groupingSeparator = if (decimalSeparator == ',') '.' else ','
                value.replace(groupingSeparator.toString(), "")
                    .replace(decimalSeparator, '.')
            }
            comma >= 0 -> value.replace(".", "").replace(',', '.')
            dot >= 0 -> {
                val decimals = value.length - dot - 1
                if (decimals in 1..2) value else value.replace(".", "")
            }
            else -> value
        }
        val amount = runCatching { BigDecimal(value) }.getOrNull() ?: return null
        if (amount.signum() < 0 || amount > BigDecimal("999999999.99") || amount.scale().coerceAtLeast(0) > 2) {
            return null
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
}
