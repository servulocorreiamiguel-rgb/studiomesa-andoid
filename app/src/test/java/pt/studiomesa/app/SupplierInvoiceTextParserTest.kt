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
    fun recognizesTotalIncludingVatWithoutMistakingItForTheVatAmount() {
        val draft = SupplierInvoiceTextParser.parse(
            """
                FORNECEDOR: Mercearia Central Lda
                FATURA FT/2026/000126
                Data da fatura: 07/11/2026
                Base tributável: 100,00 €
                IVA: 23,00 €
                Total com IVA: 123,00 €
            """.trimIndent()
        )

        assertEquals(BigDecimal("100.00"), draft.netAmount)
        assertEquals(BigDecimal("23.00"), draft.vatAmount)
        assertEquals(BigDecimal("123.00"), draft.totalAmount)
        assertNull(draft.arithmeticWarning())
    }

    @Test
    fun recognizesCompactTotalWithVatLabelAndIgnoresTaxTotalLine() {
        val draft = SupplierInvoiceTextParser.parse(
            """
                FORNECEDOR: Distribuidor Norte
                FATURA FT/2026/000127
                Data da fatura: 08/11/2026
                Base tributável: 50,00 €
                Total IVA: 11,50 €
                Total c/ IVA: 61,50 €
            """.trimIndent()
        )

        assertEquals(BigDecimal("50.00"), draft.netAmount)
        assertEquals(BigDecimal("11.50"), draft.vatAmount)
        assertEquals(BigDecimal("61.50"), draft.totalAmount)
        assertNull(draft.arithmeticWarning())
    }


    @Test
    fun extractsInvoiceProductRowsWithCodesQuantitiesUnitsAndTaxRates() {
        val draft = SupplierInvoiceTextParser.parse(
            """
                FORNECEDOR: Mercearia Central Lda
                FATURA FT/2026/000128
                Data da fatura: 09/11/2026
                Código Descrição Quantidade Un. Preço Total IVA
                100003 ARROZ CAROLINO 5,000 KG 1,20 6,00 6%
                100004 AZEITE VIRGEM EXTRA 2,000 LT 4,50 9,00 23%
                Base tributável: 15,00 €
                Total IVA: 2,43 €
                Total a pagar: 17,43 €
            """.trimIndent()
        )

        assertEquals(2, draft.lines.size)
        assertEquals("100003", draft.lines[0].productCode)
        assertEquals("ARROZ CAROLINO", draft.lines[0].description)
        assertEquals(BigDecimal("5.000"), draft.lines[0].quantity)
        assertEquals("kg", draft.lines[0].unit)
        assertEquals(BigDecimal("1.20"), draft.lines[0].unitPrice)
        assertEquals(BigDecimal("6.00"), draft.lines[0].netAmount)
        assertEquals(BigDecimal("6"), draft.lines[0].vatRate)
        assertEquals("AZEITE VIRGEM EXTRA", draft.lines[1].description)
        assertEquals(BigDecimal("2.000"), draft.lines[1].quantity)
        assertEquals("l", draft.lines[1].unit)
        assertEquals(BigDecimal("9.00"), draft.lines[1].netAmount)
    }

    @Test
    fun doesNotTreatInvoiceTotalsHeadersOrAmbiguousRowsAsProducts() {
        val draft = SupplierInvoiceTextParser.parse(
            """
                FORNECEDOR: Distribuidor Norte
                FATURA FT/2026/000129
                Data da fatura: 10/11/2026
                Descrição Quantidade Valor
                Subtotal 90,00 €
                Total IVA 20,00 €
                Total a pagar 110,00 €
                Transporte 4,50
                100003 ARROZ CAROLINO 5,000 KG 1,20
                100004 AZEITE 2,000 LT 4,50 9,00 23%
            """.trimIndent()
        )

        assertEquals(1, draft.lines.size)
        assertEquals("AZEITE", draft.lines.single().description)
        assertEquals(BigDecimal("9.00"), draft.lines.single().netAmount)
    }

    @Test
    fun rejectsNonPositiveAndUnboundedInvoiceLineQuantities() {
        val draft = SupplierInvoiceTextParser.parse(
            """
                FORNECEDOR: Distribuidor Norte
                FATURA FT/2026/000130
                Data da fatura: 11/11/2026
                100003 ARROZ CAROLINO 0,000 KG 1,20 0,00 6%
                100004 AZEITE 2,000 LT 4,50 9,00 23%
            """.trimIndent()
        )

        assertEquals(1, draft.lines.size)
        assertEquals("AZEITE", draft.lines.single().description)
    }


    @Test
    fun parsesOnlyPositiveStockQuantitiesWithAtMostThreeDecimals() {
        assertEquals(BigDecimal("1.500"), SupplierInvoiceTextParser.parseQuantity("1,500"))
        assertEquals(BigDecimal("12"), SupplierInvoiceTextParser.parseQuantity("12"))
        assertNull(SupplierInvoiceTextParser.parseQuantity("0"))
        assertNull(SupplierInvoiceTextParser.parseQuantity("-1"))
        assertNull(SupplierInvoiceTextParser.parseQuantity("1.5004"))
        assertNull(SupplierInvoiceTextParser.parseQuantity("1,2,3"))
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
