package pt.studiomesa.app

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class SupplierInvoiceTextParserTest {
    @Test
    fun extractsPortugueseInvoiceFieldsAndAmounts() {
        val text = """
            SUPPLIER: Mercearia Central Lda
            NIF: 501234560
            FATURA FT/2026/000123
            Data da fatura: 05/11/2026
            Data de vencimento: 20/11/2026
            Base tributável: 100,00 €
            Total IVA: 23,00 €
            Total a pagar: 123,00 €
        """.trimIndent()
        val draft = SupplierInvoiceTextParser.parse(text)
        assertEquals("Mercearia Central Lda", draft.supplierName)
        assertEquals("501234560", draft.supplierVatNumber)
        assertEquals("FT/2026/000123", draft.invoiceNumber)
        assertEquals("2026-11-05", draft.issueDate)
        assertEquals("2026-11-20", draft.dueDate)
        assertEquals(BigDecimal("100.00"), draft.netAmount)
        assertEquals(BigDecimal("23.00"), draft.vatAmount)
        assertEquals(BigDecimal("123.00"), draft.totalAmount)
        assertTrue(draft.isReviewable)
        assertNull(draft.arithmeticWarning())
    }

    @Test
    fun parsesEuropeanAndInternationalAmountSeparators() {
        assertEquals(BigDecimal("1234.56"), SupplierInvoiceTextParser.parseAmount("1.234,56 €"))
        assertEquals(BigDecimal("1234.56"), SupplierInvoiceTextParser.parseAmount("1,234.56"))
        assertEquals(BigDecimal("1234.00"), SupplierInvoiceTextParser.parseAmount("1.234"))
        assertEquals(BigDecimal("12.50"), SupplierInvoiceTextParser.parseAmount("12,5"))
    }

    @Test
    fun doesNotConfuseDueDateWithIssueDateWhenDueDateAppearsFirst() {
        val draft = SupplierInvoiceTextParser.parse(
            """
                FORNECEDOR: Mercearia Central Lda
                FATURA FT/2026/000124
                Data de vencimento: 20/11/2026
                Data da fatura: 05/11/2026
                Base tributável: 100,00 €
                Total a pagar: 123,00 €
                Total IVA: 23,00 €
            """.trimIndent()
        )

        assertEquals("2026-11-05", draft.issueDate)
        assertEquals("2026-11-20", draft.dueDate)
        assertEquals(BigDecimal("123.00"), draft.totalAmount)
        assertEquals(BigDecimal("23.00"), draft.vatAmount)
        assertNull(draft.arithmeticWarning())
    }

    @Test
    fun genericTotalLabelDoesNotCaptureVatOrSubtotalLines() {
        val draft = SupplierInvoiceTextParser.parse(
            """
                FORNECEDOR: Distribuidor Norte
                FATURA FT/2026/000125
                Data da fatura: 06/11/2026
                Subtotal: 90,00 €
                Total a pagar: 110,00 €
                IVA total: 20,00 €
            """.trimIndent()
        )

        assertEquals(BigDecimal("110.00"), draft.totalAmount)
        assertEquals(BigDecimal("20.00"), draft.vatAmount)
        assertNull(draft.arithmeticWarning())
    }

    @Test
    fun rejectsInvalidPortugueseVatNumber() {
        assertTrue(SupplierInvoiceTextParser.isValidPortugueseVatNumber("501234560"))
        assertFalse(SupplierInvoiceTextParser.isValidPortugueseVatNumber("501234568"))
        assertFalse(SupplierInvoiceTextParser.isValidPortugueseVatNumber("123"))
    }

    @Test
    fun warnsWhenInvoiceArithmeticDoesNotMatch() {
        val draft = SupplierInvoiceDraft(netAmount = BigDecimal("100"), vatAmount = BigDecimal("23"), totalAmount = BigDecimal("125"))
        assertNotNull(draft.arithmeticWarning())
    }

    @Test
    fun doesNotMarkPartialExtractionAsReviewable() {
        val draft = SupplierInvoiceTextParser.parse("FATURA FT/22\nTotal a pagar: 12,00 €")
        assertFalse(draft.isReviewable)
        assertTrue(draft.warnings.isNotEmpty())
    }
}
