package pt.studiomesa.app

/**
 * Backend capabilities verified against the currently hosted Supabase schema.
 * Keep these disabled until the missing migrations and RPCs have been safely
 * reconciled, deployed, and exercised with isolated tenant accounts.
 */
internal object BackendSchemaFeatureGate {
    const val RECIPE_MANAGEMENT_ENABLED = false
    const val GRANULAR_STAFF_PERMISSIONS_ENABLED = false

    fun resolveStaffPermissions(
        role: String,
        granularPermissionsEnabled: Boolean,
        serverPermissions: Set<String>?
    ): Set<String> = when (role) {
        "owner" -> StaffPermissionSet.all.toSet()
        "staff" -> if (granularPermissionsEnabled) serverPermissions.orEmpty()
            else StaffPermissionSet.all.toSet()
        else -> emptySet()
    }
}
