package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RestaurantRecipeCostCalculatorTest {
    @Test
    fun estimatesCostUsingStockQuantityRoundedUpForWaste() {
        val result = RestaurantRecipeCostCalculator.estimate(
            listOf(
                RestaurantRecipeCostInput(
                    inventoryItemId = "flour",
                    quantityPerPortion = BigDecimal("0.100"),
                    wastePercent = BigDecimal("5.00"),
                    unitCostPerUnit = BigDecimal("2.0000")
                )
            )
        )

        assertEquals(BigDecimal("0.212"), result.lineCostsPerPortion["flour"])
        assertEquals(BigDecimal("0.212"), result.knownCostPerPortion)
        assertTrue(result.isComplete)
    }

    @Test
    fun reportsKnownCostAsPartialWhenAnyPurchaseCostIsMissing() {
        val result = RestaurantRecipeCostCalculator.estimate(
            listOf(
                RestaurantRecipeCostInput("oil", BigDecimal("0.010"), BigDecimal.ZERO, BigDecimal("4.0000")),
                RestaurantRecipeCostInput("salt", BigDecimal("0.002"), BigDecimal.ZERO, null)
            )
        )

        assertEquals(BigDecimal("0.04"), result.knownCostPerPortion)
        assertEquals(2, result.totalIngredients)
        assertEquals(1, result.ingredientsWithoutCost)
        assertFalse(result.isComplete)
        assertNull(result.lineCostsPerPortion["salt"])
    }

    @Test
    fun zeroPurchaseCostIsKnownAndNotTreatedAsMissing() {
        val result = RestaurantRecipeCostCalculator.estimate(
            listOf(
                RestaurantRecipeCostInput("water", BigDecimal("0.250"), BigDecimal.ZERO, BigDecimal.ZERO)
            )
        )

        assertEquals(BigDecimal.ZERO, result.knownCostPerPortion)
        assertTrue(result.isComplete)
        assertEquals(BigDecimal.ZERO, result.lineCostsPerPortion["water"])
    }

    @Test
    fun calculatesIngredientCostRatioAndAmountBeforeOtherCosts() {
        val result = RestaurantRecipeCostCalculator.estimate(
            listOf(
                RestaurantRecipeCostInput("cheese", BigDecimal("0.100"), BigDecimal.ZERO, BigDecimal("8.0000"))
            )
        )

        assertEquals(BigDecimal("8.0"), result.knownIngredientCostPercent(BigDecimal("10.00")))
        assertEquals(BigDecimal("9.20"), result.amountAfterIngredients(BigDecimal("10.00")))
    }

    @Test
    fun rejectsZeroSellingPriceForPercentage() {
        val result = RestaurantRecipeCostCalculator.estimate(
            listOf(
                RestaurantRecipeCostInput("cheese", BigDecimal("0.100"), BigDecimal.ZERO, BigDecimal("8.0000"))
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            result.knownIngredientCostPercent(BigDecimal.ZERO)
        }
    }

    @Test
    fun rejectsIngredientAndRecipeCostsAboveSupportedMoneyRange() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantRecipeCostCalculator.estimate(
                listOf(
                    RestaurantRecipeCostInput(
                        "expensive",
                        BigDecimal("999999999"),
                        BigDecimal.ZERO,
                        BigDecimal("999999999.9999")
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantRecipeCostCalculator.estimate(
                listOf(
                    RestaurantRecipeCostInput("first", BigDecimal("1"), BigDecimal.ZERO, BigDecimal("600000000")),
                    RestaurantRecipeCostInput("second", BigDecimal("1"), BigDecimal.ZERO, BigDecimal("600000000"))
                )
            )
        }
    }

    @Test
    fun rejectsDuplicateIngredientsAndInvalidCosts() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantRecipeCostCalculator.estimate(
                listOf(
                    RestaurantRecipeCostInput("milk", BigDecimal("0.100"), BigDecimal.ZERO, BigDecimal("1.0000")),
                    RestaurantRecipeCostInput("milk", BigDecimal("0.050"), BigDecimal.ZERO, BigDecimal("1.0000"))
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantRecipeCostCalculator.estimate(
                listOf(
                    RestaurantRecipeCostInput("milk", BigDecimal("0.100"), BigDecimal.ZERO, BigDecimal("-1"))
                )
            )
        }
    }
}
