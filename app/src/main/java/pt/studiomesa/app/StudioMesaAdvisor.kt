package pt.studiomesa.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

internal data class AssistantMenuItem(val name: String, val sellingPrice: BigDecimal, val unitCost: BigDecimal, val category: String = "Prato principal", val description: String = "")
internal data class AssistantSaleLine(val itemName: String, val quantity: Int, val status: String)
internal data class AssistantStockItem(
    val name: String,
    val quantity: BigDecimal,
    val minimumQuantity: BigDecimal,
    val unit: String = "",
    val unitCostPerUnit: BigDecimal? = null
)
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
                    val missingToMinimum = item.minimumQuantity.subtract(item.quantity).max(zero)
                    val replenishmentEstimate = item.unitCostPerUnit
                        ?.takeIf { it >= zero && missingToMinimum > zero }
                        ?.let { missingToMinimum.multiply(it).setScale(2, RoundingMode.HALF_UP) }
                    val replenishmentNote = replenishmentEstimate?.let {
                        " Para voltar ao limite mínimo registado faltam ${decimal(missingToMinimum)} ${item.unit.ifBlank { "unidades" }}; custo estimado: ${euro(it)}."
                    }.orEmpty()
                    out += AssistantRecommendation(
                        "stock-${item.name.hashCode()}",
                        if (item.quantity <= zero) AssistantPriority.URGENT else AssistantPriority.ATTENTION,
                        if (item.quantity <= zero) "${item.name}: stock esgotado" else "${item.name}: stock baixo",
                        "Quantidade registada: ${decimal(item.quantity)}; limite mínimo: ${decimal(item.minimumQuantity)}. ${replenishmentNote} Confirma a contagem física, a embalagem e o prazo de entrega antes de encomendar.",
                        "Stock"
                    )
                }
                val itemsWithoutCost = snapshot.stockItems.count { it.unitCostPerUnit == null }
                if (itemsWithoutCost > 0) {
                    out += AssistantRecommendation(
                        "stock-costs-unknown",
                        AssistantPriority.TIP,
                        "Faltam custos de compra em $itemsWithoutCost artigo(s) de stock",
                        "Sem custo unitário confirmado, não consigo calcular o valor dessas existências nem estimar o custo de reposição. Preenche o custo de compra em Stock.",
                        "Stock"
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
        if (snapshot.stockFeatureAvailable) lines += if (lowStock == null) {
            "Stock: dados ainda não carregados; não é possível confirmar artigos baixos nem valorizar existências."
        } else {
            val knownItems = snapshot.stockItems.filter { it.unitCostPerUnit != null && it.quantity >= zero }
            val knownStockValue = knownItems.fold(zero) { sum, item ->
                sum.add(item.quantity.multiply(item.unitCostPerUnit).setScale(2, RoundingMode.HALF_UP))
            }.setScale(2, RoundingMode.HALF_UP)
            val unknownCostCount = snapshot.stockItems.count { it.unitCostPerUnit == null }
            "Stock: $lowStock artigo(s) no limite mínimo ou abaixo dele. Valor estimado com custo conhecido: ${euro(knownStockValue)}; $unknownCostCount artigo(s) sem custo registado."
        }
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

    private fun answerMenuEngineering(rawQuestion: String, snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val q = normalizeLookupText(rawQuestion)
        if (RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance(rawQuestion)) {
            return StudioMesaAssistantAnswer(
                "Período de desempenho não confirmado",
                "O resumo recebido pelo assistente não inclui datas por pedido, por isso não consigo classificar vendas, popularidade ou margens por intervalo. Na versão Premium, a página Análise permite filtrar contagens e valor nominal de pedidos concluídos por período, mas não calcula rentabilidade histórica por artigo. Confirma os registos datados e os custos/preços históricos antes de comparar artigos.",
                "Análise"
            )
        }
        if (containsAny(q, "prejuizo", "contribuicao negativa")) {
            return answerContributionRanking(snapshot, lowestContribution = true)
        }
        if (containsAny(q, "menos rentaveis", "menor margem", "margem mais baixa", "menos margem", "margem baixa", "melhor margem", "melhores margens", "maior margem", "mais margem", "margem mais alta")) {
            val lowest = containsAny(q, "menos rentaveis", "menor margem", "margem mais baixa", "menos margem", "margem baixa")
            return answerMarginRanking(snapshot, lowest)
        }
        if (containsAny(
                q, "mais lucro", "pratos mais lucrativos", "rentabilidade do menu",
                "maior contribuicao", "contribuicao por unidade",
                "quais pratos dao mais lucro", "pratos dao mais lucro"
            )) {
            return answerContributionRanking(snapshot)
        }

        val report = menuEngineering(snapshot)
        if (!report.hasEnoughSales) return StudioMesaAssistantAnswer(
            "Engenharia da carta",
            "Só encontrei ${report.completedUnits} unidade(s) concluídas que correspondem aos artigos atuais. Preciso de pelo menos ${report.requiredUnits} para analisar popularidade. Não vou inventar popularidade nem vendas. Posso comparar contribuição unitária se os custos e preços estiverem registados.",
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

    private fun answerContributionRanking(
        snapshot: StudioMesaAssistantSnapshot,
        lowestContribution: Boolean = false
    ): StudioMesaAssistantAnswer {
        val eligible = snapshot.menuItems.filter {
            it.name.isNotBlank() && it.sellingPrice > zero && it.unitCost > zero &&
                (!lowestContribution || it.unitCost > it.sellingPrice)
        }
        if (eligible.isEmpty()) return StudioMesaAssistantAnswer(
            if (lowestContribution) "Não há contribuição unitária negativa registada"
            else "Faltam custos para comparar a carta",
            if (lowestContribution) {
                "Nos valores válidos registados, não encontrei artigos cujo custo interno unitário ultrapasse o preço de venda. Isto não prova que sejam lucrativos: mão de obra, renda, energia, desperdício, IVA e outros encargos podem alterar o resultado."
            } else {
                "Não encontrei artigos com preço de venda e custo interno válidos. Confirma esses valores na Carta antes de comparar contribuições; não vou inventar lucro."
            }
        )

        val comparator = if (lowestContribution) {
            compareBy<Triple<AssistantMenuItem, BigDecimal, BigDecimal>> { it.second }
                .thenBy { it.first.name.lowercase(Locale.ROOT) }
        } else {
            compareByDescending<Triple<AssistantMenuItem, BigDecimal, BigDecimal>> { it.second }
                .thenBy { it.first.name.lowercase(Locale.ROOT) }
        }
        val ranking = eligible.map { item ->
            val contribution = item.sellingPrice.subtract(item.unitCost)
            val costShare = item.unitCost.multiply(hundred)
                .divide(item.sellingPrice, 4, RoundingMode.HALF_UP)
            Triple(item, contribution, costShare)
        }.sortedWith(comparator)

        val details = ranking.take(5).joinToString("\n") { (item, contribution, costShare) ->
            "• ${item.name}: contribuição unitária estimada ${euro(contribution)}; custo ${euro(item.unitCost)} de ${euro(item.sellingPrice)} (${percent(costShare)} do preço)."
        }
        val orderExplanation = if (lowestContribution) {
            "Ordenei do menor para o maior pela diferença entre preço e custo interno registados. Se essa diferença for negativa, o custo interno registado ultrapassa o preço de venda; isto não prova prejuízo líquido e não inclui encargos indiretos."
        } else {
            "Ordenei os artigos pela diferença entre o preço e o custo interno registados."
        }
        return StudioMesaAssistantAnswer(
            "Contribuição estimada por artigo",
            "$orderExplanation Isto é contribuição unitária estimada, não é lucro líquido; não indica popularidade nem substitui vendas históricas. Não inclui automaticamente IVA, pessoal, renda, energia, desperdício ou taxas.\n$details",
            "Carta"
        )
    }

    private fun answerMarginRanking(snapshot: StudioMesaAssistantSnapshot, lowestMargin: Boolean): StudioMesaAssistantAnswer {
        val eligible = snapshot.menuItems.filter {
            it.name.isNotBlank() && it.sellingPrice > zero && it.unitCost > zero
        }
        if (eligible.isEmpty()) return StudioMesaAssistantAnswer(
            "Faltam custos para comparar margens",
            "Não encontrei artigos com preço de venda e custo interno válidos. Confirma os valores na Carta antes de comparar margens; não vou inventar dados."
        )
        val comparator = if (lowestMargin) {
            compareBy<Pair<AssistantMenuItem, BigDecimal>> { it.second }
                .thenBy { it.first.name.lowercase(Locale.ROOT) }
        } else {
            compareByDescending<Pair<AssistantMenuItem, BigDecimal>> { it.second }
                .thenBy { it.first.name.lowercase(Locale.ROOT) }
        }
        val ranked = eligible.map { item ->
            val margin = item.sellingPrice.subtract(item.unitCost).multiply(hundred)
                .divide(item.sellingPrice, 4, RoundingMode.HALF_UP)
            item to margin
        }.sortedWith(comparator)
        val details = ranked.take(5).joinToString("\n") { (item, margin) ->
            "• ${item.name}: margem de contribuição estimada ${percent(margin)}; custo ${euro(item.unitCost)} de ${euro(item.sellingPrice)}."
        }
        val direction = if (lowestMargin) "menor para a maior" else "maior para a menor"
        return StudioMesaAssistantAnswer(
            "Margem estimada por artigo",
            "Ordenei da $direction percentagem de margem de contribuição calculada por (preço de venda − custo interno) ÷ preço de venda. Não é lucro líquido, não mede popularidade e não inclui automaticamente IVA, pessoal, renda, energia, desperdício ou taxas.\n$details",
            "Carta"
        )
    }

    private fun answerMarketing(rawQuestion: String, snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val candidates = findMenuItemCandidates(rawQuestion, snapshot.menuItems)
        if (candidates.size > 1) return clarificationForMenuItems(candidates, "criar o rascunho de publicação")
        val selected = candidates.singleOrNull()
        if (selected != null && (selected.name.isBlank() || selected.sellingPrice <= zero)) {
            return StudioMesaAssistantAnswer(
                "Faltam dados para a publicação",
                "O artigo ${selected.name} não tem nome ou preço de venda válido na Carta. Confirma os dados antes de criar o rascunho.",
                "Carta"
            )
        }
        val drafts = if (selected != null) marketingDrafts(snapshot.copy(menuItems = listOf(selected)), limit = 1)
            else marketingDrafts(snapshot)
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
        val intentMatch = RestaurantAssistantIntentClassifier.classify(rawQuestion)
        return when (intentMatch.intent) {
            RestaurantAssistantIntent.ECONOMIC_MANAGER -> answerEconomicQuestion(rawQuestion, snapshot)
            RestaurantAssistantIntent.DAILY_BRIEFING -> dailyBriefing(snapshot)
            RestaurantAssistantIntent.MENU_ENGINEERING -> answerMenuEngineering(rawQuestion, snapshot)
            RestaurantAssistantIntent.MARKETING -> answerMarketing(rawQuestion, snapshot)
            RestaurantAssistantIntent.PRICING -> answerPricing(rawQuestion, snapshot)
            RestaurantAssistantIntent.STOCK -> answerStock(rawQuestion, snapshot)
            RestaurantAssistantIntent.RESERVATIONS -> answerReservations(rawQuestion, snapshot)
            RestaurantAssistantIntent.ORDERS -> answerOrders(rawQuestion, snapshot)
            RestaurantAssistantIntent.TROUBLESHOOTING -> answerProblem(rawQuestion, snapshot)
            RestaurantAssistantIntent.ACCESS -> StudioMesaAssistantAnswer(
                "Acesso e equipa",
                "Confirma em Conta quem tem acesso ao restaurante. Esta versão não altera permissões nem verifica revogações em tempo real. Antes de remover ou convidar alguém, confirma a identidade e o restaurante selecionado.",
                "Conta"
            )
            RestaurantAssistantIntent.GENERAL -> answerGeneral(rawQuestion)
        }
    }

    private fun answerPricing(rawQuestion: String, snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val candidates = findMenuItemCandidates(rawQuestion, snapshot.menuItems)
        if (candidates.size > 1) return clarificationForMenuItems(candidates, "analisar o preço")
        val selected = candidates.singleOrNull()
        if (selected != null && (selected.unitCost <= zero || selected.sellingPrice <= zero)) {
            val missing = buildList {
                if (selected.sellingPrice <= zero) add("preço de venda")
                if (selected.unitCost <= zero) add("custo interno")
            }.joinToString(" e ")
            return StudioMesaAssistantAnswer(
                "Faltam dados para ${selected.name}",
                "O artigo ${selected.name} não tem ${missing} válido(s) registado(s). Confirma os valores na Carta; não vou sugerir um preço com dados incompletos.",
                "Carta"
            )
        }

        val priced = snapshot.menuItems.filter { it.sellingPrice > zero && it.unitCost > zero }
        if (priced.isEmpty()) return StudioMesaAssistantAnswer(
            "Análise de preços",
            "Ainda não tenho artigos com preço de venda e custo interno válidos. Abre Carta, confirma os dois valores por artigo e volta a perguntar. Não vou inventar um preço sem custo conhecido.",
            "Carta"
        )

        val analysisItems = selected?.let(::listOf) ?: priced
        val top = analysisItems.map { item ->
            val share = item.unitCost.multiply(hundred).divide(item.sellingPrice, 4, RoundingMode.HALF_UP)
            Triple(item, share, recommendedSellingPrice(item.unitCost))
        }.sortedByDescending { it.second }.take(4)
        val details = top.joinToString("\n") { (item, share, target) ->
            val targetText = target?.let { " Referência a 30%: ${euro(it)}." } ?: ""
            "• ${item.name}: custo ${euro(item.unitCost)}, venda ${euro(item.sellingPrice)}, custo/venda ${percent(share)}.$targetText"
        }
        val over = analysisItems.count {
            it.unitCost.multiply(hundred).divide(it.sellingPrice, 4, RoundingMode.HALF_UP) > referenceCostShare
        }
        val scope = if (selected != null) "o artigo selecionado" else "${analysisItems.size} artigo(s)"
        return StudioMesaAssistantAnswer(
            "Análise de preços",
            "Analisei $scope com custo válido; $over ultrapassam a referência inicial de triagem de 30%.\n$details\n\n30% é apenas um ponto de partida, não uma regra para todos os restaurantes. O cálculo não inclui automaticamente mão de obra, renda, energia, IVA, desperdício, taxas ou concorrência. Não alterei nenhum preço.",
            "Preço Justo"
        )
    }

    private fun answerEconomicQuestion(rawQuestion: String, snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val q = normalizeLookupText(rawQuestion)
        val isDedicatedMonthlyProfitEstimate = containsAny(
            q,
            "quanto lucro tive este mes",
            "quanto foi o lucro este mes",
            "qual foi o lucro este mes",
            "lucro deste mes"
        )
        if (RestaurantAssistantIntentClassifier.requestsDatedMenuPerformance(rawQuestion) &&
            !RestaurantAssistantIntentClassifier.requestsDatedSales(rawQuestion) &&
            !isDedicatedMonthlyProfitEstimate) {
            return StudioMesaAssistantAnswer(
                "Período de desempenho não confirmado",
                "Os dados recebidos pelo Gestor económico não incluem séries temporais fiáveis de vendas, preços, custos, despesas ou margens. Os valores de custo/preço carregados são os atuais e não provam os valores históricos do período pedido. Na versão Premium, a página Análise permite consultar contagens e valor nominal de pedidos concluídos por intervalo, mas não reconstrói custos ou lucros históricos. Confirma os registos históricos antes de decidir.",
                "Análise"
            )
        }
        if (RestaurantAssistantIntentClassifier.requestsDatedSales(rawQuestion)) {
            return StudioMesaAssistantAnswer(
                "Período de vendas não confirmado",
                "Não vou apresentar totais acumulados como se pertencessem ao período pedido. Na versão Premium, abre Análise e escolhe o intervalo para consultar o número de pedidos e o valor nominal dos pedidos concluídos. Estes valores não confirmam pagamentos nem contabilidade, e não permitem inferir margem histórica sem os custos do mesmo período.",
                "Análise"
            )
        }

        if (containsAny(q, "quanto me custou") ||
            (containsAny(q, "quanto me custa") && findMenuItemCandidates(rawQuestion, snapshot.menuItems).isNotEmpty())) {
            val candidates = findMenuItemCandidates(rawQuestion, snapshot.menuItems)
            if (candidates.size > 1) return clarificationForMenuItems(candidates, "consultar o custo registado")
            if (candidates.size == 1) return answerRegisteredItemCost(rawQuestion, snapshot)
            return StudioMesaAssistantAnswer(
                "Custo do artigo não identificado",
                "Indica o nome exato de um artigo da Carta. Posso consultar o custo interno agregado atualmente registado ou ajudar a calcular uma receita com os ingredientes que introduzires; não tenho um histórico de custos de compra e não vou inventar um valor.",
                "Carta"
            )
        }

        if (containsAny(q, "ponto de equilibrio", "break even") &&
            containsAny(q, "tesouraria", "fluxo de caixa", "cash flow", "cashflow")) {
            return StudioMesaAssistantAnswer(
                "Gestor económico (opcional)",
                "A pergunta combina duas análises diferentes. Para o ponto de equilíbrio, introduz vendas e custos do mesmo mês; para a tesouraria a 30 dias, introduz saldo atual, recebimentos e pagamentos com datas compatíveis. Calcula cada uma na respetiva secção. Estes cálculos usam valores manuais e não consultam bancos ou fornecedores.",
                "Assistente"
            )
        }
        if (containsAny(q, "resumo da tesouraria", "tesouraria e custo alimentar", "resumo financeiro") &&
            containsAny(q, "tesouraria", "fluxo de caixa") && containsAny(q, "custo alimentar", "custos alimentares")) {
            return StudioMesaAssistantAnswer(
                "Gestor económico (opcional)",
                "Para resumir a tesouraria e o custo alimentar, faz duas análises na área económica: o custo alimentar usa vendas líquidas e consumo alimentar do mesmo período; a tesouraria usa saldo atual, recebimentos e pagamentos previstos a 30 dias. Os valores são introduzidos manualmente; não consultam bancos ou fornecedores.",
                "Assistente"
            )
        }

        if (containsAny(q, "prejuizo", "a perder dinheiro", "perde dinheiro", "perde dinheiro neste prato")) {
            val candidates = findMenuItemCandidates(rawQuestion, snapshot.menuItems)
            if (candidates.size > 1) return clarificationForMenuItems(candidates, "verificar o custo e a contribuição")
            if (candidates.size == 1) return answerRegisteredItemCost(rawQuestion, snapshot)
        }

        if (containsAny(
                q, "custo dos ingredientes", "custo de ingredientes", "custo da receita",
                "quanto custa cada receita", "quanto custa uma receita", "custo de uma receita",
                "quanto custa uma dose", "quanto custa a dose", "custo da dose",
                "ficha tecnica", "custo de producao", "quanto custa produzir"
            )) {
            return StudioMesaAssistantAnswer(
                "Custo por receita e ingredientes",
                "A Carta guarda um custo agregado por artigo, não uma lista fiável de ingredientes por dose. Para calcular o custo detalhado, ativa o Gestor económico e usa «Custo por receita e dose»: introduz cada ingrediente, quantidade por dose, preço por unidade de compra e desperdício %. As unidades têm de coincidir (por exemplo, kg e €/kg). Não vou inventar ingredientes nem quantidades.",
                "Assistente"
            )
        }

        if (containsAny(q, "custo do prato", "custo deste prato", "custo por dose", "custo por porcao",
                "custo de uma dose", "custo de cada dose", "custo para uma dose", "quanto custa uma dose",
                "calcular custo do prato")) {
            return answerRegisteredItemCost(rawQuestion, snapshot)
        }

        if (containsAny(q, "quanto lucro", "lucro", "prejuizo", "resultado operacional", "resultado deste mes", "lucro deste mes")) {
            return StudioMesaAssistantAnswer(
                "Estimativa de resultado mensal",
                "Não consigo confirmar o lucro de um mês apenas com os preços atuais da Carta: preciso de vendas líquidas e custos do mesmo período, incluindo pessoal, outros custos fixos e variáveis. Os pedidos carregados não são, por si só, contabilidade mensal completa. Ativa o Gestor económico e introduz os valores do mês sem IVA para calcular uma estimativa; não vou inventar um lucro.",
                "Assistente"
            )
        }

        if (containsAny(q, "concorrencia", "concorrente", "concorrentes")) {
            val energyMentioned = containsAny(q, "energia", "eletricidade", "conta de luz", "conta da luz", "gas")
            val detail = if (energyMentioned) {
                "Não tenho faturas de energia nem dados de concorrentes. Regista a fatura no custo fixo mensal para comparar períodos equivalentes e recolhe preços públicos dos concorrentes manualmente. Esta IA local não pesquisa a Internet nem assume que os preços de outras empresas são comparáveis."
            } else {
                "Esta IA local não pesquisa a Internet nem dispõe de preços atuais dos concorrentes. Para uma comparação fiável, introduz dados públicos verificáveis, o mesmo período, zona e tipo de oferta; não vou inventar preços ou posicionamento da concorrência."
            }
            return StudioMesaAssistantAnswer("Comparação com a concorrência não disponível", detail, "Assistente")
        }
        if (containsAny(q, "energia", "eletricidade", "conta de luz", "conta da luz", "gas")) {
            return StudioMesaAssistantAnswer(
                "Análise do custo de energia",
                "Esta sessão não tem faturas de eletricidade ou gás nem leituras automáticas. Introduz a fatura no campo de outros custos fixos do gestor económico e compara montantes de períodos equivalentes, tendo em conta alterações de tarifa e consumo. Não vou prometer uma poupança sem esses dados.",
                "Assistente"
            )
        }
        if (containsAny(q, "fornecedor", "fornecedores", "comparar propostas", "preco de compra") &&
            !containsAny(q, "quantidade a encomendar", "quantidade devo encomendar", "que quantidade devo encomendar", "quanto devo encomendar", "quanto encomendar", "repor stock", "reabastecer", "ponto de encomenda")) {
            return StudioMesaAssistantAnswer(
                "Comparar fornecedores",
                "Ativa o Gestor económico e usa «Comparação de fornecedores». Introduz uma linha por proposta no formato artigo | fornecedor | unidade | quantidade na embalagem | preço da embalagem. Compara o mesmo artigo e unidades equivalentes; o sistema calcula o preço por unidade com base nos valores introduzidos. Não consulta fornecedores online, não confirma qualidade, transporte ou IVA e não faz encomendas.",
                "Assistente"
            )
        }
        if (containsAny(q, "desperdicio", "perda alimentar", "comida deitada fora")) {
            return StudioMesaAssistantAnswer(
                "Analisar desperdício",
                "Ativa o Gestor económico e usa «Registo de desperdício». Introduz artigo | quantidade | custo por unidade | motivo. O total e a distribuição por motivo usam apenas os registos inseridos; não atualizam o stock. Para evitar dupla contagem, o custo alimentar mensal deve já incluir os consumos desperdiçados se o desperdício for apresentado como indicador separado.",
                "Assistente"
            )
        }
        if (containsAny(q, "consumo teorico", "consumo real", "variacao de stock", "consumo esperado")) {
            return StudioMesaAssistantAnswer(
                "Consumo teórico vs. real",
                "Ativa o Gestor económico e usa «Consumo teórico vs. real». Introduz artigo | consumo teórico | consumo real | custo por unidade. Mantém as mesmas unidades e períodos. Uma diferença positiva indica consumo acima da referência introduzida, mas pode resultar de porções, desperdício, contagem ou registos incompletos; não identifica sozinha a causa.",
                "Assistente"
            )
        }
        if (containsAny(q, "orcamento versus real", "orcamento e realizado", "comparar orcamento", "orcamento mensal", "desvio orcamental")) {
            return StudioMesaAssistantAnswer(
                "Orçamento vs. realizado",
                "Ativa o Gestor económico e usa «Orçamento vs. realizado». Introduz categoria | orçamento | valor real, com valores do mesmo período e âmbito. Um desvio positivo significa que o valor real excede o orçamento, não que seja automaticamente mau; interpreta cada categoria antes de agir.",
                "Assistente"
            )
        }
        if (containsAny(q, "custo de pessoal", "custos de pessoal", "salarios", "horas de equipa", "custo da equipa", "custo por hora", "planeamento de pessoal")) {
            return StudioMesaAssistantAnswer(
                "Cenário de custo de pessoal",
                "Ativa o Gestor económico e usa «Cenário de custo de pessoal». Introduz horas previstas | custo total por hora, incluindo encargos | vendas esperadas | objetivo de pessoal %. O resultado é uma simulação financeira, não uma escala de turnos nem aconselhamento laboral; confirma contratos, convenções, descansos e legislação aplicável antes de alterar horários.",
                "Assistente"
            )
        }
        if (containsAny(q, "quantidade a encomendar", "quantidade devo encomendar", "que quantidade devo encomendar", "quanto devo encomendar", "quanto encomendar", "repor stock", "reabastecer", "ponto de encomenda")) {
            return StudioMesaAssistantAnswer(
                "Quantidade indicativa de reposição",
                "Ativa o Gestor económico e usa «Quantidade de reposição». Introduz stock atual | consumo médio por dia | prazo do fornecedor em dias | dias de segurança. A quantidade sugerida cobre o prazo e a margem de segurança indicados, não prevê procura futura nem verifica encomendas já feitas. Confirma unidades, validade, capacidade e contagem física; o sistema não encomenda automaticamente.",
                "Assistente"
            )
        }
        if (containsAny(q, "tesouraria", "fluxo de caixa", "cash flow", "cashflow", "saldo de caixa", "caixa a 30 dias", "tesouraria a 30 dias")) {
            return StudioMesaAssistantAnswer(
                "Previsão simples de tesouraria",
                "Ativa o Gestor económico e preenche saldo de caixa/banco disponível, recebimentos previstos e pagamentos a vencer nos próximos 30 dias. Os três valores são necessários e devem ter datas compatíveis. A projeção é saldo inicial + recebimentos − pagamentos; não liga à banca, não confirma cobranças e pode omitir movimentos que não introduzas.",
                "Assistente"
            )
        }
        if (containsAny(q, "ponto de equilibrio", "break even", "vendas para cobrir custos")) {
            return StudioMesaAssistantAnswer(
                "Ponto de equilíbrio",
                "Ativa o Gestor económico e introduz vendas líquidas sem IVA, custo alimentar consumido, custo total de pessoal, outros custos fixos e outros custos variáveis do mesmo período. O cálculo simplificado trata pessoal e custos fixos como fixos e comida e outros custos variáveis como proporcionais às vendas. Se a estrutura de contribuição não for positiva, não é possível calcular um ponto de equilíbrio fiável.",
                "Assistente"
            )
        }
        if (containsAny(q,
                "previsao financeira", "prever vendas", "vendas da proxima semana",
                "faturacao esta semana", "faturacao diaria", "como esta a faturacao",
                "previsao de vendas", "quanto vou vender", "quanto vamos vender",
                "quanto venderei", "quanto venderemos", "vendas previstas",
                "estimativa de vendas", "projecao de vendas",
                "estimativa da faturacao", "projecao da faturacao")) {
            return StudioMesaAssistantAnswer(
                "Referência simples de vendas",
                "Ativa o Gestor económico e introduz pelo menos quatro vendas líquidas semanais comparáveis, da mais antiga para a mais recente. A referência calculada é a média das últimas quatro semanas, não um modelo que conhece sazonalidade, eventos, meteorologia ou tendências. Confirma que os períodos são comparáveis antes de a usar num orçamento.",
                "Assistente"
            )
        }

        return StudioMesaAssistantAnswer(
            "Gestor económico (opcional)",
            "Ativa o Gestor económico nesta página para analisar indicadores mensais, ponto de equilíbrio, tesouraria a 30 dias, custo de receitas, desperdício, comparação de fornecedores, orçamento vs. realizado, consumo teórico vs. real, reposição e cenários de custo de pessoal. Os cálculos usam os valores que o proprietário introduz nesta sessão; não consultam bancos ou fornecedores, não alteram preços, não movimentam stock nem enviam encomendas.",
            "Assistente"
        )
    }

    private fun answerRegisteredItemCost(rawQuestion: String, snapshot: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val candidates = findMenuItemCandidates(rawQuestion, snapshot.menuItems)
        if (candidates.size > 1) return clarificationForMenuItems(candidates, "analisar o custo")
        val selected = candidates.singleOrNull()
        if (selected != null) {
            if (selected.unitCost <= zero) return StudioMesaAssistantAnswer(
                "Custo em falta: ${selected.name}",
                "A Carta não tem um custo interno válido registado para ${selected.name}. Confirma o custo por artigo/porção ou ativa o Gestor económico para calcular a receita por ingredientes. Não vou inventar um valor.",
                "Carta"
            )
            val details = buildString {
                append("Custo interno registado para ${selected.name}: ").append(euro(selected.unitCost)).append(" por artigo/porção, conforme registado na Carta.")
                if (selected.sellingPrice > zero) {
                    val contribution = selected.sellingPrice.subtract(selected.unitCost)
                    val costShare = selected.unitCost.multiply(hundred).divide(selected.sellingPrice, 4, RoundingMode.HALF_UP)
                    append("\nPreço de venda registado: ").append(euro(selected.sellingPrice))
                    append("; contribuição unitária estimada: ").append(euro(contribution))
                    append("; custo/preço: ").append(percent(costShare)).append(".")
                    if (contribution < zero) {
                        append(" O custo interno registado ultrapassa o preço de venda: contribuição unitária negativa. Isto não prova o prejuízo líquido do artigo e exige confirmar custos, unidades e período.")
                    }
                } else {
                    append("\nO preço de venda ainda não é válido, por isso não calculo a contribuição.")
                }
                append("\nEste custo é o valor agregado guardado na Carta; não é uma ficha técnica de ingredientes nem lucro líquido.")
            }
            return StudioMesaAssistantAnswer("Custo registado do artigo", details, "Carta")
        }

        val knownCosts = snapshot.menuItems.filter { it.unitCost > zero }
        if (knownCosts.isEmpty()) return StudioMesaAssistantAnswer(
            "Não há custos registados",
            "Não encontrei custos internos válidos na Carta. Regista o custo por artigo/porção ou usa o Gestor económico para calcular uma receita por ingredientes antes de tirar conclusões.",
            "Carta"
        )
        val list = knownCosts.take(6).joinToString("\n") { "• ${it.name}: ${euro(it.unitCost)} por artigo/porção." }
        return StudioMesaAssistantAnswer(
            "Qual artigo da carta?",
            "Não identifiquei o prato pelo nome. Estes são alguns custos internos registados:\n$list\nIndica o nome completo para eu analisar o artigo certo. Para decompor o custo por ingrediente, usa o cálculo de receita do Gestor económico.",
            "Carta"
        )
    }

    private fun clarificationForMenuItems(candidates: List<AssistantMenuItem>, action: String): StudioMesaAssistantAnswer =
        StudioMesaAssistantAnswer(
            "Qual artigo da carta?",
            "Encontrei vários artigos que podem corresponder: ${candidates.joinToString(", ") { it.name }}. Indica o nome completo para ${action} corretamente. Não alterei nenhum valor.",
            "Carta"
        )

    private fun findMenuItemCandidates(rawQuestion: String, menuItems: List<AssistantMenuItem>): List<AssistantMenuItem> {
        val normalizedQuestion = normalizeLookupText(rawQuestion)
        if (normalizedQuestion.isBlank()) return emptyList()
        val paddedQuestion = " $normalizedQuestion "
        val genericNames = setOf("prato", "artigo", "item", "produto")
        val exactMentions = menuItems.filter { item ->
            val normalizedName = normalizeLookupText(item.name)
            normalizedName.isNotBlank() && normalizedName !in genericNames &&
                paddedQuestion.contains(" $normalizedName ")
        }
        val mostSpecific = exactMentions.filter { candidate ->
            val name = normalizeLookupText(candidate.name)
            exactMentions.none { other ->
                if (other.name == candidate.name) false
                else {
                    val otherName = normalizeLookupText(other.name)
                    otherName.length > name.length && " $otherName ".contains(" $name ")
                }
            }
        }.distinctBy { normalizeLookupText(it.name) }
        if (mostSpecific.isNotEmpty()) return mostSpecific

        val stopWords = setOf(
            "da", "de", "do", "das", "dos", "a", "o", "as", "os", "com", "e", "em", "no", "na",
            "nos", "nas", "num", "numa", "por", "para", "pelo", "pela", "pelos", "pelas",
            "quanto", "qual", "quais", "devo", "deve", "deveria", "posso", "pode", "cobrar",
            "preco", "precos", "venda", "vendas", "custo", "custos", "margem", "lucro",
            "calcular", "calculo", "calcula", "analisa", "analisar", "deste", "desta", "este", "esta",
            "estes", "estas", "prato", "pratos", "artigo", "artigos", "item", "itens", "produto",
            "produtos", "melhor", "melhores", "mais", "rentabilidade", "rentavel", "rentaveis",
            "contribuicao", "operacao", "meu", "minha", "meus", "minhas", "fazer", "faz", "faca",
            "sugere", "sugerir", "recomendar", "recomendacao", "como", "ser", "para", "tive", "este",
            "mes", "do", "da", "desta", "deste"
        )
        val questionWords = normalizedQuestion.split(" ").filter { it.length > 2 && it !in stopWords }.toSet()
        if (questionWords.isEmpty()) return emptyList()
        return menuItems.filter { item ->
            normalizeLookupText(item.name).split(" ")
                .filter { it.length > 2 && it !in stopWords }
                .any { it in questionWords }
        }.distinctBy { normalizeLookupText(it.name) }
    }

    private fun normalizeLookupText(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun answerStock(rawQuestion: String, s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        if (!s.stockFeatureAvailable) return StudioMesaAssistantAnswer("Análise de stock", "A gestão de stock está disponível no plano Premium desta versão. Não tenho dados de stock neste plano e não vou inferir quantidades.")
        if (!s.restaurantConfigured || !s.stockLoaded) return StudioMesaAssistantAnswer(
            "Análise de stock",
            "Os dados de stock ainda não foram carregados de forma confirmada. Abre Stock, configura o restaurante se necessário e atualiza os dados. Não assumo que uma cache antiga representa a quantidade física atual.",
            "Stock"
        )

        val normalizedQuestion = normalizeLookupText(rawQuestion)
        val asksTotalStockValue = containsAny(
            normalizedQuestion,
            "valor do stock", "valor em stock", "quanto vale o stock",
            "quanto vale o inventario", "valor do inventario",
            "quanto dinheiro tenho em stock", "quanto tenho empatado em stock"
        )
        if (asksTotalStockValue) {
            val knownItems = s.stockItems.filter { it.unitCostPerUnit != null && it.quantity >= zero }
            val unknownCostCount = s.stockItems.count { it.unitCostPerUnit == null }
            if (knownItems.isEmpty()) return StudioMesaAssistantAnswer(
                "Valorização do stock indisponível",
                "Não há custos unitários confirmados nos artigos carregados. Preenche o custo de compra por kg, litro ou unidade na secção Stock; não vou atribuir um valor fictício às existências.",
                "Stock"
            )
            val knownValue = knownItems.fold(zero) { sum, item ->
                sum.add(item.quantity.multiply(item.unitCostPerUnit).setScale(2, RoundingMode.HALF_UP))
            }.setScale(2, RoundingMode.HALF_UP)
            return StudioMesaAssistantAnswer(
                "Valor estimado do stock",
                "O valor estimado das existências com custo conhecido é ${euro(knownValue)} (${knownItems.size} artigo(s)). ${if (unknownCostCount > 0) "$unknownCostCount artigo(s) ficaram excluídos por não terem custo unitário registado." else "Todos os artigos carregados têm custo unitário registado."} O cálculo usa o custo de compra atual introduzido pelo restaurante; não é FIFO, custo médio contabilístico nem prova do valor de venda. Confirma a contagem física e as faturas.",
                "Stock"
            )
        }

        val paddedQuestion = " $normalizedQuestion "
        val mentioned = s.stockItems.filter { item ->
            val name = normalizeLookupText(item.name)
            name.isNotBlank() && paddedQuestion.contains(" $name ")
        }.distinctBy { normalizeLookupText(it.name) }
        val specificItems = mentioned.filter { candidate ->
            val name = normalizeLookupText(candidate.name)
            mentioned.none { other ->
                if (normalizeLookupText(other.name) == name) false
                else {
                    val otherName = normalizeLookupText(other.name)
                    otherName.length > name.length && " $otherName ".contains(" $name ")
                }
            }
        }
        if (specificItems.size > 1) return StudioMesaAssistantAnswer(
            "Qual artigo de stock?",
            "A pergunta menciona mais do que um artigo: ${specificItems.joinToString(", ") { it.name }}. Indica qual queres consultar para evitar apresentar a quantidade errada.",
            "Stock"
        )
        specificItems.singleOrNull()?.let { item ->
            val status = when {
                item.quantity <= zero -> "O registo indica quantidade zero ou negativa; confirma a contagem física antes de concluir que o artigo acabou."
                item.quantity <= item.minimumQuantity -> "Está no limite mínimo ou abaixo dele. Confirma a contagem física e o prazo de entrega antes de decidir a encomenda."
                else -> "Está acima do limite mínimo registado. Confirma a contagem física antes de tomar decisões de compra."
            }
            val unit = item.unit.ifBlank { "unidades" }
            val costDetail = item.unitCostPerUnit?.takeIf { it >= zero }?.let { unitCost ->
                val currentValue = item.quantity.takeIf { it >= zero }
                    ?.multiply(unitCost)
                    ?.setScale(2, RoundingMode.HALF_UP)
                val shortfall = item.minimumQuantity.subtract(item.quantity).max(zero)
                val topUpCost = shortfall.takeIf { it > zero }
                    ?.multiply(unitCost)
                    ?.setScale(2, RoundingMode.HALF_UP)
                buildString {
                    append(" Custo de compra registado: ${euro(unitCost)} por $unit.")
                    currentValue?.let { append(" Valor estimado da quantidade atual: ${euro(it)}.") }
                    if (shortfall > zero) {
                        append(" Para voltar ao limite mínimo faltam ${decimal(shortfall)} $unit; custo estimado: ${topUpCost?.let { euro(it) } ?: "indisponível"}.")
                    }
                }
            } ?: " Custo de compra por $unit não registado; não consigo estimar o valor nem a reposição."
            return StudioMesaAssistantAnswer(
                "Stock de ${item.name}",
                "${item.name}: ${decimal(item.quantity)} $unit disponíveis no registo; limite mínimo ${decimal(item.minimumQuantity)} $unit. $status$costDetail",
                "Stock"
            )
        }

        val requestedName = requestedStockItemName(normalizedQuestion)
        if (!requestedName.isNullOrBlank()) return StudioMesaAssistantAnswer(
            "Artigo de stock não encontrado",
            "Não encontrei «$requestedName» na lista de stock carregada. Isto não prova que não exista fisicamente; confirma a designação na secção Stock e atualiza os dados.",
            "Stock"
        )

        val low = s.stockItems.filter { it.quantity <= it.minimumQuantity }
        val detail = if (low.isEmpty()) {
            "Não encontrei artigos abaixo do limite mínimo nos dados carregados. Confirma a contagem física: só consigo analisar o que foi registado."
        } else low.take(5).joinToString("\n") { item ->
            val unit = item.unit.ifBlank { "unidades" }
            val shortfall = item.minimumQuantity.subtract(item.quantity).max(zero)
            val topUpCost = item.unitCostPerUnit?.takeIf { it >= zero && shortfall > zero }
                ?.let { shortfall.multiply(it).setScale(2, RoundingMode.HALF_UP) }
            "• ${item.name}: ${decimal(item.quantity)} $unit disponíveis; mínimo ${decimal(item.minimumQuantity)} $unit." +
                (topUpCost?.let { " Faltam ${decimal(shortfall)} $unit para o mínimo; custo estimado ${euro(it)}." } ?: "")
        } + "\nConfirma a contagem física, a embalagem e o prazo de entrega antes de encomendar."
        return StudioMesaAssistantAnswer("Análise de stock", detail, "Stock")
    }

    private fun requestedStockItemName(normalizedQuestion: String): String? {
        val patterns = listOf(
            Regex("\\b(?:quanto tenho de|quanto tem de|quanto resta de|quanto sobra de|quanto sobrou de|stock de|estoque de|tenho stock de|acabou (?:o|a|os|as)|ficou sem)\\s+(.+)$"),
            Regex("\\b(?:o|a|os|as)\\s+([a-z0-9]+)\\s+(?:acabou|acabaram|esgotou|esgotaram|esta a acabar|esta esgotado)\\b"),
            Regex("\\b(?:ha|tenho|temos|tem)\\s+(?:(?:o|a|os|as)\\s+)?([a-z0-9]+)\\s+(?:suficiente|suficientes|bastante|bastantes)\\b")
        )
        val rawCandidate = patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalizedQuestion)?.groupValues?.getOrNull(1)
        } ?: return null
        val candidate = rawCandidate
            .replace(Regex("\\s+(?:em stock|no stock|em estoque|no estoque)$"), "")
            .replace(Regex("\\s+(?:quanto|devo|encomendar|comprar|para|por favor)\\b.*$"), "")
            .trim()
            .replace(Regex("^(?:o|a|os|as)\\s+"), "")
            .trim()
        if (candidate.isBlank() || candidate in setOf("stock", "estoque", "artigo", "artigos", "produto", "produtos")) return null
        return candidate.takeIf { it.split(" ").any { word -> word.length > 2 } }
    }

    private fun answerReservations(rawQuestion: String, s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        if (!s.reservationsFeatureAvailable) return StudioMesaAssistantAnswer("Análise de reservas", "A gestão de reservas está disponível nos planos Profissional e Premium. Não há dados de reservas disponíveis neste plano.")
        if (!s.restaurantConfigured || !s.reservationsLoaded) return StudioMesaAssistantAnswer(
            "Análise de reservas",
            "Ainda não tenho uma lista de reservas carregada nesta sessão. Abre Reservas e atualiza a lista; não vou assumir que não existem reservas só porque não foram carregadas.",
            "Reservas"
        )
        val q = normalizeLookupText(rawQuestion)
        if (containsAny(q, "hoje", "ontem", "amanha", "mesas reservadas", "quantas mesas", "disponibilidade de mesa", "mesas livres",
                "reservas confirmadas", "reserva confirmada", "reservas canceladas", "reserva cancelada",
                "total de reservas", "quantas reservas no total", "por horario", "por hora", "quantas pessoas reservaram")) {
            return StudioMesaAssistantAnswer(
                "Disponibilidade de reservas não confirmada",
                "Os dados que a IA recebe só incluem o número de reservas pendentes, não um mapa confirmado de mesas nem um total filtrado por dia. Não vou afirmar quantas mesas estão ocupadas ou livres. Confirma a lista e o horário na vista Reservas.",
                "Reservas"
            )
        }
        return StudioMesaAssistantAnswer(
            "Análise de reservas",
            if (s.pendingReservations == 0) "Não encontrei reservas pendentes nos dados carregados. Confirma a lista e a capacidade antes de garantir disponibilidade."
            else "Há ${s.pendingReservations} reserva(s) pendente(s). Confirma hora, número de pessoas e capacidade antes de atribuir lugar.",
            "Reservas"
        )
    }

    private fun answerOrders(rawQuestion: String, s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val q = normalizeLookupText(rawQuestion)
        if (containsAny(q, "ontem", "hoje", "amanha", "cancelado", "cancelados", "cancelada", "canceladas",
                "concluido", "concluidos", "entregue", "entregues", "atrasado", "atrasados", "atrasada", "atrasadas",
                "quanto tempo", "tempo de espera", "tempo de entrega", "quais pedidos", "qual pedido")) {
            return StudioMesaAssistantAnswer(
                "Detalhes dos pedidos não confirmados",
                "A análise que recebi só contém contagens de pedidos ativos e em preparação; não inclui a lista de cada pedido, horários nem histórico de estados. Não vou inventar atrasos, tempos ou pedidos concluídos/cancelados. Confirma a vista Cozinha e os dados atualizados antes de agir.",
                "Pedidos"
            )
        }
        val source = if (s.isDemo) "dados de demonstração locais" else "dados que a aplicação tem carregados"
        val response = if (s.activeOrderCount == 0) "Não encontrei pedidos ativos nos $source. Isto não confirma que o servidor esteja atualizado; atualiza os pedidos antes de concluir que a fila está vazia."
        else "Há ${s.activeOrderCount} pedido(s) ativo(s), dos quais ${s.preparationOrderCount} estão em preparação, nos $source. Confirma os estados e prioriza os pedidos mais antigos na vista Cozinha. Não tenho dados suficientes para estimar tempos de entrega."
        return StudioMesaAssistantAnswer("Análise de pedidos", response, "Pedidos")
    }

    private fun answerProblem(rawQuestion: String, s: StudioMesaAssistantSnapshot): StudioMesaAssistantAnswer {
        val q = normalizeLookupText(rawQuestion)
        val response = if (containsAny(q, "nao aparece a resposta", "resposta nao aparece", "ia nao responde", "a app fecha", "aplicacao fecha", "fecha ao guardar", "fica bloqueada", "nao abre")) {
            "Não consigo ver os registos de falha do dispositivo a partir daqui. Fecha e volta a abrir a aplicação, confirma se o erro se repete e evita repetir operações de escrita sem verificar primeiro se foram guardadas. Se continuar, regista o ecrã e a mensagem de erro para diagnosticar o ponto exato; não vou fingir que testei o dispositivo."
        } else if (!s.restaurantConfigured) "A aplicação está em modo de demonstração ou ainda sem restaurante configurado. Vai a Conta e confirma que existe sessão e que o restaurante correto está associado. A demonstração não sincroniza dados com outros dispositivos."
        else "Não consigo testar a ligação à Internet nem confirmar a saúde do servidor a partir desta análise local. Abre Conta, verifica a conta e o restaurante e atualiza a secção afetada. Se falhar, confirma a Internet e inicia sessão novamente se necessário. Não repitas várias vezes uma operação de escrita sem verificar se foi registada."
        return StudioMesaAssistantAnswer("Diagnóstico inicial", response, "Conta")
    }

    private fun answerGeneral(rawQuestion: String): StudioMesaAssistantAnswer {
        val response = if (rawQuestion.isBlank()) {
            "Escreve uma pergunta concreta sobre o restaurante para eu analisar os dados disponíveis."
        } else {
            "Ainda não consegui identificar com segurança o tema desta pergunta. Experimenta perguntar sobre preços e margens, vendas e artigos da carta, marketing, stock, reservas, pedidos/cozinha, acesso ou gestão económica. Não alterei dados do restaurante."
        }
        return StudioMesaAssistantAnswer("Preciso de uma pergunta mais específica", response)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    private fun containsAny(text: String, vararg terms: String): Boolean = terms.any { text.contains(it) }
    private fun euro(v: BigDecimal): String = java.text.NumberFormat.getCurrencyInstance(Locale("pt", "PT")).format(v)
    private fun percent(v: BigDecimal): String = v.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"
    private fun decimal(v: BigDecimal): String = v.stripTrailingZeros().toPlainString().replace('.', ',')
}
