package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

internal data class MenuCostComparisonInput(
    val itemName: String,
    val sellingPrice: BigDecimal?,
    val recordedUnitCost: BigDecimal?,
    val menuItemId: String? = null,
    val recipeExists: Boolean = false,
    val recipeIsActive: Boolean = false,
    val recipeRequiresReview: Boolean = false,
    val recipeIsActiveApproved: Boolean = false,
    val recipeCostPerPortion: BigDecimal? = null,
    val recipeCostComplete: Boolean = false
)

internal data class MenuCostComparisonRow(
    val itemName: String,
    val menuItemId: String?,
    val sellingPrice: BigDecimal,
    val recordedUnitCost: BigDecimal,
    val costToPricePercent: BigDecimal,
    val amountAfterRecordedCost: BigDecimal,
    val aboveTarget: Boolean,
    val costSourceLabel: String
)

internal data class MenuCostComparisonResult(
    val rankedItems: List<MenuCostComparisonRow>,
    val excludedMissingCost: Int,
    val excludedInvalidPrice: Int,
    val excludedInvalidCost: Int,
    val aboveTargetCount: Int,
    val costAboveSellingPriceCount: Int,
    val menuItemsUsingRecipeCost: Int,
    val menuItemsUsingManualCost: Int,
    val recipesAwaitingApproval: Int,
    val recipesInactive: Int,
    val approvedRecipesIncomplete: Int
)

/**
 * Ranks menu items by their explicitly recorded per-unit cost relative to selling price.
 * This is a screening indicator, not a net-margin or accounting-profit calculation.
 */
internal object MenuCostComparisonAnalyzer {
    private val hundred = BigDecimal("100")
    private val maxAmount = BigDecimal("999999999.99")

    fun analyze(
        entries: List<MenuCostComparisonInput>,
        targetPercent: BigDecimal?
    ): MenuCostComparisonResult {
        require(entries.size <= 500) { "A carta tem demasiados artigos para comparar de uma só vez." }
        if (targetPercent != null) {
            require(targetPercent.signum() > 0 && targetPercent <= hundred) {
                "O objetivo de custo tem de estar entre 0 e 100%."
            }
        }

        val rows = mutableListOf<MenuCostComparisonRow>()
        var missingCost = 0
        var invalidPrice = 0
        var invalidCost = 0

        entries.forEach { entry ->
            require(entry.itemName.isNotBlank() && entry.itemName.length <= 120) {
                "Existe um artigo sem nome válido na carta."
            }

            val price = entry.sellingPrice
            if (price == null || price.signum() <= 0 || price > maxAmount) {
                invalidPrice++
                return@forEach
            }

            val approvedRecipe = entry.recipeExists && entry.recipeIsActiveApproved
            val cost: BigDecimal
            val sourceLabel: String
            if (approvedRecipe) {
                val recipeCost = entry.recipeCostPerPortion
                if (!entry.recipeCostComplete || recipeCost == null) {
                    return@forEach
                }
                cost = recipeCost
                sourceLabel = "Ficha técnica aprovada"
            } else {
                sourceLabel = when {
                    entry.recipeRequiresReview ->
                        "Custo manual · ficha técnica por rever"
                    entry.recipeExists && !entry.recipeIsActive ->
                        "Custo manual · ficha técnica inativa"
                    else ->
                        "Custo manual da carta"
                }
                val manualCost = entry.recordedUnitCost
                if (manualCost == null) {
                    missingCost++
                    return@forEach
                }
                cost = manualCost
            }
            if (cost.signum() < 0 || cost > maxAmount) {
                invalidCost++
                return@forEach
            }

            val ratio = cost.multiply(hundred)
                .divide(price, 6, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.HALF_UP)
            rows += MenuCostComparisonRow(
                itemName = entry.itemName.trim(),
                menuItemId = entry.menuItemId,
                sellingPrice = price.setScale(2, RoundingMode.HALF_UP),
                recordedUnitCost = cost.setScale(2, RoundingMode.HALF_UP),
                costToPricePercent = ratio,
                amountAfterRecordedCost = price.subtract(cost).setScale(2, RoundingMode.HALF_UP),
                aboveTarget = targetPercent != null && ratio > targetPercent,
                costSourceLabel = sourceLabel
            )
        }

        val ranked = rows.sortedWith(
            compareByDescending<MenuCostComparisonRow> { it.costToPricePercent }
                .thenBy { it.itemName.lowercase() }
        )
        return MenuCostComparisonResult(
            rankedItems = ranked,
            excludedMissingCost = missingCost,
            excludedInvalidPrice = invalidPrice,
            excludedInvalidCost = invalidCost,
            aboveTargetCount = ranked.count { it.aboveTarget },
            costAboveSellingPriceCount = ranked.count { it.amountAfterRecordedCost.signum() < 0 },
            menuItemsUsingRecipeCost = ranked.count { it.costSourceLabel == "Ficha técnica aprovada" },
            menuItemsUsingManualCost = ranked.count { it.costSourceLabel != "Ficha técnica aprovada" },
            recipesAwaitingApproval = entries.count { it.recipeExists && it.recipeRequiresReview },
            recipesInactive = entries.count {
                it.recipeExists && !it.recipeIsActive && !it.recipeRequiresReview
            },
            approvedRecipesIncomplete = entries.count {
                it.recipeExists && it.recipeIsActiveApproved && (!it.recipeCostComplete || it.recipeCostPerPortion == null)
            }
        )
    }
}
