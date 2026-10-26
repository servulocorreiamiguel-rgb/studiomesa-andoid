package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RestaurantPriceCalculatorTest {
    private fun bd(value: String) = BigDecimal(value)

    private val ingredients = listOf(
        RestaurantPriceCalculator.Ingredient("Arroz", bd("100"), bd("0.02"))
    )

    @Test
    fun calculatesCostMarginAndVatDeterministically() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients = ingredients,
            wastePercent = bd("10"),
            packagingCost = bd("0.50"),
            otherVariableCost = bd("0.30"),
            targetGrossMarginPercent = bd("60"),
            vatPercent = bd("13")
        )

        assertEquals(bd("2.00"), result.ingredientCost)
        assertEquals(bd("0.20"), result.wasteCost)
        assertEquals(bd("3.00"), result.totalCost)
        assertEquals(bd("7.50"), result.suggestedPriceExcludingVat)
        assertEquals(bd("0.98"), result.vatAmount)
        assertEquals(bd("8.48"), result.suggestedPriceIncludingVat)
        assertEquals(bd("60.00"), result.estimatedGrossMarginPercent)
    }

    @Test
    fun rejectsEmptyIngredients() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(
                emptyList(), bd("0"), bd("0"), bd("0"), bd("50"), bd("0")
            )
        }
    }

    @Test
    fun rejectsNegativeCostsAndQuantities() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(
                listOf(RestaurantPriceCalculator.Ingredient("Arroz", bd("-1"), bd("2"))),
                bd("0"), bd("0"), bd("0"), bd("50"), bd("0")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(
                listOf(RestaurantPriceCalculator.Ingredient("Arroz", bd("1"), bd("-2"))),
                bd("0"), bd("0"), bd("0"), bd("50"), bd("0")
            )
        }
    }

    @Test
    fun rejectsImpossibleMarginAndInvalidVat() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("0"), bd("0"), bd("0"), bd("100"), bd("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("0"), bd("0"), bd("0"), bd("50"), bd("101"))
        }
    }

    @Test
    fun rejectsZeroTotalCostBecausePriceSuggestionWouldBeMisleading() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(
                listOf(RestaurantPriceCalculator.Ingredient("Água", bd("1"), bd("0"))),
                bd("0"), bd("0"), bd("0"), bd("50"), bd("0")
            )
        }
    }

    @Test
    fun zeroTargetMarginReturnsCostPriceBeforeVat() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients, bd("0"), bd("0"), bd("1"), bd("0"), bd("0")
        )
        assertEquals(bd("3.00"), result.suggestedPriceExcludingVat)
        assertEquals(bd("0.00"), result.estimatedGrossMarginPercent)
    }
}
