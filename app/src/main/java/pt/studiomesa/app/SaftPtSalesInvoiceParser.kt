package pt.studiomesa.app

import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate

internal data class SaftSalesInvoiceLine(
    val productCode: String,
    val description: String,
    /** Signed quantity; credit notes reduce both revenue and expected ingredient use. */
    val quantity: BigDecimal,
    val netAmount: BigDecimal
)

internal data class SaftSalesInvoice(
    val id: String,
    val invoiceNumber: String,
    val issueDate: String,
    val invoiceType: String,
    val invoiceStatus: String,
    val netTotal: BigDecimal,
    val grossTotal: BigDecimal,
    val lines: List<SaftSalesInvoiceLine>
)

internal data class SaftSalesImport(
    val fileSha256: String,
    val version: String,
    val acceptedInvoices: List<SaftSalesInvoice>,
    val excludedVoided: Int,
    val excludedReplaced: Int,
    val excludedSummary: Int,
    val excludedOtherStates: Int,
    val skippedInvalid: Int
)

/**
 * Reads Portuguese SAF-T (PT) sales invoices only.
 *
 * It deliberately ignores loyalty/customer-points data: an invoice is included based on
 * its SAF-T document status and type, whether or not the customer used a loyalty card.
 * SAF-T buyer purchase documents are not supplier bills; those remain in the OCR archive.
 */
internal object SaftPtSalesInvoiceParser {
    const val MAX_FILE_BYTES = 100 * 1024 * 1024
    private const val MAX_INVOICES = 100_000
    private const val MAX_LINES_PER_INVOICE = 2_000
    private val supportedVersions = setOf("1.03_01", "1.04_01")
    private val supportedInvoiceTypes = setOf("FT", "FS", "FR", "ND", "NC")

    fun parse(bytes: ByteArray): SaftSalesImport {
        require(bytes.isNotEmpty() && bytes.size <= MAX_FILE_BYTES) {
            "O SAF-T está vazio ou excede 100 MB."
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        parser.setInput(ByteArrayInputStream(bytes), null)

        var version = ""
        var inSalesInvoices = false
        var excludedVoided = 0
        var excludedReplaced = 0
        var excludedSummary = 0
        var excludedOther = 0
        var skippedInvalid = 0
        val invoices = linkedMapOf<String, SaftSalesInvoice>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (localName(parser)) {
                    "AuditFileVersion" -> version = readText(parser).trim()
                    "SalesInvoices" -> inSalesInvoices = true
                    "Invoice" -> if (inSalesInvoices) {
                        require(invoices.size < MAX_INVOICES) {
                            "O SAF-T contém demasiadas faturas para esta importação."
                        }
                        val parsed = parseInvoice(parser)
                        when (parsed.second) {
                            "accepted" -> {
                                val invoice = parsed.first
                                if (invoice != null) invoices.put(invoice.id, invoice)
                                else skippedInvalid++
                            }
                            "voided" -> excludedVoided++
                            "replaced" -> excludedReplaced++
                            "summary" -> excludedSummary++
                            "invalid" -> skippedInvalid++
                            else -> excludedOther++
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG && localName(parser) == "SalesInvoices") {
                inSalesInvoices = false
            }
            event = parser.next()
        }
        require(version in supportedVersions) {
            "Versão SAF-T não suportada: ${version.ifBlank { "não identificada" }}. São aceites 1.03_01 e 1.04_01."
        }
        require(invoices.isNotEmpty() || excludedVoided + excludedReplaced + excludedSummary + excludedOther + skippedInvalid > 0) {
            "O ficheiro não contém documentos de venda na secção SalesInvoices."
        }
        return SaftSalesImport(
            fileSha256 = digest,
            version = version,
            acceptedInvoices = invoices.values.sortedWith(compareBy({ it.issueDate }, { it.invoiceNumber })),
            excludedVoided = excludedVoided,
            excludedReplaced = excludedReplaced,
            excludedSummary = excludedSummary,
            excludedOtherStates = excludedOther,
            skippedInvalid = skippedInvalid
        )
    }

    private fun parseInvoice(parser: XmlPullParser): Pair<SaftSalesInvoice?, String> {
        var number = ""
        var date = ""
        var type = ""
        var status = ""
        var netTotal: BigDecimal? = null
        var grossTotal: BigDecimal? = null
        val lines = mutableListOf<SaftSalesInvoiceLine>()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_TAG && localName(parser) == "Invoice") break
            if (event != XmlPullParser.START_TAG) continue
            when (localName(parser)) {
                "DocumentStatus" -> status = parseDocumentStatus(parser)
                "InvoiceNo" -> number = readText(parser).trim()
                "InvoiceDate" -> date = readText(parser).trim()
                "InvoiceType" -> type = readText(parser).trim().uppercase()
                "Line" -> {
                    require(lines.size < MAX_LINES_PER_INVOICE) {
                        "Uma fatura contém demasiadas linhas."
                    }
                    parseLine(parser)?.let(lines::add)
                }
                "DocumentTotals" -> {
                    val totals = parseTotals(parser)
                    netTotal = totals.first ?: netTotal
                    grossTotal = totals.second ?: grossTotal
                }
            }
        }

        val state = when (status.uppercase()) {
            "N", "S" -> "accepted"
            "A" -> "voided"
            "F" -> "replaced"
            "R" -> "summary"
            else -> "other"
        }
        if (state != "accepted") return null to state
        if (number.isBlank() || type !in supportedInvoiceTypes || !validIsoDate(date) ||
            netTotal == null || grossTotal == null
        ) return null to "invalid"

        val sign = if (type == "NC") BigDecimal("-1") else BigDecimal.ONE
        val signedLines = lines.map { line ->
            line.copy(
                quantity = line.quantity.multiply(sign),
                netAmount = line.netAmount.multiply(sign).setScale(2, RoundingMode.HALF_UP)
            )
        }.filter { it.quantity.signum() != 0 && it.description.isNotBlank() }
        val identity = "$type|$number|$date"
        val id = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return SaftSalesInvoice(
            id = id,
            invoiceNumber = number.take(100),
            issueDate = date,
            invoiceType = type,
            invoiceStatus = status.uppercase(),
            netTotal = netTotal.multiply(sign).setScale(2, RoundingMode.HALF_UP),
            grossTotal = grossTotal.multiply(sign).setScale(2, RoundingMode.HALF_UP),
            lines = signedLines
        ) to "accepted"
    }

