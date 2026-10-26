package pt.studiomesa.app

/**
 * Converts untrusted server-side error text into a short, single-line message suitable for UI.
 * Non-JSON responses are not passed through because they may be proxy pages or contain details
 * that are not useful to a user.
 */
internal object ApiErrorMessage {
    private const val MAX_DISPLAY_CHARS = 400
    private val controlAndWhitespace = Regex("[\\p{Cc}\\s]+")

    fun sanitize(candidate: String?, statusCode: Int): String {
        val normalized = candidate.orEmpty()
            .replace(controlAndWhitespace, " ")
            .trim()

        if (normalized.isBlank() || normalized.equals("null", ignoreCase = true)) {
            return "Erro de ligação ($statusCode)."
        }
        if (normalized.length <= MAX_DISPLAY_CHARS) return normalized

        var prefix = normalized.substring(0, MAX_DISPLAY_CHARS - 1)
        // Avoid leaving an unmatched high surrogate at the end of a truncated UTF-16 string.
        if (prefix.lastOrNull()?.let { Character.isHighSurrogate(it) } == true) {
            prefix = prefix.dropLast(1)
        }
        return prefix.trimEnd() + "…"
    }
}
