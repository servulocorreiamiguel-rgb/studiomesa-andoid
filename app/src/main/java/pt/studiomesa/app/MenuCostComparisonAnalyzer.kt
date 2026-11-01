package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

internal data class MenuCostComparisonInput(
    val itemName: String,
    val sellingPrice: BigDecimal?,
    val recordedUnitCost: BigDecimal?
)

internal data class MenuCostComparisonRow(
    val itemName: String,
    val sellingPrice: BigDecimal,
    val recordedUnitCost: BigDecimal,
    val costToPricePercent: BigDecimal,
    val amountAfterRecordedCost: BigDecimal,
    val aboveTarget: Boolean
)

internal data class MenuCostComparisonResult(
    val rankedItems: List<MenuCostComparisonRow>,
    val excludedMissingCost: Int,
    val excludedInvalidPrice: Int,
    val excludedInvalidCost: Int,
    val aboveTargetCount: Int,
    val costAboveSellingPriceCount: Int
)

/**
 * Ranks menu items by their explicitly recorded per-unit cost relative to selling price.
 * This is a screening indicator, not a net-margin or accounting-profit calculation.
 */
internal object MenuCostComparisonAnalyzer {
    private val zero = BigDecimal.ZERO
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

            val cost = entry.recordedUnitCost
            if (cost == null) {
                missingCost++
                return@forEach
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
                sellingPrice = price.setScale(2, RoundingMode.HALF_UP),
                recordedUnitCost = cost.setScale(2, RoundingMode.HALF_UP),
                costToPricePercent = ratio,
                amountAfterRecordedCost = price.subtract(cost).setScale(2, RoundingMode.HALF_UP),
                aboveTarget = targetPercent != null && ratio > targetPercent
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
            costAboveSellingPriceCount = ranked.count { it.amountAfterRecordedCost.signum() < 0 }
        )
    }
}
