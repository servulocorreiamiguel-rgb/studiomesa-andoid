package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

internal data class AssistantMenuItem(val name: String, val sellingPrice: BigDecimal, val unitCost: BigDecimal, val category: String = "Prato principal", val description: String = "")
internal data class AssistantSaleLine(val itemName: String, val quantity: Int, val status: String)
internal data class AssistantStockItem(val name: String, val quantity: BigDecimal, val minimumQuantity: BigDecimal)
internal enum class MenuEngineeringClass { STAR, WORKHORSE, PUZZLE, DOG, MISSING_COST }
internal data class AssistantMenuEngineeringItem(val itemName: String, val unitsSold: Int, val popularitySharePercent: BigDecimal, val contributionPerUnit: BigDecimal?, val classification: MenuEngineeringClass, val recommendation: String)
internal data class AssistantMenuEngineeringReport(val hasEnoughSales: Boolean, val hasMarginData: Boolean, val completedUnits: Int, val requiredUnits: Int = 5, val items: List<AssistantMenuEngineeringItem> = emptyList())
internal data class AssistantMarketingDraft(val itemName: String, val caption: String, val basis: String)
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
    val pendingReservations: Int = 0,
    val salesLines: List<AssistantSaleLine> = emptyList()
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

    fun menuEngineering(snapshot: StudioMesaAssistantSnapshot): AssistantMenuEngineeringReport {
        val menu = snapshot.menuItems.filter { it.name.isNotBlank() && it.sellingPrice > zero }.distinctBy { normalize(it.name) }
        if (menu.isEmpty()) return AssistantMenuEngineeringReport(false, false, 0)
        val names = menu.map { normalize(it.name) }.toSet()
        val sold = snapshot.salesLines.filter {
            normalize(it.status) == "concluido" && it.quantity in 1..99 && normalize(it.itemName) in names
        }.groupBy { normalize(it.itemName) }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }
        val total = sold.values.sum()
        if (total < 5) return AssistantMenuEngineeringReport(false, false, total)
        val popularityLimit = BigDecimal("70").divide(BigDecimal(menu.size), 4, RoundingMode.HALF_UP)
        val marginSamples = menu.mapNotNull { item ->
            if (item.unitCost <= zero) return@mapNotNull null
            val units = sold[normalize(item.name)] ?: 0
            if (units <= 0) return@mapNotNull null
            item.sellingPrice.subtract(item.unitCost).multiply(BigDecimal(units)) to units
        }
        val weightedUnits = marginSamples.sumOf { it.second }
        val avgMargin = if (weightedUnits == 0) null else marginSamples.fold(zero) { a, b -> a.add(b.first) }.divide(BigDecimal(weightedUnits), 4, RoundingMode.HALF_UP)
        val result = menu.map { item ->
            val units = sold[normalize(item.name)] ?: 0
            val share = BigDecimal(units).multiply(BigDecimal("100")).divide(BigDecimal(total), 2, RoundingMode.HALF_UP)
            val margin = if (item.unitCost > zero) item.sellingPrice.subtract(item.unitCost) else null
            val cls = when {
                margin == null || avgMargin == null -> MenuEngineeringClass.MISSING_COST
                share >= popularityLimit && margin >= avgMargin -> MenuEngineeringClass.STAR
                share >= popularityLimit -> MenuEngineeringClass.WORKHORSE
                margin >= avgMargin -> MenuEngineeringClass.PUZZLE
                else -> MenuEngineeringClass.DOG
            }
            val advice = when (cls) {
                MenuEngineeringClass.STAR -> "Mantém qualidade e disponibilidade; combina procura observada com contribuição unitária acima da média."
                MenuEngineeringClass.WORKHORSE -> "É procurado, mas a contribuição fica abaixo da média. Revê custo de porção, desperdício e preço sem reduzir qualidade automaticamente."
                MenuEngineeringClass.PUZZLE -> "A contribuição é alta, mas a procura observada é baixa. Testa descrição ou posição do menu antes de decidir removê-lo."
                MenuEngineeringClass.DOG -> "Procura e contribuição estão abaixo das referências deste menu. Revê custo e apresentação antes de decidir mantê-lo."
                MenuEngineeringClass.MISSING_COST -> "Falta custo válido para comparar a contribuição. Confirma o custo por porção."
            }
            AssistantMenuEngineeringItem(item.name, units, share, margin, cls, advice)
        }.sortedWith(compareBy<AssistantMenuEngineeringItem> { it.classification.ordinal }.thenByDescending { it.unitsSold }.thenBy { it.itemName.lowercase(Locale.ROOT) })
        return AssistantMenuEngineeringReport(true, avgMargin != null, total, items = result)
    }

    fun dailyBriefing(snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val missingCosts = snapshot.menuItems.count { it.unitCost <= zero }
        val lowStock = if (snapshot.stockLoaded) snapshot.stockItems.count { it.quantity <= it.minimumQuantity } else null
        val report = menuEngineering(snapshot)
        val lines = mutableListOf<String>()
        lines += "Fonte: " + if (snapshot.isDemo) "dados de demonstração locais." else "dados carregados pela aplicação."
        lines += "Carta: ${snapshot.menuItems.size} artigo(s); $missingCosts sem custo interno válido."
        lines += "Pedidos ativos: ${snapshot.activeOrderCount}; em preparação: ${snapshot.preparationOrderCount}."
        if (snapshot.stockFeatureAvailable) lines += if (lowStock == null) "Stock: dados ainda não carregados; não é possível confirmar artigos baixos." else "Stock: $lowStock artigo(s) no limite mínimo ou abaixo dele."
        if (snapshot.reservationsFeatureAvailable) lines += if (!snapshot.reservationsLoaded) "Reservas: lista ainda não carregada; não assumas que está vazia." else "Reservas pendentes: ${snapshot.pendingReservations}."
        lines += when {
            !report.hasEnoughSales -> "Engenharia da carta: só ${report.completedUnits} unidade(s) concluídas correspondem à carta atual; são precisas ${report.requiredUnits} para uma triagem inicial."
            !report.hasMarginData -> "Engenharia da carta: faltam custos válidos em artigos vendidos para comparar contribuições."
            else -> "Engenharia da carta: ${report.items.count { it.classification == MenuEngineeringClass.STAR }} artigo(s) Estrela; revê os classificados com procura ou contribuição baixa."
        }
        val priorities = recommendations(snapshot).take(3)
        if (priorities.isNotEmpty()) {
            lines += "Prioridades para rever:"
            priorities.forEach { lines += "• ${it.title}: ${it.detail}" }
        }
        lines += "Resumo dos registos disponíveis; não é previsão de vendas nem prova de pagamentos ou de atualização em tempo real."
        return StudioMesaAssistantAnswer("Resumo da operação", lines.joinToString("\n"), priorities.firstNotNullOfOrNull { it.actionPage })
    }

    fun marketingDrafts(snapshot: StudioMesaAssistantSnapshot, limit: Int = 3): List<AssistantMarketingDraft> {
        if (limit <= 0) return emptyList()
        return snapshot.menuItems.filter { it.name.isNotBlank() && it.sellingPrice > zero }
            .distinctBy { normalize(it.name) }
            .sortedWith(compareByDescending<AssistantMenuItem> { it.unitCost > zero }
                .thenByDescending { if (it.unitCost > zero) it.sellingPrice.subtract(it.unitCost) else zero }
                .thenBy { it.name.lowercase(Locale.ROOT) })
            .take(limit)
            .map { item ->
                val caption = buildString {
                    append("Na nossa carta: ${item.name}.")
                    if (item.description.isNotBlank()) append(" ").append(item.description.trim())
                    append(" Preço: ").append(euro(item.sellingPrice)).append(". Consulta a carta completa.")
                }
                AssistantMarketingDraft(item.name, caption, if (item.unitCost > zero)
                    "Ordenado por contribuição unitária com preço/custo atuais; não mede popularidade."
                else "Texto baseado no nome e preço; falta custo válido para ordenar por contribuição.")
            }
    }

    private fun answerMenuEngineering(snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val report = menuEngineering(snapshot)
        if (!report.hasEnoughSales) return StudioMesaAssistantAnswer(
            "Engenharia da carta",
            "Só encontrei ${report.completedUnits} unidade(s) concluídas que correspondem aos artigos atuais. Preciso de pelo menos ${report.requiredUnits} para iniciar a triagem. Não vou inventar popularidade.",
            "Pedidos"
        )
        if (!report.hasMarginData) return StudioMesaAssistantAnswer(
            "Engenharia da carta",
            "Há ${report.completedUnits} unidades concluídas, mas faltam custos válidos nos artigos vendidos para comparar a contribuição. Confirma os custos na Carta.",
            "Carta"
        )
        val details = report.items.take(6).joinToString("\n") { item ->
            "• ${item.itemName}: ${classificationLabel(item.classification)}, ${item.unitsSold} unidades (${item.popularitySharePercent}%), contribuição atual ${item.contributionPerUnit?.let { euro(it) } ?: "sem custo"}. ${item.recommendation}"
        }
        return StudioMesaAssistantAnswer("Engenharia da carta",
            "Triagem indicativa com ${report.completedUnits} unidades concluídas e custos/preços atuais. Não é lucro contabilístico nem custo histórico; não inclui IVA, mão de obra, desperdício ou encargos.\n$details",
            "Pedidos")
    }

    private fun answerMarketing(snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val drafts = marketingDrafts(snapshot)
        if (drafts.isEmpty()) return StudioMesaAssistantAnswer("Conteúdo para redes sociais", "Não há artigos com nome e preço válidos para criar rascunhos. Confirma a carta primeiro.", "Carta")
        return StudioMesaAssistantAnswer("Rascunhos para rever",
            drafts.joinToString("\n\n") { "• ${it.itemName}\n${it.caption}\n${it.basis}" } +
                "\nRevê e aprova o texto antes de publicar. Não foram inventados ingredientes, descontos, prémios ou alegações sobre alergénios.",
            "Carta")
    }

    private fun classificationLabel(value: MenuEngineeringClass): String = when (value) {
        MenuEngineeringClass.STAR -> "Estrela"
        MenuEngineeringClass.WORKHORSE -> "Popular com margem baixa"
        MenuEngineeringClass.PUZZLE -> "Margem alta, pouca procura"
        MenuEngineeringClass.DOG -> "Pouca procura e margem baixa"
        MenuEngineeringClass.MISSING_COST -> "Custo em falta"
    }

    fun answerQuestion(rawQuestion: String, snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val q = normalize(rawQuestion)
        return when {
            containsAny(
                q, "gestao economica", "gestor economico", "ponto de equilibrio", "break even",
                "tesouraria", "fluxo de caixa", "desperdicio", "desperdicio por motivo", "custo alimentar",
                "custo da receita", "custo por dose", "ponto de encomenda", "planeamento de compras",
                "fornecedor", "preco fornecedor", "orcamento", "consumo teorico", "consumo real",
                "custo de pessoal", "horas de equipa", "custo de horas", "variacao de stock",
                "previsao", "prever vendas", "vendas da proxima semana"
            ) -> StudioMesaAssistantAnswer(
                "Gestor económico (opcional)",
                "Nesta página, ativa o Gestor económico se o proprietário quiser fazer estas análises. Inclui indicadores mensais, ponto de equilíbrio, projeção manual de tesouraria, média simples de vendas semanais, custo por receita/dose e quantidade teórica de reposição. Usa apenas os valores introduzidos nesta sessão; não consulta bancos ou fornecedores, não altera preços nem envia encomendas."
            )
            containsAny(q, "resumo", "briefing", "prioridades de hoje", "estado da operacao") -> dailyBriefing(snapshot)
            containsAny(
                q, "engenharia da carta", "popularidade", "mais vendidos", "rentabilidade da carta",
                "artigos que vendem", "menu engineering", "vendas", "vender mais", "aumentar as vendas",
                "aumentar vendas", "melhorar as vendas", "melhorar vendas", "o que vende melhor",
                "quais pratos vendem"
            ) -> answerMenuEngineering(snapshot)
            containsAny(q, "instagram", "publicacao", "marketing", "redes sociais", "divulgacao", "post para") -> answerMarketing(snapshot)
            containsAny(q, "preco", "custo", "margem", "lucro") -> answerPricing(snapshot)
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
