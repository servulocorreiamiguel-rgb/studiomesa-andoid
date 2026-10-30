package pt.studiomesa.app

import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class KitchenQueueRefreshPolicyTest {
    @Test
    fun automaticRefreshRunsOnlyOnForegroundRestaurantKitchenScreen() {
        assertTrue(
            KitchenQueueRefreshPolicy.shouldScheduleAutoRefresh(
                activityResumed = true,
                isCustomerApp = false,
                activePage = "Cozinha",
                hasRemoteRestaurant = true
            )
        )

        val blockedStates = listOf(
            listOf(false, false, "Cozinha", true),
            listOf(true, true, "Cozinha", true),
            listOf(true, false, "Pedidos", true),
            listOf(true, false, "Cozinha", false)
        )
        blockedStates.forEach { state ->
            assertFalse(
                KitchenQueueRefreshPolicy.shouldScheduleAutoRefresh(
                    activityResumed = state[0] as Boolean,
                    isCustomerApp = state[1] as Boolean,
                    activePage = state[2] as String,
                    hasRemoteRestaurant = state[3] as Boolean
                )
            )
        }
    }

    @Test
    fun refreshCannotOverlapAnotherKitchenRequest() {
        assertTrue(
            KitchenQueueRefreshPolicy.mayStartRefresh(
                activityResumed = true,
                isCustomerApp = false,
                activePage = "Cozinha",
                hasRemoteRestaurant = true,
                refreshInFlight = false
            )
        )
        assertFalse(
            KitchenQueueRefreshPolicy.mayStartRefresh(
                activityResumed = true,
                isCustomerApp = false,
                activePage = "Cozinha",
                hasRemoteRestaurant = true,
                refreshInFlight = true
            )
        )
        assertEquals(15_000L, KitchenQueueRefreshPolicy.REFRESH_INTERVAL_MILLIS)
    }

    @Test
    fun kitchenQueryRequestsOnlyActiveStatusesAndPaginatesDeterministically() {
        val path = KitchenQueueQuery.path("restaurant-123", offset = 500, limit = 500)
        assertTrue(path.contains("restaurant_id=eq.restaurant-123"))
        assertTrue(path.contains("order=created_at.asc,id.asc"))
        assertTrue(path.endsWith("limit=500&offset=500"))

        val encodedFilter = path.substringAfter("&status=").substringBefore("&order=")
        assertEquals(
            "in.(\"Aberto\",\"Em preparação\",\"Pronto\")",
            URLDecoder.decode(encodedFilter, "UTF-8")
        )
        assertTrue(path.contains("select=id,table_number,item_name,amount,quantity"))
    }

    @Test
    fun kitchenQueryRejectsInvalidRestaurantAndPageBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            KitchenQueueQuery.path("", offset = 0, limit = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KitchenQueueQuery.path("restaurant-123", offset = -1, limit = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KitchenQueueQuery.path("restaurant-123", offset = 0, limit = 501)
        }
    }
}
