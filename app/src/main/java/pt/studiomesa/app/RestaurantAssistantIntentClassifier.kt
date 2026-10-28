package pt.studiomesa.app

import java.text.Normalizer
import java.util.Locale

internal enum class RestaurantAssistantIntent {
    ECONOMIC_MANAGER, DAILY_BRIEFING, MENU_ENGINEERING, MARKETING, PRICING,
    STOCK, RESERVATIONS, ORDERS, TROUBLESHOOTING, ACCESS, GENERAL
}

internal data class RestaurantAssistantIntentMatch(
    val intent: RestaurantAssistantIntent,
    val confidence: Int,
    val matchedPhrases: List<String>
)

/**
 * StudioMesa's own offline intent classifier.
 *
 * No downloaded model, third-party API or network transmission. It scores
 * domain-specific phrases after Portuguese text normalization. The score is
 * a routing heuristic, not a probability or a claim of generative AI.
 */
internal object RestaurantAssistantIntentClassifier {
    private data class Rule(
        val intent: RestaurantAssistantIntent,
        val phrase: String,
        val weight: Int
    )

    private val rules = listOf(
        rule(RestaurantAssistantIntent.ECONOMIC_MANAGER, 10,
            "gestao economica", "gestor economico", "gestao financeira", "gestor financeiro",
            "ponto de equilibrio", "break even", "quantidade a encomendar", "quanto devo encomendar", "quanto encomendar", "ponto de encomenda", "previsao financeira",
            "ficha tecnica", "custo dos ingredientes", "custo de ingredientes", "custo do prato",
            "custo deste prato", "custo por dose", "custo por porcao", "preco de compra",
            "precos de compra", "comparar fornecedores",
            "comparo fornecedores", "comparacao de fornecedores",
            "planeamento de compras", "desperdicio por motivo", "consumo teorico", "vendas da proxima semana",
            "resumo financeiro", "resumo economico", "quanto custa cada receita",
            "quanto custa uma receita", "custo de uma receita", "custo de cada dose",
            "custo de uma dose", "custo para uma dose", "vendas desta semana",
            "vendas esta semana", "faturacao esta semana", "faturacao diaria",
            "como esta a faturacao", "custos operacionais", "custo operacional"),
        rule(RestaurantAssistantIntent.ECONOMIC_MANAGER, 9,
            "fluxo de caixa", "custos fixos", "custo fixo", "custos variaveis", "custo variavel",
            "custo de pessoal", "horas de equipa", "prever vendas", "repor stock", "reabastecer",
            "custo de producao", "custo para produzir", "calcular custo do prato", "calcular custo da receita",
            "quanto custa produzir", "preco fornecedor", "resultado operacional", "resultado mensal", "lucro mensal",
            "prejuizo mensal", "prejuizo", "perder dinheiro", "perco dinheiro", "onde perco dinheiro",
            "estou a perder dinheiro", "quanto lucro tive este mes", "lucro tive este mes", "lucro este mes",
            "lucro deste mes", "resultado deste mes", "qual foi o meu lucro", "reduzir custos",
            "poupar dinheiro", "quanto gastei em salarios", "gasto com salarios", "custos salariais",
            "quanto gastei em pessoal", "quanto resta em caixa", "quanto resta no caixa",
            "quanto tenho em caixa", "saldo de caixa", "poupar nas compras", "custos dos ingredientes",
            "custos de ingredientes", "cash flow", "cashflow",
            "custo alimentar", "despesas mensais", "orcamento versus real", "orcamento e realizado",
            "consumo real", "custo da receita", "variacao de stock", "rentabilidade do negocio",
            "analise financeira", "margem de contribuicao", "saldo no fim do mes", "caixa negativo"),
        rule(RestaurantAssistantIntent.ECONOMIC_MANAGER, 8,
            "tesouraria", "fornecedor", "fornecedores", "orcamento", "desperdicio", "previsao", "financas",
            "financeiro", "financeira", "planeamento de compras", "despesas", "rentabilidade"),

        rule(RestaurantAssistantIntent.DAILY_BRIEFING, 10,
            "resumo da operacao", "estado da operacao", "prioridades de hoje", "resumo do dia",
            "resumo das reservas", "resumo dos pedidos", "resumo da cozinha", "resumo do stock"),
        rule(RestaurantAssistantIntent.DAILY_BRIEFING, 8, "briefing", "resumo geral"),

        rule(RestaurantAssistantIntent.MENU_ENGINEERING, 10, "engenharia da carta", "menu engineering"),
        rule(RestaurantAssistantIntent.MENU_ENGINEERING, 9,
            "mais vendidos", "rentabilidade da carta", "rentabilidade do menu", "o que vende melhor",
            "quais pratos vendem", "quais pratos dao mais lucro", "pratos dao mais lucro",
            "pratos mais lucrativos", "aumentar as vendas", "aumentar vendas", "melhorar as vendas",
            "melhorar vendas", "aumento a faturacao", "aumento da faturacao", "aumentar a faturacao",
            "melhorar a faturacao", "aumentar receitas", "aumento de vendas", "crescer as vendas",
            "aumentar o volume de vendas"),
        rule(RestaurantAssistantIntent.MENU_ENGINEERING, 10,
            "quais pratos estao a dar prejuizo", "quais pratos dao prejuizo",
            "quais pratos sao menos rentaveis", "pratos menos rentaveis", "artigos menos rentaveis",
            "quais artigos tem melhor margem", "artigos com melhor margem", "melhor margem",
            "maior margem", "margem mais baixa", "menor margem", "mais vendido", "vendeu mais"),
        rule(RestaurantAssistantIntent.MENU_ENGINEERING, 8,
            "popularidade", "artigos que vendem", "vender mais", "analisa as vendas", "vendas do menu"),

        rule(RestaurantAssistantIntent.MARKETING, 9, "redes sociais", "texto promocional", "ideias de publicacao"),
        rule(RestaurantAssistantIntent.MARKETING, 8,
            "instagram", "marketing", "publicacao", "divulgacao", "post para", "campanha",
            "o que devo publicar", "o que publicar", "story", "stories", "legenda para",
            "publicar nas redes", "cria um post"),
        rule(RestaurantAssistantIntent.MARKETING, 7, "conteudo", "anuncio"),

        rule(RestaurantAssistantIntent.PRICING, 9,
            "quanto devo cobrar", "que preco devo cobrar", "calcular preco", "analise de preco"),
        rule(RestaurantAssistantIntent.PRICING, 8, "preco de venda", "preco justo", "definir o preco",
            "custo dos pratos", "custos dos pratos", "custo dos artigos", "custos dos artigos"),
        rule(RestaurantAssistantIntent.PRICING, 6, "preco", "margem", "lucro", "custo por artigo"),

        rule(RestaurantAssistantIntent.STOCK, 9, "quantidade em stock", "quanto tenho de stock"),
        rule(RestaurantAssistantIntent.STOCK, 8,
            "stock baixo", "stock esgotado", "stock a acabar", "ingrediente em falta",
            "ingredientes a acabar", "ingredientes estao a acabar", "ingredientes quase esgotados",
            "produto a acabar", "produtos a acabar", "artigos a acabar"),
        rule(RestaurantAssistantIntent.STOCK, 9,
            "quanto resta de", "quanto sobrou de", "stock do", "quantidade disponivel"),
        rule(RestaurantAssistantIntent.STOCK, 5, "stock"),

        rule(RestaurantAssistantIntent.RESERVATIONS, 9,
            "confirmar reserva", "reservas pendentes", "marcar mesa", "mesas reservadas",
            "reservas de hoje", "reservas para hoje", "disponibilidade de mesa", "mesas estao reservadas"),
        rule(RestaurantAssistantIntent.RESERVATIONS, 7, "mesa para", "reservar mesa"),
        rule(RestaurantAssistantIntent.RESERVATIONS, 5, "reserva", "reservas"),

        rule(RestaurantAssistantIntent.ORDERS, 8,
            "pedidos ativos", "pedidos em aberto", "pedidos concluidos", "pedidos em preparacao",
            "fila da cozinha", "pedido atrasado"),
        rule(RestaurantAssistantIntent.ORDERS, 7, "em preparacao", "pedido atrasado", "pedidos"),
        rule(RestaurantAssistantIntent.ORDERS, 5, "pedido", "cozinha", "fila", "atraso"),

        rule(RestaurantAssistantIntent.TROUBLESHOOTING, 10,
            "nao consigo guardar", "erro ao guardar", "falha ao guardar",
            "nao consigo criar reserva", "nao consigo guardar uma reserva",
            "nao consigo confirmar reserva", "reserva nao guarda"),
        rule(RestaurantAssistantIntent.TROUBLESHOOTING, 9,
            "nao consigo iniciar sessao", "nao consigo entrar", "erro de sincronizacao",
            "problema de ligacao", "a app nao funciona", "a app fecha", "aplicacao fecha",
            "fecha ao guardar", "nao aparece a resposta", "resposta nao aparece",
            "a ia nao responde", "ia nao responde", "app nao abre", "aplicacao nao abre",
            "fica bloqueada", "erro ao iniciar"),
        rule(RestaurantAssistantIntent.TROUBLESHOOTING, 7,
            "internet", "ligacao", "sincronizacao", "sincronizar", "servidor"),
        rule(RestaurantAssistantIntent.TROUBLESHOOTING, 5, "erro", "problema", "falha"),

        rule(RestaurantAssistantIntent.ACCESS, 9, "convidar colaborador", "remover utilizador", "alterar permissoes"),
        rule(RestaurantAssistantIntent.ACCESS, 7, "permissoes", "permissao", "acesso a conta"),
        rule(RestaurantAssistantIntent.ACCESS, 5, "equipa", "funcionario", "acesso")
    ).flatten()

