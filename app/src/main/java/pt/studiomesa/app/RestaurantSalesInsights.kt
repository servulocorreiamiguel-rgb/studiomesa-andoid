package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone

internal data class RestaurantSpecialDay(val date: LocalDate, val name: String)
internal enum class SalesForecastConfidence { NONE, LOW, MEDIUM, HIGH }

internal data class RestaurantWeekdaySales(
    val dayOfWeek: DayOfWeek,
    val label: String,
    val observedDays: Int,
    val completedOrderLines: Int,
    val totalNominalValue: BigDecimal,
    val averageDailyNominalValue: BigDecimal?,
    val averageDailyOrderLines: BigDecimal?,
    val isUsuallyHighTraffic: Boolean
)

internal data class RestaurantSalesPeakDay(
    val date: LocalDate,
    val completedOrderLines: Int,
    val nominalValue: BigDecimal
)

internal data class RestaurantDailySalesForecast(
    val date: LocalDate,
    val weekdayLabel: String,
    val nominalValue: BigDecimal?,
    val expectedOrderLines: BigDecimal?,
    val comparableDays: Int,
    val confidence: SalesForecastConfidence,
    val specialDayName: String?,
    val isHighTraffic: Boolean,
    val holidayEffectMeasured: Boolean,
    val trendAdjustmentPercent: BigDecimal?,
    val explanation: String
)

internal data class RestaurantSalesInsightsReport(
    val observedDays: Int,
    val completedOrderLines: Int,
    val invalidOrderLines: Int,
    val excludedUndatedOrFutureLines: Int,
    val weekdaySales: List<RestaurantWeekdaySales>,
    val mostRevenueWeekday: String?,
    val highestTrafficWeekday: String?,
    val busiestHistoricalDays: List<RestaurantSalesPeakDay>,
    val forecasts: List<RestaurantDailySalesForecast>,
    val hasUsableHistory: Boolean
)

/**
 * Deterministic analysis of completed order lines already loaded in StudioMesa.
 * It is not a payment ledger: the current order schema does not store payment time/status.
 * Dates without completed rows are not assumed to be zero-sales days because closures
 * and opening hours are not recorded by this data source.
 */
internal object RestaurantSalesInsights {
    private val zero = BigDecimal.ZERO
    private val hundred = BigDecimal("100")
    private const val MAX_UNIT_AMOUNT = 99_999_999.99
    private const val MAX_FORECAST_DAYS = 30
    private const val MAX_CUSTOM_DAYS = 60
    private const val MAX_CUSTOM_DAY_NAME_CHARS = 80
    private const val RECENT_WEEKDAY_SAMPLES = 12
    private const val HISTORICAL_LOOKBACK_DAYS = 1826L
    private const val HIGH_TRAFFIC_MULTIPLIER = 1.20

    private data class DailyTotal(
        val date: LocalDate,
        var lines: Int = 0,
        var value: BigDecimal = zero
    )

    private data class Holiday(val name: String, val optional: Boolean = false)

    fun parseSpecialDays(raw: String): List<RestaurantSpecialDay> {
        if (raw.isBlank()) return emptyList()
        val parsed = linkedMapOf<LocalDate, RestaurantSpecialDay>()
        raw.lineSequence().take(MAX_CUSTOM_DAYS).forEach { line ->
            val parts = line.split('|', limit = 2)
            if (parts.size != 2) return@forEach
            val dateText = parts[0].trim()
            val name = parts[1].trim()
            if (name.length !in 2..MAX_CUSTOM_DAY_NAME_CHARS) return@forEach
            if (!dateText.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) return@forEach
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return@forEach
            if (date.year !in 2000..2100) return@forEach
            parsed[date] = RestaurantSpecialDay(date, name)
        }
        return parsed.values.sortedBy { it.date }
    }

    fun formatSpecialDays(days: List<RestaurantSpecialDay>): String =
        days.sortedBy { it.date }.joinToString("\n") { "${it.date} | ${it.name}" }

