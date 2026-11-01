package pt.studiomesa.app

/**
 * Poll reservations only while the authenticated restaurant agenda is visible
 * and the activity is in the foreground. This is not a push notification service.
 */
internal object ReservationRefreshPolicy {
    const val REFRESH_INTERVAL_MILLIS = 30_000L

    fun shouldScheduleAutoRefresh(
        activityResumed: Boolean,
        isCustomerApp: Boolean,
        activePage: String,
        hasRemoteRestaurant: Boolean
    ): Boolean =
        activityResumed &&
            !isCustomerApp &&
            activePage == "Reservas" &&
            hasRemoteRestaurant

    fun mayStartRefresh(
        activityResumed: Boolean,
        isCustomerApp: Boolean,
        activePage: String,
        hasRemoteRestaurant: Boolean,
        refreshInFlight: Boolean
    ): Boolean =
        shouldScheduleAutoRefresh(
            activityResumed,
            isCustomerApp,
            activePage,
            hasRemoteRestaurant
        ) && !refreshInFlight
}
