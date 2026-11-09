package pt.studiomesa.app

import com.google.ai.edge.litertlm.OpenApiTool
import java.math.BigDecimal
import java.math.RoundingMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic, read-only calculations for the on-device Economic Assistant.
 * It only calculates figures explicitly supplied in the tool call; it never fetches
 * accounting records, changes restaurant data, places orders or approves documents.
 */
internal class StudioMesaEconomicCalculationsTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String {
        val operations = JSONArray().apply {
            listOf(
                "monthly_analysis", "recipe_cost", "waste_log", "supplier_quotes",
                "budget_variance", "usage_variance", "labour_plan", "reorder"
            ).forEach { put(it) }
        }
        val properties = JSONObject()
            .put("operation", JSONObject()
                .put("type", "string")
                .put("enum", operations)
                .put("description", "Cálculo pretendido. Escolhe a operação que corresponde ao pedido."))
            .put("raw_lines", JSONObject()
                .put("type", "string")
                .put("description", "Para recipe_cost: ingrediente | quantidade por dose | preço por unidade | desperdício %. waste_log: artigo | quantidade | custo por unidade | motivo. supplier_quotes: artigo | fornecedor | unidade | quantidade embalagem | preço embalagem. budget_variance: categoria | orçamento | valor real. usage_variance: artigo | consumo teórico | consumo real | custo por unidade. labour_plan: horas previstas | custo total por hora | vendas esperadas | objetivo pessoal %. reorder: stock atual | consumo diário | prazo fornecedor em dias | dias de segurança."))
            .put("target_food_cost_percent", number("Percentagem-alvo de custo alimentar entre 0 e 100; obrigatória em monthly_analysis e recipe_cost."))
            .put("monthly_net_sales", number("Vendas mensais sem IVA, no período analisado."))
            .put("food_cost_consumed_including_waste", number("Custo alimentar consumido, já incluindo desperdício."))
            .put("monthly_payroll", number("Custo mensal de pessoal no mesmo período."))
            .put("other_fixed_costs", number("Outros custos fixos do mesmo período."))
            .put("other_variable_costs", number("Outros custos variáveis, excluindo custo alimentar e pessoal."))
            .put("recorded_waste_cost", number("Custo do desperdício registado; não é subtraído novamente ao resultado."))
            .put("cash_available_now", number("Saldo disponível introduzido pelo proprietário."))
            .put("expected_receipts_next_30_days", number("Recebimentos previstos nos próximos 30 dias."))
            .put("payments_due_next_30_days", number("Pagamentos a vencer nos próximos 30 dias."))
            .put("weekly_net_sales_history", JSONObject()
                .put("type", "string")
                .put("description", "Histórico de vendas líquidas semanais separado por ponto e vírgula; usa pelo menos quatro semanas comparáveis."))
        return JSONObject()
            .put("name", "calculate_restaurant_economics")
            .put("description", "Executa cálculos económicos determinísticos e só de leitura. monthly_analysis calcula rácios, resultado operacional simplificado, ponto de equilíbrio, tesouraria e média de quatro semanas; recipe_cost calcula custo e preço de referência por dose; waste_log analisa desperdício; supplier_quotes compara preço por unidade; budget_variance calcula desvios; usage_variance compara consumo real e teórico; labour_plan calcula um cenário de pessoal; reorder estima quantidade de reposição. Usa apenas os valores fornecidos nesta chamada. Não inventes dados em falta, não consultas bancos/contabilidade, não alteras registos e não fazes encomendas. Montantes em EUR e unidades compatíveis.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray().put("operation")))
            .toString()
    }

    private fun number(description: String) = JSONObject()
        .put("type", "number")
        .put("minimum", 0)
        .put("description", description)

    override fun execute(paramsJsonString: String): String = runCatching {
        val params = JSONObject(paramsJsonString)
        val operation = params.optString("operation").trim()
        val result = when (operation) {
            "monthly_analysis" -> monthlyAnalysis(params)
            "recipe_cost" -> recipeCost(params)
            "waste_log" -> wasteLog(params)
            "supplier_quotes" -> supplierQuotes(params)
            "budget_variance" -> budgetVariance(params)
            "usage_variance" -> usageVariance(params)
            "labour_plan" -> labourPlan(params)
            "reorder" -> reorder(params)
            else -> throw IllegalArgumentException("Operação económica desconhecida.")
        }
        JSONObject()
            .put("operation", operation)
            .put("data_source", "parâmetros recebidos do modelo; a origem dos valores não é verificada pela ferramenta")
            .put("confirmation_required", true)
            .put("read_only", true)
            .put("result", result)
            .put("warning", "Estimativa matemática baseada apenas nos valores introduzidos. Confirma período, unidades, IVA e origem dos dados antes de decidir.")
            .toString()
    }.getOrElse { error ->
        JSONObject().put("error", error.message?.take(400) ?: "Não foi possível executar o cálculo.")
            .put("read_only", true).toString()
    }

