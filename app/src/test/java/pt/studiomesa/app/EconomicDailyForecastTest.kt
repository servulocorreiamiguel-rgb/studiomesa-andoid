package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

class EconomicDailyForecastTest {
    private val today = LocalDate.of(2026, 11, 13)
    private val zone = ZoneOffset.UTC

    private fun order(date: LocalDate, id: String = "dish-1", quantity: Int = 1,
                     amount: String = "10.00", status: String = "Concluído") =
        EconomicForecastOrderLine(
            createdAtMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            status = status,
            menuItemId = id,
            itemName = "Prato",
            quantity = quantity,
            unitAmount = BigDecimal(amount)
        )

    @Test
    fun revenueForecastUsesAverageOfComparableWeekdays() {
        val report = EconomicDailyForecastCalculator.calculate(
            orders = listOf(
                order(today.minusDays(7), amount = "10.00"),
                order(today.minusDays(14), amount = "20.00")
            ),
            recipes = emptyList(),
            today = today,
            daysAhead = 7,
            zoneId = zone
        )

        val friday = report.days.last()
        assertEquals(today.plusDays(7), friday.date)
        assertEquals(BigDecimal("15.00"), friday.expectedRevenue)
        assertEquals(2, friday.comparableDays)
    }

    @Test
    fun ingredientForecastAppliesRecordedWasteAndAveragesAcrossSevenDays() {
        val report = EconomicDailyForecastCalculator.calculate(
            orders = listOf(
                order(today.minusDays(7), quantity = 2),
                order(today.minusDays(14), quantity = 4)
            ),
            recipes = listOf(
                EconomicRecipeIngredient(
                    menuItemId = "dish-1",
                    ingredientName = "Farinha",
                    unit = "kg",
                    quantityPerPortion = BigDecimal("0.100"),
                    wastePercent = BigDecimal("10.00")
                )
            ),
            today = today,
            daysAhead = 7,
            zoneId = zone
        )

        assertEquals(1, report.ingredientAverages.size)
        val flour = report.ingredientAverages.single()
        // Friday forecast = average 3 portions x 0.1 kg / (1 - 10% waste).
        assertEquals(BigDecimal("0.333"), flour.expectedSevenDayQuantity)
        assertEquals(BigDecimal("0.048"), flour.averageDailyQuantity)
    }

    @Test
    fun ignoresOpenCancelledAndCurrentDayOrders() {
        val report = EconomicDailyForecastCalculator.calculate(
            orders = listOf(
                order(today.minusDays(7), amount = "10.00"),
                order(today.minusDays(14), amount = "20.00", status = "Cancelado"),
                order(today, amount = "900.00"),
                order(today.minusDays(21), amount = "800.00", status = "Aberto")
            ),
            recipes = emptyList(),
            today = today,
            daysAhead = 7,
            zoneId = zone
        )

        assertEquals(BigDecimal("10.00"), report.days.last().expectedRevenue)
        assertEquals(1, report.days.last().comparableDays)
    }

    @Test
    fun missingRecipeNeverInventsIngredientQuantities() {
        val report = EconomicDailyForecastCalculator.calculate(
            orders = listOf(order(today.minusDays(7), id = "unmapped")),
            recipes = emptyList(),
            today = today,
            daysAhead = 7,
            zoneId = zone
        )

        assertTrue(report.ingredientAverages.isEmpty())
        assertEquals(listOf("unmapped"), report.unmappedMenuItems)
        assertFalse(report.days.last().expectedRevenue == null)
    }
}
