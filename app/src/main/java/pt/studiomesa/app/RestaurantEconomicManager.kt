package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

internal data class EconomicManagerInput(
    val monthlyNetSales: BigDecimal,
    val foodCostConsumedIncludingWaste: BigDecimal,
    val monthlyPayroll: BigDecimal,
    val otherFixedCosts: BigDecimal,
    val otherVariableCosts: BigDecimal,
    val targetFoodCostPercent: BigDecimal,
    val recordedWasteCost: BigDecimal? = null,
    val cashAvailableNow: BigDecimal? = null,
    val expectedReceiptsNext30Days: BigDecimal? = null,
    val paymentsDueNext30Days: BigDecimal? = null,
    val weeklyNetSalesHistory: List<BigDecimal> = emptyList()
)

internal data class EconomicManagerAnalysis(
    val foodCostPercent: BigDecimal,
    val payrollPercent: BigDecimal,
    val primeCostPercent: BigDecimal,
    val estimatedOperatingResult: BigDecimal,
    val estimatedBreakEvenSales: BigDecimal?,
    val gapToBreakEven: BigDecimal?,
    val projectedCashIn30Days: BigDecimal?,
    val recordedWasteAsPercentOfSales: BigDecimal?,
    val recordedWasteAsPercentOfFoodCost: BigDecimal?,
    val forecastNextWeekSales: BigDecimal?,
    val warnings: List<String>
)

internal data class RecipeIngredientCost(
    val name: String,
    val costPerPortion: BigDecimal
)

internal data class RecipeCostAnalysis(
    val ingredients: List<RecipeIngredientCost>,
    val totalCostPerPortion: BigDecimal,
    val suggestedPriceAtTarget: BigDecimal,
    val targetFoodCostPercent: BigDecimal
)

internal data class ReorderAnalysis(
    val targetStockForLeadAndSafetyDays: BigDecimal,
    val suggestedOrderQuantity: BigDecimal,
    val totalCoverageDays: Int
)

internal data class WasteLogEntry(
    val itemName: String,
    val quantity: BigDecimal,
    val unitCost: BigDecimal,
    val reason: String,
    val totalCost: BigDecimal
)

internal data class WasteLogReport(
    val entries: List<WasteLogEntry>,
    val totalCost: BigDecimal,
    val costByReason: List<Pair<String, BigDecimal>>
)

internal data class SupplierQuoteOption(
    val supplierName: String,
    val packQuantity: BigDecimal,
    val packPrice: BigDecimal,
    val pricePerUnit: BigDecimal
)

internal data class SupplierQuoteComparison(
    val itemName: String,
    val unit: String,
    val options: List<SupplierQuoteOption>
)

internal data class BudgetVariance(
    val category: String,
    val budget: BigDecimal,
    val actual: BigDecimal,
    val variance: BigDecimal,
    val variancePercent: BigDecimal?
)

internal data class UsageVariance(
    val itemName: String,
    val expectedUse: BigDecimal,
    val actualUse: BigDecimal,
    val varianceQuantity: BigDecimal,
    val unitCost: BigDecimal,
    val varianceCost: BigDecimal
)

internal data class LabourPlanAnalysis(
    val scheduledHours: BigDecimal,
    val fullyLoadedHourlyCost: BigDecimal,
    val expectedSales: BigDecimal,
    val targetPayrollPercent: BigDecimal,
    val estimatedPayrollCost: BigDecimal,
    val estimatedPayrollPercent: BigDecimal,
    val hoursAtTarget: BigDecimal,
    val hoursAboveTarget: BigDecimal
)

/**
 * Deterministic restaurant economics tools. All outputs are estimates from figures
 * entered by the operator, not an accounting record or autonomous business decision.
 */
internal object RestaurantEconomicManager {
    private val zero = BigDecimal.ZERO
    private val hundred = BigDecimal("100")
    private val maxMoney = BigDecimal("999999999.99")
    private val decimalPattern = Regex("^(?:\\d+(?:[.,]\\d*)?|[.,]\\d+)$")

