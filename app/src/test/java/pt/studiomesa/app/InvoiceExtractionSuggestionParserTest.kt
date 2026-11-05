package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceExtractionSuggestionParserTest {
    @Test
    fun extractsPortugueseInvoiceFieldsAsUnconfirmedSuggestions() {
        val result = InvoiceExtractionSuggestionParser.parse(
            """
            Emitente: Distribuidor Alimentar, Lda.
            N.º de fatura: FT 2026/104
            Data do documento: 03/11/2026
            Base tributável: 100,00 €
            IVA: 23,00 €
            Total a pagar: 123,00 €
            """.trimIndent()
        )

        assertEquals("Distribuidor Alimentar, Lda", result.supplier?.value)
        assertEquals("FT 2026/104", result.documentNumber?.value)
        assertEquals("2026-11-03", result.invoiceDate?.value)
        assertEquals("100.00", result.netAmount?.value)
        assertEquals("23.00", result.taxAmount?.value)
        assertEquals("123.00", result.totalAmount?.value)
        assertTrue(result.supplier!!.confidence in 0..100)
        assertTrue(result.netAmount!!.evidence.contains("Base tributável"))
        assertFalse(result.isEmpty())
    }

    @Test
    fun recognizesCreditNotesButDoesNotInventMissingFiscalValues() {
        val result = InvoiceExtractionSuggestionParser.parse(
            """
            Nota de crédito
            Emitente: Fornecedor Exemplo
            Documento: NC 2026/5
            Data de emissão: 2026-11-02
            Observações: devolução de mercadoria
            """.trimIndent()
        )
        assertEquals("credit_note", result.invoiceType?.value)
        assertEquals("Fornecedor Exemplo", result.supplier?.value)
        assertEquals("2026-11-02", result.invoiceDate?.value)
        assertNull(result.netAmount)
        assertNull(result.taxAmount)
        assertNull(result.totalAmount)
    }

    @Test
    fun supportsXmlExtractorFieldNamesAndPortugueseNumberGrouping() {
        val result = InvoiceExtractionSuggestionParser.parse(
            """
            Supplier: Distribuidor Alimentar
            InvoiceNo: FT 2026/104
            InvoiceDate: 2026-11-03
            InvoiceNet: 1.234,56
            InvoiceTax: 283,95
            InvoiceTotal: 1.518,51
            """.trimIndent()
        )
        assertEquals("Distribuidor Alimentar", result.supplier?.value)
        assertEquals("FT 2026/104", result.documentNumber?.value)
        assertEquals("2026-11-03", result.invoiceDate?.value)
        assertEquals("1234.56", result.netAmount?.value)
        assertEquals("283.95", result.taxAmount?.value)
        assertEquals("1518.51", result.totalAmount?.value)
    }

    @Test
    fun doesNotTreatBareVatRateOrUnlabelledNumberAsAnInvoiceAmount() {
        val result = InvoiceExtractionSuggestionParser.parse(
            """
            IVA 23%
            123,00
            """.trimIndent()
        )
        assertNull(result.taxAmount)
        assertNull(result.totalAmount)
        assertNull(result.netAmount)
    }

    @Test
    fun invalidDatesAndImplausibleAmountsAreNotSuggested() {
        val result = InvoiceExtractionSuggestionParser.parse(
            """
            Data: 31/02/2026
            Base tributável: 1000000000,00 €
            Total a pagar: -123,00 €
            """.trimIndent()
        )
        assertNull(result.invoiceDate)
        assertNull(result.netAmount)
        assertNull(result.totalAmount)
    }
}
