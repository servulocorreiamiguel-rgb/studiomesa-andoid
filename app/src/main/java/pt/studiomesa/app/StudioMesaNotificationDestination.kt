package pt.studiomesa.app

/** Maps notification event keys to a destination, respecting the pages visible to the account. */
internal object StudioMesaNotificationDestination {
    fun pageFor(
        isCustomerApp: Boolean,
        eventKey: String,
        availableRestaurantPages: Set<String> = emptySet()
    ): String? {
        if (isCustomerApp) {
            return when {
                eventKey.startsWith("customer:reservation:") -> "Reservas"
                eventKey.startsWith("customer:reward:") -> "Cartão"
                else -> null
            }
        }

        val page = when {
            eventKey.startsWith("restaurant:order:") -> "Cozinha"
            eventKey.startsWith("restaurant:reservation:") -> "Reservas"
            eventKey.startsWith("restaurant:redemption:") -> "Pontos"
            else -> return null
        }
        return page.takeIf { it in availableRestaurantPages }
    }
}
