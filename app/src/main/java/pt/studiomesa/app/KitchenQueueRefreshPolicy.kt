package pt.studiomesa.app

import java.net.URLEncoder

/**
 * Keeps kitchen polling limited to the visible, foreground kitchen screen.
 * A 15-second interval gives the team fresh tickets without a permanent background loop.
 */
internal object KitchenQueueRefreshPolicy {
    const val REFRESH_INTERVAL_MILLIS = 15_000L

    fun shouldScheduleAutoRefresh(
        activityResumed: Boolean,
        isCustomerApp: Boolean,
        activePage: String,
        hasRemoteRestaurant: Boolean
    ): Boolean =
        activityResumed &&
            !isCustomerApp &&
            activePage == "Cozinha" &&
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

/**
 * Builds a bounded PostgREST request for active kitchen tickets only.
 * The restaurant ID and status filter are URL-encoded before reaching the endpoint.
 */
internal object KitchenQueueQuery {
    private const val SELECT =
        "id,table_number,item_name,amount,quantity,service_course,notes,status,created_at"

    fun path(restaurantId: String, offset: Int, limit: Int): String {
        require(restaurantId.isNotBlank()) { "O identificador do restaurante é obrigatório." }
        require(offset >= 0) { "O deslocamento da fila não pode ser negativo." }
        require(limit in 1..OrderHistoryPagination.DEFAULT_PAGE_SIZE) {
            "O limite da página da cozinha não é válido."
        }

        val encodedRestaurant = URLEncoder.encode(restaurantId, "UTF-8")
        val encodedStatuses = URLEncoder.encode(
            "in.(\"Aberto\",\"Em preparação\",\"Pronto\")",
            "UTF-8"
        )
        return "/rest/v1/restaurant_orders?select=$SELECT" +
            "&restaurant_id=eq.$encodedRestaurant" +
            "&status=$encodedStatuses" +
            "&order=created_at.asc,id.asc" +
            "&limit=$limit&offset=$offset"
    }
}
