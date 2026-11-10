package pt.studiomesa.app

import org.junit.Assert.assertFalse
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
}
