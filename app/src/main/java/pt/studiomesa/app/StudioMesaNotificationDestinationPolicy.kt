package pt.studiomesa.app

/**
 * Resolves notification navigation using strict allow-lists.
 * Restaurant destinations must also be present in the current permission-filtered page set.
 */
internal object StudioMesaNotificationDestinationPolicy {
    private val customerPages = setOf("Cartão", "Carta", "Avisos", "Reservas", "Conta")
    private val restaurantPages = setOf("Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos", "Reservas")

    fun forCustomer(requested: String?): String? =
        requested?.trim()?.takeIf { it in customerPages }

    fun forRestaurant(requested: String?, availablePages: Collection<String>): String? {
        val candidate = requested?.trim() ?: return null
        if (candidate !in restaurantPages) return null
        return candidate.takeIf { it in availablePages }
    }
}
