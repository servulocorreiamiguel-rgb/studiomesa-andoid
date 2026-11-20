package pt.studiomesa.app

/**
 * Parses restaurant invitation QR payloads. Loyalty award QR tokens use a
 * different prefix and must never be mistaken for restaurant invitations.
 */
object RestaurantJoinQrParser {
    const val PREFIX = "STUDIOMESA-JOIN:"

    fun hasPrefix(payload: String): Boolean =
        payload.startsWith(PREFIX, ignoreCase = true)

    fun extractCode(payload: String): String? {
        if (!hasPrefix(payload)) return null
        val code = payload.substring(PREFIX.length).trim().uppercase()
        return code.takeIf { it.matches(Regex("^[A-Z0-9]{6,12}$")) }
    }
}
