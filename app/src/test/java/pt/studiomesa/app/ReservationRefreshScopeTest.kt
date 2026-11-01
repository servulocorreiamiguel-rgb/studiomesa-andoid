package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationRefreshScopeTest {
    @Test
    fun restaurantRefreshAcceptsOnlyTheSameNonBlankRestaurantAndAccount() {
        assertTrue(
            ReservationRefreshScope.matchesRestaurant(
                "restaurant-a", "restaurant-a", "account-a", "account-a"
            )
        )
        assertFalse(
            ReservationRefreshScope.matchesRestaurant(
                "restaurant-a", "restaurant-b", "account-a", "account-a"
            )
        )
        assertFalse(
            ReservationRefreshScope.matchesRestaurant(
                "restaurant-a", "restaurant-a", "account-a", "account-b"
            )
        )
        assertFalse(
            ReservationRefreshScope.matchesRestaurant(
                "", "", "account-a", "account-a"
            )
        )
        assertFalse(
            ReservationRefreshScope.matchesRestaurant(
                "restaurant-a", "restaurant-a", "", ""
            )
        )
    }

    @Test
    fun customerRefreshRequiresSameCustomerMembershipAndAccount() {
        assertTrue(
            ReservationRefreshScope.matchesCustomer(
                true, "member-a", "member-a", "account-a", "account-a"
            )
        )
        assertFalse(
            ReservationRefreshScope.matchesCustomer(
                false, "member-a", "member-a", "account-a", "account-a"
            )
        )
        assertFalse(
            ReservationRefreshScope.matchesCustomer(
                true, "member-a", "member-b", "account-a", "account-a"
            )
        )
        assertFalse(
            ReservationRefreshScope.matchesCustomer(
                true, "member-a", "member-a", "account-a", "account-b"
            )
        )
        assertFalse(
            ReservationRefreshScope.matchesCustomer(
                true, "", "", "account-a", "account-a"
            )
        )
    }
}
