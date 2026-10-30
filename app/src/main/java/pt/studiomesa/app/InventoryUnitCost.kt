package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Validates the purchase cost of one stock unit (for example €/kg, €/L or €/unit).
 * A blank value means that the restaurant has not provided a trustworthy cost yet.
 */
object InventoryUnitCostValidator {
    private val inputPattern = Regex("^(?:\\d{1,8}(?:\\.\\d{0,4})?|\\.\\d{1,4})$")
    private val maximum = BigDecimal("99999999.9999")

    fun parseOrNull(rawValue: String): BigDecimal? {
        require(rawValue.length <= 32) { "O custo unitário é demasiado longo." }
        val normalized = rawValue.trim().replace(',', '.')
        if (normalized.isEmpty()) return null

        require(inputPattern.matches(normalized)) {
            "Indica um custo não negativo com, no máximo, quatro casas decimais."
        }
        val amount = BigDecimal(normalized)
        require(amount.signum() >= 0 && amount <= maximum) {
            "O custo unitário tem de estar entre 0 € e 99 999 999,9999 €."
        }
        return amount.setScale(4, RoundingMode.UNNECESSARY)
    }
}

/**
 * Uses the unit cost captured on the stock movement, not today's supplier cost.
 * Each movement is rounded to cents before it is included in the waste total.
 */
object InventoryWasteCostCalculator {
    private val maximumQuantity = BigDecimal("999999999")
    private val maximumUnitCost = BigDecimal("99999999.9999")

    data class Summary(
        val knownTotal: BigDecimal,
        val movementsWithoutCost: Int
    )

    fun estimate(quantity: BigDecimal, unitCostPerUnit: BigDecimal?): BigDecimal? {
        require(quantity.signum() >= 0 && quantity <= maximumQuantity) {
            "A quantidade para cálculo de desperdício não é válida."
        }
        require(quantity.scale() <= 3) {
            "A quantidade de stock admite no máximo três casas decimais."
        }
        if (unitCostPerUnit == null) return null

        require(unitCostPerUnit.signum() >= 0 && unitCostPerUnit <= maximumUnitCost) {
            "O custo unitário guardado não é válido."
        }
        require(unitCostPerUnit.scale() <= 4) {
            "O custo unitário admite no máximo quatro casas decimais."
        }

        return quantity.multiply(unitCostPerUnit).setScale(2, RoundingMode.HALF_UP)
    }

    fun summarize(movementCosts: Iterable<BigDecimal?>): Summary {
        var knownTotal = BigDecimal.ZERO.setScale(2)
        var movementsWithoutCost = 0
        movementCosts.forEach { cost ->
            if (cost == null) {
                movementsWithoutCost++
            } else {
                require(cost.scale() == 2 && cost.signum() >= 0) {
                    "O custo estimado de cada perda tem de estar arredondado a cêntimos."
                }
                knownTotal = knownTotal.add(cost)
            }
        }
        return Summary(knownTotal, movementsWithoutCost)
    }
}
