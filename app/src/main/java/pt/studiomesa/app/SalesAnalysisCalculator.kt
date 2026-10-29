package pt.studiomesa.app

import java.util.Calendar
import java.util.TimeZone

internal enum class SalesAnalysisPeriod(val label: String) {
    TODAY("Hoje"),
    LAST_7_DAYS("Últimos 7 dias"),
    LAST_30_DAYS("Últimos 30 dias"),
    THIS_MONTH("Este mês"),
    ALL_TIME("Todo o histórico")
}

internal data class SalesAnalysisOrder(
    val createdAtMillis: Long,
    val status: String,
    val unitAmount: Double,
    val quantity: Int = 1
)

internal data class SalesAnalysisSummary(
    val ordersInPeriod: Int,
    val activeOrderCount: Int,
    val completedOrderCount: Int,
    val nominalCompletedValue: Double,
    val ordersWithInvalidTimestampsExcluded: Int,
    val completedOrdersWithInvalidAmount: Int
)

/**
 * Date-filtered operational summaries for the Analysis screen.
 *
 * The interval is based on the device's local calendar and the order creation
 * timestamp. It is not a payment ledger or an accounting report.
 */
internal object SalesAnalysisCalculator {
    private val activeStatuses = setOf("Aberto", "Em preparação", "Pronto")
    private const val maximumUnitAmount = 99_999_999.99

    fun startInclusiveMillis(
        period: SalesAnalysisPeriod,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (period == SalesAnalysisPeriod.ALL_TIME) return null

        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            SalesAnalysisPeriod.TODAY -> Unit
            SalesAnalysisPeriod.LAST_7_DAYS -> calendar.add(Calendar.DAY_OF_YEAR, -6)
            SalesAnalysisPeriod.LAST_30_DAYS -> calendar.add(Calendar.DAY_OF_YEAR, -29)
            SalesAnalysisPeriod.THIS_MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            SalesAnalysisPeriod.ALL_TIME -> return null
        }
        return calendar.timeInMillis
    }

    fun includes(
        period: SalesAnalysisPeriod,
        createdAtMillis: Long,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (period == SalesAnalysisPeriod.ALL_TIME) return true
        if (createdAtMillis <= 0L || createdAtMillis > nowMillis) return false
        val start = startInclusiveMillis(period, nowMillis, timeZone) ?: return true
        return createdAtMillis >= start
    }

    fun summarize(
        orders: List<SalesAnalysisOrder>,
        period: SalesAnalysisPeriod,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): SalesAnalysisSummary {
        val invalidTimestamps = if (period == SalesAnalysisPeriod.ALL_TIME) 0
            else orders.count { it.createdAtMillis <= 0L || it.createdAtMillis > nowMillis }
        val filtered = orders.filter {
            includes(period, it.createdAtMillis, nowMillis, timeZone)
        }
        val countedOrders = filtered.count { it.status != "Cancelado" }
        val active = orders.count { it.status in activeStatuses }
        val completed = filtered.filter { it.status == "Concluído" }
        val invalidAmounts = completed.count {
            !it.unitAmount.isFinite() ||
                it.unitAmount < 0.0 ||
                it.unitAmount > maximumUnitAmount ||
                it.quantity !in 1..99 ||
                !(it.unitAmount * it.quantity).isFinite()
        }
        val value = completed.sumOf { order ->
            if (
                order.unitAmount.isFinite() &&
                order.unitAmount in 0.0..maximumUnitAmount &&
                order.quantity in 1..99 &&
                (order.unitAmount * order.quantity).isFinite()
            ) {
                order.unitAmount * order.quantity
            } else {
                0.0
            }
        }
        return SalesAnalysisSummary(
            ordersInPeriod = countedOrders,
            activeOrderCount = active,
            completedOrderCount = completed.size,
            nominalCompletedValue = value,
            ordersWithInvalidTimestampsExcluded = invalidTimestamps,
            completedOrdersWithInvalidAmount = invalidAmounts
        )
    }
}
