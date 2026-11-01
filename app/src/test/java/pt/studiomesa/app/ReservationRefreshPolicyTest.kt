package pt.studiomesa.app

import org.junit.Assert.*
import org.junit.Test

class ReservationRefreshPolicyTest {
    @Test
    fun automaticRefreshRunsOnlyOnForegroundRestaurantReservationsPage() {
        assertTrue(
            ReservationRefreshPolicy.shouldScheduleAutoRefresh(
                activityResumed = true,
                isCustomerApp = false,
                activePage = "Reservas",
                hasRemoteRestaurant = true
            )
        )
        val blockedStates = listOf(
            listOf(false, false, "Reservas", true),
            listOf(true, true, "Reservas", true),
            listOf(true, false, "Início", true),
            listOf(true, false, "Reservas", false)
        )
        blockedStates.forEach { state ->
            assertFalse(
                ReservationRefreshPolicy.shouldScheduleAutoRefresh(
                    activityResumed = state[0] as Boolean,
                    isCustomerApp = state[1] as Boolean,
                    activePage = state[2] as String,
                    hasRemoteRestaurant = state[3] as Boolean
                )
            )
        }
    }

    @Test
    fun reservationRefreshCannotOverlapAnotherRequest() {
        assertTrue(
            ReservationRefreshPolicy.mayStartRefresh(
                activityResumed = true,
                isCustomerApp = false,
                activePage = "Reservas",
                hasRemoteRestaurant = true,
                refreshInFlight = false
            )
        )
        assertFalse(
            ReservationRefreshPolicy.mayStartRefresh(
                activityResumed = true,
                isCustomerApp = false,
                activePage = "Reservas",
                hasRemoteRestaurant = true,
                refreshInFlight = true
            )
        )
        assertEquals(30_000L, ReservationRefreshPolicy.REFRESH_INTERVAL_MILLIS)
    }
}
