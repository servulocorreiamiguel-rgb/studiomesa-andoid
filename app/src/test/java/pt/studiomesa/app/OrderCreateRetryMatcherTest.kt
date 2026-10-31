package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderCreateRetryMatcherTest {
    @Test
    fun acceptsServerOwnedSnapshotsThatDifferFromStaleClientCache() {
        assertTrue(
            OrderCreateRetryMatcher.matches(
                requestedMenuItemId = "11111111-1111-4111-8111-111111111111",
                savedMenuItemId = "11111111-1111-4111-8111-111111111111",
                requestedItemName = "Hambúrguer antigo",
                savedItemName = "Hambúrguer atualizado",
                requestedAmount = 10.00,
                savedAmount = 12.50,
                requestedUnitCost = BigDecimal("3.50"),
                savedUnitCost = BigDecimal("4.25"),
                savedUnitCostIsNull = false
            )
        )
    }

    @Test
    fun acceptsUnknownServerCostForLinkedOrder() {
        assertTrue(
            OrderCreateRetryMatcher.matches(
                requestedMenuItemId = "11111111-1111-4111-8111-111111111111",
                savedMenuItemId = "11111111-1111-4111-8111-111111111111",
                requestedItemName = "Prato",
                savedItemName = "Prato",
                requestedAmount = 8.00,
                savedAmount = 9.00,
                requestedUnitCost = BigDecimal("2.00"),
                savedUnitCost = null,
                savedUnitCostIsNull = true
            )
        )
    }

    @Test
    fun rejectsDifferentMenuItemOnRetry() {
        assertFalse(
            OrderCreateRetryMatcher.matches(
                requestedMenuItemId = "11111111-1111-4111-8111-111111111111",
                savedMenuItemId = "22222222-2222-4222-8222-222222222222",
                requestedItemName = "Prato",
                savedItemName = "Prato",
                requestedAmount = 8.00,
                savedAmount = 8.00,
                requestedUnitCost = null,
                savedUnitCost = null,
                savedUnitCostIsNull = true
            )
        )
    }

    @Test
    fun rejectsMalformedServerOwnedSnapshots() {
        val menuItemId = "11111111-1111-4111-8111-111111111111"
        assertFalse(
            OrderCreateRetryMatcher.matches(
                menuItemId, menuItemId, "Prato", "", 8.00, 8.00,
                null, null, true
            )
        )
        assertFalse(
            OrderCreateRetryMatcher.matches(
                menuItemId, menuItemId, "Prato", "Prato", 8.00, -1.00,
                null, null, true
            )
        )
        assertFalse(
            OrderCreateRetryMatcher.matches(
                menuItemId, menuItemId, "Prato", "Prato", 8.00, Double.NaN,
                null, null, true
            )
        )
        assertFalse(
            OrderCreateRetryMatcher.matches(
                menuItemId, menuItemId, "Prato", "Prato", 8.00, 8.00,
                null, null, false
            )
        )
    }

    @Test
    fun legacyUnlinkedOrderStillRequiresExactClientSnapshot() {
        assertTrue(
            OrderCreateRetryMatcher.matches(
                requestedMenuItemId = null,
                savedMenuItemId = null,
                requestedItemName = "Pedido antigo",
                savedItemName = "Pedido antigo",
                requestedAmount = 7.50,
                savedAmount = 7.50,
                requestedUnitCost = null,
                savedUnitCost = null,
                savedUnitCostIsNull = true
            )
        )
    }

    @Test
    fun legacyUnlinkedOrderRejectsChangedSnapshotOrLinkedRow() {
        assertFalse(
            OrderCreateRetryMatcher.matches(
                null, null, "Pedido antigo", "Outro nome", 7.50, 7.50,
                null, null, true
            )
        )
        assertFalse(
            OrderCreateRetryMatcher.matches(
                null, "11111111-1111-4111-8111-111111111111",
                "Pedido antigo", "Pedido antigo", 7.50, 7.50,
                null, null, true
            )
        )
        assertFalse(
            OrderCreateRetryMatcher.matches(
                null, null, "Pedido antigo", "Pedido antigo", 7.50, 7.50,
                null, BigDecimal("1.00"), false
            )
        )
    }
}
