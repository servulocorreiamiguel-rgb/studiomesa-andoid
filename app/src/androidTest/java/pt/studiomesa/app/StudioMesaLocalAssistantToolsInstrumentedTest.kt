package pt.studiomesa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioMesaLocalAssistantToolsInstrumentedTest {

    @Test
    fun priceReferenceToolCalculatesWithoutChangingTheMenu() {
        val item = AssistantMenuItem(
            name = "Bife da casa",
            sellingPrice = BigDecimal("10.00"),
            unitCost = BigDecimal("2.50")
        )
        val snapshot = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(item)
        )
        val tool = StudioMesaPriceReferenceTool(snapshot)
        val result = JSONObject(
            tool.execute(
                JSONObject()
                    .put("item_name", "Bife da casa")
                    .put("target_cost_percent", 25)
                    .toString()
            )
        )

        assertEquals("10.00", result.getString("calculated_reference_price_eur"))
        assertEquals("10.00", result.getString("current_selling_price_eur"))
        assertTrue(result.getBoolean("read_only"))
        assertEquals("10.00", snapshot.menuItems.single().sellingPrice.toPlainString())
    }

    @Test
    fun priceReferenceToolRejectsAmbiguousAndMissingCosts() {
        val ambiguous = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(
                AssistantMenuItem("Bife grelhado", BigDecimal("12"), BigDecimal("3")),
                AssistantMenuItem("Bife da casa", BigDecimal("14"), BigDecimal("4"))
            )
        )
        val ambiguousResult = JSONObject(
            StudioMesaPriceReferenceTool(ambiguous).execute(
                JSONObject().put("item_name", "Bife").put("target_cost_percent", 30).toString()
            )
        )
        assertTrue(ambiguousResult.has("error"))

        val missingCost = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(
                AssistantMenuItem("Sopa", BigDecimal("3.50"), BigDecimal.ZERO)
            )
        )
        val missingResult = JSONObject(
            StudioMesaPriceReferenceTool(missingCost).execute(
                JSONObject().put("item_name", "Sopa").put("target_cost_percent", 30).toString()
            )
        )
        assertTrue(missingResult.has("error"))
    }

    @Test
    fun stockToolNeverPretendsAnUnloadedSnapshotIsCurrent() {
        val snapshot = StudioMesaAssistantSnapshot(
            isDemo = false,
            restaurantConfigured = true,
            menuItems = emptyList(),
            stockFeatureAvailable = true,
            stockLoaded = false
        )
        val result = JSONObject(
            StudioMesaRestaurantDataTool(snapshot).execute(
                JSONObject().put("area", "stock").toString()
            )
        )

        assertFalse(result.getBoolean("loaded"))
        assertTrue(result.getString("message").contains("não está carregado"))
        assertTrue(result.getBoolean("read_only"))
    }
    @Test
    fun invoiceToolSummarisesOnlyImportedDocumentsAndNeverWrites() {
        val invoice = SupplierInvoiceRecord(
            id = "00000000-0000-0000-0000-000000000001",
            supplierName = "Mercearia Central",
            supplierVatNumber = "501234560",
            invoiceNumber = "FT/2026/001",
            issueDate = "2026-11-05",
            dueDate = "",
            netAmount = "100.00",
            vatAmount = "23.00",
            totalAmount = "123.00",
            currency = "EUR",
            mimeType = "application/pdf",
            byteSize = 1024,
            documentSha256 = "a".repeat(64),
            documentUri = null,
            documentPath = null,
            sourceType = "pdf_ocr",
            reviewStatus = "approved",
            warnings = emptyList(),
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_000_000_000L
        )
        val snapshot = StudioMesaAssistantSnapshot(
            isDemo = false,
            restaurantConfigured = true,
            menuItems = emptyList(),
            supplierInvoices = listOf(invoice)
        )
        val tool = StudioMesaSupplierInvoiceTool(snapshot)
        val result = JSONObject(
            tool.execute(JSONObject().put("status", "approved").toString())
        )
        assertEquals(1, result.getInt("count"))
        assertEquals("123.00", result.getString("gross_total_eur"))
        assertEquals(1, result.getInt("approved"))
        assertTrue(result.getBoolean("read_only"))
        assertTrue(result.getString("warning").contains("Não prova pagamento"))
        val filtered = JSONObject(
            tool.execute(JSONObject().put("supplier", "outro").toString())
        )
        assertEquals(0, filtered.getInt("count"))
    }

    @Test
    fun localToolsUseTheLatestSnapshotAfterEachChatTurn() {
        val firstSnapshot = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(
                AssistantMenuItem("Bife", BigDecimal("10.00"), BigDecimal("2.50"))
            )
        )
        val refreshedSnapshot = StudioMesaAssistantSnapshot(
            isDemo = true,
            restaurantConfigured = false,
            menuItems = listOf(
                AssistantMenuItem("Bife", BigDecimal("12.00"), BigDecimal("3.00"))
            )
        )

        val priceTool = StudioMesaPriceReferenceTool(firstSnapshot)
        val dataTool = StudioMesaRestaurantDataTool(firstSnapshot)
        priceTool.updateSnapshot(refreshedSnapshot)
        dataTool.updateSnapshot(refreshedSnapshot)

        val price = JSONObject(
            priceTool.execute(
                JSONObject()
                    .put("item_name", "Bife")
                    .put("target_cost_percent", 25)
                    .toString()
            )
        )
        val menu = JSONObject(
            dataTool.execute(
                JSONObject()
                    .put("area", "menu")
                    .toString()
            )
        ).getJSONArray("items").getJSONObject(0)

        assertEquals("12.00", price.getString("current_selling_price_eur"))
        assertEquals("3.00", price.getString("registered_unit_cost_eur"))
        assertEquals("12.00", menu.getString("selling_price_eur"))
    }

}
