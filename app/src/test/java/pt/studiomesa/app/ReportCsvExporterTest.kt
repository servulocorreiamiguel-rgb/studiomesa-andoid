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
                OrderReportRecord("ord-1", "2026-11-03 12:30:00", 4, "Bife da casa", 2, BigDecimal("12.50"), "Concluído")
            )
        )
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"Preço unitário (€)\""))
        assertTrue(csv.contains("\"Bife da casa\""))
        assertTrue(csv.contains("2;12,50;25,00;\"Concluído\""))
    }

    @Test
    fun neutralisesSpreadsheetFormulaInjectionAndNormalisesControlCharacters() {
        val csv = ReportCsvExporter.toCsv(
            listOf(
                OrderReportRecord("=1+1", "", 1, "\t=HYPERLINK(\"https://example.invalid\")", 1, BigDecimal("1.00"), "+RUN")
            )
        )
        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"' =HYPERLINK(\"\"https://example.invalid\"\")\""))
        assertTrue(csv.contains(";\"'+RUN\""))
    }

    @Test
    fun escapesQuotesAndKeepsEachRecordOnOneLine() {
        val csv = ReportCsvExporter.toCsv(
            listOf(
                OrderReportRecord("ord-3", "", 2, "Hambúrguer \"Especial\"\nsem cebola", 1, BigDecimal("9.99"), "Aberto")
            )
        )
        assertTrue(csv.contains("\"Hambúrguer \"\"Especial\"\" sem cebola\""))
        assertEquals(2, csv.trimEnd().split("\n").size)
    }

    @Test
    fun exportsOnlyHeaderForAnEmptyReport() {
        val csv = ReportCsvExporter.toCsv(emptyList())
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"ID do pedido\""))
        assertEquals(1, csv.trimEnd().split("\n").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidQuantityInsteadOfSilentlyChangingTheReport() {
        ReportCsvExporter.toCsv(
            listOf(OrderReportRecord("ord-4", "", 1, "Prato", 0, BigDecimal("8.00"), "Concluído"))
        )
    }
}