    fun classify(question: String): RestaurantAssistantIntentMatch {
        val normalized = normalize(question)
        if (normalized.isBlank()) {
            return RestaurantAssistantIntentMatch(RestaurantAssistantIntent.GENERAL, 0, emptyList())
        }

        val stockActionPattern = Regex("\\b(?:quanto resta de|quanto sobrou de|acabou (?:o|a|os|as)|ficou sem) [a-z0-9]+\\b")
        val financialContext = Regex("\\b(?:dinheiro|saldo|caixa|tesouraria|faturacao|orcamento|pagamento|pagar)\\b")
        val nonStockContext = Regex("\\b(?:internet|servidor|ligacao|resposta|aplicacao|app|pedido|reserva|energia|servico)\\b")
        if (stockActionPattern.containsMatchIn(normalized) &&
            !financialContext.containsMatchIn(normalized) &&
            !nonStockContext.containsMatchIn(normalized)
        ) {
            return RestaurantAssistantIntentMatch(
                RestaurantAssistantIntent.STOCK, 10, listOf("pergunta de disponibilidade de artigo")
            )
        }

        val padded = " $normalized "
        val scores = mutableMapOf<RestaurantAssistantIntent, Int>()
        val matched = mutableMapOf<RestaurantAssistantIntent, MutableList<String>>()
        rules.forEach { item ->
            val phrase = normalize(item.phrase)
            if (padded.contains(" $phrase ")) {
                scores[item.intent] = (scores[item.intent] ?: 0) + item.weight
                matched.getOrPut(item.intent) { mutableListOf() }.add(item.phrase)
            }
        }

        if (scores.isEmpty()) {
            return RestaurantAssistantIntentMatch(RestaurantAssistantIntent.GENERAL, 0, emptyList())
        }

        // Specific domains win close ties: for example, a finance question
        // containing "resumo", "preco" or "stock" stays in economic management.
        val tieBreak = listOf(
            RestaurantAssistantIntent.ECONOMIC_MANAGER,
            RestaurantAssistantIntent.DAILY_BRIEFING,
            RestaurantAssistantIntent.MENU_ENGINEERING,
            RestaurantAssistantIntent.MARKETING,
            RestaurantAssistantIntent.PRICING,
            RestaurantAssistantIntent.STOCK,
            RestaurantAssistantIntent.RESERVATIONS,
            RestaurantAssistantIntent.ORDERS,
            RestaurantAssistantIntent.TROUBLESHOOTING,
            RestaurantAssistantIntent.ACCESS
        )
        val maxScore = scores.values.maxOrNull() ?: 0
        val intent = tieBreak.firstOrNull { scores[it] == maxScore }
            ?: scores.maxByOrNull { it.value }?.key
            ?: RestaurantAssistantIntent.GENERAL
        return RestaurantAssistantIntentMatch(intent, maxScore.coerceIn(0, 100), matched[intent].orEmpty().distinct())
    }

    private fun rule(intent: RestaurantAssistantIntent, weight: Int, vararg phrases: String): List<Rule> =
        phrases.map { Rule(intent, it, weight) }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
