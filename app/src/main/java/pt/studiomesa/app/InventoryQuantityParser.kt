package pt.studiomesa.app

import java.math.BigDecimal

/**
 * Parses a quantity entered in a compatible display unit and converts it to
 * the exact unit used by the inventory item before sending it to the backend.
 */
internal object InventoryQuantityParser {
    fun parseToStockUnit(
        rawValue: String,
        enteredUnit: String,
        stockUnit: String
    ): BigDecimal {
        val normalized = rawValue.trim().replace(',', '.')
        require(StockDecimalValidator.hasAtMostThreeDecimalPlaces(normalized)) {
            "Usa uma quantidade positiva com no máximo três casas decimais."
        }
        val amount = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("Indica uma quantidade numérica válida.")
        }
        return InventoryUnitConverter.convertToStockUnit(
            amount,
            enteredUnit,
            stockUnit.trim().ifBlank { "unidades" }
        )
    }
}
