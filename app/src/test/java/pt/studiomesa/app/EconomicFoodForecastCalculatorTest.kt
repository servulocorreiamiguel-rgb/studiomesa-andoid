package pt.studiomesa.app

import java.math.BigDecimal
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomicFoodForecastCalculatorTest {
    private val now = Instant.parse("2026-11-13T12:00:00Z").toEpochMilli()
    private val zone = TimeZone.getTimeZone("UTC")

    @Test
    fun estimatesIngredientAverageFromComparableWeekdays() {
        val dates = listOf("2026-11-07", "2026-10-31", "2026-10-24", "2026-10-17")
        val orders = dates.mapIndexed { index, date ->
            EconomicFoodOrderLine(
                createdAtMillis = Instant.parse("${date}T12:00:00Z").toEpochMilli(),
                status = "Concluído",
                menuItemId = "dish-1",
                quantity = index + 1
            )
        }
        val recipes = mapOf(
            "dish-1" to listOf(
                EconomicFoodRecipeIngredient(
                    inventoryItemId = "flour",
                    name = "Farinha",
                    unit = "kg",
                    quantityPerPortion = BigDecimal("0.200"),
                    wastePercent = BigDecimal.ZERO
                )
            )
        )

        val result = EconomicFoodForecastCalculator.calculate(
            orders, recipes, daysAhead = 7, nowMillis = now, timeZone = zone
        )

        assertEquals(1, result.forecastDaysCovered)
        assertEquals(4, result.minimumComparableDays)
        assertEquals(BigDecimal("0.500"), result.items.single().averageQuantityPerCoveredDay)
    }

    @Test
    fun ignoresWholeDaysWhenAnySoldDishHasNoApprovedRecipe() {
        val date = "2026-11-07"
        val orders = listOf(
            EconomicFoodOrderLine(
                Instant.parse("${date}T12:00:00Z").toEpochMilli(),
                "Concluído", "dish-1", 2
            ),
            EconomicFoodOrderLine(
                Instant.parse("${date}T13:00:00Z").toEpochMilli(),
                "Concluído", "no-recipe", 1
            )
        )
        val recipes = mapOf(
            "dish-1" to listOf(
                EconomicFoodRecipeIngredient(
                    "flour", "Farinha", "kg", BigDecimal("0.200"), BigDecimal.ZERO
                )
            )
        )

        val result = EconomicFoodForecastCalculator.calculate(
            orders, recipes, nowMillis = now, timeZone = zone
        )

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.eligibleHistoricalDays)
        assertTrue(result.message.contains("receita ativa e aprovada"))
    }

    @Test
    fun excludesCancelledOrdersAndIncludesPreparationWaste() {
        val dates = listOf("2026-11-07", "2026-10-31", "2026-10-24", "2026-10-17")
        val orders = dates.flatMap { date ->
            listOf(
                EconomicFoodOrderLine(
                    Instant.parse("${date}T12:00:00Z").toEpochMilli(),
                    "Concluído", "dish-1", 1
                ),
                EconomicFoodOrderLine(
                    Instant.parse("${date}T13:00:00Z").toEpochMilli(),
                    "Cancelado", "dish-1", 99
                )
            )
        }
        val recipes = mapOf(
            "dish-1" to listOf(
                EconomicFoodRecipeIngredient(
                    "flour", "Farinha", "kg", BigDecimal("0.200"), BigDecimal("10")
                )
            )
        )

        val result = EconomicFoodForecastCalculator.calculate(
            orders, recipes, nowMillis = now, timeZone = zone
        )

        assertEquals(BigDecimal("0.222"), result.items.single().averageQuantityPerCoveredDay)
    }
}
