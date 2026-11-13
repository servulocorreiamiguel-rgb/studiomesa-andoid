package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal data class EconomicForecastOrderLine(
    val createdAtMillis: Long,
    val status: String,
    val menuItemId: String,
    val itemName: String,
    val quantity: Int,
    val unitAmount: BigDecimal
)

internal data class EconomicRecipeIngredient(
    val menuItemId: String,
    val ingredientName: String,
    val unit: String,
    val quantityPerPortion: BigDecimal,
    val wastePercent: BigDecimal
)

internal data class EconomicRevenueForecastDay(
    val date: LocalDate,
    val weekdayLabel: String,
    val expectedRevenue: BigDecimal?,
    val comparableDays: Int
)

internal data class EconomicIngredientForecast(
    val ingredientName: String,
    val unit: String,
    val expectedSevenDayQuantity: BigDecimal,
    val averageDailyQuantity: BigDecimal
)

internal data class EconomicDailyForecast(
    val days: List<EconomicRevenueForecastDay>,
    val ingredientAverages: List<EconomicIngredientForecast>,
    val hasSalesHistory: Boolean,
    val unmappedMenuItems: List<String>
)

/**
 * Transparent daily forecast based on completed historical order lines.
 * Only dates with observed completed orders are comparison samples; closed days are
 * not silently treated as zero sales. Ingredient estimates require an active,
 * reviewed recipe for each menu item and compatible stock units.
 */
internal object EconomicDailyForecastCalculator {
    private data class DailySales(
        val date: LocalDate,
        val revenue: BigDecimal,
        val portionsByMenuItem: Map<String, BigDecimal>
    )

    private val hundred = BigDecimal("100")
    private val zero = BigDecimal.ZERO
    private val maxDaysAhead = 14
    private val maxHistoryDays = 1095L
    private val maxComparableDays = 12

    fun calculate(
        orders: List<EconomicForecastOrderLine>,
        recipes: List<EconomicRecipeIngredient>,
        today: LocalDate = LocalDate.now(),
        daysAhead: Int = 7,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): EconomicDailyForecast {
        require(daysAhead in 1..maxDaysAhead)
        val daily = linkedMapOf<LocalDate, MutableDaily>()
        orders.forEach { order ->
            if (order.status != "Concluído" || order.createdAtMillis <= 0L ||
                order.quantity !in 1..99 || order.unitAmount.signum() < 0 ||
                order.unitAmount > BigDecimal("99999999.99")) return@forEach
            val date = runCatching {
                Instant.ofEpochMilli(order.createdAtMillis).atZone(zoneId).toLocalDate()
            }.getOrNull() ?: return@forEach
            if (!date.isBefore(today) || date.isBefore(today.minusDays(maxHistoryDays))) return@forEach
            val amount = order.unitAmount.multiply(BigDecimal(order.quantity))
            val bucket = daily.getOrPut(date) { MutableDaily() }
            bucket.revenue = bucket.revenue.add(amount)
            if (order.menuItemId.isNotBlank()) {
                bucket.portions[order.menuItemId] =
                    (bucket.portions[order.menuItemId] ?: zero).add(BigDecimal(order.quantity))
            }
        }
        val historical = daily.map { (date, value) ->
            DailySales(date, value.revenue, value.portions.toMap())
        }
        val recipeByMenu = recipes.filter(::validRecipe)
            .groupBy { it.menuItemId }
        val unmapped = linkedSetOf<String>()
        val forecastDays = (1..daysAhead).map { offset ->
            val target = today.plusDays(offset.toLong())
            val samples = historical.asReversed()
                .filter { it.date.dayOfWeek == target.dayOfWeek }
                .take(maxComparableDays)
            val revenue = if (samples.isEmpty()) null else {
                samples.fold(zero) { sum, row -> sum.add(row.revenue) }
                    .divide(BigDecimal(samples.size), 2, RoundingMode.HALF_UP)
            }
            EconomicRevenueForecastDay(
                date = target,
                weekdayLabel = target.dayOfWeek.getDisplayName(
                    java.time.format.TextStyle.FULL,
                    java.util.Locale("pt", "PT")
                ),
                expectedRevenue = revenue,
                comparableDays = samples.size
            )
        }
        val ingredientTotals = linkedMapOf<Pair<String, String>, BigDecimal>()
        forecastDays.forEach { forecastDay ->
            val samples = historical.asReversed()
                .filter { it.date.dayOfWeek == forecastDay.date.dayOfWeek }
                .take(maxComparableDays)
            if (samples.isEmpty()) return@forEach
            val allMenuIds = samples.flatMap { it.portionsByMenuItem.keys }.toSet()
            allMenuIds.forEach { menuId ->
                val perDayAverage = samples.fold(zero) { sum, row ->
                    sum.add(row.portionsByMenuItem[menuId] ?: zero)
                }.divide(BigDecimal(samples.size), 6, RoundingMode.HALF_UP)
                if (perDayAverage.signum() <= 0) return@forEach
                val requirements = recipeByMenu[menuId].orEmpty()
                if (requirements.isEmpty()) {
                    unmapped += menuId
                    return@forEach
                }
                requirements.forEach { ingredient ->
                    val usablePercent = hundred.subtract(ingredient.wastePercent)
                    val adjustedPerPortion = ingredient.quantityPerPortion
                        .multiply(hundred)
                        .divide(usablePercent, 8, RoundingMode.HALF_UP)
                    val quantity = adjustedPerPortion.multiply(perDayAverage)
                    val key = ingredient.ingredientName to ingredient.unit
                    ingredientTotals[key] = (ingredientTotals[key] ?: zero).add(quantity)
                }
            }
        }
        val ingredients = ingredientTotals.map { (key, total) ->
            val totalRounded = total.setScale(3, RoundingMode.HALF_UP)
            EconomicIngredientForecast(
                ingredientName = key.first,
                unit = key.second,
                expectedSevenDayQuantity = totalRounded,
                averageDailyQuantity = total.divide(BigDecimal(daysAhead), 3, RoundingMode.HALF_UP)
            )
        }.sortedBy { it.ingredientName.lowercase() }
        return EconomicDailyForecast(
            days = forecastDays,
            ingredientAverages = ingredients,
            hasSalesHistory = historical.isNotEmpty(),
            unmappedMenuItems = unmapped.toList()
        )
    }

    private data class MutableDaily(
        var revenue: BigDecimal = zero,
        val portions: MutableMap<String, BigDecimal> = linkedMapOf()
    )

    private fun validRecipe(item: EconomicRecipeIngredient): Boolean =
        item.menuItemId.isNotBlank() && item.ingredientName.isNotBlank() && item.unit.isNotBlank() &&
            item.quantityPerPortion.signum() > 0 &&
            item.wastePercent.signum() >= 0 && item.wastePercent < hundred
}
