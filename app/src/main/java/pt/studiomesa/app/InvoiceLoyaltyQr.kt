package pt.studiomesa.app

import java.util.UUID
import java.util.Locale

/**
 * Stable payload format for StudioMesa invoice-loyalty QR codes.
 * This is deliberately distinct from fiscal QR content and old customer QR tokens.
 */
internal object InvoiceLoyaltyQr {
    private const val PREFIX = "STUDIOMESA-LOYALTY-INVOICE:"

    fun payloadForToken(token: String): String {
        val canonical = UUID.fromString(token).toString()
        require(canonical.equals(token, ignoreCase = true)) {
            "O identificador do QR não está no formato esperado."
        }
        return PREFIX + canonical
    }

    fun parseToken(payload: String?): String? {
        val value = payload?.trim() ?: return null
        if (!value.startsWith(PREFIX)) return null
        val rawToken = value.removePrefix(PREFIX)
        if (rawToken.length != 36) return null
        val canonical = runCatching { UUID.fromString(rawToken).toString() }.getOrNull() ?: return null
        return canonical.takeIf { it == rawToken.lowercase(Locale.ROOT) }
    }
}
