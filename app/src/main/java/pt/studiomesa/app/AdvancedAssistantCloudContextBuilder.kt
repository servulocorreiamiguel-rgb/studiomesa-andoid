package pt.studiomesa.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the minimised data allowed to leave the device for the opt-in cloud model.
 * Never includes supplier invoices, OCR, customer records, staff or reservation details.
 * A short user-controlled memory snippet is added only after renewed opt-in consent.
 */
internal object AdvancedAssistantCloudContextBuilder {
    fun build(
        snapshot: StudioMesaAssistantSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
        memorySnippet: String = ""
    ): JSONObject {
        val menu = JSONArray()
        snapshot.menuItems.take(25).forEach { item ->
            menu.put(
                JSONObject()
                    .put("name", item.name.take(90))
                    .put("category", item.category.take(60))
                    .put("selling_price_eur", item.sellingPrice.toPlainString())
                    .put(
                        "recorded_unit_cost_eur",
                        if (item.unitCost > java.math.BigDecimal.ZERO) item.unitCost.toPlainString() else JSONObject.NULL
                    )
            )
        }

        val stock = if (snapshot.stockLoaded) {
            JSONArray().also { rows ->
                snapshot.stockItems.take(20).forEach { item ->
                    rows.put(
                        JSONObject()
                            .put("item", item.name.take(90))
                            .put("quantity", item.quantity.toPlainString())
                            .put("minimum_quantity", item.minimumQuantity.toPlainString())
                            .put("unit", item.unit.take(24))
                            .put("unit_cost_eur", item.unitCostPerUnit?.toPlainString() ?: JSONObject.NULL)
                    )
                }
            }
        } else JSONObject.NULL

        val report = runCatching {
            RestaurantSalesInsights.analyze(
                orders = snapshot.salesHistory,
                daysAhead = 7,
                nowMillis = nowMillis,
                configuredSpecialDays = snapshot.salesSpecialDays
            )
        }.getOrNull()

        val weekdays = JSONArray()
        report?.weekdaySales?.forEach { day ->
            weekdays.put(
                JSONObject()
                    .put("weekday", day.label)
                    .put("observed_days", day.observedDays)
                    .put("completed_order_lines", day.completedOrderLines)
                    .put("nominal_value_eur", day.totalNominalValue.toPlainString())
                    .put("average_daily_nominal_value_eur", day.averageDailyNominalValue?.toPlainString() ?: JSONObject.NULL)
                    .put("usually_high_traffic", day.isUsuallyHighTraffic)
            )
        }

        val forecasts = JSONArray()
        report?.forecasts?.forEach { day ->
            forecasts.put(
                JSONObject()
                    .put("date", day.date.toString())
                    .put("weekday", day.weekdayLabel)
                    .put("forecast_nominal_value_eur", day.nominalValue?.toPlainString() ?: JSONObject.NULL)
                    .put("expected_order_lines", day.expectedOrderLines?.toPlainString() ?: JSONObject.NULL)
                    .put("confidence", day.confidence.name.lowercase())
                    .put("high_traffic", day.isHighTraffic)
                    .put("special_day", day.specialDayName ?: JSONObject.NULL)
                    .put("holiday_effect_measured", day.holidayEffectMeasured)
            )
        }

        val sales = JSONObject()
            .put("has_usable_history", report?.hasUsableHistory ?: false)
            .put("observed_days", report?.observedDays ?: 0)
            .put("completed_order_lines", report?.completedOrderLines ?: 0)
            .put("invalid_lines_excluded", report?.invalidOrderLines ?: 0)
            .put("undated_or_future_lines_excluded", report?.excludedUndatedOrFutureLines ?: 0)
            .put("most_revenue_weekday", report?.mostRevenueWeekday ?: JSONObject.NULL)
            .put("highest_traffic_weekday", report?.highestTrafficWeekday ?: JSONObject.NULL)
            .put("weekday_profile", weekdays)
            .put("next_seven_days_forecast", forecasts)
            .put("values_are_nominal_order_values_not_confirmed_payments", true)

        return JSONObject()
            .put("data_mode", if (snapshot.isDemo) "demo" else "restaurant_owner_snapshot")
            .put("menu_items", menu)
            .put("active_orders", snapshot.activeOrderCount)
            .put("orders_in_preparation", snapshot.preparationOrderCount)
            .put("stock_loaded", snapshot.stockLoaded)
            .put("stock_items", stock)
            .put("pending_reservations_count", if (snapshot.reservationsLoaded) snapshot.pendingReservations else JSONObject.NULL)
            .put("sales", sales)
            .apply {
                val safeMemorySnippet = memorySnippet.trim()
                    .take(AssistantAutomaticMemoryPolicy.MAX_CONTEXT_MEMORY_CHARS)
                if (safeMemorySnippet.isNotBlank()) put("assistant_memory", safeMemorySnippet)
            }
            .put(
                "limitations",
                JSONArray()
                    .put("Order values are not proof of payments or accounting turnover.")
                    .put("Missing costs and incomplete history must be reported, not guessed.")
                    .put("Recommendations are advisory and cannot change records.")
            )
    }
}
