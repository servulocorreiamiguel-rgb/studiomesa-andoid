package pt.studiomesa.app

import java.net.URLDecoder

/**
 * Parses form-style parameters from an authentication callback query or fragment.
 * Malformed percent-encoding in one component is ignored rather than crashing the Activity.
 * Input is bounded because the callback URI can be supplied by another application.
 */
internal object AuthCallbackParser {
    private const val MAX_INPUT_LENGTH = 8_192
    private const val MAX_PARAMETER_COUNT = 32

    fun parse(raw: String?): Map<String, String> {
        val input = raw.orEmpty()
        if (input.length > MAX_INPUT_LENGTH) return emptyMap()
        // Count separators before split() so an untrusted URI cannot allocate an unbounded list.
        if (input.count { it == '&' } >= MAX_PARAMETER_COUNT) return emptyMap()

        return input.split("&").mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else runCatching {
                val key = URLDecoder.decode(part.substring(0, separator), "UTF-8")
                val value = URLDecoder.decode(part.substring(separator + 1), "UTF-8")
                key to value
            }.getOrNull()
        }.filter { (key, _) -> key.isNotBlank() }.toMap()
    }
}
