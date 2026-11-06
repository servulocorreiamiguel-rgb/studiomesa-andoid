package pt.studiomesa.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
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
            updatedAtMillis = 1_700_000_000_000L
        )
        try {
            store.save(scope, invoice)
            val reopened = SupplierInvoiceStore(context)
            assertEquals(invoice, reopened.get(scope, invoice.id))
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
