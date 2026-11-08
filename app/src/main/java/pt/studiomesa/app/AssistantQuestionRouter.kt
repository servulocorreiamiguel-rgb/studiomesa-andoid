package pt.studiomesa.app

import java.text.Normalizer
import java.util.Locale

/**
 * Routes only questions that clearly need current information to public search.
 * Ordinary general conversation remains a model-only request.
 */
internal object AssistantQuestionRouter {
    private val currentSignals = listOf(
        "atual", "atualiz", "hoje", "agora", "neste momento", "recent",
        "ultima noticia", "ultimas noticias", "noticia", "noticias",
        "tendencia", "tendencias", "mercado atual", "dicas de negocio",
        "dicas de negocios", "legislacao", "lei em vigor", "taxa atual",
        "preco atual", "precos atuais", "cotacao", "cambio", "estatisticas atuais",
        "pesquisa na web", "pesquisa na internet", "fontes atuais"
    )

    fun requiresWebSearch(question: String): Boolean {
        val normalized = Normalizer.normalize(question.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized.length >= 3 && currentSignals.any(normalized::contains)
    }
}
