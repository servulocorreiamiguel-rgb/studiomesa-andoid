package pt.studiomesa.app

/** Prevents a late reservation response from overwriting a different signed-in scope. */
internal object ReservationRefreshScope {
    fun matchesRestaurant(
        requestedRestaurantId: String,
        currentRestaurantId: String,
        requestedAccountId: String,
        currentAccountId: String
    ): Boolean =
        requestedRestaurantId.isNotBlank() &&
            requestedAccountId.isNotBlank() &&
            requestedRestaurantId == currentRestaurantId &&
            requestedAccountId == currentAccountId

    fun matchesCustomer(
        isCustomerApp: Boolean,
        requestedMemberId: String,
        currentMemberId: String,
        requestedAccountId: String,
        currentAccountId: String
    ): Boolean =
        isCustomerApp &&
            requestedMemberId.isNotBlank() &&
            requestedAccountId.isNotBlank() &&
            requestedMemberId == currentMemberId &&
            requestedAccountId == currentAccountId
}
