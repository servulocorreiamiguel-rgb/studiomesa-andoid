package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryUnitConverterTest {
    @Test
    fun convertsGramsIntoKilogramsExactly() {
        assertEquals(
            BigDecimal("0.150"),
            InventoryUnitConverter.convertToStockUnit(
                BigDecimal("150"), "g", "kg"
            )
        )
    }

    @Test
    fun convertsKilogramsIntoGrams() {
        assertEquals(
            BigDecimal("1500.000"),
            InventoryUnitConverter.convertToStockUnit(
                BigDecimal("1.5"), "kg", "g"
            )
        )
    }

    @Test
    fun convertsMillilitresIntoLitres() {
        assertEquals(
            BigDecimal("0.750"),
            InventoryUnitConverter.convertToStockUnit(
                BigDecimal("750"), "ml", "L"
            )
        )
    }

    @Test
    fun allowsEquivalentPortugueseUnitNames() {
        assertEquals(
            BigDecimal("0.250"),
            InventoryUnitConverter.convertToStockUnit(
                BigDecimal("250"), "gramas", "quilogramas"
            )
        )
    }

    @Test
    fun doesNotConvertMassIntoVolume() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryUnitConverter.convertToStockUnit(
                BigDecimal("1"), "kg", "L"
            )
        }
    }

    @Test
    fun doesNotConvertUnrelatedCustomUnits() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryUnitConverter.convertToStockUnit(
                BigDecimal("1"), "caixa", "unidades"
            )
        }
    }

    @Test
    fun rejectsConversionsThatWouldLoseDatabasePrecision() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryUnitConverter.convertToStockUnit(
                BigDecimal("0.5"), "g", "kg"
            )
        }
    }

    @Test
    fun rejectsZeroNegativeAndExcessiveValues() {
        listOf(BigDecimal.ZERO, BigDecimal("-1"), BigDecimal("1000000000"))
            .forEach { amount ->
                assertThrows(IllegalArgumentException::class.java) {
                    InventoryUnitConverter.convertToStockUnit(amount, "kg", "kg")
                }
            }
    }

    @Test
    fun exposesOnlyCompatibleEntryUnits() {
        assertEquals(listOf("kg", "g"), InventoryUnitConverter.compatibleUnits("kg"))
        assertEquals(listOf("ml", "L"), InventoryUnitConverter.compatibleUnits("ml"))
        assertEquals(listOf("unidades"), InventoryUnitConverter.compatibleUnits("unidades"))
        assertEquals(listOf("caixa"), InventoryUnitConverter.compatibleUnits("caixa"))
    }
}
