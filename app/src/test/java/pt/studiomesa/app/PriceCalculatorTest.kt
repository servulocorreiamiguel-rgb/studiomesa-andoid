package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PriceCalculatorTest {
    @Test
    fun roundsPriceToCentsBeforeCalculatingProfitAndMargin() {
        val result = PriceCalculator.calculate("3,20", "30")

        assertEquals(BigDecimal("3.20"), result.cost)
        assertEquals(BigDecimal("10.67"), result.suggestedPrice)
        assertEquals(BigDecimal("7.47"), result.grossProfit)
        assertEquals(BigDecimal("30.0"), result.actualCostPercent)
        assertEquals(BigDecimal("70.0"), result.grossMarginPercent)
    }

    @Test
    fun acceptsPortugueseDecimalComma() {
        val result = PriceCalculator.calculate("1,00", "30,0")
        assertEquals(BigDecimal("3.33"), result.suggestedPrice)
        assertEquals(BigDecimal("2.33"), result.grossProfit)
    }

    @Test
    fun hundredPercentCostMeansNoGrossProfit() {
        val result = PriceCalculator.calculate("12.99", "100")
        assertEquals(BigDecimal("12.99"), result.suggestedPrice)
        assertEquals(BigDecimal("0.00"), result.grossProfit)
        assertEquals(BigDecimal("0.0"), result.grossMarginPercent)
    }

    @Test
    fun rejectsZeroCost() {
        val error = assertThrows(PriceCalculationException::class.java) {
            PriceCalculator.calculate("0", "30")
        }
        assertEquals(PriceInputField.COST, error.field)
    }

    @Test
    fun rejectsCostWithMoreThanTwoMeaningfulDecimalPlaces() {
        val error = assertThrows(PriceCalculationException::class.java) {
            PriceCalculator.calculate("3.201", "30")
        }
        assertEquals(PriceInputField.COST, error.field)
    }

    @Test
    fun rejectsZeroOrNegativeTargetPercentage() {
        listOf("0", "-2").forEach { percentage ->
            val error = assertThrows(PriceCalculationException::class.java) {
                PriceCalculator.calculate("3.20", percentage)
            }
            assertEquals(PriceInputField.TARGET_COST_PERCENT, error.field)
        }
    }

    @Test
    fun rejectsTargetPercentageAboveOneHundred() {
        val error = assertThrows(PriceCalculationException::class.java) {
            PriceCalculator.calculate("3.20", "100.01")
        }
        assertEquals(PriceInputField.TARGET_COST_PERCENT, error.field)
    }

    @Test
    fun rejectsExcessivelyLongCostInputBeforeParsing() {
        val error = assertThrows(PriceCalculationException::class.java) {
            PriceCalculator.calculate("9".repeat(65), "30")
        }
        assertEquals(PriceInputField.COST, error.field)
    }

    @Test
    fun rejectsExcessivelyLongPercentageInputBeforeParsing() {
        val error = assertThrows(PriceCalculationException::class.java) {
            PriceCalculator.calculate("3.20", "9".repeat(65))
        }
        assertEquals(PriceInputField.TARGET_COST_PERCENT, error.field)
    }

    @Test
    fun rejectsInvalidAndNonFiniteNumericText() {
        listOf("", "1.2.3", "NaN", "Infinity", "1e3").forEach { value ->
            assertThrows(PriceCalculationException::class.java) {
                PriceCalculator.calculate(value, "30")
            }
        }
    }
}
