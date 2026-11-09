package pt.studiomesa.app

import com.google.ai.edge.litertlm.OpenApiTool
import org.json.JSONArray
import org.json.JSONObject

/** Local, read-only weekday analysis and short-term sales forecast. */
internal class StudioMesaSalesForecastTool(snapshot: StudioMesaAssistantSnapshot) : OpenApiTool {
    @Volatile private var snapshot = snapshot

    fun updateSnapshot(snapshot: StudioMesaAssistantSnapshot) {
        this.snapshot = snapshot
    }

    override fun getToolDescriptionJsonString(): String {
        val properties = JSONObject()
            .put(
                "action",
                JSONObject()
                    .put("type", "string")
                    .put("enum", JSONArray().put("weekday_profile").put("forecast").put("full_report"))
                    .put("description", "weekday_profile compara os sete dias da semana; forecast prevê os próximos dias; full_report devolve ambos.")
            )
            .put(
                "days_ahead",
                JSONObject()
                    .put("type", "integer")
                    .put("minimum", 1)
                    .put("maximum", 30)
                    .put("description", "Número de dias futuros a prever. Por omissão são 14.")
            )
        return JSONObject()
            .put("name", "analyze_sales_patterns_and_forecast")
            .put(
                "description",
                "Analisa o valor nominal dos pedidos concluídos por dia da semana, identifica os dias que historicamente têm mais linhas de pedidos e prevê valores para os próximos dias. Usa datas de criação e valores dos pedidos já carregados na StudioMesa; não consulta bancos, pagamentos, clientes ou sistemas externos. Reconhece automaticamente os feriados nacionais portugueses e o Carnaval facultativo; os feriados municipais e eventos locais têm de ser configurados no ecrã Análise. A previsão usa médias dos dias comparáveis mais recentes e, com amostra suficiente, uma tendência limitada a ±15%. Mostra sempre o número de dias de amostra e a confiança. A afluência é aproximada por linhas de pedidos, não por pessoas. Não assume que um dia sem registos teve vendas zero. Nunca apresentes valores previstos como garantidos nem como pagamentos recebidos."
            )
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray().put("action")))
            .toString()
    }

    override fun execute(paramsJsonString: String): String = runCatching {
        val params = JSONObject(paramsJsonString)
        val action = params.optString("action", "full_report").ifBlank { "full_report" }
        if (action !in setOf("weekday_profile", "forecast", "full_report")) {
            return@runCatching error("Ação de análise de vendas inválida.")
        }
        val daysAhead = if (params.has("days_ahead")) params.optInt("days_ahead", -1) else 14
        if (daysAhead !in 1..30) return@runCatching error("Escolhe um período entre 1 e 30 dias.")

        val current = snapshot
        val report = RestaurantSalesInsights.analyze(
            orders = current.salesHistory,
            daysAhead = daysAhead,
            configuredSpecialDays = current.salesSpecialDays
        )
        val result = JSONObject()
            .put("historical_observed_days", report.observedDays)
            .put("historical_completed_order_lines", report.completedOrderLines)
            .put("invalid_order_lines_excluded", report.invalidOrderLines)
            .put("undated_or_future_order_lines_excluded", report.excludedUndatedOrFutureLines)
            .put("most_revenue_weekday_by_average", report.mostRevenueWeekday ?: JSONObject.NULL)
            .put("highest_traffic_weekday", report.highestTrafficWeekday ?: JSONObject.NULL)
            .put("read_only", true)
            .put("source", if (current.isDemo) "dados locais de demonstração" else "histórico de pedidos carregado da StudioMesa")
            .put(
                "limitations",
                JSONArray()
                    .put("O valor é nominal e usa a data de criação do pedido; não confirma pagamento.")
                    .put("A afluência é aproximada pelo número de linhas de pedidos concluídos; não equivale a clientes ou pessoas.")
                    .put("Dias sem linhas concluídas não são tratados como dias com zero vendas, porque o horário de abertura/fecho não está registado.")
                    .put("Feriados municipais e eventos locais só entram na análise depois de serem configurados.")
            )

        if (action == "weekday_profile" || action == "full_report") {
            val weekdays = JSONArray()
            report.weekdaySales.forEach { day ->
                weekdays.put(
                    JSONObject()
                        .put("weekday", day.label)
                        .put("observed_days", day.observedDays)
                        .put("completed_order_lines", day.completedOrderLines)
                        .put("total_nominal_value_eur", day.totalNominalValue.toPlainString())
                        .put("average_nominal_value_per_observed_day_eur", day.averageDailyNominalValue?.toPlainString() ?: JSONObject.NULL)
                        .put("average_completed_order_lines_per_observed_day", day.averageDailyOrderLines?.toPlainString() ?: JSONObject.NULL)
                        .put("usually_high_traffic", day.isUsuallyHighTraffic)
                )
            }
            result.put("weekday_profile", weekdays)
        }

        if (action == "forecast" || action == "full_report") {
            val forecasts = JSONArray()
            report.forecasts.forEach { day ->
                forecasts.put(
                    JSONObject()
                        .put("date", day.date.toString())
                        .put("weekday", day.weekdayLabel)
                        .put("recognized_holiday_or_event", day.specialDayName ?: JSONObject.NULL)
                        .put("forecast_nominal_order_value_eur", day.nominalValue?.toPlainString() ?: JSONObject.NULL)
                        .put("forecast_completed_order_lines", day.expectedOrderLines?.toPlainString() ?: JSONObject.NULL)
                        .put("comparable_days", day.comparableDays)
                        .put("confidence", confidenceLabel(day.confidence))
                        .put("usual_high_traffic_pattern", day.isHighTraffic)
                        .put("holiday_effect_measured_from_history", day.holidayEffectMeasured)
                        .put("trend_adjustment_percent", day.trendAdjustmentPercent?.toPlainString() ?: JSONObject.NULL)
                        .put("explanation", day.explanation)
                )
            }
            result.put("forecast_days", forecasts)
                .put("forecast_horizon_days", daysAhead)
        }

        val peakDays = JSONArray()
        if (action == "full_report") {
            report.busiestHistoricalDays.forEach { day ->
                peakDays.put(
                    JSONObject()
                        .put("date", day.date.toString())
                        .put("completed_order_lines", day.completedOrderLines)
                        .put("nominal_order_value_eur", day.nominalValue.toPlainString())
                )
            }
            result.put("busiest_historical_days", peakDays)
        }

        if (!report.hasUsableHistory) {
            result.put("no_history", true)
                .put("message", "Não há histórico de pedidos concluídos com datas e valores válidos. Não foi inventada qualquer previsão.")
        } else {
            result.put("no_history", false)
        }
        result.toString()
    }.getOrElse {
        error(it.message?.take(300) ?: "Não foi possível analisar o histórico de vendas.")
    }

    private fun confidenceLabel(confidence: SalesForecastConfidence): String = when (confidence) {
        SalesForecastConfidence.NONE -> "Sem dados comparáveis"
        SalesForecastConfidence.LOW -> "Baixa"
        SalesForecastConfidence.MEDIUM -> "Média"
        SalesForecastConfidence.HIGH -> "Alta"
    }

    private fun error(message: String): String =
        JSONObject().put("error", message).put("read_only", true).toString()
}
