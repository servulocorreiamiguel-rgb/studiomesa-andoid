package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode

internal enum class MenuPriceAdviceStatus {
    INCOMPLETE_DATA,
    URGENT_REVIEW,
    REVIEW,
    WITHIN_TARGET
}

internal data class MenuPriceAdvice(
    val itemName: String,
    val status: MenuPriceAdviceStatus,
    val currentPrice: BigDecimal?,
    val unitCost: BigDecimal?,
    val directCostPercent: BigDecimal?,
    val indicativePriceAtTarget: BigDecimal?,
    val explanation: String
)

internal data class RestaurantMenuAdviceReport(
    val targetCostPercent: BigDecimal,
    val items: List<MenuPriceAdvice>
) {
    val itemsNeedingReview: Int
        get() = items.count {
            it.status == MenuPriceAdviceStatus.URGENT_REVIEW ||
                it.status == MenuPriceAdviceStatus.REVIEW
        }

    val itemsWithIncompleteData: Int
        get() = items.count { it.status == MenuPriceAdviceStatus.INCOMPLETE_DATA }
}

/**
 * Deterministic, offline price adviser. It analyses recorded unit cost and selling price;
 * it does not edit menu records, infer missing costs, or claim that a price is profitable.
 */
internal object RestaurantAdvisorEngine {
    private val decimalPercentPattern = Regex("""^\d{1,3}(?:[.,]\d{1,2})?$""")
    private val zero = BigDecimal.ZERO
    private val oneHundred = BigDecimal("100")

    fun analyzeMenu(
        items: List<MenuEntry>,
        targetCostPercentText: String
    ): RestaurantMenuAdviceReport {
        val rawTarget = targetCostPercentText.trim()
        require(rawTarget.length in 1..6 && decimalPercentPattern.matches(rawTarget)) {
            "Introduz uma percentagem numérica válida, por exemplo 30 ou 25,5."
        }

        val target = try {
            BigDecimal(rawTarget.replace(',', '.')).setScale(2, RoundingMode.HALF_UP)
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("Introduz uma percentagem numérica válida.")
        }
        require(target > zero && target <= oneHundred) {
            "O objetivo tem de ser superior a 0% e até 100%."
        }

        return RestaurantMenuAdviceReport(
            targetCostPercent = target,
            items = items.map { analyzeItem(it, target) }
        )
    }

    private fun analyzeItem(item: MenuEntry, target: BigDecimal): MenuPriceAdvice {
        val name = item.name.trim().ifBlank { "Artigo sem nome" }
        val price = normalizedMoney(item.price)
        val cost = normalizedMoney(item.cost)
        val indicativePrice = cost
            ?.takeIf { it > zero }
            ?.let { calculateIndicativePrice(it, target) }

        if (price == null || cost == null) {
            return MenuPriceAdvice(
                name, MenuPriceAdviceStatus.INCOMPLETE_DATA, price, cost, null, indicativePrice,
                "O preço ou custo registado é inválido. Confirma os valores antes de decidir."
            )
        }

        if (price <= zero) {
            return MenuPriceAdvice(
                name, MenuPriceAdviceStatus.INCOMPLETE_DATA, price, cost, null, indicativePrice,
                "O preço de venda tem de ser superior a zero para avaliar a relação custo/preço."
            )
        }

        if (cost <= zero) {
            return MenuPriceAdvice(
                name, MenuPriceAdviceStatus.INCOMPLETE_DATA, price, cost, null, null,
                "O custo unitário está registado como zero. Confirma se é real ou se falta preencher o custo."
            )
        }

        val costPercent = cost
            .divide(price, 4, RoundingMode.HALF_UP)
            .multiply(oneHundred)
            .setScale(1, RoundingMode.HALF_UP)

        return when {
            price <= cost -> MenuPriceAdvice(
                name, MenuPriceAdviceStatus.URGENT_REVIEW, price, cost, costPercent, indicativePrice,
                "O preço atual não cobre o custo unitário registado, antes de IVA e outros encargos."
            )

            costPercent > target -> MenuPriceAdvice(
                name, MenuPriceAdviceStatus.REVIEW, price, cost, costPercent, indicativePrice,
                "O custo direto representa " + percentText(costPercent) +
                    "% do preço, acima do objetivo configurado de " + percentText(target) +
                    "%. Revê o custo, a porção ou o preço; esta simulação não inclui outros encargos."
            )

            else -> MenuPriceAdvice(
                name, MenuPriceAdviceStatus.WITHIN_TARGET, price, cost, costPercent, indicativePrice,
                "A relação custo/preço está dentro do objetivo configurado. Isto, por si só, não garante rentabilidade."
            )
        }
    }

    private fun normalizedMoney(value: Double): BigDecimal? {
        if (!value.isFinite() || value < 0.0) return null
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
    }

    private fun calculateIndicativePrice(cost: BigDecimal, target: BigDecimal): BigDecimal =
        cost.divide(target.movePointLeft(2), 2, RoundingMode.HALF_UP)

    private fun percentText(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString().replace('.', ',')
}