    private fun parseDocumentStatus(parser: XmlPullParser): String {
        var status = ""
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_TAG && localName(parser) == "DocumentStatus") return status
            if (event == XmlPullParser.START_TAG && localName(parser) == "InvoiceStatus") {
                status = readText(parser).trim()
            }
        }
    }

    private fun parseTotals(parser: XmlPullParser): Pair<BigDecimal?, BigDecimal?> {
        var net: BigDecimal? = null
        var gross: BigDecimal? = null
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_TAG && localName(parser) == "DocumentTotals") return net to gross
            if (event == XmlPullParser.START_TAG) {
                when (localName(parser)) {
                    "NetTotal" -> net = readText(parser).trim().toBigDecimalOrNull()
                    "GrossTotal" -> gross = readText(parser).trim().toBigDecimalOrNull()
                }
            }
        }
    }

    private fun parseLine(parser: XmlPullParser): SaftSalesInvoiceLine? {
        var productCode = ""
        var description = ""
        var quantity: BigDecimal? = null
        var unitPrice: BigDecimal? = null
        var debit: BigDecimal? = null
        var credit: BigDecimal? = null
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_TAG && localName(parser) == "Line") break
            if (event != XmlPullParser.START_TAG) continue
            when (localName(parser)) {
                "ProductCode" -> productCode = readText(parser).trim()
                "Description", "ProductDescription" -> {
                    val candidate = readText(parser).trim()
                    if (candidate.isNotBlank()) description = candidate
                }
                "Quantity" -> quantity = readText(parser).trim().toBigDecimalOrNull()
                "UnitPrice" -> unitPrice = readText(parser).trim().toBigDecimalOrNull()
                "DebitAmount" -> debit = readText(parser).trim().toBigDecimalOrNull()
                "CreditAmount" -> credit = readText(parser).trim().toBigDecimalOrNull()
            }
        }
        val qty = quantity ?: return null
        if (qty.signum() <= 0) return null
        val lineAmount = when {
            debit != null && credit != null -> debit.subtract(credit)
            debit != null -> debit
            credit != null -> credit
            unitPrice != null -> unitPrice.multiply(qty)
            else -> return null
        }
        if (description.isBlank()) description = productCode
        if (description.isBlank()) return null
        return SaftSalesInvoiceLine(
            productCode = productCode.take(100),
            description = description.take(200),
            quantity = qty,
            netAmount = lineAmount.setScale(2, RoundingMode.HALF_UP)
        )
    }

    private fun readText(parser: XmlPullParser): String = parser.nextText()

    private fun localName(parser: XmlPullParser): String =
        parser.name.substringAfter(':').substringAfterLast(':')

    private fun validIsoDate(raw: String): Boolean =
        runCatching { LocalDate.parse(raw) }.isSuccess
}
