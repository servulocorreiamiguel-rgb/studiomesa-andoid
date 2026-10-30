package pt.studiomesa.app

import java.math.BigDecimal

/**
 * Verifies an order retry without treating client-cached menu values as authoritative.
 *
 * For linked orders, PostgreSQL owns the name, selling price and cost snapshots. A retry
 * after a lost HTTP response must accept those server values even when the menu cache on
 * the Android device has become stale. Legacy orders without a menu UUID still use the
 * exact values submitted by the client because no server-side menu lookup is performed.
 */
internal object OrderCreateRetryMatcher {
    fun matches(
        requestedMenuItemId: String?,
        savedMenuItemId: String?,
        requestedItemName: String,
        savedItemName: String,
        requestedAmount: Double,
        savedAmount: Double,
        requestedUnitCost: BigDecimal?,
        savedUnitCost: BigDecimal?,
        savedUnitCostIsNull: Boolean
    ): Boolean {
        val menuItemId = requestedMenuItemId?.takeIf { it.isNotBlank() }
        if (menuItemId != null) {
            return savedMenuItemId == menuItemId &&
                savedItemName.isNotBlank() &&
                savedAmount.isFinite() &&
                savedAmount >= 0.0 &&
                (savedUnitCostIsNull ||
                    (savedUnitCost != null && savedUnitCost.signum() >= 0))
        }

        // Preserve the exact-match contract for legacy, unlinked orders.
        return savedMenuItemId.isNullOrBlank() &&
            requestedUnitCost == null &&
            savedUnitCostIsNull &&
            savedUnitCost == null &&
            requestedItemName == savedItemName &&
            savedAmount.isFinite() &&
            kotlin.math.abs(savedAmount - requestedAmount) < 0.000001
    }
}
