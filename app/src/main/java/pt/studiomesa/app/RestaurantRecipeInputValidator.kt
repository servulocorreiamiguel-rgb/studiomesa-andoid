package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Matches PostgreSQL numeric(12,3) recipe quantities and numeric(5,2) waste percentages.
 * Stock deductions are calculated by the server; this helper validates and previews inputs.
 */
internal object RestaurantRecipeInputValidator {
    private val quantityPattern = Regex("^(?:\\d+(?:[.,]\\d{0,3})?|[.,]\\d{1,3})$")
    private val wastePattern = Regex("^(?:\\d+(?:[.,]\\d{0,2})?|[.,]\\d{1,2})$")
    private val maxQuantity = BigDecimal("999999999")
    private val oneHundred = BigDecimal("100")

    fun parseQuantityPerPortion(raw: String): BigDecimal {
        require(raw.length <= 40) { "A quantidade por dose é demasiado longa." }
        val normalized = raw.trim().replace(',', '.')
        require(quantityPattern.matches(normalized)) {
            "Indica uma quantidade positiva com no máximo três casas decimais."
        }
        val value = BigDecimal(normalized)
        require(value.signum() > 0 && value <= maxQuantity) {
            "A quantidade por dose tem de ser superior a zero e não exceder o limite suportado."
        }
        return value
    }

    fun parseWastePercent(raw: String): BigDecimal {
        require(raw.length <= 16) { "A percentagem de desperdício é demasiado longa." }
        val normalized = raw.trim().replace(',', '.')
        require(wastePattern.matches(normalized)) {
            "Usa uma percentagem entre 0 e menos de 100, com no máximo duas casas decimais."
        }
        val value = BigDecimal(normalized)
        require(value.signum() >= 0 && value < oneHundred) {
            "A percentagem de desperdício tem de estar entre 0 e menos de 100."
        }
        return value
    }

    fun requiredStockForOrder(
        quantityPerPortion: BigDecimal,
        portions: Int,
        wastePercent: BigDecimal
    ): BigDecimal {
        require(quantityPerPortion.signum() > 0 && quantityPerPortion <= maxQuantity) {
            "A quantidade por dose não é válida."
        }
        require(portions in 1..99) { "O número de doses deve estar entre 1 e 99." }
        require(wastePercent.signum() >= 0 && wastePercent < oneHundred) {
            "A percentagem de desperdício não é válida."
        }
        val usablePercent = oneHundred.subtract(wastePercent)
        val raw = quantityPerPortion
            .multiply(BigDecimal(portions))
            .multiply(oneHundred)
            .divide(usablePercent, 8, RoundingMode.UP)
        val result = raw.setScale(3, RoundingMode.CEILING)
        require(result.signum() > 0 && result <= maxQuantity) {
            "A quantidade calculada excede o limite de stock suportado."
        }
        return result
    }
}
