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
    val quantity: BigDecimal,
    val unitAmount: BigDecimal,
    val documentRevenueAmount: BigDecimal? = null
)

internal data class EconomicRecipeIngredient(
    val menuItemId: String,
    val inventoryItemId: String,
    val ingredientName: String,
    val unit: String,
    val quantityPerPortion: BigDecimal,
    val wastePercent: BigDecimal,
    val currentStock: BigDecimal?
)

internal data class EconomicRevenueForecastDay(
    val date: LocalDate,
    val weekdayLabel: String,
    val expectedRevenue: BigDecimal?,
    val comparableDays: Int
)

internal data class EconomicIngredientForecast(
    val inventoryItemId: String,
    val ingredientName: String,
    val unit: String,
    val expectedSevenDayQuantity: BigDecimal,
    val averageDailyQuantity: BigDecimal,
    val currentStock: BigDecimal?,
    val suggestedPurchaseQuantity: BigDecimal?
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
                order.quantity.signum() == 0 ||
                order.quantity.abs() > BigDecimal("999999") ||
                order.unitAmount.signum() < 0 ||
                order.unitAmount > BigDecimal("99999999.99")) return@forEach
            val date = runCatching {
                Instant.ofEpochMilli(order.createdAtMillis).atZone(zoneId).toLocalDate()
            }.getOrNull() ?: return@forEach
            if (!date.isBefore(today) || date.isBefore(today.minusDays(maxHistoryDays))) return@forEach
            val amount = order.documentRevenueAmount ?: order.unitAmount.multiply(order.quantity)
            val bucket = daily.getOrPut(date) { MutableDaily() }
            bucket.revenue = bucket.revenue.add(amount)
            // A credit note reduces revenue, but it does not prove that food was
            // physically returned to stock. Keep it out of recipe consumption.
            if (order.menuItemId.isNotBlank() && order.quantity.signum() > 0) {
                bucket.portions[order.menuItemId] =
                    (bucket.portions[order.menuItemId] ?: zero).add(order.quantity)
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
        val ingredientTotals = linkedMapOf<String, BigDecimal>()
        val ingredientMetadata = linkedMapOf<String, EconomicRecipeIngredient>()
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
                    val key = ingredient.inventoryItemId
                    if (key.isBlank()) return@forEach
                    ingredientTotals[key] = (ingredientTotals[key] ?: zero).add(quantity)
                    ingredientMetadata[key] = ingredient
                }
            }
        }
        val ingredients = ingredientTotals.map { (key, total) ->
            val ingredient = ingredientMetadata.getValue(key)
            val totalRounded = total.max(zero).setScale(3, RoundingMode.HALF_UP)
            val available = ingredient.currentStock?.max(zero)?.setScale(3, RoundingMode.HALF_UP)
            EconomicIngredientForecast(
                inventoryItemId = key,
                ingredientName = ingredient.ingredientName,
                unit = ingredient.unit,
                expectedSevenDayQuantity = totalRounded,
                averageDailyQuantity = total.max(zero).divide(BigDecimal(daysAhead), 3, RoundingMode.HALF_UP),
                currentStock = available,
                suggestedPurchaseQuantity = available?.let { totalRounded.subtract(it).max(zero).setScale(3, RoundingMode.HALF_UP) }
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
        item.menuItemId.isNotBlank() && item.inventoryItemId.isNotBlank() &&
            item.ingredientName.isNotBlank() && item.unit.isNotBlank() &&
            item.quantityPerPortion.signum() > 0 &&
            item.wastePercent.signum() >= 0 && item.wastePercent < hundred
}
