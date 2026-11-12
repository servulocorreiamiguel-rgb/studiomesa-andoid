package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendSchemaFeatureGateTest {
    @Test
    fun recipeManagementRemainsHiddenUntilHostedSchemaIsProvisioned() {
        assertFalse(BackendSchemaFeatureGate.RECIPE_MANAGEMENT_ENABLED)
    }

    @Test
    fun granularStaffPermissionsRemainUnavailableUntilHostedRpcsAreProvisioned() {
        assertFalse(BackendSchemaFeatureGate.GRANULAR_STAFF_PERMISSIONS_ENABLED)
    }

    @Test
    fun legacySchemaPreservesThePreviousOperationalPermissionsForStaff() {
        assertEquals(
            StaffPermissionSet.all.toSet(),
            BackendSchemaFeatureGate.resolveStaffPermissions(
                role = "staff",
                granularPermissionsEnabled = false,
                serverPermissions = null
            )
        )
    }

    @Test
    fun granularSchemaUsesOnlyThePermissionsReturnedByTheServer() {
        assertEquals(
            setOf("orders", "tables"),
            BackendSchemaFeatureGate.resolveStaffPermissions(
                role = "staff",
                granularPermissionsEnabled = true,
                serverPermissions = setOf("orders", "tables")
            )
        )
    }

    @Test
    fun granularSchemaFailsClosedWhenThePermissionsCouldNotBeLoaded() {
        assertEquals(
            emptySet<String>(),
            BackendSchemaFeatureGate.resolveStaffPermissions(
                role = "staff",
                granularPermissionsEnabled = true,
                serverPermissions = null
            )
        )
    }

    @Test
    fun ownersAlwaysKeepAllOperationalPermissions() {
        assertEquals(
            StaffPermissionSet.all.toSet(),
            BackendSchemaFeatureGate.resolveStaffPermissions(
                role = "owner",
                granularPermissionsEnabled = true,
                serverPermissions = emptySet()
            )
        )
    }

    @Test
    fun unknownRolesReceiveNoOperationalPermissions() {
        assertEquals(
            emptySet<String>(),
            BackendSchemaFeatureGate.resolveStaffPermissions(
                role = "customer",
                granularPermissionsEnabled = false,
                serverPermissions = null
            )
        )
    }
}
