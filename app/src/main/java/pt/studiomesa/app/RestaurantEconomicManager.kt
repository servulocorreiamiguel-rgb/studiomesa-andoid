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