    private fun monthlyAnalysis(p: JSONObject): JSONObject {
        val input = EconomicManagerInput(
            monthlyNetSales = requiredMoney(p, "monthly_net_sales"),
            foodCostConsumedIncludingWaste = requiredMoney(p, "food_cost_consumed_including_waste"),
            monthlyPayroll = requiredMoney(p, "monthly_payroll"),
            otherFixedCosts = requiredMoney(p, "other_fixed_costs"),
            otherVariableCosts = requiredMoney(p, "other_variable_costs"),
            targetFoodCostPercent = requiredMoney(p, "target_food_cost_percent"),
            recordedWasteCost = optionalMoney(p, "recorded_waste_cost"),
            cashAvailableNow = optionalMoney(p, "cash_available_now"),
            expectedReceiptsNext30Days = optionalMoney(p, "expected_receipts_next_30_days"),
            paymentsDueNext30Days = optionalMoney(p, "payments_due_next_30_days"),
            weeklyNetSalesHistory = RestaurantEconomicManager.parseWeeklySales(p.optString("weekly_net_sales_history"))
        )
        val a = RestaurantEconomicManager.analyze(input)
        return JSONObject()
            .put("food_cost_percent", a.foodCostPercent.toPlainString())
            .put("payroll_percent", a.payrollPercent.toPlainString())
            .put("prime_cost_percent", a.primeCostPercent.toPlainString())
            .put("estimated_operating_result_eur", a.estimatedOperatingResult.toPlainString())
            .putNullable("estimated_break_even_sales_eur", a.estimatedBreakEvenSales)
            .putNullable("gap_to_break_even_eur", a.gapToBreakEven)
            .putNullable("projected_cash_in_30_days_eur", a.projectedCashIn30Days)
            .putNullable("recorded_waste_as_percent_of_sales", a.recordedWasteAsPercentOfSales)
            .putNullable("recorded_waste_as_percent_of_food_cost", a.recordedWasteAsPercentOfFoodCost)
            .putNullable("forecast_next_week_sales_eur", a.forecastNextWeekSales)
            .put("warnings", JSONArray(a.warnings))
            .put("formula_note", "Resultado operacional simplificado = vendas líquidas - custo alimentar - pessoal - outros custos fixos - outros custos variáveis.")
    }

    private fun recipeCost(p: JSONObject): JSONObject {
        val a = RestaurantEconomicManager.costRecipe(requiredText(p, "raw_lines"), requiredMoney(p, "target_food_cost_percent"))
        val ingredients = JSONArray()
        a.ingredients.take(20).forEach { row ->
            ingredients.put(JSONObject()
                .put("ingredient", row.name)
                .put("cost_per_portion_eur", row.costPerPortion.setScale(4, RoundingMode.HALF_UP).toPlainString()))
        }
        return JSONObject()
            .put("ingredients", ingredients)
            .put("ingredient_count", a.ingredients.size)
            .put("truncated", a.ingredients.size > 20)
            .put("total_cost_per_portion_eur", a.totalCostPerPortion.toPlainString())
            .put("suggested_price_at_target_eur", a.suggestedPriceAtTarget.toPlainString())
            .put("target_food_cost_percent", a.targetFoodCostPercent.toPlainString())
            .put("warning", "Preço indicativo antes de IVA, mão de obra, renda, energia, taxas, procura e outros encargos.")
    }

    private fun wasteLog(p: JSONObject): JSONObject {
        val a = RestaurantEconomicManager.analyzeWasteLog(requiredText(p, "raw_lines"))
        val entries = JSONArray()
        a.entries.take(20).forEach { row ->
            entries.put(JSONObject()
                .put("item", row.itemName)
                .put("quantity", row.quantity.toPlainString())
                .put("unit_cost_eur", row.unitCost.toPlainString())
                .put("reason", row.reason)
                .put("cost_eur", row.totalCost.toPlainString()))
        }
        val reasons = JSONArray()
        a.costByReason.take(20).forEach { (reason, amount) ->
            reasons.put(JSONObject().put("reason", reason).put("cost_eur", amount.toPlainString()))
        }
        return JSONObject()
            .put("total_recorded_waste_eur", a.totalCost.toPlainString())
            .put("entries", entries)
            .put("entry_count", a.entries.size)
            .put("cost_by_reason", reasons)
            .put("truncated", a.entries.size > 20 || a.costByReason.size > 20)
            .put("warning", "Analisa apenas os registos introduzidos; não prova que todos os desperdícios foram registados.")
    }

