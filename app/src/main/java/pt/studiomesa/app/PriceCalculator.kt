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
    private const val MAX_INPUT_LENGTH = 64
    private val zero = BigDecimal.ZERO
    private val oneHundred = BigDecimal("100")
    // Matches the positive maximum of numeric(10,2) used by menu price/cost columns.
    private val maximumMoney = BigDecimal("99999999.99")
    // Percentages are shown to at most two decimal places; smaller targets can produce
    // misleading zero-looking margins and implausibly large suggested prices.
    private val minimumTargetPercent = BigDecimal("0.01")

    fun calculate(costText: String, targetCostPercentText: String): PriceCalculationResult {
        val costEntered = parseDecimal(costText, PriceInputField.COST, "Introduz um custo numérico válido.")
        if (costEntered <= zero) {
            throw PriceCalculationException(PriceInputField.COST, "Introduz um custo superior a zero.")
        }
        if (costEntered.stripTrailingZeros().scale() > 2) {
            throw PriceCalculationException(PriceInputField.COST, "Usa no máximo duas casas decimais no custo.")
        }
        val cost = costEntered.setScale(2, RoundingMode.UNNECESSARY)
        if (cost > maximumMoney) {
            throw PriceCalculationException(
                PriceInputField.COST,
                "O custo excede o limite suportado pela carta (99 999 999,99 €)."
            )
        }

        val targetPercent = parseDecimal(
            targetCostPercentText,
            PriceInputField.TARGET_COST_PERCENT,
            "Introduz uma percentagem numérica válida."
        )
        if (targetPercent < minimumTargetPercent || targetPercent > oneHundred) {
            throw PriceCalculationException(
                PriceInputField.TARGET_COST_PERCENT,
                "Usa uma percentagem entre 0,01 e 100."
            )
        }

        val suggestedPrice = cost.divide(
            targetPercent.movePointLeft(2),
            2,
            RoundingMode.HALF_UP
        )
        if (suggestedPrice <= zero) {
            throw PriceCalculationException(
                PriceInputField.TARGET_COST_PERCENT,
                "Os valores são demasiado elevados para calcular um preço."
            )
        }
        if (suggestedPrice > maximumMoney) {
            throw PriceCalculationException(
                PriceInputField.TARGET_COST_PERCENT,
                "O preço sugerido excede o limite suportado pela carta (99 999 999,99 €). Aumenta a percentagem pretendida ou revê os valores."
            )
        }

        val grossProfit = suggestedPrice.subtract(cost).setScale(2, RoundingMode.HALF_UP)
        // Multiply before dividing, and keep guard digits until the final percentage rounding.
        // Rounding the ratio first can move values across a HALF_UP boundary (e.g. 50.01 / 100.01).
        val actualCostPercent = cost
            .multiply(oneHundred)
            .divide(suggestedPrice, 10, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP)
        val grossMarginPercent = grossProfit
            .multiply(oneHundred)
            .divide(suggestedPrice, 10, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP)

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
        // Enforce the limit before trimming or replacing characters, so oversized pasted
        // input cannot trigger avoidable copies and surrounding whitespace cannot bypass it.
        if (rawText.length > MAX_INPUT_LENGTH) {
            throw PriceCalculationException(field, "O valor introduzido é demasiado longo.")
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
