package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerativeAssistantConsentPolicyTest {
    @Test
    fun consentMatchesOnlyTheSameOwnerAndRestaurant() {
        val consentedScope = GenerativeAssistantConsentPolicy.scope(
            userId = "owner-a",
            restaurantId = "restaurant-1"
        )
        assertNotNull(consentedScope)
        assertTrue(
            GenerativeAssistantConsentPolicy.matches(
                consentedScope,
                userId = "owner-a",
                restaurantId = "restaurant-1"
            )
        )
        assertFalse(
            GenerativeAssistantConsentPolicy.matches(
                consentedScope,
                userId = "owner-b",
                restaurantId = "restaurant-1"
            )
        )
        assertFalse(
            GenerativeAssistantConsentPolicy.matches(
                consentedScope,
                userId = "owner-a",
                restaurantId = "restaurant-2"
            )
        )
    }

    @Test
    fun consentCannotBeEnabledWithoutAnAuthenticatedOwnerAndRestaurantScope() {
        assertNull(GenerativeAssistantConsentPolicy.scope(null, "restaurant-1"))
        assertNull(GenerativeAssistantConsentPolicy.scope("", "restaurant-1"))
        assertNull(GenerativeAssistantConsentPolicy.scope("owner-a", null))
        assertNull(GenerativeAssistantConsentPolicy.scope("owner-a", "   "))
        assertFalse(GenerativeAssistantConsentPolicy.matches(null, "owner-a", "restaurant-1"))
        assertFalse(GenerativeAssistantConsentPolicy.matches("old-global-flag", null, "restaurant-1"))
    }

    @Test
    fun consentScopeIsStableWhenIdentifiersOnlyDifferBySurroundingWhitespace() {
        val original = GenerativeAssistantConsentPolicy.scope(" owner-a ", " restaurant-1 ")
        val normalized = GenerativeAssistantConsentPolicy.scope("owner-a", "restaurant-1")
        assertTrue(GenerativeAssistantConsentPolicy.matches(original, "owner-a", "restaurant-1"))
        assertTrue(GenerativeAssistantConsentPolicy.matches(normalized, " owner-a ", " restaurant-1 "))
    }
}
