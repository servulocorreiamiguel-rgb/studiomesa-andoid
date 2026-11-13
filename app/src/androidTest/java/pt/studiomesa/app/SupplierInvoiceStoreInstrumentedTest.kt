package pt.studiomesa.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupplierInvoiceStoreInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun invoiceMetadataPersistsEncryptedAndRemainsAccountScoped() {
        val store = SupplierInvoiceStore(context)
        val scope = "invoice-test:" + UUID.randomUUID()
        val otherScope = "invoice-test:" + UUID.randomUUID()
        val invoice = SupplierInvoiceRecord(
            id = UUID.randomUUID().toString(),
            supplierName = "Fornecedor de teste",
            supplierVatNumber = "501234560",
            invoiceNumber = "FT/2026/900",
            issueDate = "2026-11-05",
            dueDate = "",
            netAmount = "100.00",
            vatAmount = "23.00",
            totalAmount = "123.00",
            currency = "EUR",
            mimeType = "application/pdf",
            byteSize = 2048,
            documentSha256 = "a".repeat(64),
            documentUri = "content://test/invoice.pdf",
            documentPath = null,
            sourceType = "pdf_ocr",
            reviewStatus = "pending_review",
            warnings = listOf("Confirma o NIF."),
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_000_000_000L,
            lineItems = listOf(
                SupplierInvoiceLineRecord(
                    id = "a".repeat(64),
                    productCode = "FL001",
                    description = "Farinha",
                    quantity = "2.000",
                    unit = "kg",
                    unitPrice = "10.00",
                    netAmount = "20.00",
                    vatRate = "23",
                    inventoryItemId = UUID.randomUUID().toString()
                )
            ),
            stockReceivedAtMillis = 1_700_000_100_000L,
            stockReceiptRequestId = UUID.randomUUID().toString()
        )
        try {
            store.save(scope, invoice)
            val reopened = SupplierInvoiceStore(context)
            assertEquals(invoice, reopened.get(scope, invoice.id))
            val storedValues = context.getSharedPreferences(
                "studiomesa_supplier_invoice_index_v1",
                Context.MODE_PRIVATE
            ).all.values.filterIsInstance<String>()
            assertFalse(storedValues.any { value ->
                value.contains("FT/2026/900") ||
                    value.contains("Fornecedor de teste") ||
                    value.contains("Farinha")
            })
            assertTrue(reopened.list(otherScope).isEmpty())
            assertNull(reopened.get(otherScope, invoice.id))
            assertEquals(invoice, reopened.findDuplicate(
                scope,
                "Fornecedor de teste",
                "501234560",
                "FT/2026/900",
                "2026-11-05"
            ))
            reopened.delete(scope, invoice.id)
            assertTrue(reopened.list(scope).isEmpty())
        } finally {
            runCatching { store.clearScope(scope) }
            runCatching { store.clearScope(otherScope) }
        }
    }
}
