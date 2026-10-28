package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class RestaurantEconomicManagerTest {
    private fun amount(value: String) = BigDecimal(value)

    private fun input(
        sales: String = "10000.00",
        food: String = "3000.00",
        payroll: String = "2500.00",
        fixed: String = "2000.00",
        variable: String = "500.00",
        targetFoodPercent: String = "30",
        waste: String? = null,
        cash: String? = null,
        receipts: String? = null,
        due: String? = null,
        weeks: List<String> = emptyList()
    ) = EconomicManagerInput(
        monthlyNetSales = amount(sales),
        foodCostConsumedIncludingWaste = amount(food),
        monthlyPayroll = amount(payroll),
        otherFixedCosts = amount(fixed),
        otherVariableCosts = amount(variable),
        targetFoodCostPercent = amount(targetFoodPercent),
        recordedWasteCost = waste?.let(::amount),
        cashAvailableNow = cash?.let(::amount),
        expectedReceiptsNext30Days = receipts?.let(::amount),
        paymentsDueNext30Days = due?.let(::amount),
        weeklyNetSalesHistory = weeks.map(::amount)
    )

    @Test fun calculatesFoodLaborPrimeCostAndOperatingResult() {
        val result = RestaurantEconomicManager.analyze(input())
        assertEquals(amount("30.0"), result.foodCostPercent)
        assertEquals(amount("25.0"), result.payrollPercent)
        assertEquals(amount("55.0"), result.primeCostPercent)
        assertEquals(amount("2000.00"), result.estimatedOperatingResult)
        assertEquals(amount("6923.08"), result.estimatedBreakEvenSales)
        assertEquals(amount("0.00"), result.gapToBreakEven)
    }

    @Test fun flagsLossAndDoesNotUseRecordedWasteTwice() {
        val result = RestaurantEconomicManager.analyze(
            input(sales = "5000", food = "2200", payroll = "2000", fixed = "1000", variable = "300", waste = "200")
        )
        assertEquals(amount("-500.00"), result.estimatedOperatingResult)
        assertEquals(amount("4.0"), result.recordedWasteAsPercentOfSales)
        assertTrue(result.warnings.any { it.contains("negativo") })
        assertTrue(result.warnings.any { it.contains("não é subtraído novamente") })
    }

    @Test fun requiresComparableNonZeroSalesAndValidCostTarget() {
        try {
            RestaurantEconomicManager.analyze(input(sales = "0"))
            fail("Sales of zero must not produce financial ratios.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("superiores a zero"))
        }
        try {
            RestaurantEconomicManager.analyze(input(targetFoodPercent = "101"))
            fail("A target above 100% is invalid.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("objetivo"))
        }
    }

    @Test fun cashForecastRequiresAllThreeInputsAndCalculatesManualProjection() {
        assertNull(RestaurantEconomicManager.analyze(input(cash = "1000")).projectedCashIn30Days)
        val result = RestaurantEconomicManager.analyze(input(cash = "1000", receipts = "4000", due = "3500"))
        assertEquals(amount("1500.00"), result.projectedCashIn30Days)
    }

    @Test fun weeklyForecastRequiresFourComparableObservationsAndAveragesLastFour() {
        assertNull(RestaurantEconomicManager.analyze(input(weeks = listOf("1000", "2000", "3000"))).forecastNextWeekSales)
        val result = RestaurantEconomicManager.analyze(
            input(weeks = listOf("1000", "4000", "5000", "6000", "7000"))
        )
        assertEquals(amount("5500.00"), result.forecastNextWeekSales)
    }

    @Test fun parsesEuropeanDecimalNotationWithoutAcceptingNegativeOrMalformedValues() {
        assertEquals(amount("12.50"), RestaurantEconomicManager.parseNonNegative("12,50", "teste"))
        listOf("-10", "12.3.4", "", "999999999999999999999999999999999").forEach { invalid ->
            try {
                RestaurantEconomicManager.parseNonNegative(invalid, "teste")
                fail("Invalid input was accepted: $invalid")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        }
    }

    @Test fun recipeCostIncludesIngredientWasteAndSuggestsPriceAtChosenTarget() {
        val result = RestaurantEconomicManager.costRecipe(
            "Farinha | 0,2 | 2,50 | 5\nMolho | 0,1 | 4,00 | 0",
            amount("30")
        )
        assertEquals(amount("0.93"), result.totalCostPerPortion)
        assertEquals(amount("3.09"), result.suggestedPriceAtTarget)
        assertEquals(2, result.ingredients.size)
    }

    @Test fun recipeCostRejectsMalformedLinesAndWasteOfOneHundredPercent() {
        listOf(
            "Farinha | 0,2 | 2,50",
            "Farinha | 0,2 | 2,50 | 100"
        ).forEach { recipe ->
            try {
                RestaurantEconomicManager.costRecipe(recipe, amount("30"))
                fail("Malformed recipe was accepted.")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        }
    }

    @Test fun reorderQuantityAccountsForLeadTimeAndSafetyStockWithoutOrdering() {
        val result = RestaurantEconomicManager.recommendReorder("5 | 3 | 4 | 2")
        assertEquals(amount("18.000"), result.targetStockForLeadAndSafetyDays)
        assertEquals(amount("13.000"), result.suggestedOrderQuantity)
        assertEquals(6, result.totalCoverageDays)
        assertEquals(amount("0.000"), RestaurantEconomicManager.recommendReorder("25 | 3 | 4 | 2").suggestedOrderQuantity)
    }

    @Test fun reorderRejectsInvalidDayCounts() {
        try {
            RestaurantEconomicManager.recommendReorder("5 | 3 | -1 | 2")
            fail("Negative lead time must be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
