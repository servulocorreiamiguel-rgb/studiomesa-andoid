package pt.studiomesa.app

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesAnalysisCalculatorTest {
    private val zone = TimeZone.getTimeZone("Europe/Lisbon")

    private fun instant(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.timeInMillis

    @Test
    fun todayUsesLocalMidnightAndExcludesUndatedAndFutureRecords() {
        val now = instant(2026, Calendar.OCTOBER, 29, 12)
        assertTrue(SalesAnalysisCalculator.includes(
            SalesAnalysisPeriod.TODAY,
            instant(2026, Calendar.OCTOBER, 29),
            now,
            zone
        ))
        assertFalse(SalesAnalysisCalculator.includes(
            SalesAnalysisPeriod.TODAY,
            instant(2026, Calendar.OCTOBER, 28, 23, 59),
            now,
            zone
        ))
        assertFalse(SalesAnalysisCalculator.includes(SalesAnalysisPeriod.TODAY, 0L, now, zone))
        assertFalse(SalesAnalysisCalculator.includes(SalesAnalysisPeriod.TODAY, now + 1_000L, now, zone))
    }

    @Test
    fun rollingWindowsAndThisMonthHaveExplicitCalendarBoundaries() {
        val now = instant(2026, Calendar.OCTOBER, 29, 12)
        assertEquals(instant(2026, Calendar.OCTOBER, 23),
            (SalesAnalysisCalculator.startInclusiveMillis(SalesAnalysisPeriod.LAST_7_DAYS, now, zone) ?: -1L))
        assertEquals(instant(2026, Calendar.SEPTEMBER, 30),
            (SalesAnalysisCalculator.startInclusiveMillis(SalesAnalysisPeriod.LAST_30_DAYS, now, zone) ?: -1L))
        assertEquals(instant(2026, Calendar.OCTOBER, 1),
            (SalesAnalysisCalculator.startInclusiveMillis(SalesAnalysisPeriod.THIS_MONTH, now, zone) ?: -1L))
        assertNull(SalesAnalysisCalculator.startInclusiveMillis(SalesAnalysisPeriod.ALL_TIME, now, zone))
        assertTrue(SalesAnalysisCalculator.includes(
            SalesAnalysisPeriod.LAST_7_DAYS,
            instant(2026, Calendar.OCTOBER, 23),
            now,
            zone
        ))
        assertFalse(SalesAnalysisCalculator.includes(
            SalesAnalysisPeriod.LAST_7_DAYS,
            instant(2026, Calendar.OCTOBER, 22, 23, 59),
            now,
            zone
        ))
    }

    @Test
    fun summarySeparatesOrdersCreatedInPeriodFromCurrentActiveOrders() {
        val now = instant(2026, Calendar.OCTOBER, 29, 12)
        val orders = listOf(
            SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 29, 10), "Concluído", 12.50, 2),
            SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 29, 11), "Aberto", 8.00),
            SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 29, 9), "Cancelado", 100.00),
            SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 28, 15), "Concluído", 30.00),
            SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 28, 12), "Em preparação", 9.00),
            SalesAnalysisOrder(0L, "Concluído", 5.00),
            SalesAnalysisOrder(now + 1_000L, "Concluído", 999.00)
        )

        val summary = SalesAnalysisCalculator.summarize(
            orders, SalesAnalysisPeriod.TODAY, now, zone
        )

        assertEquals(2, summary.ordersInPeriod)
        assertEquals(2, summary.activeOrderCount)
        assertEquals(1, summary.completedOrderCount)
        assertEquals(25.0, summary.nominalCompletedValue, 0.001)
        assertEquals(2, summary.ordersWithInvalidTimestampsExcluded)
        assertEquals(0, summary.completedOrdersWithInvalidAmount)
    }

    @Test
    fun allHistoryKeepsUndatedRecordsButExcludesFutureDatedOrders() {
        val now = instant(2026, Calendar.OCTOBER, 29, 12)
        val orders = listOf(
            SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 29, 10), "Concluído", 10.00, 2),
            SalesAnalysisOrder(0L, "Concluído", 4.00),
            SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 29, 11), "Cancelado", 100.00),
            SalesAnalysisOrder(now + 1_000L, "Concluído", 999.00)
        )

        val summary = SalesAnalysisCalculator.summarize(
            orders, SalesAnalysisPeriod.ALL_TIME, now, zone
        )

        assertTrue(SalesAnalysisCalculator.includes(SalesAnalysisPeriod.ALL_TIME, 0L, now, zone))
        assertFalse(SalesAnalysisCalculator.includes(
            SalesAnalysisPeriod.ALL_TIME, now + 1_000L, now, zone
        ))
        assertEquals(2, summary.ordersInPeriod)
        assertEquals(2, summary.completedOrderCount)
        assertEquals(24.0, summary.nominalCompletedValue, 0.001)
        assertEquals(1, summary.ordersWithInvalidTimestampsExcluded)
    }

    @Test
    fun invalidCompletedAmountsAreNotAddedToNominalValue() {
        val now = instant(2026, Calendar.OCTOBER, 29, 12)
        val summary = SalesAnalysisCalculator.summarize(
            listOf(
                SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 29, 10), "Concluído", Double.NaN),
                SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 29, 11), "Concluído", -1.0),
                SalesAnalysisOrder(instant(2026, Calendar.OCTOBER, 29, 11, 30), "Concluído", Double.MAX_VALUE)
            ),
            SalesAnalysisPeriod.TODAY,
            now,
            zone
        )
        assertEquals(3, summary.completedOrderCount)
        assertEquals(0.0, summary.nominalCompletedValue, 0.001)
        assertEquals(3, summary.completedOrdersWithInvalidAmount)
    }
}
