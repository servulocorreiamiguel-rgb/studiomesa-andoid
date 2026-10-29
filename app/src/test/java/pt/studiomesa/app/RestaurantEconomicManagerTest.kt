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
        assertEquals(amount("9.1"), result.recordedWasteAsPercentOfFoodCost)
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
        assertNull(RestaurantEconomicManager.analyze(input()).projectedCashIn30Days)
        try {
            RestaurantEconomicManager.analyze(input(cash = "1000"))
            fail("Partial cash inputs must be rejected.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("preenche saldo disponível"))
        }
        val result = RestaurantEconomicManager.analyze(input(cash = "1000", receipts = "4000", due = "3500"))
        assertEquals(amount("1500.00"), result.projectedCashIn30Days)
    }

    @Test fun rejectsCashProjectionAboveSupportedMoneyRange() {
        try {
            RestaurantEconomicManager.analyze(
                input(cash = "999999999,99", receipts = "999999999,99", due = "0")
            )
            fail("An out-of-range projected cash amount must be rejected.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("tesouraria projetada ultrapassa"))
        }
    }

    @Test fun warnsWhenProjectedCashIsZeroOrNegative() {
        val negative = RestaurantEconomicManager.analyze(
            input(cash = "500", receipts = "100", due = "900")
        )
        assertEquals(amount("-300.00"), negative.projectedCashIn30Days)
        assertTrue(negative.warnings.any { it.contains("tesouraria projetada a 30 dias fica a zero ou negativa") })

        val zero = RestaurantEconomicManager.analyze(
            input(cash = "500", receipts = "100", due = "600")
        )
        assertEquals(amount("0.00"), zero.projectedCashIn30Days)
        assertTrue(zero.warnings.any { it.contains("tesouraria projetada a 30 dias fica a zero ou negativa") })

        val positive = RestaurantEconomicManager.analyze(
            input(cash = "500", receipts = "1000", due = "600")
        )
        assertEquals(amount("900.00"), positive.projectedCashIn30Days)
        assertFalse(positive.warnings.any { it.contains("tesouraria projetada a 30 dias fica a zero ou negativa") })
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

    @Test fun reorderRejectsCalculatedStockBeyondTheSupportedDatabaseRange() {
        try {
            RestaurantEconomicManager.recommendReorder("0 | 999999999 | 2 | 0")
            fail("An impossible target stock quantity must be rejected.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("excede o limite"))
        }
    }

    @Test fun reorderRejectsInvalidDayCounts() {
        try {
            RestaurantEconomicManager.recommendReorder("5 | 3 | -1 | 2")
            fail("Negative lead time must be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }

    @Test fun wasteLogTotalsCostAndGroupsReasons() {
        val report = RestaurantEconomicManager.analyzeWasteLog(
            "Frango | 2 | 6,50 | Validade\nSopa | 1 | 4,00 | Produção"
        )
        assertEquals(amount("17.00"), report.totalCost)
        assertEquals(2, report.entries.size)
        assertEquals("Validade", report.costByReason.first().first)
        assertEquals(amount("13.00"), report.costByReason.first().second)
    }

    @Test fun wasteLogRejectsMissingReasonAndZeroQuantity() {
        listOf("Frango | 2 | 6,50 | ", "Frango | 0 | 6,50 | Validade").forEach { raw ->
            try {
                RestaurantEconomicManager.analyzeWasteLog(raw)
                fail("Invalid waste entry was accepted.")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        }
    }

    @Test fun supplierQuotesCompareSameItemAndUnitByCostPerUnit() {
        val comparisons = RestaurantEconomicManager.compareSupplierQuotes(
            "Tomate | Fornecedor A | kg | 10 | 16,00\nTomate | Fornecedor B | kg | 5 | 7,50"
        )
        assertEquals(1, comparisons.size)
        assertEquals("Tomate", comparisons[0].itemName)
        assertEquals("Fornecedor B", comparisons[0].options.first().supplierName)
        assertEquals(amount("1.5000"), comparisons[0].options.first().pricePerUnit)
    }

    @Test fun wasteLogRejectsDerivedCostsAboveTheSupportedMoneyRange() {
        val invalidLogs = listOf(
            "A | 999999999,99 | 999999999,99 | Validade",
            "A | 600000000 | 1 | Validade\nB | 500000000 | 1 | Produção"
        )
        invalidLogs.forEach { raw ->
            try {
                RestaurantEconomicManager.analyzeWasteLog(raw)
                fail("An out-of-range waste cost must be rejected.")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message.orEmpty().contains("limite monetário suportado"))
            }
        }
    }

    @Test fun supplierQuotesRejectDuplicateSupplierAndInvalidPackSize() {
        listOf(
            "Tomate | A | kg | 10 | 16\nTomate | A | kg | 5 | 7",
            "Tomate | A | kg | 0 | 16"
        ).forEach { raw ->
            try {
                RestaurantEconomicManager.compareSupplierQuotes(raw)
                fail("Invalid supplier quote was accepted.")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        }
    }

    @Test fun supplierQuotesRejectCalculatedUnitPricesAboveTheSupportedRange() {
        try {
            RestaurantEconomicManager.compareSupplierQuotes("Sal | Fornecedor A | kg | 0,0001 | 999999999")
            fail("An out-of-range unit price must be rejected.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("preço por unidade calculado"))
        }
    }

    @Test fun budgetComparisonLabelsVarianceWithoutAssumingItIsFavourable() {
        val rows = RestaurantEconomicManager.compareBudget(
            "Vendas | 10000 | 9500\nPessoal | 2000 | 2200"
        )
        assertEquals(amount("-500.00"), rows[0].variance)
        assertEquals(amount("-5.0"), rows[0].variancePercent)
        assertEquals(amount("200.00"), rows[1].variance)
        assertEquals(amount("10.0"), rows[1].variancePercent)
    }

    @Test fun usageVarianceCalculatesExcessConsumptionCost() {
        val rows = RestaurantEconomicManager.compareTheoreticalAndActualUsage(
            "Frango | 20 | 23 | 6,50\nQueijo | 5 | 4 | 8,00"
        )
        assertEquals(amount("3.000"), rows[0].varianceQuantity)
        assertEquals(amount("19.50"), rows[0].varianceCost)
        assertEquals(amount("-1.000"), rows[1].varianceQuantity)
        assertEquals(amount("-8.00"), rows[1].varianceCost)
    }


    @Test fun usageVarianceRejectsDerivedCostImpactAboveTheSupportedRange() {
        try {
            RestaurantEconomicManager.compareTheoreticalAndActualUsage(
                "Frango | 0 | 999999999,99 | 999999999,99"
            )
            fail("An out-of-range consumption impact must be rejected.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("impacto monetário calculado"))
        }
    }

    @Test fun labourPlanRejectsDerivedPayrollCostAboveTheSupportedRange() {
        try {
            RestaurantEconomicManager.analyzeLabourPlan("2 | 999999999 | 8000 | 25")
            fail("An out-of-range payroll amount must be rejected.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("custo calculado das horas"))
        }
    }

    @Test fun aBreakEvenEstimateOutsideTheSupportedRangeIsReportedAsUnavailable() {
        val result = RestaurantEconomicManager.analyze(
            input(
                sales = "999999999,99",
                food = "999999989,99",
                payroll = "0",
                fixed = "999999999,99",
                variable = "0"
            )
        )
        assertNull(result.estimatedBreakEvenSales)
        assertTrue(result.warnings.any { it.contains("ponto de equilíbrio estimado ultrapassa o limite") })
    }

    @Test fun rejectsOperatingResultOutsideTheSupportedMoneyRange() {
        try {
            RestaurantEconomicManager.analyze(
                input(sales = "1", food = "999999999", payroll = "999999999", fixed = "999999999", variable = "999999999")
            )
            fail("An out-of-range operating result must be rejected.")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("resultado operacional calculado ultrapassa"))
        }
    }

    @Test fun labourPlanCalculatesEstimatedCostAndHoursAtTarget() {
        val result = RestaurantEconomicManager.analyzeLabourPlan("120 | 12,50 | 8000 | 25")
        assertEquals(amount("1500.00"), result.estimatedPayrollCost)
        assertEquals(amount("18.8"), result.estimatedPayrollPercent)
        assertEquals(amount("160.00"), result.hoursAtTarget)
        assertEquals(amount("0.00"), result.hoursAboveTarget)
    }

    @Test fun labourPlanRejectsMissingOrInvalidSalesAndTarget() {
        listOf("120 | 12,50 | 0 | 25", "120 | 12,50 | 8000 | 101").forEach { raw ->
            try {
                RestaurantEconomicManager.analyzeLabourPlan(raw)
                fail("Invalid labour plan was accepted.")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        }
    }

}