    fun parseNonNegative(raw: String, fieldName: String): BigDecimal {
        val normalized = raw.trim().replace(',', '.')
        require(normalized.isNotEmpty()) { "Preenche o campo «$fieldName»." }
        require(normalized.length <= 40 && decimalPattern.matches(normalized)) {
            "O campo «$fieldName» tem de ser um número válido, sem valores negativos."
        }
        val value = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("O campo «$fieldName» não contém um número válido.")
        }
        require(value <= maxMoney && value.scale().coerceAtLeast(0) <= 4) {
            "O valor em «$fieldName» é demasiado elevado ou tem demasiadas casas decimais."
        }
        return value
    }

    fun analyze(input: EconomicManagerInput): EconomicManagerAnalysis {
        val amounts = listOf(
            "vendas" to input.monthlyNetSales,
            "custo alimentar" to input.foodCostConsumedIncludingWaste,
            "pessoal" to input.monthlyPayroll,
            "custos fixos" to input.otherFixedCosts,
            "outros custos variáveis" to input.otherVariableCosts
        )
        amounts.forEach { (name, value) ->
            require(value >= zero && value <= maxMoney) { "Confirma o valor de $name." }
        }
        require(input.monthlyNetSales > zero) { "As vendas mensais têm de ser superiores a zero para calcular rácios." }
        require(input.targetFoodCostPercent > zero && input.targetFoodCostPercent <= hundred) {
            "O objetivo de custo alimentar tem de estar entre 0 e 100%."
        }
        listOf(
            input.recordedWasteCost,
            input.cashAvailableNow,
            input.expectedReceiptsNext30Days,
            input.paymentsDueNext30Days
        ).filterNotNull().forEach {
            require(it >= zero && it <= maxMoney) { "Os valores opcionais não podem ser negativos nem excessivos." }
        }
        require(
            listOf(input.cashAvailableNow, input.expectedReceiptsNext30Days, input.paymentsDueNext30Days).all { it == null } ||
                listOf(input.cashAvailableNow, input.expectedReceiptsNext30Days, input.paymentsDueNext30Days).all { it != null }
        ) { "Para estimar a tesouraria, preenche saldo disponível, recebimentos previstos e pagamentos a vencer." }
        require(input.weeklyNetSalesHistory.all { it >= zero && it <= maxMoney }) {
            "O histórico semanal não pode conter valores negativos ou excessivos."
        }

        val sales = input.monthlyNetSales
        val foodPercent = percent(input.foodCostConsumedIncludingWaste, sales)
        val payrollPercent = percent(input.monthlyPayroll, sales)
        val primeCostPercent = percent(input.foodCostConsumedIncludingWaste.add(input.monthlyPayroll), sales)
        val operatingResult = sales
            .subtract(input.foodCostConsumedIncludingWaste)
            .subtract(input.monthlyPayroll)
            .subtract(input.otherFixedCosts)
            .subtract(input.otherVariableCosts)
            .setScale(2, RoundingMode.HALF_UP)

        // Simplified break-even: payroll and fixed operating costs are treated as fixed;
        // food consumed and other variable costs are treated as variable with sales.
        val contributionRate = sales
            .subtract(input.foodCostConsumedIncludingWaste)
            .subtract(input.otherVariableCosts)
            .divide(sales, 8, RoundingMode.HALF_UP)
        val estimatedBreakEven = if (contributionRate > zero) {
            input.monthlyPayroll.add(input.otherFixedCosts)
                .divide(contributionRate, 2, RoundingMode.HALF_UP)
        } else null
        val gapToBreakEven = estimatedBreakEven?.subtract(sales)?.max(zero)?.setScale(2, RoundingMode.HALF_UP)
        val projectedCash = if (
            input.cashAvailableNow != null &&
            input.expectedReceiptsNext30Days != null &&
            input.paymentsDueNext30Days != null
        ) {
            input.cashAvailableNow.add(input.expectedReceiptsNext30Days)
                .subtract(input.paymentsDueNext30Days)
                .setScale(2, RoundingMode.HALF_UP)
        } else null
        val wastePercentSales = input.recordedWasteCost?.let { percent(it, sales) }
        val wastePercentFoodCost = input.recordedWasteCost?.takeIf {
            input.foodCostConsumedIncludingWaste > zero
        }?.let { percent(it, input.foodCostConsumedIncludingWaste) }
        val forecast = input.weeklyNetSalesHistory.takeLast(4).takeIf { it.size == 4 }?.let { values ->
            values.fold(zero, BigDecimal::add).divide(BigDecimal("4"), 2, RoundingMode.HALF_UP)
        }

        val warnings = mutableListOf<String>()
        if (foodPercent > input.targetFoodCostPercent) {
            warnings += "O custo alimentar registado ultrapassa o objetivo que definiste. Confirma receitas, porções, preços de compra, inventário e desperdício antes de alterar preços."
        }
        if (primeCostPercent >= BigDecimal("65")) {
            warnings += "O custo alimentar mais pessoal representa pelo menos 65% das vendas introduzidas. Isto é um sinal para investigar, não um limite universal nem um diagnóstico contabilístico."
        }
        if (operatingResult < zero) {
            warnings += "Com os valores introduzidos, o resultado operacional estimado é negativo. Confirma se o período e os custos estão completos e pede apoio contabilístico antes de tomar decisões importantes."
        }
        if (estimatedBreakEven == null) {
            warnings += "Não é possível calcular o ponto de equilíbrio com esta estrutura: os custos alimentares e variáveis igualam ou ultrapassam as vendas."
        }
        if (input.recordedWasteCost != null && input.recordedWasteCost > zero) {
            warnings += "O desperdício é mostrado como indicador separado e não é subtraído novamente ao resultado, porque o custo alimentar introduzido deve já incluir os consumos desperdiçados."
        }
        if (input.weeklyNetSalesHistory.isNotEmpty() && input.weeklyNetSalesHistory.size < 4) {
            warnings += "Para a estimativa semanal simples são precisos pelo menos quatro valores semanais comparáveis; a previsão não está disponível ainda."
        }
        warnings += "Estimativa simplificada: confirma que as vendas não incluem IVA e que os custos pertencem ao mesmo período. Não inclui automaticamente impostos, amortizações, juros, retiradas do proprietário ou movimentos contabilísticos."
        return EconomicManagerAnalysis(
            foodCostPercent = foodPercent,
            payrollPercent = payrollPercent,
            primeCostPercent = primeCostPercent,
            estimatedOperatingResult = operatingResult,
            estimatedBreakEvenSales = estimatedBreakEven,
            gapToBreakEven = gapToBreakEven,
            projectedCashIn30Days = projectedCash,
            recordedWasteAsPercentOfSales = wastePercentSales,
            recordedWasteAsPercentOfFoodCost = wastePercentFoodCost,
            forecastNextWeekSales = forecast,
            warnings = warnings
        )
    }

    /**
     * Each line: ingredient | quantity per portion | price per unit | waste percent.
     * Quantity and unit price must use matching measurement units (e.g. kg and €/kg).
     */
    fun costRecipe(rawLines: String, targetFoodCostPercent: BigDecimal): RecipeCostAnalysis {
        require(targetFoodCostPercent > zero && targetFoodCostPercent <= hundred) {
            "O objetivo de custo alimentar tem de estar entre 0 e 100%."
        }
        val lines = rawLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.isNotEmpty()) { "Introduz pelo menos um ingrediente para calcular a receita." }
        require(lines.size <= 100) { "A receita tem demasiadas linhas; divide-a em receitas mais pequenas." }
        val parsed = lines.mapIndexed { index, line ->
            val parts = line.split('|').map { it.trim() }
            require(parts.size == 4 && parts[0].isNotBlank()) {
                "Linha ${index + 1}: usa nome | quantidade por dose | preço por unidade | desperdício %."
            }
            val quantity = parseNonNegative(parts[1], "quantidade da linha ${index + 1}")
            val unitPrice = parseNonNegative(parts[2], "preço unitário da linha ${index + 1}")
            val wastePercent = parseNonNegative(parts[3], "desperdício da linha ${index + 1}")
            require(quantity > zero) { "Linha ${index + 1}: a quantidade por dose tem de ser superior a zero." }
            require(unitPrice > zero) { "Linha ${index + 1}: introduz um preço unitário superior a zero." }
            require(wastePercent < hundred) { "Linha ${index + 1}: o desperdício tem de ser inferior a 100%." }
            Triple(parts[0], quantity, Pair(unitPrice, wastePercent))
        }
        val ingredientCosts = parsed.map { (name, quantity, costAndWaste) ->
            val unitPrice = costAndWaste.first
            val wastePercent = costAndWaste.second
            val usableRatio = hundred.subtract(wastePercent).divide(hundred, 8, RoundingMode.HALF_UP)
            val cost = quantity.multiply(unitPrice).divide(usableRatio, 8, RoundingMode.HALF_UP)
            RecipeIngredientCost(name, cost.setScale(2, RoundingMode.HALF_UP))
        }
        val exactTotal = parsed.map { (_, quantity, costAndWaste) ->
            val unitPrice = costAndWaste.first
            val wastePercent = costAndWaste.second
            quantity.multiply(unitPrice).multiply(hundred)
                .divide(hundred.subtract(wastePercent), 8, RoundingMode.HALF_UP)
        }.fold(zero, BigDecimal::add)
        require(exactTotal > zero) { "O custo total da receita tem de ser superior a zero." }
        val total = exactTotal.setScale(2, RoundingMode.HALF_UP)
        val suggested = exactTotal.multiply(hundred).divide(targetFoodCostPercent, 2, RoundingMode.HALF_UP)
        require(suggested <= maxMoney) { "O preço sugerido excede o limite suportado." }
        return RecipeCostAnalysis(ingredientCosts, total, suggested, targetFoodCostPercent)
    }

    /**
     * Input: current stock | average daily usage | supplier lead time in whole days |
     * safety stock in whole days. Returns a quantity only; it never places an order.
     */
    fun recommendReorder(raw: String): ReorderAnalysis {
        val parts = raw.split('|').map { it.trim() }
        require(parts.size == 4) {
            "Usa stock atual | consumo médio por dia | prazo do fornecedor em dias | dias de segurança."
        }
        val onHand = parseNonNegative(parts[0], "stock atual")
        val dailyUse = parseNonNegative(parts[1], "consumo médio diário")
        val leadDays = parts[2].toIntOrNull()
        val safetyDays = parts[3].toIntOrNull()
        require(leadDays != null && leadDays in 0..365) { "O prazo do fornecedor tem de ser um número inteiro entre 0 e 365 dias." }
        require(safetyDays != null && safetyDays in 0..365) { "Os dias de segurança têm de ser um número inteiro entre 0 e 365." }
        val coverage = leadDays + safetyDays
        val targetStock = dailyUse.multiply(BigDecimal(coverage)).setScale(3, RoundingMode.CEILING)
        val suggested = targetStock.subtract(onHand).max(zero).setScale(3, RoundingMode.CEILING)
        return ReorderAnalysis(targetStock, suggested, coverage)
    }


    /**
     * Lines: item | quantity wasted | cost per unit | reason.
     * This calculates manual records only; it does not subtract them from inventory.
     */
    fun analyzeWasteLog(rawLines: String): WasteLogReport {
        val lines = rawLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.isNotEmpty()) { "Introduz pelo menos um registo de desperdício." }
        require(lines.size <= 200) { "O registo tem demasiadas linhas para uma única análise." }
        val entries = lines.mapIndexed { index, line ->
            val parts = line.split('|').map { it.trim() }
            require(parts.size == 4 && parts[0].isNotBlank() && parts[3].isNotBlank()) {
                "Linha ${index + 1}: usa artigo | quantidade | custo por unidade | motivo."
            }
            val quantity = parseNonNegative(parts[1], "quantidade desperdiçada na linha ${index + 1}")
            val unitCost = parseNonNegative(parts[2], "custo unitário na linha ${index + 1}")
            require(quantity > zero) { "Linha ${index + 1}: a quantidade tem de ser superior a zero." }
            require(unitCost > zero) { "Linha ${index + 1}: o custo unitário tem de ser superior a zero." }
            WasteLogEntry(parts[0], quantity, unitCost, parts[3], quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP))
        }
        val totals = entries.groupBy { it.reason.trim().lowercase(java.util.Locale.ROOT) }
            .map { (_, group) ->
                group.first().reason.trim() to group.fold(zero) { sum, entry -> sum.add(entry.totalCost) }
                    .setScale(2, RoundingMode.HALF_UP)
            }
            .sortedByDescending { it.second }
        return WasteLogReport(
            entries,
            entries.fold(zero) { sum, entry -> sum.add(entry.totalCost) }.setScale(2, RoundingMode.HALF_UP),
            totals
        )
    }

    /**
     * Lines: item | supplier | unit | quantity in pack | price per pack.
     * Quotes are comparable only for the same item and same usage/purchase unit.
     */
    fun compareSupplierQuotes(rawLines: String): List<SupplierQuoteComparison> {
        val lines = rawLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.isNotEmpty()) { "Introduz pelo menos uma proposta para comparar preços." }
        require(lines.size <= 200) { "Existem demasiadas propostas para uma única comparação." }
        val quotes = lines.mapIndexed { index, line ->
            val parts = line.split('|').map { it.trim() }
            require(parts.size == 5 && parts[0].isNotBlank() && parts[1].isNotBlank() && parts[2].isNotBlank()) {
                "Linha ${index + 1}: usa artigo | fornecedor | unidade | quantidade na embalagem | preço da embalagem."
            }
            val packQuantity = parseNonNegative(parts[3], "quantidade da embalagem na linha ${index + 1}")
            val packPrice = parseNonNegative(parts[4], "preço da embalagem na linha ${index + 1}")
            require(packQuantity > zero && packPrice > zero) {
                "Linha ${index + 1}: quantidade e preço da embalagem têm de ser superiores a zero."
            }
            listOf(parts[0], parts[1], parts[2], packQuantity.toPlainString(), packPrice.toPlainString())
        }
        val grouped = quotes.groupBy {
            it[0].trim().lowercase(java.util.Locale.ROOT) + "|" + it[2].trim().lowercase(java.util.Locale.ROOT)
        }
        return grouped.values.map { group ->
            val first = group.first()
            val suppliers = group.map { it[1].trim().lowercase(java.util.Locale.ROOT) }
            require(suppliers.distinct().size == suppliers.size) {
                "Há duas propostas do mesmo fornecedor para ${first[0]} na mesma unidade; deixa apenas a proposta atual."
            }
            val options = group.map { q ->
                val quantity = BigDecimal(q[3])
                val packPrice = BigDecimal(q[4])
                SupplierQuoteOption(q[1], quantity, packPrice,
                    packPrice.divide(quantity, 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP))
            }.sortedBy { it.pricePerUnit }
            SupplierQuoteComparison(first[0], first[2], options)
        }.sortedBy { it.itemName.lowercase(java.util.Locale.ROOT) }
    }

    /**
     * Lines: category | budget for period | actual amount for the same period.
     * Positive variance means actual is above budget; it is not automatically good or bad.
     */
    fun compareBudget(rawLines: String): List<BudgetVariance> {
        val lines = rawLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.isNotEmpty()) { "Introduz pelo menos uma categoria com orçamento e valor real." }
        require(lines.size <= 100) { "Existem demasiadas categorias para uma única comparação." }
        val rows = lines.mapIndexed { index, line ->
            val parts = line.split('|').map { it.trim() }
            require(parts.size == 3 && parts[0].isNotBlank()) {
                "Linha ${index + 1}: usa categoria | orçamento | valor real."
            }
            val budget = parseNonNegative(parts[1], "orçamento da linha ${index + 1}")
            val actual = parseNonNegative(parts[2], "valor real da linha ${index + 1}")
            val variance = actual.subtract(budget).setScale(2, RoundingMode.HALF_UP)
            val pct = if (budget > zero) variance.multiply(hundred)
                .divide(budget, 4, RoundingMode.HALF_UP).setScale(1, RoundingMode.HALF_UP) else null
            BudgetVariance(parts[0], budget, actual, variance, pct)
        }
        require(rows.map { it.category.trim().lowercase(java.util.Locale.ROOT) }.distinct().size == rows.size) {
            "Não repitas categorias na mesma comparação de orçamento."
        }
        return rows
    }

    /**
     * Lines: item | expected use | actual use | cost per unit.
     * Positive variance means more product was used than expected from the recipe/sales baseline.
     */
    fun compareTheoreticalAndActualUsage(rawLines: String): List<UsageVariance> {
        val lines = rawLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.isNotEmpty()) { "Introduz pelo menos um artigo para comparar consumo teórico e real." }
        require(lines.size <= 200) { "Existem demasiados artigos para uma única comparação." }
        return lines.mapIndexed { index, line ->
            val parts = line.split('|').map { it.trim() }
            require(parts.size == 4 && parts[0].isNotBlank()) {
                "Linha ${index + 1}: usa artigo | consumo teórico | consumo real | custo por unidade."
            }
            val expected = parseNonNegative(parts[1], "consumo teórico da linha ${index + 1}")
            val actual = parseNonNegative(parts[2], "consumo real da linha ${index + 1}")
            val cost = parseNonNegative(parts[3], "custo unitário da linha ${index + 1}")
            require(cost > zero) { "Linha ${index + 1}: o custo unitário tem de ser superior a zero." }
            val variance = actual.subtract(expected).setScale(3, RoundingMode.HALF_UP)
            UsageVariance(parts[0], expected, actual, variance, cost, variance.multiply(cost).setScale(2, RoundingMode.HALF_UP))
        }
    }


    /**
     * Input: scheduled hours | fully loaded hourly cost | expected sales | target payroll %.
     * This is a labour-cost scenario, not a roster optimizer or employment-law recommendation.
     */
    fun analyzeLabourPlan(raw: String): LabourPlanAnalysis {
        val parts = raw.split('|').map { it.trim() }
        require(parts.size == 4) {
            "Usa horas previstas | custo total por hora | vendas esperadas | objetivo de pessoal %."
        }
        val hours = parseNonNegative(parts[0], "horas previstas")
        val hourlyCost = parseNonNegative(parts[1], "custo por hora")
        val sales = parseNonNegative(parts[2], "vendas esperadas")
        val targetPercent = parseNonNegative(parts[3], "objetivo de custo de pessoal")
        require(hourlyCost > zero) { "O custo total por hora tem de ser superior a zero." }
        require(sales > zero) { "As vendas esperadas têm de ser superiores a zero." }
        require(targetPercent > zero && targetPercent <= hundred) {
            "O objetivo de pessoal tem de estar entre 0 e 100%."
        }
        val payrollCost = hours.multiply(hourlyCost).setScale(2, RoundingMode.HALF_UP)
        val payrollPercent = percent(payrollCost, sales)
        val budgetCost = sales.multiply(targetPercent).divide(hundred, 8, RoundingMode.HALF_UP)
        val hoursAtTarget = budgetCost.divide(hourlyCost, 2, RoundingMode.HALF_UP)
        return LabourPlanAnalysis(
            scheduledHours = hours,
            fullyLoadedHourlyCost = hourlyCost,
            expectedSales = sales,
            targetPayrollPercent = targetPercent,
            estimatedPayrollCost = payrollCost,
            estimatedPayrollPercent = payrollPercent,
            hoursAtTarget = hoursAtTarget,
            hoursAboveTarget = hours.subtract(hoursAtTarget).max(zero).setScale(2, RoundingMode.HALF_UP)
        )
    }

    fun parseWeeklySales(raw: String): List<BigDecimal> {
        if (raw.isBlank()) return emptyList()
        val tokens = raw.split(Regex("[;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        require(tokens.size <= 52) { "Introduz no máximo 52 valores semanais." }
        return tokens.mapIndexed { index, token ->
            parseNonNegative(token, "vendas da semana ${index + 1}")
        }
    }

    private fun percent(part: BigDecimal, whole: BigDecimal): BigDecimal =
        part.multiply(hundred).divide(whole, 4, RoundingMode.HALF_UP).setScale(1, RoundingMode.HALF_UP)
}