    fun holidayName(
        date: LocalDate,
        configuredSpecialDays: List<RestaurantSpecialDay> = emptyList()
    ): String? {
        val national = portugueseHoliday(date)
        val configured = configuredSpecialDays.firstOrNull { it.date == date }
        return when {
            national != null && configured != null ->
                if (national.optional) "${national.name} (facultativo) · ${configured.name}"
                else "${national.name} · ${configured.name}"
            configured != null -> configured.name
            national != null -> if (national.optional) national.name + " (facultativo)" else national.name
            else -> null
        }
    }

    fun analyze(
        orders: List<SalesAnalysisOrder>,
        daysAhead: Int = 14,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        configuredSpecialDays: List<RestaurantSpecialDay> = emptyList()
    ): RestaurantSalesInsightsReport {
        require(daysAhead in 1..MAX_FORECAST_DAYS) {
            "O período de previsão tem de estar entre 1 e $MAX_FORECAST_DAYS dias."
        }
        require(nowMillis > 0L) { "A data de referência não é válida." }

        val zone = timeZone.toZoneId()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val daily = linkedMapOf<LocalDate, DailyTotal>()
        var invalidLines = 0
        var undatedOrFutureLines = 0
        var completedLines = 0

        orders.forEach { order ->
            if (order.status != "Concluído") return@forEach
            if (order.createdAtMillis <= 0L || order.createdAtMillis > nowMillis) {
                undatedOrFutureLines += 1
                return@forEach
            }
            if (
                !order.unitAmount.isFinite() ||
                order.unitAmount < 0.0 ||
                order.unitAmount > MAX_UNIT_AMOUNT ||
                order.quantity !in 1..99 ||
                !(order.unitAmount * order.quantity).isFinite()
            ) {
                invalidLines += 1
                return@forEach
            }

            val date = Instant.ofEpochMilli(order.createdAtMillis).atZone(zone).toLocalDate()
            // Exclude today: it is an incomplete trading day and would bias weekday averages down.
            if (!date.isBefore(today)) return@forEach

            val amount = BigDecimal.valueOf(order.unitAmount)
                .multiply(BigDecimal.valueOf(order.quantity.toLong()))
                .setScale(2, RoundingMode.HALF_UP)
            val total = daily.getOrPut(date) { DailyTotal(date) }
            total.lines += 1
            total.value = total.value.add(amount)
            completedLines += 1
        }

        val historical = daily.values
            .filter { !it.date.isBefore(today.minusDays(HISTORICAL_LOOKBACK_DAYS)) }
            .sortedBy { it.date }
        val overallAverageLines = if (historical.isEmpty()) 0.0 else {
            historical.map { it.lines.toDouble() }.average()
        }

        val weekdaySales = DayOfWeek.values().map { day ->
            val samples = historical.filter { it.date.dayOfWeek == day }
            val lines = samples.sumOf { it.lines }
            val total = samples.fold(zero) { sum, sample -> sum.add(sample.value) }
            val averageValue = averageMoney(total, samples.size)
            val averageLines = if (samples.isEmpty()) null else {
                BigDecimal.valueOf(lines.toDouble() / samples.size)
                    .setScale(2, RoundingMode.HALF_UP)
            }
            RestaurantWeekdaySales(
                dayOfWeek = day,
                label = weekdayLabel(day),
                observedDays = samples.size,
                completedOrderLines = lines,
                totalNominalValue = total.setScale(2, RoundingMode.HALF_UP),
                averageDailyNominalValue = averageValue,
                averageDailyOrderLines = averageLines,
                isUsuallyHighTraffic = samples.size >= 4 &&
                    averageLines != null &&
                    overallAverageLines > 0.0 &&
                    averageLines.toDouble() >= overallAverageLines * HIGH_TRAFFIC_MULTIPLIER
            )
        }

        val bestRevenueDay = weekdaySales
            .filter { it.averageDailyNominalValue != null }
            .maxByOrNull { it.averageDailyNominalValue ?: zero }
            ?.label
        val busiestWeekday = weekdaySales
            .filter { it.averageDailyOrderLines != null }
            .maxByOrNull { it.averageDailyOrderLines ?: zero }
            ?.label

        val busiestHistoricalDays = historical
            .sortedWith(compareByDescending<DailyTotal> { it.lines }.thenByDescending { it.value })
            .take(5)
            .map { RestaurantSalesPeakDay(it.date, it.lines, it.value.setScale(2, RoundingMode.HALF_UP)) }

        val forecasts = (1..daysAhead).map { offset ->
            val targetDate = today.plusDays(offset.toLong())
            val targetDay = targetDate.dayOfWeek
            val label = weekdayLabel(targetDay)
            val specialName = holidayName(targetDate, configuredSpecialDays)
            val weekdaySamples = historical
                .asReversed()
                .filter { it.date.dayOfWeek == targetDay }
                .take(RECENT_WEEKDAY_SAMPLES)
            val specialSamples = if (specialName.isNullOrBlank()) emptyList() else {
                historical.filter { sample ->
                    holidayName(sample.date, configuredSpecialDays) == specialName
                }
            }

            val useHolidayBaseline = specialSamples.size >= 2
            val useSingleHolidaySample = specialSamples.size == 1 && weekdaySamples.isEmpty()
            val chosenSamples = when {
                useHolidayBaseline -> specialSamples
                useSingleHolidaySample -> specialSamples
                else -> weekdaySamples
            }

            if (chosenSamples.isEmpty()) {
                RestaurantDailySalesForecast(
                    date = targetDate,
                    weekdayLabel = label,
                    nominalValue = null,
                    expectedOrderLines = null,
                    comparableDays = 0,
                    confidence = SalesForecastConfidence.NONE,
                    specialDayName = specialName,
                    isHighTraffic = false,
                    holidayEffectMeasured = false,
                    trendAdjustmentPercent = null,
                    explanation = "Não existem dias comparáveis com pedidos concluídos e valores válidos. Não é possível produzir uma previsão fiável."
                )
            } else {
                val sumValue = chosenSamples.fold(zero) { sum, sample -> sum.add(sample.value) }
                val sumLines = chosenSamples.sumOf { it.lines }
                val averageValue = averageMoney(sumValue, chosenSamples.size) ?: zero
                val averageLines = BigDecimal.valueOf(sumLines.toDouble() / chosenSamples.size)
                    .setScale(2, RoundingMode.HALF_UP)

                val trendPercent = if (!useHolidayBaseline && chosenSamples.size >= 8) {
                    val recent = chosenSamples.take(4)
                    val older = chosenSamples.drop(4).take(4)
                    val oldTotal = older.fold(zero) { sum, sample -> sum.add(sample.value) }
                    val recentTotal = recent.fold(zero) { sum, sample -> sum.add(sample.value) }
                    val oldAverage = averageMoney(oldTotal, older.size)
                    val recentAverage = averageMoney(recentTotal, recent.size)
                    if (oldAverage != null && oldAverage > zero && recentAverage != null) {
                        recentAverage.subtract(oldAverage)
                            .multiply(hundred)
                            .divide(oldAverage, 4, RoundingMode.HALF_UP)
                            .max(BigDecimal("-15"))
                            .min(BigDecimal("15"))
                    } else null
                } else null

                val forecastValue = if (trendPercent == null) averageValue else {
                    averageValue.multiply(BigDecimal.ONE.add(trendPercent.movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP)
                }
                val highTraffic = chosenSamples.size >= 4 &&
                    overallAverageLines > 0.0 &&
                    averageLines.toDouble() >= overallAverageLines * HIGH_TRAFFIC_MULTIPLIER
                val confidence = confidenceFor(chosenSamples.size)
                val explanation = buildList {
                    add(
                        if (useHolidayBaseline)
                            "Previsão baseada em ${chosenSamples.size} ocorrência(s) histórica(s) com o mesmo feriado/evento."
                        else
                            "Previsão baseada na média de ${chosenSamples.size} dia(s) comparável(is) de ${label}."
                    )
                    if (trendPercent != null && trendPercent.compareTo(zero) != 0) {
                        add("A tendência recente foi limitada a um ajuste máximo de ±15% para evitar extrapolações excessivas.")
                    }
                    if (!specialName.isNullOrBlank() && !useHolidayBaseline) {
                        add(
                            if (useSingleHolidaySample)
                                "Só existe uma ocorrência anterior deste feriado/evento; a previsão usa essa amostra e tem confiança baixa."
                            else
                                "Feriado/evento reconhecido, mas sem duas ocorrências históricas comparáveis. O efeito específico não foi estimado; foi usado o padrão semanal."
                        )
                    }
                    if (confidence == SalesForecastConfidence.LOW) {
                        add("Confiança baixa: há menos de quatro dias comparáveis.")
                    }
                    add("A afluência é aproximada pelo número de linhas de pedidos concluídos, não pelo número de clientes ou pessoas.")
                }.joinToString(" ")

                RestaurantDailySalesForecast(
                    date = targetDate,
                    weekdayLabel = label,
                    nominalValue = forecastValue,
                    expectedOrderLines = averageLines,
                    comparableDays = chosenSamples.size,
                    confidence = confidence,
                    specialDayName = specialName,
                    isHighTraffic = highTraffic,
                    holidayEffectMeasured = useHolidayBaseline,
                    trendAdjustmentPercent = trendPercent,
                    explanation = explanation
                )
            }
        }

        return RestaurantSalesInsightsReport(
            observedDays = historical.size,
            completedOrderLines = completedLines,
            invalidOrderLines = invalidLines,
            excludedUndatedOrFutureLines = undatedOrFutureLines,
            weekdaySales = weekdaySales,
            mostRevenueWeekday = bestRevenueDay,
            highestTrafficWeekday = busiestWeekday,
            busiestHistoricalDays = busiestHistoricalDays,
            forecasts = forecasts,
            hasUsableHistory = historical.isNotEmpty()
        )
    }

    private fun averageMoney(total: BigDecimal, count: Int): BigDecimal? =
        if (count <= 0) null else total.divide(BigDecimal.valueOf(count.toLong()), 2, RoundingMode.HALF_UP)

    private fun confidenceFor(samples: Int): SalesForecastConfidence = when {
        samples <= 0 -> SalesForecastConfidence.NONE
        samples < 4 -> SalesForecastConfidence.LOW
        samples < 8 -> SalesForecastConfidence.MEDIUM
        else -> SalesForecastConfidence.HIGH
    }

    private fun weekdayLabel(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.FULL, Locale("pt", "PT"))

    private fun portugueseHoliday(date: LocalDate): Holiday? {
        val year = date.year
        val easter = easterSunday(year)
        val fixed = mapOf(
            LocalDate.of(year, 1, 1) to Holiday("Ano Novo"),
            LocalDate.of(year, 4, 25) to Holiday("Dia da Liberdade"),
            LocalDate.of(year, 5, 1) to Holiday("Dia do Trabalhador"),
            LocalDate.of(year, 6, 10) to Holiday("Dia de Portugal"),
            LocalDate.of(year, 8, 15) to Holiday("Assunção de Nossa Senhora"),
            LocalDate.of(year, 10, 5) to Holiday("Implantação da República"),
            LocalDate.of(year, 11, 1) to Holiday("Dia de Todos os Santos"),
            LocalDate.of(year, 12, 1) to Holiday("Restauração da Independência"),
            LocalDate.of(year, 12, 8) to Holiday("Imaculada Conceição"),
            LocalDate.of(year, 12, 25) to Holiday("Natal")
        )
        return fixed[date] ?: when (date) {
            easter.minusDays(2) -> Holiday("Sexta-feira Santa")
            easter -> Holiday("Domingo de Páscoa")
            easter.plusDays(60) -> Holiday("Corpo de Deus")
            easter.minusDays(47) -> Holiday("Carnaval", optional = true)
            else -> null
        }
    }

    /** Meeus/Jones/Butcher Gregorian computus. */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val monthValue = (h + l - 7 * m + 114) / 31
        val dayValue = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, monthValue, dayValue)
    }
}
