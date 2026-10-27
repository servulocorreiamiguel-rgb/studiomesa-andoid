package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

internal data class AssistantMenuItem(val name: String, val sellingPrice: BigDecimal, val unitCost: BigDecimal)
internal data class AssistantStockItem(val name: String, val quantity: BigDecimal, val minimumQuantity: BigDecimal)
internal data class StudioMesaAssistantSnapshot(
    val isDemo: Boolean,
    val restaurantConfigured: Boolean,
    val menuItems: List<AssistantMenuItem>,
    val activeOrderCount: Int = 0,
    val preparationOrderCount: Int = 0,
    val stockFeatureAvailable: Boolean = false,
    val stockLoaded: Boolean = false,
    val stockItems: List<AssistantStockItem> = emptyList(),
    val reservationsFeatureAvailable: Boolean = false,
    val reservationsLoaded: Boolean = false,
    val pendingReservations: Int = 0
)
internal enum class AssistantPriority { URGENT, ATTENTION, TIP }
internal data class AssistantRecommendation(
    val id: String,
    val priority: AssistantPriority,
    val title: String,
    val detail: String,
    val actionPage: String? = null
)
internal data class StudioMesaAssistantAnswer(val title: String, val response: String, val actionPage: String? = null)

/** Free local decision support. No hosted model, network transmission or automatic data mutation. */
internal object StudioMesaAdvisor {
    private val zero = BigDecimal.ZERO
    private val hundred = BigDecimal("100")
    private val referenceCostShare = BigDecimal("30")

    fun recommendedSellingPrice(unitCost: BigDecimal, targetCostSharePercent: BigDecimal = referenceCostShare): BigDecimal? {
        if (unitCost <= zero || targetCostSharePercent <= zero || targetCostSharePercent > hundred) return null
        return unitCost.multiply(hundred).divide(targetCostSharePercent, 2, RoundingMode.HALF_UP)
    }

    fun recommendations(snapshot: StudioMesaAssistantSnapshot): List<AssistantRecommendation> {
        val out = mutableListOf<AssistantRecommendation>()
        if (snapshot.isDemo || !snapshot.restaurantConfigured) {
            out += AssistantRecommendation(
                "demo-mode", AssistantPriority.ATTENTION, "Confirma que estás a analisar os dados certos",
                if (snapshot.isDemo) "A aplicação está em modo de demonstração. As sugestões usam exemplos locais e não representam a operação real de um restaurante."
                else "Ainda não há um restaurante configurado. Liga a conta na secção Conta para trabalhar com dados partilhados.", "Conta"
            )
        }
        if (snapshot.menuItems.isEmpty()) {
            out += AssistantRecommendation(
                "menu-empty", AssistantPriority.ATTENTION, "A carta ainda não tem artigos para analisar",
                "Adiciona artigos e confirma os respetivos preços e custos. Sem estes dados, não é possível sugerir preços específicos.", "Carta"
            )
        } else {
            val invalid = snapshot.menuItems.filter { it.sellingPrice <= zero && it.unitCost > zero }
            if (invalid.isNotEmpty()) out += AssistantRecommendation(
                "invalid-selling-price", AssistantPriority.URGENT, "Há artigos com custo, mas sem preço de venda válido",
                "Revê: " + invalid.take(3).joinToString(", ") { it.name } + ". Não alteres preços automaticamente com base nesta análise.", "Carta"
            )
            val noCost = snapshot.menuItems.filter { it.unitCost <= zero }
            if (noCost.isNotEmpty()) out += AssistantRecommendation(
                "missing-costs", AssistantPriority.ATTENTION, "Completa os custos internos da carta",
                "${noCost.size} artigo(s) não têm um custo interno superior a zero. A análise de margem fica incompleta até confirmares os custos de ingredientes e porções.", "Carta"
            )
            snapshot.menuItems.filter { it.sellingPrice > zero && it.unitCost > zero }
                .map { item ->
                    val share = item.unitCost.multiply(hundred).divide(item.sellingPrice, 4, RoundingMode.HALF_UP)
                    Triple(item, share, recommendedSellingPrice(item.unitCost))
                }
                .filter { it.second > referenceCostShare }
                .sortedByDescending { it.second }
                .take(3)
                .forEach { (item, share, target) ->
                    val suggested = target ?: return@forEach
                    out += AssistantRecommendation(
                        "price-${item.name.hashCode()}",
                        if (share > BigDecimal("35") || item.unitCost >= item.sellingPrice) AssistantPriority.URGENT else AssistantPriority.ATTENTION,
                        "Revê o custo de ${item.name}",
                        "O custo registado é ${euro(item.unitCost)} (${percent(share)} do preço atual de ${euro(item.sellingPrice)}). Com uma referência ilustrativa de 30% de custo sobre a venda, o cálculo aponta para ${euro(suggested)}. Não é um preço universal: confirma IVA, mão de obra, energia, desperdício, taxas, procura e posicionamento antes de decidir.",
                        "Preço Justo"
                    )
                }
        }
        when {
            snapshot.activeOrderCount >= 6 -> out += AssistantRecommendation(
                "busy-order-queue", AssistantPriority.URGENT, "A fila de pedidos merece atenção",
                "Há ${snapshot.activeOrderCount} pedidos ativos nos dados carregados. Confirma os estados e atualiza a fila. O assistente não mede tempos reais de espera pela rede.", "Pedidos"
            )
            snapshot.activeOrderCount >= 3 -> out += AssistantRecommendation(
                "active-order-queue", AssistantPriority.ATTENTION, "Acompanha os pedidos em curso",
                "Há ${snapshot.activeOrderCount} pedidos ativos. Mantém os estados atualizados para que a equipa saiba o que falta preparar.", "Cozinha"
            )
        }
        if (snapshot.stockFeatureAvailable) {
            if (!snapshot.stockLoaded && snapshot.restaurantConfigured) out += AssistantRecommendation(
                "stock-not-loaded", AssistantPriority.TIP, "Carrega os dados de stock para os analisar",
                "O assistente não assume que a cache de stock está atualizada. Abre Stock e atualiza os dados antes de decidir sobre ingredientes.", "Stock"
            ) else if (snapshot.stockLoaded) {
                snapshot.stockItems.filter { it.quantity <= it.minimumQuantity }.take(4).forEach { item ->
                    out += AssistantRecommendation(
                        "stock-${item.name.hashCode()}",
                        if (item.quantity <= zero) AssistantPriority.URGENT else AssistantPriority.ATTENTION,
                        if (item.quantity <= zero) "${item.name}: stock esgotado" else "${item.name}: stock baixo",
                        "Quantidade registada: ${decimal(item.quantity)}; limite mínimo: ${decimal(item.minimumQuantity)}. Confirma a contagem física antes de encomendar ou registar movimentos.", "Stock"
                    )
                }
            }
        }
        if (snapshot.reservationsFeatureAvailable && snapshot.restaurantConfigured &&
            snapshot.reservationsLoaded && snapshot.pendingReservations > 0) {
            out += AssistantRecommendation(
                "pending-reservations", AssistantPriority.ATTENTION, "Há reservas por confirmar",
                "A lista carregada contém ${snapshot.pendingReservations} reserva(s) pendente(s). Confirma hora e capacidade antes de atribuir uma mesa.", "Reservas"
            )
        }
        if (out.isEmpty()) out += AssistantRecommendation(
            "keep-data-current", AssistantPriority.TIP, "Mantém os dados operacionais atualizados",
            "Não foi detetado um alerta claro nos dados disponíveis. Isto não garante que a operação esteja sem problemas: revê custos, pedidos e dados ainda não carregados."
        )
        val ranks = mapOf(AssistantPriority.URGENT to 0, AssistantPriority.ATTENTION to 1, AssistantPriority.TIP to 2)
        return out.sortedBy { ranks[it.priority] ?: 3 }.take(7)
    }

