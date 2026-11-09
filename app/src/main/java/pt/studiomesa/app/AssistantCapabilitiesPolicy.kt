package pt.studiomesa.app

import java.text.Normalizer
import java.util.Locale

/**
 * Handles direct questions about the assistant's own role in both local-model
 * and rule-based modes, so users can discover available app features.
 */
internal object AssistantCapabilitiesPolicy {
    data class Features(
        val hasReservationsAndPricing: Boolean,
        val isPremium: Boolean,
        val localModelInstalled: Boolean,
        val canAccessInvoices: Boolean = false
    )

    private val capabilityPhrases = listOf(
        "o que consegues fazer",
        "o que consegues",
        "consegues fazer o que",
        "o que podes fazer",
        "o que sabes fazer",
        "o que e que consegues fazer",
        "como me podes ajudar",
        "como podes ajudar",
        "como te posso usar",
        "que tipo de perguntas posso fazer",
        "que perguntas posso fazer",
        "quais sao as tuas capacidades",
        "em que me podes ajudar",
        "em que podes ajudar",
        "quais sao as tuas funcoes",
        "que funcionalidades tens",
        "que funcoes tens",
        "para que serves",
        "qual e o teu objetivo",
        "como funciona a ia",
        "como funciona o assistente"
    )

    fun answer(question: String, features: Features): String? {
        val normalized = normalize(question)
        val asksAboutCapabilities = capabilityPhrases.any { normalized.contains(it) } ||
            normalized == "ajuda" ||
            normalized == "o que fazes"
        if (!asksAboutCapabilities) return null

        return buildString {
            appendLine("Sou o Assistente StudioMesa. Posso ajudar-te a perceber e gerir melhor a operação do restaurante.")
            appendLine()
            appendLine("Posso:")
            appendLine("• Explicar como utilizar a StudioMesa e as suas secções.")
            appendLine("• Analisar a carta, custos por dose, margens e preços, usando os dados disponíveis.")
            appendLine(
                if (features.hasReservationsAndPricing)
                    "• Ajudar a interpretar pedidos, mesas, cozinha e reservas."
                else
                    "• Ajudar a interpretar pedidos, mesas e cozinha."
            )
            appendLine("• Dar sugestões para reduzir desperdício, organizar a operação e melhorar resultados.")
            if (features.hasReservationsAndPricing) {
                appendLine("• Ajudar com reservas e referências de preço.")
            }
            if (features.isPremium) {
                appendLine("• Analisar stock, movimentos, custos e indicadores de desempenho.")
            }
            if (features.canAccessInvoices) {
                appendLine("• Usar o Gestor Económico para calcular resultado simplificado, ponto de equilíbrio, tesouraria projetada, custo por dose, desperdício, desvios de orçamento, consumo e reposição a partir dos valores fornecidos.")
                appendLine("• Consultar faturas por fornecedor, estado e período de emissão, separando aprovadas, pendentes e rejeitadas. A consulta é apenas de leitura; aprovação não significa pagamento.")
            }
            appendLine("• Usar o histórico e a memória para manter contexto entre conversas neste restaurante.")
            if (features.localModelInstalled) {
                appendLine()
                appendLine("O modelo generativo local está instalado. A geração de respostas corre no telemóvel; pode funcionar sem Internet. A pesquisa pública e a sincronização cloud são operações separadas e exigem ligação, sendo a sincronização sujeita à autorização do proprietário.")
            } else {
                appendLine()
                appendLine("O modelo generativo local ainda não está instalado. Sem ele, algumas respostas ficam limitadas às funções e regras já disponíveis na aplicação.")
            }
            appendLine()
            append("Limites importantes: posso pesquisar informação pública na Web quando a conta tem sessão iniciada e o serviço está configurado. Comparo fontes e assinalo contradições ou falta de provas, mas não consigo garantir a verdade absoluta. Não envio dados privados do restaurante para pesquisas públicas. Analiso e recomendo, mas não altero preços, stock, pedidos, reservas, pagamentos ou documentos por iniciativa própria; as operações exigem confirmação. Não substituo um contabilista.")
        }.trim()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
