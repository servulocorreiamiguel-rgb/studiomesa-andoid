package pt.studiomesa.app

/**
 * Backend capabilities verified against the currently hosted Supabase schema.
 * Keep these disabled until the missing migrations and RPCs have been safely
 * reconciled, deployed, and exercised with isolated tenant accounts.
 */
internal enum class CustomerJoinMethod {
    SEARCH_BY_NAME,
    ACCESS_CODE,
    QR
}

internal object BackendSchemaFeatureGate {
    const val RECIPE_MANAGEMENT_ENABLED = false
    const val GRANULAR_STAFF_PERMISSIONS_ENABLED = false
    const val CUSTOMER_MULTI_RESTAURANT_ENABLED = false

    fun customerJoinMethods(): List<CustomerJoinMethod> = if (CUSTOMER_MULTI_RESTAURANT_ENABLED) {
        listOf(CustomerJoinMethod.SEARCH_BY_NAME, CustomerJoinMethod.ACCESS_CODE, CustomerJoinMethod.QR)
    } else {
        listOf(CustomerJoinMethod.ACCESS_CODE, CustomerJoinMethod.QR)
    }

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