    private fun supplierQuotes(p: JSONObject): JSONObject {
        val groups = JSONArray()
        val comparisons = RestaurantEconomicManager.compareSupplierQuotes(requiredText(p, "raw_lines"))
        comparisons.take(20).forEach { group ->
            val options = JSONArray()
            group.options.forEach { option ->
                options.put(JSONObject()
                    .put("supplier", option.supplierName)
                    .put("pack_quantity", option.packQuantity.toPlainString())
                    .put("pack_price_eur", option.packPrice.toPlainString())
                    .put("price_per_unit_eur", option.pricePerUnit.toPlainString()))
            }
            groups.put(JSONObject()
                .put("item", group.itemName)
                .put("unit", group.unit)
                .put("options_ranked_by_unit_price", options)
                .put("lowest_unit_price_supplier", group.options.firstOrNull()?.supplierName ?: JSONObject.NULL))
        }
        return JSONObject()
            .put("comparisons", groups)
            .put("group_count", comparisons.size)
            .put("truncated", comparisons.size > 20)
            .put("warning", "Compara preço por unidade apenas quando artigo e unidade são equivalentes. Não inclui transporte, qualidade, prazos ou condições comerciais.")
    }

    private fun budgetVariance(p: JSONObject): JSONObject {
        val all = RestaurantEconomicManager.compareBudget(requiredText(p, "raw_lines"))
        val rows = JSONArray()
        all.take(30).forEach { row ->
            rows.put(JSONObject()
                .put("category", row.category)
                .put("budget_eur", row.budget.toPlainString())
                .put("actual_eur", row.actual.toPlainString())
                .put("variance_eur", row.variance.toPlainString())
                .putNullable("variance_percent", row.variancePercent))
        }
        return JSONObject().put("categories", rows).put("category_count", all.size).put("truncated", all.size > 30)
            .put("interpretation", "Desvio positivo significa valor real acima do orçamento; negativo significa abaixo. Interpreta conforme a categoria.")
    }

    private fun usageVariance(p: JSONObject): JSONObject {
        val all = RestaurantEconomicManager.compareTheoreticalAndActualUsage(requiredText(p, "raw_lines"))
        val rows = JSONArray()
        all.take(30).forEach { row ->
            rows.put(JSONObject()
                .put("item", row.itemName)
                .put("expected_use", row.expectedUse.toPlainString())
                .put("actual_use", row.actualUse.toPlainString())
                .put("variance_quantity", row.varianceQuantity.toPlainString())
                .put("unit_cost_eur", row.unitCost.toPlainString())
                .put("variance_cost_eur", row.varianceCost.toPlainString()))
        }
        return JSONObject().put("items", rows).put("item_count", all.size).put("truncated", all.size > 30)
            .put("interpretation", "Desvio positivo significa maior consumo real do que o teórico; pode refletir desperdício, porções, ofertas, perdas ou erros de registo.")
    }

    private fun labourPlan(p: JSONObject): JSONObject {
        val a = RestaurantEconomicManager.analyzeLabourPlan(requiredText(p, "raw_lines"))
        return JSONObject()
            .put("scheduled_hours", a.scheduledHours.toPlainString())
            .put("fully_loaded_hourly_cost_eur", a.fullyLoadedHourlyCost.toPlainString())
            .put("expected_sales_eur", a.expectedSales.toPlainString())
            .put("target_payroll_percent", a.targetPayrollPercent.toPlainString())
            .put("estimated_payroll_cost_eur", a.estimatedPayrollCost.toPlainString())
            .put("estimated_payroll_percent", a.estimatedPayrollPercent.toPlainString())
            .put("hours_at_target", a.hoursAtTarget.toPlainString())
            .put("hours_above_target", a.hoursAboveTarget.toPlainString())
            .put("warning", "Cenário de custo, não otimização de escalas nem aconselhamento sobre legislação laboral.")
    }

    private fun reorder(p: JSONObject): JSONObject {
        val a = RestaurantEconomicManager.recommendReorder(requiredText(p, "raw_lines"))
        return JSONObject()
            .put("target_stock_for_lead_and_safety_days", a.targetStockForLeadAndSafetyDays.toPlainString())
            .put("suggested_order_quantity", a.suggestedOrderQuantity.toPlainString())
            .put("coverage_days", a.totalCoverageDays)
            .put("warning", "Quantidade indicativa. Confirma stock físico, embalagem, mínimo de encomenda e prazo do fornecedor. Não faz encomendas.")
    }

    private fun requiredText(p: JSONObject, key: String): String =
        p.optString(key).trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Falta o campo $key.")

    private fun requiredMoney(p: JSONObject, key: String): BigDecimal =
        optionalMoney(p, key) ?: throw IllegalArgumentException("Falta o valor numérico $key.")

    private fun optionalMoney(p: JSONObject, key: String): BigDecimal? {
        if (!p.has(key) || p.isNull(key)) return null
        val raw = p.optString(key).trim()
        if (raw.isBlank()) return null
        return raw.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("O valor $key não é um número válido.")
    }

    private fun JSONObject.putNullable(key: String, value: BigDecimal?): JSONObject =
        put(key, value?.toPlainString() ?: JSONObject.NULL)
}
