package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCsvExporterTest {
    @Test
    fun exportsPortugueseHeadersAndDecimalCommasWithUtf8Bom() {
        val csv = ReportCsvExporter.toCsv(
            listOf(
                OrderReportRecord(
                    orderId = "ord-1",
                    createdAt = "2026-10-23 12:30:00",
                    tableNumber = 4,
                    itemName = "Bife da casa",
                    quantity = 2,
                    unitPrice = BigDecimal("12.50"),
                    status = "Concluído"
                )
            )
        )

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"Preço unitário (€)\""))
        assertTrue(csv.contains("\"Bife da casa\""))
        assertTrue(csv.contains("2;12,50;25,00;\"Concluído\""))
    }

    @Test
    fun neutralizesSpreadsheetFormulaInjectionInTextFields() {
        val csv = ReportCsvExporter.toCsv(
            listOf(
                OrderReportRecord(
                    orderId = "ord-2",
                    createdAt = "",
                    tableNumber = 1,
                    itemName = " =HYPERLINK(\"https://example.invalid\")",
                    quantity = 1,
                    unitPrice = BigDecimal("1.00"),
                    status = "+RUN"
                )
            )
        )

        assertTrue(csv.contains("\"' =HYPERLINK(\"\"https://example.invalid\"\")\""))
        assertTrue(csv.contains(";\"'+RUN\""))
    }

    @Test
    fun escapesQuotesAndKeepsEachRecordOnOneLine() {
        val csv = ReportCsvExporter.toCsv(
            listOf(
                OrderReportRecord(
                    orderId = "ord-3",
                    createdAt = "",
                    tableNumber = 2,
                    itemName = "Hambúrguer \"Especial\"\nsem cebola",
                    quantity = 1,
                    unitPrice = BigDecimal("9.99"),
                    status = "Aberto"
                )
            )
        )

        assertTrue(csv.contains("\"Hambúrguer \"\"Especial\"\" sem cebola\""))
        assertEquals(2, csv.trimEnd().split("\n").size)
    }

    @Test
    fun exportsHeaderForEmptyReportWithoutAddingUnrequestedData() {
        val csv = ReportCsvExporter.toCsv(emptyList())

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"ID do pedido\""))
        assertEquals(1, csv.trimEnd().split("\n").size)
    }
}
