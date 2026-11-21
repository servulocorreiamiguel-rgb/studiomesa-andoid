package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StaffRolePresetsTest {
    @Test
    fun managerHasEveryOperationalPermission() {
        assertEquals(StaffPermissionSet.all.toSet(), StaffRolePresets.permissionsFor("Gerente"))
    }

    @Test
    fun waiterCanReadMenuAndCreateFrontOfHouseOrders() {
        val permissions = StaffRolePresets.permissionsFor("Empregado de mesa").orEmpty()
        assertEquals(
            setOf("orders", "tables", "reservations", "menu", "loyalty"),
            permissions
        )
        assertEquals(true, StaffPermissionSet.canCreateOrder(permissions, role = "staff"))
    }

    @Test
    fun kitchenCanSeeOrdersButCannotCreateCustomerOrders() {
        val permissions = StaffRolePresets.permissionsFor("Cozinha").orEmpty()
        assertEquals(setOf("orders"), permissions)
        assertEquals(false, StaffPermissionSet.canCreateOrder(permissions, role = "staff"))
    }

    @Test
    fun cashierCanReadMenuAndCreateOrdersWithoutInventoryAccess() {
        val permissions = StaffRolePresets.permissionsFor("Caixa").orEmpty()
        assertEquals(setOf("orders", "tables", "menu", "loyalty"), permissions)
        assertEquals(true, StaffPermissionSet.canCreateOrder(permissions, role = "staff"))
        assertEquals(false, "inventory" in permissions)
    }

    @Test
    fun customPermissionsAreNotMislabelledAsPreset() {
        assertEquals(StaffRolePresets.CUSTOM, StaffRolePresets.labelFor(setOf("orders", "menu")))
        assertEquals(0, StaffRolePresets.indexFor(setOf("orders", "menu")))
    }

    @Test
    fun unknownPresetDoesNotGrantPermissions() {
        assertNull(StaffRolePresets.permissionsFor("Administrador do sistema"))
    }
}
