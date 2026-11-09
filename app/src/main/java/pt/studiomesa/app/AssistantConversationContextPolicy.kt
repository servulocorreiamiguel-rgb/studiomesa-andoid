package pt.studiomesa.app

/**
 * Keeps recent conversation context bounded for the 4K-token on-device model.
 * Older messages are dropped first; only user/assistant turns are passed to the LLM.
 */
internal data class AssistantPromptTurn(val role: String, val content: String)

internal object AssistantConversationContextPolicy {
    const val MAX_TOTAL_HISTORY_CHARS = 1_800

    fun compactHistory(history: List<LocalAssistantModelManager.Turn>): List<AssistantPromptTurn> {
        val selectedNewestFirst = mutableListOf<AssistantPromptTurn>()
        var usedChars = 0
        for (turn in history.asReversed()) {
            if (selectedNewestFirst.size >= AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGES) break
            if (turn.role != "user" && turn.role != "assistant") continue
            val raw = turn.content.trim()
            if (raw.isBlank()) continue
            val limit = AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGE_CHARS
            val clean = if (raw.length > limit) "…" + raw.takeLast(limit - 1) else raw
            val separator = if (selectedNewestFirst.isEmpty()) 0 else 1
            if (usedChars + separator + clean.length > MAX_TOTAL_HISTORY_CHARS) continue
            selectedNewestFirst.add(AssistantPromptTurn(turn.role, clean))
            usedChars += separator + clean.length
        }
        return selectedNewestFirst.asReversed()
    }
}
