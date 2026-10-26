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
    fun rejectsBlankIngredientNames() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(
                listOf(RestaurantPriceCalculator.Ingredient("  ", bd("1"), bd("1"))),
                bd("0"), bd("0"), bd("0"), bd("50"), bd("0")
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
    fun rejectsInvalidWasteAndAdditionalCosts() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("-1"), bd("0"), bd("0"), bd("50"), bd("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("101"), bd("0"), bd("0"), bd("50"), bd("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("0"), bd("-0.01"), bd("0"), bd("50"), bd("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("0"), bd("0"), bd("-0.01"), bd("50"), bd("0"))
        }
    }

    @Test
    fun rejectsImpossibleMarginAndInvalidVat() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("0"), bd("0"), bd("0"), bd("100"), bd("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("0"), bd("0"), bd("0"), bd("-1"), bd("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("0"), bd("0"), bd("0"), bd("50"), bd("101"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("0"), bd("0"), bd("0"), bd("50"), bd("-1"))
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
    fun displayedRoundedComponentsAddUpToDisplayedTotal() {
        val result = RestaurantPriceCalculator.calculate(
            listOf(
                RestaurantPriceCalculator.Ingredient("Ingrediente A", bd("1"), bd("0.004")),
                RestaurantPriceCalculator.Ingredient("Ingrediente B", bd("1"), bd("0.006"))
            ),
            wastePercent = bd("50"),
            packagingCost = bd("0.104"),
            otherVariableCost = bd("0.104"),
            targetGrossMarginPercent = bd("50"),
            vatPercent = bd("0")
        )

        assertEquals(bd("0.01"), result.ingredientCost)
        assertEquals(bd("0.01"), result.wasteCost)
        assertEquals(bd("0.10"), result.packagingCost)
        assertEquals(bd("0.10"), result.otherVariableCost)
        assertEquals(
            result.ingredientCost.add(result.wasteCost)
                .add(result.packagingCost).add(result.otherVariableCost),
            result.totalCost
        )
    }

    @Test
    fun rejectsBlankIngredientNameAndWasteOutsideRange() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(
                listOf(RestaurantPriceCalculator.Ingredient("  ", bd("1"), bd("1"))),
                bd("0"), bd("0"), bd("0"), bd("50"), bd("0")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("-0.01"), bd("0"), bd("0"), bd("50"), bd("0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantPriceCalculator.calculate(ingredients, bd("100.01"), bd("0"), bd("0"), bd("50"), bd("0"))
        }
    }

    @Test
    fun highValidMarginProducesFinitePriceAndZeroVatIsHandled() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients, bd("0"), bd("0"), bd("0"), bd("99.99"), bd("0")
        )
        assertEquals(bd("20000.00"), result.suggestedPriceExcludingVat)
        assertEquals(bd("0.00"), result.vatAmount)
        assertEquals(result.suggestedPriceExcludingVat, result.suggestedPriceIncludingVat)
    }

    @Test
    fun zeroTargetMarginReturnsCostPriceBeforeVat() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients, bd("0"), bd("0"), bd("1"), bd("0"), bd("0")
        )
        assertEquals(bd("3.00"), result.suggestedPriceExcludingVat)
        assertEquals(bd("0.00"), result.estimatedGrossMarginPercent)
    }

    @Test
    fun appliesWasteOnlyToIngredientsAndIncludesAdditionalVariableCosts() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients = ingredients,
            wastePercent = bd("100"),
            packagingCost = bd("0.50"),
            otherVariableCost = bd("0.25"),
            targetGrossMarginPercent = bd("50"),
            vatPercent = bd("100")
        )

        assertEquals(bd("2.00"), result.ingredientCost)
        assertEquals(bd("2.00"), result.wasteCost)
        assertEquals(bd("4.75"), result.totalCost)
        assertEquals(bd("9.50"), result.suggestedPriceExcludingVat)
        assertEquals(bd("9.50"), result.vatAmount)
        assertEquals(bd("19.00"), result.suggestedPriceIncludingVat)
    }

    @Test
    fun roundsCurrencyHalfUpToTwoDecimals() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients = listOf(
                RestaurantPriceCalculator.Ingredient("Ingrediente de teste", bd("1"), bd("0.005"))
            ),
            wastePercent = bd("0"),
            packagingCost = bd("0"),
            otherVariableCost = bd("0"),
            targetGrossMarginPercent = bd("0"),
            vatPercent = bd("0")
        )

        assertEquals(bd("0.01"), result.ingredientCost)
        assertEquals(bd("0.01"), result.totalCost)
        assertEquals(bd("0.01"), result.suggestedPriceExcludingVat)
    }

    @Test
    fun roundsPriceFromExactMarginInputWithoutPrematurePrecisionLoss() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients = listOf(
                RestaurantPriceCalculator.Ingredient("Custo pequeno", bd("1"), bd("0.01"))
            ),
            wastePercent = bd("0"),
            packagingCost = bd("0"),
            otherVariableCost = bd("0"),
            targetGrossMarginPercent = bd("33.33333333"),
            vatPercent = bd("0")
        )

        // The exact unrounded price is just below €0.015, so HALF_UP at currency precision gives €0.01.
        assertEquals(bd("0.01"), result.suggestedPriceExcludingVat)
        assertEquals(bd("0.01"), result.suggestedPriceIncludingVat)
    }

    @Test
    fun roundsVatFromExactRateWithoutPrematurePrecisionLoss() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients = listOf(
                RestaurantPriceCalculator.Ingredient("Custo pequeno", bd("1"), bd("0.03"))
            ),
            wastePercent = bd("0"),
            packagingCost = bd("0"),
            otherVariableCost = bd("0"),
            targetGrossMarginPercent = bd("0"),
            vatPercent = bd("16.66666666")
        )

        // €0.03 × 16.66666666% is just below €0.005 and rounds to €0.00.
        assertEquals(bd("0.03"), result.suggestedPriceExcludingVat)
        assertEquals(bd("0.00"), result.vatAmount)
        assertEquals(bd("0.03"), result.suggestedPriceIncludingVat)
    }

    @Test
    fun calculatesWasteFromUnroundedIngredientSubtotal() {
        val result = RestaurantPriceCalculator.calculate(
            ingredients = listOf(
                RestaurantPriceCalculator.Ingredient("Ingrediente fracionado", bd("1"), bd("0.014"))
            ),
            wastePercent = bd("40"),
            packagingCost = bd("0"),
            otherVariableCost = bd("0"),
            targetGrossMarginPercent = bd("50"),
            vatPercent = bd("0")
        )

        // €0.014 × 40% = €0.0056, which rounds to €0.01.
        assertEquals(bd("0.01"), result.ingredientCost)
        assertEquals(bd("0.01"), result.wasteCost)
        assertEquals(bd("0.02"), result.totalCost)
        assertEquals(bd("0.02"), result.ingredientCost.add(result.wasteCost))
    }
}
