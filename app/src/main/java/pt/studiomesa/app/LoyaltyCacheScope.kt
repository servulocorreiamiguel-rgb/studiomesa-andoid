package pt.studiomesa.app

/**
 * Prevents a cached loyalty catalogue or redemption history from being shown
 * under another restaurant or customer membership on the same device.
 */
internal object LoyaltyCacheScope {
    fun belongsTo(cachedScopeId: String?, activeScopeId: String?): Boolean {
        if (cachedScopeId.isNullOrBlank() || activeScopeId.isNullOrBlank()) return false
        return cachedScopeId == activeScopeId
    }
}
