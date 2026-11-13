package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.TimeZone

internal data class EconomicFoodOrderLine(
    val createdAtMillis: Long,
    val status: String,
    val menuItemId: String,
    val quantity: Int
)

internal data class EconomicFoodRecipeIngredient(
    val inventoryItemId: String,
    val name: String,
    val unit: String,
    val quantityPerPortion: BigDecimal,
    val wastePercent: BigDecimal
)

internal data class EconomicFoodDailyAverage(
    val inventoryItemId: String,
    val name: String,
    val unit: String,
    val averageQuantityPerCoveredDay: BigDecimal
)

internal data class EconomicFoodForecastResult(
    val items: List<EconomicFoodDailyAverage>,
    val eligibleHistoricalDays: Int,
    val forecastDaysCovered: Int,
    val minimumComparableDays: Int,
    val message: String
)

/**
 * Read-only food demand estimate based on completed orders and active, approved
 * recipes. If one dish sold on a date has no valid recipe, the whole date is
 * excluded: missing recipes are never interpreted as zero ingredient use.
 */
internal object EconomicFoodForecastCalculator {
    private val zero = BigDecimal.ZERO
    private const val MAX_LOOKBACK_DAYS = 1826L
    private const val MAX_COMPARABLE_DAYS = 12

    private data class DailyUsage(
        var complete: Boolean = true,
        var completedLines: Int = 0,
        val amounts: MutableMap<String, BigDecimal> = mutableMapOf()
    )

    fun calculate(
        orders: List<EconomicFoodOrderLine>,
        recipesByMenuItemId: Map<String, List<EconomicFoodRecipeIngredient>>,
        daysAhead: Int = 7,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): EconomicFoodForecastResult {
        require(daysAhead in 1..30) { "O período de previsão tem de estar entre 1 e 30 dias." }
        require(nowMillis > 0L) { "A data de referência não é válida." }

        val zone = timeZone.toZoneId()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val oldestAllowedDate = today.minusDays(MAX_LOOKBACK_DAYS)
        val dailyUsage = mutableMapOf<java.time.LocalDate, DailyUsage>()
        val ingredientDetails = linkedMapOf<String, EconomicFoodRecipeIngredient>()

        for (order in orders) {
            if (order.status != "Concluído") continue
            if (order.createdAtMillis <= 0L || order.createdAtMillis > nowMillis) continue

            val date = Instant.ofEpochMilli(order.createdAtMillis).atZone(zone).toLocalDate()
            if (date < oldestAllowedDate || date >= today) continue

            val day = dailyUsage.getOrPut(date) { DailyUsage() }
            day.completedLines += 1
            val recipe = recipesByMenuItemId[order.menuItemId]
            if (order.menuItemId.isBlank() || order.quantity !in 1..99 || recipe.isNullOrEmpty()) {
                day.complete = false
                continue
            }

            for (ingredient in recipe) {
                val validIdentity = ingredient.inventoryItemId.isNotBlank() &&
                    ingredient.name.isNotBlank() && ingredient.unit.isNotBlank()
                if (!validIdentity) {
                    day.complete = false
                    break
                }
                val required = runCatching {
                    RestaurantRecipeInputValidator.requiredStockForOrder(
                        ingredient.quantityPerPortion,
                        order.quantity,
                        ingredient.wastePercent
                    )
                }.getOrNull()
                if (required == null) {
                    day.complete = false
                    break
                }
                val previous = ingredientDetails[ingredient.inventoryItemId]
                if (previous != null &&
                    (previous.name != ingredient.name || previous.unit != ingredient.unit)
                ) {
                    day.complete = false
                    break
                }
                ingredientDetails.putIfAbsent(ingredient.inventoryItemId, ingredient)
                day.amounts[ingredient.inventoryItemId] =
                    (day.amounts[ingredient.inventoryItemId] ?: zero).add(required)
            }
        }

        val eligibleDays = dailyUsage
            .filterValues { it.complete && it.completedLines > 0 }
            .toSortedMap()
        if (eligibleDays.isEmpty()) {
            return EconomicFoodForecastResult(
                items = emptyList(),
                eligibleHistoricalDays = 0,
                forecastDaysCovered = 0,
                minimumComparableDays = 0,
                message = "Ainda não há dias históricos completos para calcular ingredientes. Todos os pratos vendidos precisam de ter uma receita ativa e aprovada, com ingredientes ligados ao stock."
            )
        }

        val forecastAmounts = mutableMapOf<String, BigDecimal>()
        var coveredForecastDays = 0
        var minimumComparableDays = Int.MAX_VALUE

        for (offset in 1..daysAhead) {
            val targetDate = today.plusDays(offset.toLong())
            val samples = eligibleDays.keys
                .asSequence()
                .filter { it.dayOfWeek == targetDate.dayOfWeek }
                .sortedDescending()
                .take(MAX_COMPARABLE_DAYS)
                .toList()
            if (samples.isEmpty()) continue

            coveredForecastDays += 1
            minimumComparableDays = minOf(minimumComparableDays, samples.size)
            for (ingredientId in ingredientDetails.keys) {
                val total = samples.fold(zero) { sum, sampleDate ->
                    sum.add(eligibleDays[sampleDate]?.amounts?.get(ingredientId) ?: zero)
                }
                val averageForTargetDay = total.divide(
                    BigDecimal.valueOf(samples.size.toLong()),
                    3,
                    RoundingMode.HALF_UP
                )
                forecastAmounts[ingredientId] =
                    (forecastAmounts[ingredientId] ?: zero).add(averageForTargetDay)
            }
        }

        if (coveredForecastDays == 0) {
            return EconomicFoodForecastResult(
                items = emptyList(),
                eligibleHistoricalDays = eligibleDays.size,
                forecastDaysCovered = 0,
                minimumComparableDays = 0,
                message = "Há receitas aprovadas, mas não existem dias históricos comparáveis com os próximos dias da semana."
            )
        }

        val averages = forecastAmounts.mapNotNull { (ingredientId, total) ->
            val ingredient = ingredientDetails[ingredientId] ?: return@mapNotNull null
            val average = total.divide(
                BigDecimal.valueOf(coveredForecastDays.toLong()),
                3,
                RoundingMode.HALF_UP
            )
            if (average <= zero) return@mapNotNull null
            EconomicFoodDailyAverage(
                inventoryItemId = ingredientId,
                name = ingredient.name,
                unit = ingredient.unit,
                averageQuantityPerCoveredDay = average
            )
        }.sortedBy { it.name.lowercase() }

        val confidence = if (minimumComparableDays >= 4) "média ou superior" else "baixa"
        val message = "Média diária nos dias com histórico comparável: $coveredForecastDays de $daysAhead dias previstos. Confiança $confidence; foram usados até $MAX_COMPARABLE_DAYS dias históricos comparáveis por dia da semana."
        return EconomicFoodForecastResult(
            items = averages,
            eligibleHistoricalDays = eligibleDays.size,
            forecastDaysCovered = coveredForecastDays,
            minimumComparableDays = if (minimumComparableDays == Int.MAX_VALUE) 0 else minimumComparableDays,
            message = message
        )
    }
}
