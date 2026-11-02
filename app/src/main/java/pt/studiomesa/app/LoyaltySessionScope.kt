package pt.studiomesa.app

/**
 * Ensures an asynchronous loyalty response still belongs to the authenticated
 * account and the same customer card or restaurant that initiated the request.
 */
internal object LoyaltySessionScope {
    fun matches(
        requestedAccountId: String?,
        activeAccountId: String?,
        requestedScopeId: String?,
        activeScopeId: String?
    ): Boolean {
        if (requestedAccountId.isNullOrBlank() || activeAccountId.isNullOrBlank()) return false
        if (requestedScopeId.isNullOrBlank() || activeScopeId.isNullOrBlank()) return false
        return requestedAccountId == activeAccountId && requestedScopeId == activeScopeId
    }
}
