package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryQuantityParserTest {
    @Test
    fun parsesPortugueseDecimalCommaAndConvertsGramsToKilograms() {
        assertEquals(
            BigDecimal("0.150"),
            InventoryQuantityParser.parseToStockUnit("150,0", "g", "kg")
        )
    }

    @Test
    fun convertsLitresToMillilitresForStockRecordedInMl() {
        assertEquals(
            BigDecimal("750.000"),
            InventoryQuantityParser.parseToStockUnit("0,750", "L", "ml")
        )
    }

    @Test
    fun keepsTheStockUnitWhenNoConversionIsNeeded() {
        assertEquals(
            BigDecimal("2.500"),
            InventoryQuantityParser.parseToStockUnit("2,500", "unidades", "unidades")
        )
    }

    @Test
    fun rejectsEmptyZeroAndNegativeAmounts() {
        listOf("", "0", "-1").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                InventoryQuantityParser.parseToStockUnit(value, "kg", "kg")
            }
        }
    }

    @Test
    fun rejectsMoreThanThreeDecimalPlacesBeforeConversion() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryQuantityParser.parseToStockUnit("1,2345", "kg", "kg")
        }
    }

    @Test
    fun rejectsIncompatibleUnits() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryQuantityParser.parseToStockUnit("1", "kg", "L")
        }
    }

    @Test
    fun rejectsUnrepresentablePrecisionInStockUnit() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryQuantityParser.parseToStockUnit("0,5", "g", "kg")
        }
    }
}
