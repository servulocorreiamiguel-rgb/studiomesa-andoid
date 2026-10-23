package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * CSV export for operational order reports.
 *
 * Uses a semicolon delimiter for Portuguese spreadsheet locales, UTF-8 BOM for
 * Excel compatibility, stable two-decimal money formatting, and formula-injection
 * protection for values originating from editable restaurant data.
 */
internal data class OrderReportRecord(
    val orderId: String,
    val createdAt: String,
    val tableNumber: Int,
    val itemName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val status: String
)

internal object ReportCsvExporter {
    private val headers = listOf(
        "ID do pedido",
        "Data",
        "Mesa",
        "Artigo",
        "Quantidade",
        "Preço unitário (€)",
        "Valor nominal (€)",
        "Estado"
    )

    fun toCsv(records: List<OrderReportRecord>): String = buildString {
        append('\uFEFF')
        appendLine(headers.joinToString(";") { textCell(it) })
        records.forEach { record ->
            val quantity = record.quantity
            val total = record.unitPrice.multiply(BigDecimal.valueOf(quantity.toLong()))
            appendLine(
                listOf(
                    textCell(record.orderId),
                    textCell(record.createdAt),
                    quantityCell(record.tableNumber),
                    textCell(record.itemName),
                    quantityCell(quantity),
                    moneyCell(record.unitPrice),
                    moneyCell(total),
                    textCell(record.status)
                ).joinToString(";")
            )
        }
    }

    private fun quantityCell(value: Int): String = value.toString()

    private fun moneyCell(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

    private fun textCell(raw: String): String {
        val clean = raw
            .replace("\u0000", "")
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
        val firstMeaningful = clean.trimStart().firstOrNull()
        val safe = if (firstMeaningful in setOf('=', '+', '-', '@')) "'$clean" else clean
        return "\"\${safe.replace("\"", "\"\"")}\""
    }
}
