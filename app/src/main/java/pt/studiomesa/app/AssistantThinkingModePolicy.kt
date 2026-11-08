package pt.studiomesa.app

/**
 * Keeps the optional deep-analysis mode explicit, bounded and testable.
 *
 * The mode asks the local model to verify assumptions and calculations. It does not
 * expose hidden reasoning or claim a stronger model than the one actually installed.
 */
internal object AssistantThinkingModePolicy {
    const val DEFAULT_MAX_OUTPUT_TOKENS = 1024
    const val DEEP_MAX_OUTPUT_TOKENS = 2048
    const val DEFAULT_TEMPERATURE = 0.3
    const val DEEP_TEMPERATURE = 0.2

    private const val DEEP_ANALYSIS_INSTRUCTION =
        "MODO PENSAR MAIS: revê a pergunta com especial cuidado; identifica os dados confirmados, as hipóteses e o que falta; verifica novamente cálculos e unidades; compara alternativas e riscos; procura contradições antes de responder. Apresenta apenas conclusões, fórmulas e justificações verificáveis, sem revelar raciocínio interno privado."

    fun buildSystemInstruction(
        baseInstruction: String,
        enabled: Boolean,
        maxChars: Int
    ): String {
        require(maxChars > 0) { "O limite da instrução tem de ser positivo." }
        val cleanBase = baseInstruction.trim()
        if (!enabled) return cleanBase.take(maxChars)

        require(maxChars > DEEP_ANALYSIS_INSTRUCTION.length + 2) {
            "O limite é demasiado pequeno para o modo de análise aprofundada."
        }
        val baseBudget = maxChars - DEEP_ANALYSIS_INSTRUCTION.length - 2
        val preservedBase = cleanBase.take(baseBudget)
        return if (preservedBase.isBlank()) {
            DEEP_ANALYSIS_INSTRUCTION
        } else {
            "$preservedBase\n\n$DEEP_ANALYSIS_INSTRUCTION"
        }
    }

    val ENGINE_CONTEXT_CANDIDATES = listOf(4096, 2048, 1024)

    fun maxOutputTokens(enabled: Boolean, contextTokens: Int = 4096): Int {
        require(contextTokens > 0) { "O contexto do motor tem de ser positivo." }
        val requested = if (enabled) DEEP_MAX_OUTPUT_TOKENS else DEFAULT_MAX_OUTPUT_TOKENS
        // Keep at least half of the context available for system instructions, tools and history.
        return minOf(requested, (contextTokens / 2).coerceAtLeast(256))
    }

    fun temperature(enabled: Boolean): Double =
        if (enabled) DEEP_TEMPERATURE else DEFAULT_TEMPERATURE
}
