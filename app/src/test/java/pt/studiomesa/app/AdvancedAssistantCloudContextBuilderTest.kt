package pt.studiomesa.app

import java.math.BigDecimal
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedAssistantCloudContextBuilderTest {
    @Test
    fun exportsOnlyRestaurantAggregatesAndExcludesInvoiceAndCustomerData() {
        val snapshot = StudioMesaAssistantSnapshot(
            isDemo = false,
            restaurantConfigured = true,
            menuItems = listOf(
                AssistantMenuItem("Hambúrguer", BigDecimal("12.50"), BigDecimal("3.20"), "Burgers"),
                AssistantMenuItem("Sopa", BigDecimal("4.00"), BigDecimal.ZERO, "Entradas")
            ),
            activeOrderCount = 4,
            preparationOrderCount = 2,
            stockFeatureAvailable = true,
            stockLoaded = true,
            stockItems = listOf(
                AssistantStockItem("Pão", BigDecimal("18"), BigDecimal("5"), "un", BigDecimal("0.30"))
            ),
            reservationsFeatureAvailable = true,
            reservationsLoaded = true,
            pendingReservations = 3,
            salesHistory = listOf(
                SalesAnalysisOrder(Instant.parse("2026-11-08T12:00:00Z").toEpochMilli(), "Concluído", 12.50, 2),
                SalesAnalysisOrder(Instant.parse("2026-11-09T12:00:00Z").toEpochMilli(), "Concluído", 4.00, 1)
            )
        )

        val context = AdvancedAssistantCloudContextBuilder.build(
            snapshot,
            Instant.parse("2026-11-10T12:00:00Z").toEpochMilli()
        )

        assertEquals(2, context.getJSONArray("menu_items").length())
        assertEquals(4, context.getInt("active_orders"))
        assertEquals(1, context.getJSONArray("stock_items").length())
        assertEquals(3, context.getInt("pending_reservations_count"))
        assertTrue(context.getJSONObject("sales").has("weekday_profile"))
        assertTrue(context.getJSONObject("sales").has("next_seven_days_forecast"))
        assertFalse(context.has("supplier_invoices"))
        assertFalse(context.has("invoice_documents"))
        assertFalse(context.has("customer_records"))
        assertFalse(context.has("staff_records"))
        assertFalse(context.has("reservation_contacts"))
        assertFalse(context.has("conversation_memory"))
    }

    @Test
    fun doesNotSendStockWhenTheLocalSnapshotIsNotFresh() {
        val context = AdvancedAssistantCloudContextBuilder.build(
            StudioMesaAssistantSnapshot(
                isDemo = false,
                restaurantConfigured = true,
                menuItems = emptyList(),
                stockFeatureAvailable = true,
                stockLoaded = false,
                stockItems = listOf(
                    AssistantStockItem("Pão", BigDecimal("10"), BigDecimal("3"))
                )
            ),
            Instant.parse("2026-11-10T12:00:00Z").toEpochMilli()
        )

        assertFalse(context.getBoolean("stock_loaded"))
        assertEquals(JSONObject.NULL, context.get("stock_items"))
    }

    @Test
    fun sendsNoRawOrderLinesOrSupplierNames() {
        val context = AdvancedAssistantCloudContextBuilder.build(
            StudioMesaAssistantSnapshot(
                isDemo = false,
                restaurantConfigured = true,
                menuItems = emptyList(),
                salesHistory = listOf(
                    SalesAnalysisOrder(Instant.parse("2026-11-09T12:00:00Z").toEpochMilli(), "Concluído", 8.0, 1)
                )
            ),
            Instant.parse("2026-11-10T12:00:00Z").toEpochMilli()
        )

        assertFalse(context.has("sales_lines"))
        assertFalse(context.has("supplier_invoices"))
        assertTrue(context.getJSONObject("sales").getBoolean("values_are_nominal_order_values_not_confirmed_payments"))
    }
}
