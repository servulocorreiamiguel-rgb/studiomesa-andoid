package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRefreshPolicyTest {
    @Test
    fun automaticRefreshRunsOnlyOnForegroundPremiumRestaurantStockScreen() {
        assertTrue(
            InventoryRefreshPolicy.shouldScheduleAutoRefresh(
                activityResumed = true,
                isCustomerApp = false,
                isPremium = true,
                activePage = "Stock",
                hasRemoteRestaurant = true
            )
        )

        val blockedStates = listOf(
            listOf(false, false, true, "Stock", true),
            listOf(true, true, true, "Stock", true),
            listOf(true, false, false, "Stock", true),
            listOf(true, false, true, "Início", true),
            listOf(true, false, true, "Stock", false)
        )
        blockedStates.forEach { state ->
            assertFalse(
                InventoryRefreshPolicy.shouldScheduleAutoRefresh(
                    activityResumed = state[0] as Boolean,
                    isCustomerApp = state[1] as Boolean,
                    isPremium = state[2] as Boolean,
                    activePage = state[3] as String,
                    hasRemoteRestaurant = state[4] as Boolean
                )
            )
        }
    }

    @Test
    fun refreshDoesNotOverlapAnExistingInventoryRequest() {
        val common = mapOf(
            "activityResumed" to true,
            "isCustomerApp" to false,
            "isPremium" to true,
            "activePage" to "Stock",
            "hasRemoteRestaurant" to true
        )
        assertTrue(
            InventoryRefreshPolicy.mayStartRefresh(
                activityResumed = common["activityResumed"] as Boolean,
                isCustomerApp = common["isCustomerApp"] as Boolean,
                isPremium = common["isPremium"] as Boolean,
                activePage = common["activePage"] as String,
                hasRemoteRestaurant = common["hasRemoteRestaurant"] as Boolean,
                refreshInFlight = false
            )
        )
        assertFalse(
            InventoryRefreshPolicy.mayStartRefresh(
                activityResumed = true,
                isCustomerApp = false,
                isPremium = true,
                activePage = "Stock",
                hasRemoteRestaurant = true,
                refreshInFlight = true
            )
        )
        assertEquals(30_000L, InventoryRefreshPolicy.REFRESH_INTERVAL_MILLIS)
    }
}
