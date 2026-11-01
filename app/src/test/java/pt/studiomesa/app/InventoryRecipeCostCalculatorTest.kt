package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRecipeCostCalculatorTest {
    @Test
    fun calculatesCostFromStockUnitPriceAndRecipeQuantity() {
        val result = InventoryRecipeCostCalculator.calculate(
            listOf(
                InventoryRecipeIngredientInput(
                    "Farinha", "kg", BigDecimal("0.200"), BigDecimal("2.5000"), BigDecimal.ZERO
                ),
                InventoryRecipeIngredientInput(
                    "Molho", "L", BigDecimal("0.100"), BigDecimal("4.0000"), BigDecimal.ZERO
                )
            )
        )
        assertEquals(BigDecimal("0.90"), result.totalCostPerPortion)
        assertTrue(result.isComplete)
    }

    @Test
    fun includesPreparationWasteInIngredientCost() {
        val result = InventoryRecipeCostCalculator.calculate(
            listOf(
                InventoryRecipeIngredientInput(
                    "Peixe", "kg", BigDecimal("0.150"), BigDecimal("12.0000"), BigDecimal("5.00")
                )
            )
        )
        assertEquals(BigDecimal("1.89"), result.totalCostPerPortion)
    }

    @Test
    fun missingCostDoesNotBecomeZeroOrACompleteTotal() {
        val result = InventoryRecipeCostCalculator.calculate(
            listOf(
                InventoryRecipeIngredientInput(
                    "Arroz", "kg", BigDecimal("0.100"), BigDecimal("3.0000"), BigDecimal.ZERO
                ),
                InventoryRecipeIngredientInput(
                    "Água", "L", BigDecimal("0.200"), null, BigDecimal.ZERO
                )
            )
        )
        assertNull(result.totalCostPerPortion)
        assertFalse(result.isComplete)
        assertEquals(listOf("Água"), result.missingCostIngredientNames)
        assertEquals(BigDecimal("0.30"), result.knownCostSubtotal)
    }

    @Test
    fun suggestsPriceOnlyFromACompleteRecipe() {
        val result = InventoryRecipeCostCalculator.calculate(
            listOf(
                InventoryRecipeIngredientInput(
                    "Café", "kg", BigDecimal("0.020"), BigDecimal("18.0000"), BigDecimal.ZERO
                )
            )
        )
        assertEquals(
            BigDecimal("1.20"),
            result.suggestedPriceAtTarget(BigDecimal("30"))
        )
    }

    @Test
    fun acceptsAnExplicitZeroCostWhenAnotherIngredientHasARealCost() {
        val result = InventoryRecipeCostCalculator.calculate(
            listOf(
                InventoryRecipeIngredientInput(
                    "Água", "L", BigDecimal("0.200"), BigDecimal.ZERO.setScale(4), BigDecimal.ZERO
                ),
                InventoryRecipeIngredientInput(
                    "Café", "kg", BigDecimal("0.020"), BigDecimal("18.0000"), BigDecimal.ZERO
                )
            )
        )
        assertTrue(result.isComplete)
        assertEquals(BigDecimal("0.36"), result.totalCostPerPortion)
    }

    @Test
    fun refusesSuggestedPriceWhenAnyCostIsMissing() {
        val result = InventoryRecipeCostCalculator.calculate(
            listOf(
                InventoryRecipeIngredientInput(
                    "Ingrediente", "unidades", BigDecimal("1.000"), null, BigDecimal.ZERO
                )
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            result.suggestedPriceAtTarget(BigDecimal("30"))
        }
    }

    @Test
    fun rejectsInvalidWasteAndQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryRecipeCostCalculator.calculate(
                listOf(
                    InventoryRecipeIngredientInput(
                        "Carne", "kg", BigDecimal.ZERO, BigDecimal("10.0000"), BigDecimal.ZERO
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventoryRecipeCostCalculator.calculate(
                listOf(
                    InventoryRecipeIngredientInput(
                        "Carne", "kg", BigDecimal("0.100"), BigDecimal("10.0000"), BigDecimal("100")
                    )
                )
            )
        }
    }

    @Test
    fun suggestedPriceUsesPreciseSubtotalBeforeRoundingDisplayCost() {
        val result = InventoryRecipeCostCalculator.calculate(
            listOf(
                InventoryRecipeIngredientInput(
                    "Ingrediente", "kg", BigDecimal("0.122"), BigDecimal("3.0000"), BigDecimal.ZERO
                )
            )
        )
        assertEquals(BigDecimal("0.37"), result.totalCostPerPortion)
        assertEquals(BigDecimal("0.36600000"), result.preciseTotalCostPerPortion)
        assertEquals(
            BigDecimal("1.22"),
            result.suggestedPriceAtTarget(BigDecimal("30"))
        )
    }

    @Test
    fun rejectsInvalidTargetFoodCostPercent() {
        val result = InventoryRecipeCostCalculator.calculate(
            listOf(
                InventoryRecipeIngredientInput(
                    "Café", "kg", BigDecimal("0.020"), BigDecimal("18.0000"), BigDecimal.ZERO
                )
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            result.suggestedPriceAtTarget(BigDecimal.ZERO)
        }
    }
}
