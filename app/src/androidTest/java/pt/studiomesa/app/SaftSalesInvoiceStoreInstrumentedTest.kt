package pt.studiomesa.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SaftSalesInvoiceStoreInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun importsAreDeduplicatedEncryptedScopedAndSafeAgainstStaleSyncAcknowledgements() {
        val store = SaftSalesInvoiceStore(context)
        val scope = "saft-test:" + UUID.randomUUID()
        val otherScope = "saft-test:" + UUID.randomUUID()
        val invoiceId = sha256("FT|FT-TEST-2026-001|2026-11-13")
        val firstInvoice = invoice(invoiceId, "FT-TEST-2026-001", "12.00", "14.76")
        val firstImport = parsed("original-export", firstInvoice)

        try {
            val initial = store.import(scope, firstImport)
            assertEquals(1, initial.importedInvoices)
            assertEquals(0, initial.updatedExistingInvoices)
            assertFalse(initial.repeatedFile)
            assertEquals(1, store.count(scope))
            assertEquals(0, store.count(otherScope))

            val repeated = store.import(scope, firstImport)
            assertTrue(repeated.repeatedFile)
            assertEquals(0, repeated.importedInvoices)
            assertEquals(1, store.count(scope))

            val staleBatch = store.pendingCloudRows(scope, limit = 10)
            assertEquals(1, staleBatch.size)
            val staleVersion = staleBatch.single().getLong("updated_at_ms")

            // A second export changes this same invoice. Its local version must be
            // strictly newer, even when the two imports share the same clock tick.
            val revisedInvoice = invoice(invoiceId, "FT-TEST-2026-001", "18.00", "22.14")
            val revised = store.import(scope, parsed("revised-export", revisedInvoice))
            assertEquals(0, revised.importedInvoices)
            assertEquals(1, revised.updatedExistingInvoices)
            val latestBatch = store.pendingCloudRows(scope, limit = 10)
            assertEquals(1, latestBatch.size)
            val latestVersion = latestBatch.single().getLong("updated_at_ms")
            assertTrue("A re-import must advance the row version", latestVersion > staleVersion)

            // Simulate the server acknowledging an earlier batch after the newer
            // local invoice has already replaced it. The newer invoice stays pending.
            store.markCloudRowsSynced(scope, staleBatch)
            val stillPending = store.pendingCloudRows(scope, limit = 10)
            assertEquals(1, stillPending.size)
            assertEquals(latestVersion, stillPending.single().getLong("updated_at_ms"))
            assertEquals("22.14", stillPending.single().getJSONObject("payload").getString("gross_total"))

            store.markCloudRowsSynced(scope, stillPending)
            assertTrue(store.pendingCloudRows(scope, limit = 10).isEmpty())

            // A strictly newer server copy is merged, while an older copy cannot
            // overwrite that version on this device.
            val serverVersion = latestVersion + 10_000L
            val serverInvoice = invoice(invoiceId, "FT-TEST-2026-001", "30.00", "36.90")
            assertEquals(1, store.mergeCloudRows(scope, listOf(remoteRow(serverInvoice, serverVersion))))
            assertEquals(BigDecimal("36.90"), store.list(scope).single().grossTotal)
            val staleServerInvoice = invoice(invoiceId, "FT-TEST-2026-001", "5.00", "6.15")
            assertEquals(0, store.mergeCloudRows(scope, listOf(remoteRow(staleServerInvoice, serverVersion - 1L))))
            assertEquals(BigDecimal("36.90"), store.list(scope).single().grossTotal)

            // The database stores ciphertext, not the invoice number or product data.
            val database = context.openOrCreateDatabase(
                "studiomesa_saft_sales_v1.db",
                Context.MODE_PRIVATE,
                null
            )
            try {
                database.rawQuery(
                    "SELECT encrypted_payload FROM sales_invoices WHERE scope_key = ? AND invoice_id = ?",
                    arrayOf(scopeHash(scope), invoiceId)
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    val encrypted = cursor.getString(0)
                    assertFalse(encrypted.contains("FT-TEST-2026-001"))
                    assertFalse(encrypted.contains("Prato de teste"))
                }
            } finally {
                database.close()
            }

            store.clearScope(scope)
            assertEquals(0, store.count(scope))
            assertTrue(store.list(scope).isEmpty())
            assertTrue(store.pendingCloudRows(scope, limit = 10).isEmpty())

            // Deletion also removes file fingerprints, so a restored/new account
            // scope is not blocked by stale local deduplication state.
            val afterClear = store.import(scope, firstImport)
            assertFalse(afterClear.repeatedFile)
            assertEquals(1, afterClear.importedInvoices)
        } finally {
            runCatching { store.clearScope(scope) }
            runCatching { store.clearScope(otherScope) }
            store.close()
        }
    }

    private fun parsed(fileName: String, invoice: SaftSalesInvoice) = SaftSalesImport(
        fileSha256 = sha256(fileName),
        version = "1.04_01",
        acceptedInvoices = listOf(invoice),
        excludedVoided = 0,
        excludedReplaced = 0,
        excludedSummary = 0,
        excludedOtherStates = 0,
        skippedInvalid = 0
    )

    private fun invoice(
        id: String,
        number: String,
        net: String,
        gross: String
    ) = SaftSalesInvoice(
        id = id,
        invoiceNumber = number,
        issueDate = "2026-11-13",
        invoiceType = "FT",
        invoiceStatus = "N",
        netTotal = BigDecimal(net),
        grossTotal = BigDecimal(gross),
        lines = listOf(
            SaftSalesInvoiceLine(
                productCode = "PRATO-001",
                description = "Prato de teste",
                quantity = BigDecimal("2"),
                netAmount = BigDecimal(net)
            )
        )
    )

    private fun remoteRow(invoice: SaftSalesInvoice, updatedAt: Long) = JSONObject()
        .put("invoice_id", invoice.id)
        .put("issue_date", invoice.issueDate)
        .put("updated_at_ms", updatedAt)
        .put(
            "payload",
            JSONObject()
                .put("id", invoice.id)
                .put("invoice_number", invoice.invoiceNumber)
                .put("issue_date", invoice.issueDate)
                .put("invoice_type", invoice.invoiceType)
                .put("invoice_status", invoice.invoiceStatus)
                .put("net_total", invoice.netTotal.toPlainString())
                .put("gross_total", invoice.grossTotal.toPlainString())
                .put("lines", JSONArray().put(
                    JSONObject()
                        .put("product_code", "PRATO-001")
                        .put("description", "Prato de teste")
                        .put("quantity", "2")
                        .put("net_amount", invoice.netTotal.toPlainString())
                ))
        )

    private fun scopeHash(value: String): String = sha256(value)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
