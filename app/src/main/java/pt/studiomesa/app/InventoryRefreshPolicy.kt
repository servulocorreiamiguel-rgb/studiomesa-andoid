package pt.studiomesa.app

/**
 * Poll stock only while a Premium restaurant's inventory screen is visible.
 * Requests are bounded to the foreground page; no background polling is performed.
 */
internal object InventoryRefreshPolicy {
    const val REFRESH_INTERVAL_MILLIS = 30_000L

    fun shouldScheduleAutoRefresh(
        activityResumed: Boolean,
        isCustomerApp: Boolean,
        isPremium: Boolean,
        activePage: String,
        hasRemoteRestaurant: Boolean
    ): Boolean =
        activityResumed &&
            !isCustomerApp &&
            isPremium &&
            activePage == "Stock" &&
            hasRemoteRestaurant

    fun mayStartRefresh(
        activityResumed: Boolean,
        isCustomerApp: Boolean,
        isPremium: Boolean,
        activePage: String,
        hasRemoteRestaurant: Boolean,
        refreshInFlight: Boolean
    ): Boolean =
        shouldScheduleAutoRefresh(
            activityResumed = activityResumed,
            isCustomerApp = isCustomerApp,
            isPremium = isPremium,
            activePage = activePage,
            hasRemoteRestaurant = hasRemoteRestaurant
        ) && !refreshInFlight
}
