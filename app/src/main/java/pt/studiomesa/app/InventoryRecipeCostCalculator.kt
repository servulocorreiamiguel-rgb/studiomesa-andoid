package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

internal data class InventoryRecipeIngredientInput(
    val name: String,
    val stockUnit: String,
    val quantityPerPortion: BigDecimal,
    val unitCostPerStockUnit: BigDecimal?,
    val wastePercent: BigDecimal
)

internal data class InventoryRecipeIngredientCost(
    val name: String,
    val stockUnit: String,
    val quantityPerPortion: BigDecimal,
    val unitCostPerStockUnit: BigDecimal?,
    val wastePercent: BigDecimal,
    val costPerPortion: BigDecimal?
)

internal data class InventoryRecipeCostAnalysis(
    val ingredients: List<InventoryRecipeIngredientCost>,
    val knownCostSubtotal: BigDecimal,
    /** Null unless every ingredient has a known purchase cost. */
    val totalCostPerPortion: BigDecimal?,
    val preciseTotalCostPerPortion: BigDecimal?,
    val missingCostIngredientNames: List<String>
) {
    val isComplete: Boolean
        get() = missingCostIngredientNames.isEmpty() && totalCostPerPortion != null

    fun suggestedPriceAtTarget(targetFoodCostPercent: BigDecimal): BigDecimal {
        require(isComplete) {
            "Regista primeiro o custo de compra de todos os ingredientes."
        }
        require(targetFoodCostPercent.signum() > 0 && targetFoodCostPercent <= BigDecimal("100")) {
            "O objetivo de custo alimentar tem de estar entre 0 e 100%."
        }
        val preciseTotal = preciseTotalCostPerPortion
            ?: throw IllegalStateException("O custo total não está disponível.")
        require(preciseTotal.signum() > 0) {
            "O custo da receita tem de ser superior a zero para sugerir um preço."
        }
        return preciseTotal.multiply(BigDecimal("100"))
            .divide(targetFoodCostPercent, 2, RoundingMode.HALF_UP)
    }
}

/**
 * Estimates recipe cost from the current purchase cost recorded for each stock item.
 * Recipe quantities and unit costs must already use the same native stock unit.
 * A missing unit cost makes the total incomplete; it is never silently treated as zero.
 */
internal object InventoryRecipeCostCalculator {
    private val zero = BigDecimal.ZERO
    private val hundred = BigDecimal("100")
    private val maxQuantity = BigDecimal("999999999")
    private val maxUnitCost = BigDecimal("99999999.9999")

    fun calculate(lines: List<InventoryRecipeIngredientInput>): InventoryRecipeCostAnalysis {
        require(lines.isNotEmpty()) { "A receita não tem ingredientes para calcular." }
        require(lines.size <= 100) { "A receita tem demasiados ingredientes." }

        val results = lines.mapIndexed { index, line ->
            val name = line.name.trim()
            val unit = line.stockUnit.trim()
            require(name.isNotBlank() && name.length <= 100) {
                "Ingrediente ${index + 1}: nome inválido."
            }
            require(unit.isNotBlank() && unit.length <= 24) {
                "Ingrediente «$name»: unidade de stock inválida."
            }
            require(
                line.quantityPerPortion.signum() > 0 &&
                    line.quantityPerPortion <= maxQuantity &&
                    line.quantityPerPortion.scale() <= 3
            ) {
                "Ingrediente «$name»: quantidade por dose inválida."
            }
            require(
                line.wastePercent.signum() >= 0 &&
                    line.wastePercent < hundred &&
                    line.wastePercent.scale() <= 2
            ) {
                "Ingrediente «$name»: desperdício tem de estar entre 0 e menos de 100%."
            }
            val unitCost = line.unitCostPerStockUnit
            if (unitCost != null) {
                require(
                    unitCost.signum() >= 0 &&
                        unitCost <= maxUnitCost &&
                        unitCost.scale() <= 4
                ) {
                    "Ingrediente «$name»: custo por unidade inválido."
                }
            }

            val lineCost = unitCost?.let {
                line.quantityPerPortion.multiply(it).multiply(hundred)
                    .divide(hundred.subtract(line.wastePercent), 8, RoundingMode.HALF_UP)
            }
            InventoryRecipeIngredientCost(
                name = name,
                stockUnit = unit,
                quantityPerPortion = line.quantityPerPortion,
                unitCostPerStockUnit = unitCost,
                wastePercent = line.wastePercent,
                costPerPortion = lineCost
            )
        }

        val missing = results.filter { it.costPerPortion == null }.map { it.name }
        val knownExact = results.mapNotNull { it.costPerPortion }.fold(zero, BigDecimal::add)
        val knownSubtotal = knownExact.setScale(2, RoundingMode.HALF_UP)
        val total = if (missing.isEmpty()) knownSubtotal else null

        return InventoryRecipeCostAnalysis(
            ingredients = results,
            knownCostSubtotal = knownSubtotal,
            totalCostPerPortion = total,
            preciseTotalCostPerPortion = if (missing.isEmpty()) knownExact else null,
            missingCostIngredientNames = missing
        )
    }
}
