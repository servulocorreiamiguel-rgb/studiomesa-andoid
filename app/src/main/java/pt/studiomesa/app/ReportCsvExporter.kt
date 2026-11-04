package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * CSV export for operational order reports.
 *
 * Uses semicolon delimiters and decimal commas for Portuguese spreadsheet locales.
 * Text fields are quoted and neutralised against spreadsheet formula injection.
 * This is an operational report, not an accounting or payment statement.
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
            require(record.tableNumber > 0) { "O número da mesa tem de ser positivo." }
            require(record.quantity in 1..99) { "A quantidade do pedido está fora do intervalo suportado." }
            require(record.unitPrice >= BigDecimal.ZERO) { "O preço unitário não pode ser negativo." }
            val total = record.unitPrice.multiply(BigDecimal.valueOf(record.quantity.toLong()))
            appendLine(
                listOf(
                    textCell(record.orderId),
                    textCell(record.createdAt),
                    record.tableNumber.toString(),
                    textCell(record.itemName),
                    record.quantity.toString(),
                    moneyCell(record.unitPrice),
                    moneyCell(total),
                    textCell(record.status)
                ).joinToString(";")
            )
        }
    }

    private fun moneyCell(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

    private fun textCell(raw: String): String {
        val clean = raw
            .replace("\u0000", "")
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
        val firstMeaningful = clean.trimStart().firstOrNull()
        val safe = if (firstMeaningful != null && firstMeaningful in setOf('=', '+', '-', '@')) "'$clean" else clean
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
