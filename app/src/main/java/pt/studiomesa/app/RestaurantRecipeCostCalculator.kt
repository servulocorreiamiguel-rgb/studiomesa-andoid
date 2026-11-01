package pt.studiomesa.app

import java.math.BigDecimal

internal data class RestaurantRecipeCostInput(
    val inventoryItemId: String,
    val quantityPerPortion: BigDecimal,
    val wastePercent: BigDecimal,
    val unitCostPerUnit: BigDecimal?
)

internal data class RestaurantRecipeCostEstimate(
    val knownCostPerPortion: BigDecimal,
    val totalIngredients: Int,
    val ingredientsWithoutCost: Int,
    val lineCostsPerPortion: Map<String, BigDecimal?>
) {
    val isComplete: Boolean
        get() = totalIngredients > 0 && ingredientsWithoutCost == 0

    fun knownIngredientCostPercent(sellingPrice: BigDecimal): BigDecimal {
        require(sellingPrice.signum() > 0) { "O preço de venda tem de ser superior a zero." }
        return knownCostPerPortion
            .multiply(BigDecimal("100"))
            .divide(sellingPrice, 6, java.math.RoundingMode.HALF_UP)
            .setScale(1, java.math.RoundingMode.HALF_UP)
    }

    fun amountAfterIngredients(sellingPrice: BigDecimal): BigDecimal =
        sellingPrice.subtract(knownCostPerPortion)
}

/**
 * Estimates recipe cost using the same rounded stock quantity that the database
 * deducts for one portion. Unknown purchase costs are excluded, never treated as zero.
 */
internal object RestaurantRecipeCostCalculator {
    fun estimate(inputs: List<RestaurantRecipeCostInput>): RestaurantRecipeCostEstimate {
        require(inputs.isNotEmpty()) { "A receita não tem ingredientes." }
        require(inputs.map { it.inventoryItemId }.toSet().size == inputs.size) {
            "A receita contém ingredientes repetidos."
        }

        var knownCost = BigDecimal.ZERO
        var missingCostCount = 0
        val lineCosts = linkedMapOf<String, BigDecimal?>()

        inputs.forEach { input ->
            require(input.inventoryItemId.isNotBlank()) { "O identificador do ingrediente é inválido." }
            val stockQuantity = RestaurantRecipeInputValidator.requiredStockForOrder(
                input.quantityPerPortion,
                1,
                input.wastePercent
            )
            val unitCost = input.unitCostPerUnit
            if (unitCost == null) {
                missingCostCount++
                lineCosts[input.inventoryItemId] = null
            } else {
                require(unitCost.signum() >= 0 && unitCost <= BigDecimal("99999999.9999") &&
                    unitCost.scale() <= 4) {
                    "O custo unitário do ingrediente não é válido."
                }
                val lineCost = stockQuantity.multiply(unitCost).stripTrailingZeros()
                knownCost = knownCost.add(lineCost)
                lineCosts[input.inventoryItemId] = lineCost
            }
        }

        return RestaurantRecipeCostEstimate(
            knownCostPerPortion = knownCost,
            totalIngredients = inputs.size,
            ingredientsWithoutCost = missingCostCount,
            lineCostsPerPortion = lineCosts
        )
    }
}
