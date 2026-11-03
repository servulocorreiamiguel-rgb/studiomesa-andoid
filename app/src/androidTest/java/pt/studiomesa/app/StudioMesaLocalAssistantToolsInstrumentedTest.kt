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
}
