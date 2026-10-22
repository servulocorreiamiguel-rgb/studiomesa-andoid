package pt.studiomesa.app

/**
 * Numeric(12,3) stock columns preserve at most three fractional decimal digits.
 * Accept plain decimal notation only, so UI validation matches the way quantities
 * are presented to restaurant staff and does not silently accept values such as 1e3.
 */
internal object StockDecimalValidator {
    private val decimalInput = Regex("^[+-]?(?:\\d+(?:\\.\\d{0,3})?|\\.\\d{1,3})$")

    fun hasAtMostThreeDecimalPlaces(rawValue: String): Boolean {
        val normalized = rawValue.trim().replace(',', '.')
        return decimalInput.matches(normalized)
    }
}
