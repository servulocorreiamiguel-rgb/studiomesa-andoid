package pt.studiomesa.app

import java.net.URLDecoder

/**
 * Parses form-style parameters from an authentication callback query or fragment.
 * Malformed percent-encoding in one component is ignored rather than crashing the Activity.
 */
internal object AuthCallbackParser {
    fun parse(raw: String?): Map<String, String> =
        raw.orEmpty().split("&").mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else runCatching {
                val key = URLDecoder.decode(part.substring(0, separator), "UTF-8")
                val value = URLDecoder.decode(part.substring(separator + 1), "UTF-8")
                key to value
            }.getOrNull()
        }.filter { (key, _) -> key.isNotBlank() }.toMap()
}
