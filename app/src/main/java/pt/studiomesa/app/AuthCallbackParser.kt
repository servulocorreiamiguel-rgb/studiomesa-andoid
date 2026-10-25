package pt.studiomesa.app

import java.net.URLDecoder

/**
 * Parses form-style parameters from an authentication callback query or fragment.
 * Malformed percent-encoding in one component is ignored rather than crashing the Activity.
 */
internal object AuthCallbackParser {
    private const val MAX_CALLBACK_CHARS = 8192
    private const val MAX_PARAMETERS = 32
    private const val MAX_PARAMETER_CHARS = 4096

    fun parse(raw: String?): Map<String, String> {
        // A custom-scheme deep link can be opened by another app. Bound input size and
        // parameter count before split/decode to avoid excessive allocation or CPU use.
        if (raw.isNullOrEmpty() || raw.length > MAX_CALLBACK_CHARS) return emptyMap()

        val components = raw.split("&", limit = MAX_PARAMETERS + 1)
        if (components.size > MAX_PARAMETERS) return emptyMap()

        return components.mapNotNull { part ->
            if (part.length > MAX_PARAMETER_CHARS) return@mapNotNull null
            val separator = part.indexOf('=')
            if (separator <= 0) null else runCatching {
                val key = URLDecoder.decode(part.substring(0, separator), "UTF-8")
                val value = URLDecoder.decode(part.substring(separator + 1), "UTF-8")
                key to value
            }.getOrNull()
        }.filter { (key, _) -> key.isNotBlank() }.toMap()
    }
}
