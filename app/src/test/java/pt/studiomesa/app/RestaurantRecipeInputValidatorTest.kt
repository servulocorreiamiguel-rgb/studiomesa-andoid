package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RestaurantRecipeInputValidatorTest {
    @Test
    fun parsesCommaAndDotDecimalSeparators() {
        assertEquals(
            BigDecimal("0.300"),
            RestaurantRecipeInputValidator.parseQuantityPerPortion("0,300")
        )
        assertEquals(
            BigDecimal("5.25"),
            RestaurantRecipeInputValidator.parseWastePercent("5,25")
        )
    }

    @Test
    fun computesConservativeRequiredStockIncludingPreparationWaste() {
        val required = RestaurantRecipeInputValidator.requiredStockForOrder(
            BigDecimal("0.300"), 2, BigDecimal("5.00")
        )
        assertEquals(BigDecimal("0.632"), required)
    }

    @Test
    fun computesRequiredStockWithoutWaste() {
        assertEquals(
            BigDecimal("0.200"),
            RestaurantRecipeInputValidator.requiredStockForOrder(
                BigDecimal("0.100"), 2, BigDecimal.ZERO
            )
        )
    }

    @Test
    fun roundsFractionalMilligramsUpToTheNextThousandth() {
        assertEquals(
            BigDecimal("0.001"),
            RestaurantRecipeInputValidator.requiredStockForOrder(
                BigDecimal("0.0001"), 1, BigDecimal.ZERO
            )
        )
    }

    @Test
    fun rejectsInvalidQuantityInputs() {
        listOf("", "0", "-1", "1.2345", "1e2", "NaN", "1000000000")
            .forEach { raw ->
                assertThrows(IllegalArgumentException::class.java) {
                    RestaurantRecipeInputValidator.parseQuantityPerPortion(raw)
                }
            }
    }

    @Test
    fun rejectsInvalidWastePercentages() {
        listOf("-1", "100", "100.01", "1.234", "1e2", "")
            .forEach { raw ->
                assertThrows(IllegalArgumentException::class.java) {
                    RestaurantRecipeInputValidator.parseWastePercent(raw)
                }
            }
    }

    @Test
    fun rejectsInvalidPortionCountsAndDirectInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantRecipeInputValidator.requiredStockForOrder(
                BigDecimal("0.100"), 0, BigDecimal.ZERO
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantRecipeInputValidator.requiredStockForOrder(
                BigDecimal("0.100"), 2, BigDecimal("100")
            )
        }
    }
}
