package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

internal enum class PriceInputField {
    COST,
    TARGET_COST_PERCENT
}

internal class PriceCalculationException(
    val field: PriceInputField,
    message: String
) : IllegalArgumentException(message)

internal data class PriceCalculationResult(
    val cost: BigDecimal,
    val suggestedPrice: BigDecimal,
    val grossProfit: BigDecimal,
    val actualCostPercent: BigDecimal,
    val grossMarginPercent: BigDecimal
)

/**
 * Calculates a suggested selling price from the target percentage of selling price
 * represented by the unit cost. All monetary arithmetic is done in decimal euros,
 * rounded to cents using HALF_UP, before profit and margins are computed.
 */
internal object PriceCalculator {
    private val decimalInput = Regex("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)$")
    private val zero = BigDecimal.ZERO
    private val oneHundred = BigDecimal("100")
    private const val MAX_INPUT_LENGTH = 64

    fun calculate(costText: String, targetCostPercentText: String): PriceCalculationResult {
        val costEntered = parseDecimal(costText, PriceInputField.COST, "Introduz um custo numérico válido.")
        if (costEntered <= zero) {
            throw PriceCalculationException(PriceInputField.COST, "Introduz um custo superior a zero.")
        }
        if (costEntered.stripTrailingZeros().scale() > 2) {
            throw PriceCalculationException(PriceInputField.COST, "Usa no máximo duas casas decimais no custo.")
        }
        val cost = costEntered.setScale(2, RoundingMode.UNNECESSARY)

        val targetPercent = parseDecimal(
            targetCostPercentText,
            PriceInputField.TARGET_COST_PERCENT,
            "Introduz uma percentagem numérica válida."
        )
        if (targetPercent <= zero || targetPercent > oneHundred) {
            throw PriceCalculationException(
                PriceInputField.TARGET_COST_PERCENT,
                "Usa uma percentagem superior a 0 e até 100."
            )
        }

        val suggestedPrice = cost.divide(
            targetPercent.movePointLeft(2),
            2,
            RoundingMode.HALF_UP
        )
        if (suggestedPrice <= zero || !suggestedPrice.toDouble().isFinite()) {
            throw PriceCalculationException(
                PriceInputField.TARGET_COST_PERCENT,
                "Os valores são demasiado elevados para calcular um preço."
            )
        }

        val grossProfit = suggestedPrice.subtract(cost).setScale(2, RoundingMode.HALF_UP)
        val actualCostPercent = cost
            .divide(suggestedPrice, 8, RoundingMode.HALF_UP)
            .multiply(oneHundred)
            .setScale(1, RoundingMode.HALF_UP)
        val grossMarginPercent = grossProfit
            .divide(suggestedPrice, 8, RoundingMode.HALF_UP)
            .multiply(oneHundred)
            .setScale(1, RoundingMode.HALF_UP)

        return PriceCalculationResult(
            cost = cost,
            suggestedPrice = suggestedPrice,
            grossProfit = grossProfit,
            actualCostPercent = actualCostPercent,
            grossMarginPercent = grossMarginPercent
        )
    }

    private fun parseDecimal(
        rawText: String,
        field: PriceInputField,
        errorMessage: String
    ): BigDecimal {
        // Bound input size before constructing BigDecimal to avoid wasting CPU/memory
        // on pasted or generated strings that no restaurant price needs.
        if (rawText.length > MAX_INPUT_LENGTH) {
            throw PriceCalculationException(field, errorMessage)
        }
        val normalized = rawText.trim().replace(',', '.')
        if (normalized.isEmpty() || !decimalInput.matches(normalized)) {
            throw PriceCalculationException(field, errorMessage)
        }
        return try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            throw PriceCalculationException(field, errorMessage)
        }
    }
}
