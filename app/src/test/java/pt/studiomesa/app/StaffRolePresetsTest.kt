package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StaffRolePresetsTest {
    @Test
    fun managerHasEveryOperationalPermission() {
        assertEquals(StaffPermissionSet.all.toSet(), StaffRolePresets.permissionsFor("Gerente"))
    }

    @Test
    fun waiterCanHandleFrontOfHouseOperations() {
        assertEquals(
            setOf("orders", "tables", "reservations", "loyalty"),
            StaffRolePresets.permissionsFor("Empregado de mesa")
        )
    }

    @Test
    fun kitchenAndCashierHaveSeparatePresets() {
        assertEquals(setOf("orders"), StaffRolePresets.permissionsFor("Cozinha"))
        assertEquals(setOf("orders", "loyalty"), StaffRolePresets.permissionsFor("Caixa"))
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
