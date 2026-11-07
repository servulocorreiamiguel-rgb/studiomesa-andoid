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
        val localModelInstalled: Boolean
    )

    private val capabilityPhrases = listOf(
        "o que consegues fazer",
        "o que consegues",
        "o que podes fazer",
        "o que sabes fazer",
        "como me podes ajudar",
        "como podes ajudar",
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
            appendLine("• Ajudar a interpretar pedidos, mesas, cozinha e reservas.")
            appendLine("• Dar sugestões para reduzir desperdício, organizar a operação e melhorar resultados.")
            if (features.hasReservationsAndPricing) {
                appendLine("• Ajudar com reservas e referências de preço.")
            }
            if (features.isPremium) {
                appendLine("• Analisar stock, movimentos, custos e indicadores de desempenho.")
            }
            appendLine("• Consultar o arquivo de faturas importadas e distinguir documentos confirmados dos que ainda precisam de revisão.")
            appendLine("• Usar o histórico e a memória para manter contexto entre conversas neste restaurante.")
            if (features.localModelInstalled) {
                appendLine()
                appendLine("O modelo generativo local está instalado: podes fazer perguntas abertas e continuar a conversa no próprio telemóvel, mesmo sem Internet.")
            } else {
                appendLine()
                appendLine("O modelo generativo local ainda não está instalado. Sem ele, algumas respostas ficam limitadas às funções e regras já disponíveis na aplicação.")
            }
            appendLine()
            append("Limites importantes: analiso e recomendo, mas não altero preços, stock, pedidos, reservas, pagamentos ou documentos por iniciativa própria. As operações exigem confirmação nos controlos da aplicação. Não substituo um contabilista nem tenho acesso geral à Internet.")
        }.trim()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
