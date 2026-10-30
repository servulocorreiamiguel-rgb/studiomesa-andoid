package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryUnitCostTest {
    @Test
    fun parsesEuropeanDecimalNotationAndPadsToFourPlaces() {
        assertEquals(
            BigDecimal("12.5000"),
            InventoryUnitCostValidator.parseOrNull(" 12,5 ")
        )
        assertEquals(BigDecimal("0.0000"), InventoryUnitCostValidator.parseOrNull("0"))
        assertNull(InventoryUnitCostValidator.parseOrNull(""))
        assertNull(InventoryUnitCostValidator.parseOrNull("   "))
    }

    @Test
    fun acceptsMaximumSupportedUnitCost() {
        assertEquals(
            BigDecimal("99999999.9999"),
            InventoryUnitCostValidator.parseOrNull("99999999,9999")
        )
    }

    @Test
    fun rejectsNegativeMalformedOverPreciseAndOutOfRangeCosts() {
        listOf(
            "-1", "+1", "1.23456", "1.2.3", "1e3",
            "100000000", "NaN", "Infinity"
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                InventoryUnitCostValidator.parseOrNull(value)
            }
        }
    }

    @Test
    fun rejectsOversizedRawInputBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryUnitCostValidator.parseOrNull(" ".repeat(33))
        }
    }

    @Test
    fun estimatesWasteCostUsingFourDecimalUnitPriceAndRoundsToCents() {
        assertEquals(
            BigDecimal("3.09"),
            InventoryWasteCostCalculator.estimate(
                BigDecimal("0.250"),
                BigDecimal("12.3456")
            )
        )
        assertEquals(
            BigDecimal("0.03"),
            InventoryWasteCostCalculator.estimate(
                BigDecimal("0.500"),
                BigDecimal("0.0500")
            )
        )
    }

    @Test
    fun distinguishesMissingHistoricalCostFromARealZeroCost() {
        assertNull(
            InventoryWasteCostCalculator.estimate(
                BigDecimal("1.000"),
                null
            )
        )
        assertEquals(
            BigDecimal("0.00"),
            InventoryWasteCostCalculator.estimate(
                BigDecimal("1.000"),
                BigDecimal("0.0000")
            )
        )
    }

    @Test
    fun rejectsInvalidQuantityOrUnitCostPrecision() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryWasteCostCalculator.estimate(BigDecimal("-1"), BigDecimal("2.0000"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventoryWasteCostCalculator.estimate(BigDecimal("0.0001"), BigDecimal("2.0000"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InventoryWasteCostCalculator.estimate(BigDecimal("1.000"), BigDecimal("2.00001"))
        }
    }

    @Test
    fun summarisesKnownMovementCostsWithoutInventingMissingValues() {
        val summary = InventoryWasteCostCalculator.summarize(
            listOf(BigDecimal("1.25"), null, BigDecimal("0.00"), BigDecimal("2.50"))
        )
        assertEquals(BigDecimal("3.75"), summary.knownTotal)
        assertEquals(1, summary.movementsWithoutCost)
    }
}
