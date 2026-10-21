package pt.studiomesa.app

/**
 * Numeric(12,3) stock columns preserve at most three fractional decimal digits.
 * The UI and database use the same precision rule so values are never rounded silently.
 */
internal object StockDecimalValidator {
    fun hasAtMostThreeDecimalPlaces(rawValue: String): Boolean {
        val normalized = rawValue.trim().replace(',', '.')
        val separator = normalized.indexOf('.')
        if (separator < 0) return true
        return separator == normalized.lastIndexOf('.') &&
            normalized.length - separator - 1 <= 3
    }
}
