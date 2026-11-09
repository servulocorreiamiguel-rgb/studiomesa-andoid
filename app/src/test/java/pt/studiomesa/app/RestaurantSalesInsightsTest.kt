package pt.studiomesa.app

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantSalesInsightsTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val timeZone = TimeZone.getTimeZone(zone)

    private fun millis(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun order(
        date: LocalDate,
        amount: Double = 10.0,
        status: String = "Concluído",
        quantity: Int = 1
    ) = SalesAnalysisOrder(
        createdAtMillis = millis(date),
        status = status,
        unitAmount = amount,
        quantity = quantity
    )

    @Test
    fun recognizesMandatoryAndMovablePortugueseNationalHolidays() {
        assertEquals("Ano Novo", RestaurantSalesInsights.holidayName(LocalDate.of(2026, 1, 1)))
        assertEquals("Sexta-feira Santa", RestaurantSalesInsights.holidayName(LocalDate.of(2026, 4, 3)))
        assertEquals("Domingo de Páscoa", RestaurantSalesInsights.holidayName(LocalDate.of(2026, 4, 5)))
        assertEquals("Dia da Liberdade", RestaurantSalesInsights.holidayName(LocalDate.of(2026, 4, 25)))
        assertEquals("Corpo de Deus", RestaurantSalesInsights.holidayName(LocalDate.of(2026, 6, 4)))
        assertEquals("Natal", RestaurantSalesInsights.holidayName(LocalDate.of(2026, 12, 25)))
        assertEquals("Carnaval (facultativo)", RestaurantSalesInsights.holidayName(LocalDate.of(2026, 2, 17)))
    }

    @Test
    fun parsesAndFormatsMunicipalHolidaysAndLocalEvents() {
        val parsed = RestaurantSalesInsights.parseSpecialDays(
            """
                2027-09-15 | Festa local
                2027-06-24 | São João
                linha inválida
                2101-01-01 | Fora do intervalo
                2027-13-01 | Data impossível
            """.trimIndent()
        )

        assertEquals(2, parsed.size)
        assertEquals(LocalDate.of(2027, 6, 24), parsed.first().date)
        assertEquals(
            "2027-06-24 | São João\n2027-09-15 | Festa local",
            RestaurantSalesInsights.formatSpecialDays(parsed)
        )
        assertEquals(
            "São João",
            RestaurantSalesInsights.holidayName(LocalDate.of(2027, 6, 24), parsed)
        )
    }

    @Test
    fun combinesAConfiguredEventWithAnOfficialHolidayWithoutLosingEitherName() {
        val date = LocalDate.of(2027, 4, 25)
        val configured = listOf(RestaurantSpecialDay(date, "Feira da cidade"))
        val name = RestaurantSalesInsights.holidayName(date, configured)

        assertTrue(name.orEmpty().contains("Dia da Liberdade"))
        assertTrue(name.orEmpty().contains("Feira da cidade"))
    }

    @Test
    fun comparesAllWeekdaysAndMarksOnlyRepeatedHighTrafficPatterns() {
        val today = LocalDate.of(2026, 11, 9)
        val orders = buildList {
            for (weeksBack in 1L..4L) {
                val monday = today.minusWeeks(weeksBack)
                add(order(monday, amount = 10.0))
                add(order(monday.plusDays(4), amount = 15.0))
                add(order(monday.plusDays(4), amount = 15.0))
            }
        }

        val report = RestaurantSalesInsights.analyze(
            orders = orders,
            daysAhead = 7,
            nowMillis = millis(today, hour = 13),
            timeZone = timeZone
        )

        assertEquals(8, report.observedDays)
        assertEquals(12, report.completedOrderLines)
        assertEquals(7, report.weekdaySales.size)

        val monday = report.weekdaySales.single { it.dayOfWeek == DayOfWeek.MONDAY }
        val friday = report.weekdaySales.single { it.dayOfWeek == DayOfWeek.FRIDAY }

        assertEquals(BigDecimal("10.00"), monday.averageDailyNominalValue)
        assertEquals(BigDecimal("30.00"), friday.averageDailyNominalValue)
        assertFalse(monday.isUsuallyHighTraffic)
        assertTrue(friday.isUsuallyHighTraffic)
        assertEquals("sexta-feira", report.mostRevenueWeekday)
        assertEquals("sexta-feira", report.highestTrafficWeekday)
    }

    @Test
    fun forecastUsesComparableWeekdaysAndCapsTrendAdjustment() {
        val today = LocalDate.of(2026, 11, 9)
        val tomorrow = today.plusDays(1)
        val orders = (1L..8L).map { weeksBack ->
            val historicalDate = tomorrow.minusWeeks(weeksBack)
            val amount = if (weeksBack <= 4L) 120.0 else 100.0
            order(historicalDate, amount = amount)
        }

        val forecast = RestaurantSalesInsights.analyze(
            orders = orders,
            daysAhead = 1,
            nowMillis = millis(today, hour = 13),
            timeZone = timeZone
        ).forecasts.single()

        assertEquals(tomorrow, forecast.date)
        assertEquals(8, forecast.comparableDays)
        assertEquals(SalesForecastConfidence.HIGH, forecast.confidence)
        assertEquals(0, forecast.trendAdjustmentPercent?.compareTo(BigDecimal("15")))
        assertEquals(BigDecimal("126.50"), forecast.nominalValue)
        assertFalse(forecast.isHighTraffic)
    }

    @Test
    fun holidayForecastUsesPastOccurrencesOfTheSameHolidayWhenThereAreEnough() {
        val today = LocalDate.of(2027, 4, 1)
        val orders = listOf(
            order(LocalDate.of(2025, 4, 25), amount = 200.0),
            order(LocalDate.of(2026, 4, 25), amount = 240.0)
        )

        val report = RestaurantSalesInsights.analyze(
            orders = orders,
            daysAhead = 30,
            nowMillis = millis(today, hour = 13),
            timeZone = timeZone
        )
        val holidayForecast = report.forecasts.single { it.date == LocalDate.of(2027, 4, 25) }

        assertEquals("Dia da Liberdade", holidayForecast.specialDayName)
        assertTrue(holidayForecast.holidayEffectMeasured)
        assertEquals(2, holidayForecast.comparableDays)
        assertEquals(BigDecimal("220.00"), holidayForecast.nominalValue)
        assertEquals(SalesForecastConfidence.LOW, holidayForecast.confidence)
    }

    @Test
    fun configuredEventIsShownButItsEffectIsNotClaimedWithoutRepeatHistory() {
        val today = LocalDate.of(2026, 11, 9)
        val previousTuesday = today.minusWeeks(1).plusDays(1)
        val targetTuesday = today.plusDays(1)
        val configured = listOf(
            RestaurantSpecialDay(previousTuesday, "Jogo local"),
            RestaurantSpecialDay(targetTuesday, "Jogo local")
        )
        val report = RestaurantSalesInsights.analyze(
            orders = listOf(order(previousTuesday, amount = 50.0)),
            daysAhead = 1,
            nowMillis = millis(today, hour = 13),
            timeZone = timeZone,
            configuredSpecialDays = configured
        )
        val forecast = report.forecasts.single()

        assertEquals("Jogo local", forecast.specialDayName)
        assertFalse(forecast.holidayEffectMeasured)
        assertTrue(forecast.explanation.contains("efeito específico não foi estimado"))
    }

    @Test
    fun excludesTodayFutureCancelledUndatedAndInvalidOrdersFromHistoricalForecasts() {
        val today = LocalDate.of(2026, 11, 9)
        val orders = listOf(
            order(today.minusDays(1), amount = 12.0),
            order(today, amount = 900.0),
            order(today.plusDays(1), amount = 800.0),
            SalesAnalysisOrder(0L, "Concluído", 700.0),
            order(today.minusDays(1), amount = Double.NaN),
            order(today.minusDays(1), amount = 600.0, status = "Cancelado")
        )

        val report = RestaurantSalesInsights.analyze(
            orders = orders,
            daysAhead = 7,
            nowMillis = millis(today, hour = 13),
            timeZone = timeZone
        )

        assertEquals(1, report.observedDays)
        assertEquals(1, report.completedOrderLines)
        assertEquals(1, report.invalidOrderLines)
        assertEquals(2, report.excludedUndatedOrFutureLines)
    }

    @Test
    fun doesNotInventForecastWhenThereAreNoValidCompletedOrders() {
        val today = LocalDate.of(2026, 11, 9)
        val report = RestaurantSalesInsights.analyze(
            orders = emptyList(),
            daysAhead = 7,
            nowMillis = millis(today, hour = 13),
            timeZone = timeZone
        )

        assertFalse(report.hasUsableHistory)
        assertEquals(7, report.forecasts.size)
        assertTrue(report.forecasts.all { it.nominalValue == null })
        assertTrue(report.forecasts.all { it.confidence == SalesForecastConfidence.NONE })
    }

    @Test
    fun rejectsForecastHorizonsOutsideOneToThirtyDays() {
        val today = LocalDate.of(2026, 11, 9)
        assertTrue(runCatching {
            RestaurantSalesInsights.analyze(emptyList(), daysAhead = 0, nowMillis = millis(today))
        }.isFailure)
        assertTrue(runCatching {
            RestaurantSalesInsights.analyze(emptyList(), daysAhead = 31, nowMillis = millis(today))
        }.isFailure)
    }
}