    fun answerQuestion(rawQuestion: String, snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val q = normalize(rawQuestion)
        return when {
            containsAny(q, "preco", "custo", "margem", "lucro", "venda") -> answerPricing(snapshot)
            containsAny(q, "stock", "inventario", "ingrediente", "esgotado", "quantidade") -> answerStock(snapshot)
            containsAny(q, "reserva", "reservar", "mesa para") -> answerReservations(snapshot)
            containsAny(q, "pedido", "cozinha", "fila", "atraso", "preparacao") -> answerOrders(snapshot)
            containsAny(q, "app", "erro", "internet", "ligacao", "sincron", "servidor", "problema") -> answerProblem(snapshot)
            containsAny(q, "equipa", "funcionario", "permiss", "acesso") -> StudioMesaAssistantAnswer(
                "Acesso e equipa",
                "Confirma em Conta quem tem acesso ao restaurante. Esta versão não altera permissões nem verifica revogações em tempo real. Antes de remover ou convidar alguém, confirma a identidade e o restaurante selecionado.",
                "Conta"
            )
            else -> answerGeneral(snapshot)
        }
    }

    private fun answerPricing(snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val priced = snapshot.menuItems.filter { it.sellingPrice > zero && it.unitCost > zero }
        if (priced.isEmpty()) return StudioMesaAssistantAnswer(
            "Análise de preços",
            "Ainda não tenho artigos com preço de venda e custo interno válidos. Abre Carta, confirma os dois valores por artigo e volta a perguntar. Não vou inventar um preço sem custo conhecido.",
            "Carta"
        )
        val top = priced.map { item ->
            val share = item.unitCost.multiply(hundred).divide(item.sellingPrice, 4, RoundingMode.HALF_UP)
            Triple(item, share, recommendedSellingPrice(item.unitCost))
        }.sortedByDescending { it.second }.take(4)
        val details = top.joinToString("\n") { (item, share, target) ->
            val targetText = target?.let { " Referência a 30%: ${euro(it)}." } ?: ""
            "• ${item.name}: custo ${euro(item.unitCost)}, venda ${euro(item.sellingPrice)}, custo/venda ${percent(share)}.$targetText"
        }
        val over = priced.count { it.unitCost.multiply(hundred).divide(it.sellingPrice, 4, RoundingMode.HALF_UP) > referenceCostShare }
        return StudioMesaAssistantAnswer(
            "Análise de preços",
            "Analisei ${priced.size} artigo(s) com custos válidos; $over ultrapassam a referência inicial de triagem de 30%.\n$details\n\n30% é apenas um ponto de partida, não uma regra para todos os restaurantes. O cálculo não inclui automaticamente mão de obra, renda, energia, IVA, desperdício, taxas ou concorrência. Não alterei nenhum preço.",
            "Preço Justo"
        )
    }

