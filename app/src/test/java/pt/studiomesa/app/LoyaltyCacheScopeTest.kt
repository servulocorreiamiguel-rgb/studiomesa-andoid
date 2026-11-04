package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoyaltyCacheScopeTest {
    @Test
    fun cacheIsVisibleToTheSameRestaurantOrCustomer() {
        assertTrue(LoyaltyCacheScope.belongsTo("same-id", "same-id"))
    }

    @Test
    fun cacheIsHiddenWhenTheRestaurantOrCustomerChanges() {
        assertFalse(LoyaltyCacheScope.belongsTo("restaurant-a", "restaurant-b"))
        assertFalse(LoyaltyCacheScope.belongsTo("member-a", "member-b"))
    }

    @Test
    fun missingOrBlankScopeNeverAuthorizesCachedData() {
        assertFalse(LoyaltyCacheScope.belongsTo(null, "active-id"))
        assertFalse(LoyaltyCacheScope.belongsTo("", "active-id"))
        assertFalse(LoyaltyCacheScope.belongsTo("cached-id", null))
        assertFalse(LoyaltyCacheScope.belongsTo("cached-id", ""))
    }
}
