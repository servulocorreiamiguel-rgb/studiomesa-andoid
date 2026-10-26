package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Deterministic, offline cost and selling-price calculator.
 * All monetary outputs are rounded to two decimal places with HALF_UP.
 * The caller must supply verified ingredient costs, quantities and the applicable VAT rate.
 */
object RestaurantPriceCalculator {
    data class Ingredient(
        val name: String,
        val quantityUsed: BigDecimal,
        val costPerUnit: BigDecimal
    )

    data class Result(
        val ingredientCost: BigDecimal,
        val wasteCost: BigDecimal,
        val packagingCost: BigDecimal,
        val otherVariableCost: BigDecimal,
        val totalCost: BigDecimal,
        val suggestedPriceExcludingVat: BigDecimal,
        val vatAmount: BigDecimal,
        val suggestedPriceIncludingVat: BigDecimal,
        val estimatedGrossMarginPercent: BigDecimal
    )

    fun calculate(
        ingredients: List<Ingredient>,
        wastePercent: BigDecimal,
        packagingCost: BigDecimal,
        otherVariableCost: BigDecimal,
        targetGrossMarginPercent: BigDecimal,
        vatPercent: BigDecimal
    ): Result {
        require(ingredients.isNotEmpty()) { "Adiciona pelo menos um ingrediente." }
        require(wastePercent >= BigDecimal.ZERO && wastePercent <= BigDecimal("100")) {
            "O desperdício deve estar entre 0% e 100%."
        }
        require(packagingCost >= BigDecimal.ZERO) { "O custo da embalagem não pode ser negativo." }
        require(otherVariableCost >= BigDecimal.ZERO) { "Os outros custos não podem ser negativos." }
        require(targetGrossMarginPercent >= BigDecimal.ZERO && targetGrossMarginPercent < BigDecimal("100")) {
            "A margem pretendida deve ser igual ou superior a 0% e inferior a 100%."
        }
        require(vatPercent >= BigDecimal.ZERO && vatPercent <= BigDecimal("100")) {
            "A taxa de IVA deve estar entre 0% e 100% e ser confirmada pelo restaurante."
        }
        ingredients.forEach {
            require(it.name.isNotBlank()) { "Cada ingrediente precisa de um nome." }
            require(it.quantityUsed > BigDecimal.ZERO) { "A quantidade usada deve ser superior a zero." }
            require(it.costPerUnit >= BigDecimal.ZERO) { "O custo unitário não pode ser negativo." }
        }

        val ingredientCostRaw = ingredients.fold(BigDecimal.ZERO) { sum, item ->
            sum.add(item.quantityUsed.multiply(item.costPerUnit))
        }
        // Round displayed components independently, then sum those displayed amounts.
        val ingredientCost = money(ingredientCostRaw)
        // Apply waste to the unrounded ingredient subtotal, preserving calculation accuracy.
        val wasteCost = ingredientCostRaw.multiply(wastePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP)
        val packaging = money(packagingCost)
        val other = money(otherVariableCost)
        val total = money(ingredientCost.add(wasteCost).add(packaging).add(other))
        require(total > BigDecimal.ZERO) { "O custo total tem de ser superior a zero para sugerir um preço." }

        val marginFraction = targetGrossMarginPercent.divide(HUNDRED)
        // Round directly to currency precision. An intermediate fixed 8-decimal scale
        // can turn a value just below half a cent into an apparent exact tie.
        val exVat = total.divide(BigDecimal.ONE.subtract(marginFraction), 2, RoundingMode.HALF_UP)
        val vat = exVat.multiply(vatPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP)
        val includingVat = money(exVat.add(vat))
        val estimatedMargin = money(
            exVat.subtract(total).multiply(HUNDRED).divide(exVat, 8, RoundingMode.HALF_UP)
        )

        return Result(
            ingredientCost = ingredientCost,
            wasteCost = wasteCost,
            packagingCost = packaging,
            otherVariableCost = other,
            totalCost = total,
            suggestedPriceExcludingVat = exVat,
            vatAmount = vat,
            suggestedPriceIncludingVat = includingVat,
            estimatedGrossMarginPercent = estimatedMargin
        )
    }

    private fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal("100")
}