    private fun answerStock(s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        if (!s.stockFeatureAvailable) return StudioMesaAssistantAnswer("Análise de stock", "A gestão de stock está disponível no plano Premium desta versão. Não tenho dados de stock neste plano e não vou inferir quantidades.")
        if (!s.restaurantConfigured || !s.stockLoaded) return StudioMesaAssistantAnswer(
            "Análise de stock",
            "Os dados de stock ainda não foram carregados de forma confirmada. Abre Stock, configura o restaurante se necessário e atualiza os dados. Não assumo que uma cache antiga representa a quantidade física atual.",
            "Stock"
        )
        val low = s.stockItems.filter { it.quantity <= it.minimumQuantity }
        val detail = if (low.isEmpty()) "Não encontrei artigos abaixo do limite mínimo nos dados carregados. Confirma a contagem física: só consigo analisar o que foi registado."
        else low.take(5).joinToString("\n") { "• ${it.name}: ${decimal(it.quantity)} disponíveis; mínimo ${decimal(it.minimumQuantity)}." } + "\nConfirma a contagem física antes de encomendar ou alterar stock."
        return StudioMesaAssistantAnswer("Análise de stock", detail, "Stock")
    }

    private fun answerReservations(s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        if (!s.reservationsFeatureAvailable) return StudioMesaAssistantAnswer("Análise de reservas", "A gestão de reservas está disponível nos planos Profissional e Premium. Não há dados de reservas disponíveis neste plano.")
        if (!s.restaurantConfigured || !s.reservationsLoaded) return StudioMesaAssistantAnswer(
            "Análise de reservas",
            "Ainda não tenho uma lista de reservas carregada nesta sessão. Abre Reservas e atualiza a lista; não vou assumir que não existem reservas só porque não foram carregadas.",
            "Reservas"
        )
        return StudioMesaAssistantAnswer(
            "Análise de reservas",
            if (s.pendingReservations == 0) "Não encontrei reservas pendentes nos dados carregados. Confirma a lista e a capacidade antes de garantir disponibilidade."
            else "Há ${s.pendingReservations} reserva(s) pendente(s). Confirma hora, número de pessoas e capacidade antes de atribuir lugar.",
            "Reservas"
        )
    }

    private fun answerOrders(s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val source = if (s.isDemo) "dados de demonstração locais" else "dados que a aplicação tem carregados"
        val response = if (s.activeOrderCount == 0) "Não encontrei pedidos ativos nos $source. Isto não confirma que o servidor esteja atualizado; atualiza os pedidos antes de concluir que a fila está vazia."
        else "Há ${s.activeOrderCount} pedido(s) ativo(s), dos quais ${s.preparationOrderCount} estão em preparação, nos $source. Confirma os estados e prioriza os pedidos mais antigos na vista Cozinha. Não tenho dados suficientes para estimar tempos de entrega."
        return StudioMesaAssistantAnswer("Análise de pedidos", response, "Pedidos")
    }

    private fun answerProblem(s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val response = if (!s.restaurantConfigured) "A aplicação está em modo de demonstração ou ainda sem restaurante configurado. Vai a Conta e confirma que existe sessão e que o restaurante correto está associado. A demonstração não sincroniza dados com outros dispositivos."
        else "Não consigo testar a ligação à Internet nem confirmar a saúde do servidor a partir desta análise local. Abre Conta, verifica a conta e o restaurante e atualiza a secção afetada. Se falhar, confirma a Internet e inicia sessão novamente se necessário. Não repitas várias vezes uma operação de escrita sem verificar se foi registada."
        return StudioMesaAssistantAnswer("Diagnóstico inicial", response, "Conta")
    }

    private fun answerGeneral(s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val insights = recommendations(s).take(4)
        val detail = insights.joinToString("\n\n") { "• ${it.title}: ${it.detail}" }
        return StudioMesaAssistantAnswer(
            "Resumo do assistente",
            if (detail.isBlank()) "Ainda não há dados suficientes. Atualiza os dados do restaurante e volta a analisar." else detail,
            insights.firstNotNullOfOrNull { it.actionPage }
        )
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    private fun containsAny(text: String, vararg terms: String): Boolean = terms.any { text.contains(it) }
    private fun euro(v: BigDecimal): String = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(v)
    private fun percent(v: BigDecimal): String = v.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"
    private fun decimal(v: BigDecimal): String = v.stripTrailingZeros().toPlainString().replace('.', ',')
}
