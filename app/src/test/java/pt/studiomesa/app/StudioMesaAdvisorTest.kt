package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class StudioMesaAdvisorTest {
    private fun item(name: String, price: String, cost: String) = AssistantMenuItem(name, BigDecimal(price), BigDecimal(cost))
    private fun snapshot(
        menu: List<AssistantMenuItem> = listOf(item("Sopa", "5.00", "2.00")),
        demo: Boolean = false, configured: Boolean = true, activeOrders: Int = 0,
        stockAvailable: Boolean = false, stockLoaded: Boolean = false,
        stockItems: List<AssistantStockItem> = emptyList(),
        reservationsAvailable: Boolean = false, reservationsLoaded: Boolean = false, pendingReservations: Int = 0
    ) = StudioMesaAssistantSnapshot(
        isDemo = demo, restaurantConfigured = configured, menuItems = menu,
        activeOrderCount = activeOrders, preparationOrderCount = activeOrders.coerceAtMost(1),
        stockFeatureAvailable = stockAvailable, stockLoaded = stockLoaded, stockItems = stockItems,
        reservationsFeatureAvailable = reservationsAvailable, reservationsLoaded = reservationsLoaded,
        pendingReservations = pendingReservations
    )

    @Test fun suggestedSellingPriceUsesChosenCostShareAndRoundsToCents() {
        assertEquals(BigDecimal("10.67"), StudioMesaAdvisor.recommendedSellingPrice(BigDecimal("3.20"), BigDecimal("30")))
    }
    @Test fun refusesToSuggestPriceForInvalidCostOrTarget() {
        assertNull(StudioMesaAdvisor.recommendedSellingPrice(BigDecimal.ZERO))
        assertNull(StudioMesaAdvisor.recommendedSellingPrice(BigDecimal("3.20"), BigDecimal.ZERO))
        assertNull(StudioMesaAdvisor.recommendedSellingPrice(BigDecimal("3.20"), BigDecimal("101")))
    }
    @Test fun flagsMenuItemsAboveReferenceCostShare() {
        val insight = StudioMesaAdvisor.recommendations(snapshot(menu = listOf(item("Prato", "10.00", "4.00")))).first { it.id.startsWith("price-") }
        assertTrue(insight.detail.contains("40,0%"))
        assertTrue(insight.detail.contains("13,33"))
        assertEquals(AssistantPriority.URGENT, insight.priority)
    }
    @Test fun missingCostNeverProducesAnInventedSuggestedPrice() {
        val data = snapshot(menu = listOf(item("Prato", "10.00", "0")))
        assertTrue(StudioMesaAdvisor.recommendations(data).any { it.id == "missing-costs" })
        assertTrue(StudioMesaAdvisor.answerQuestion("Que preço devo cobrar?", data).response.contains("Não vou inventar"))
    }
    @Test fun flagsStockOnlyAfterTheStockListWasLoaded() {
        val stock = AssistantStockItem("Arroz", BigDecimal("1"), BigDecimal("3"))
        assertFalse(StudioMesaAdvisor.recommendations(snapshot(stockAvailable = true, stockItems = listOf(stock))).any { it.title.contains("Arroz") })
        assertTrue(StudioMesaAdvisor.recommendations(snapshot(stockAvailable = true, stockLoaded = true, stockItems = listOf(stock))).any { it.title.contains("Arroz") })
    }
    @Test fun questionIntentFindsPricingAndOrderGuidance() {
        val data = snapshot(menu = listOf(item("Prato", "10.00", "4.00")), activeOrders = 4)
        assertEquals("Análise de preços", StudioMesaAdvisor.answerQuestion("Como melhoro o preço?", data).title)
        assertEquals("Análise de pedidos", StudioMesaAdvisor.answerQuestion("Como está a fila da cozinha?", data).title)
    }
    @Test fun problemResponseDoesNotPretendToTestConnectivity() {
        assertTrue(StudioMesaAdvisor.answerQuestion("A app está com erro de ligação?", snapshot()).response.contains("Não consigo testar a ligação à Internet"))
    }
    @Test fun labelsDemoModeClearly() {
        val demo = StudioMesaAdvisor.recommendations(snapshot(demo = true, configured = false)).first { it.id == "demo-mode" }
        assertTrue(demo.detail.contains("demonstração"))
    }
    @Test fun onlyReportsPendingReservationsWhenListWasLoaded() {
        val notLoaded = StudioMesaAdvisor.recommendations(snapshot(reservationsAvailable = true, pendingReservations = 3))
        assertFalse(notLoaded.any { it.id == "pending-reservations" })
        val loaded = StudioMesaAdvisor.recommendations(snapshot(reservationsAvailable = true, reservationsLoaded = true, pendingReservations = 3))
        assertTrue(loaded.any { it.id == "pending-reservations" })
    }
}
