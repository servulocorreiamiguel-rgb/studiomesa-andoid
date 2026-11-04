package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoyaltySessionScopeTest {
    @Test
    fun responseCanUpdateTheSameAccountAndSameMembership() {
        assertTrue(LoyaltySessionScope.matches("account-a", "account-a", "member-a", "member-a"))
        assertTrue(LoyaltySessionScope.matches("account-a", "account-a", "restaurant-a", "restaurant-a"))
    }

    @Test
    fun responseCannotCrossAccountsEvenWhenTenantIsTheSame() {
        assertFalse(LoyaltySessionScope.matches("account-a", "account-b", "restaurant-a", "restaurant-a"))
    }

    @Test
    fun responseCannotCrossCustomerCardsOrRestaurants() {
        assertFalse(LoyaltySessionScope.matches("account-a", "account-a", "member-a", "member-b"))
        assertFalse(LoyaltySessionScope.matches("account-a", "account-a", "restaurant-a", "restaurant-b"))
    }

    @Test
    fun missingContextNeverMatches() {
        assertFalse(LoyaltySessionScope.matches(null, "account-a", "member-a", "member-a"))
        assertFalse(LoyaltySessionScope.matches("account-a", null, "member-a", "member-a"))
        assertFalse(LoyaltySessionScope.matches("account-a", "account-a", null, "member-a"))
        assertFalse(LoyaltySessionScope.matches("account-a", "account-a", "member-a", ""))
    }
}
