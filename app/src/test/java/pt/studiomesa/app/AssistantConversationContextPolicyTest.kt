package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantConversationContextPolicyTest {
    @Test
    fun keepsLatestValidTurnsInChronologicalOrderWithinTotalBudget() {
        val history = listOf(
            LocalAssistantModelManager.Turn("user", "pergunta antiga"),
            LocalAssistantModelManager.Turn("assistant", "resposta antiga"),
            LocalAssistantModelManager.Turn("system", "não deve ser passado"),
            LocalAssistantModelManager.Turn("user", "pergunta recente"),
            LocalAssistantModelManager.Turn("assistant", "resposta recente")
        )

        val result = AssistantConversationContextPolicy.compactHistory(history)

        assertEquals(
            listOf("pergunta antiga", "resposta antiga", "pergunta recente", "resposta recente"),
            result.map { it.content }
        )
        assertTrue(result.sumOf { it.content.length } <= AssistantConversationContextPolicy.MAX_TOTAL_HISTORY_CHARS)
    }

    @Test
    fun truncatesLongMessagesWithAnExplicitEllipsisInsteadOfSilentMeaningLoss() {
        val long = "x".repeat(1_000)
        val result = AssistantConversationContextPolicy.compactHistory(
            listOf(
                LocalAssistantModelManager.Turn("user", "pergunta recente"),
                LocalAssistantModelManager.Turn("assistant", long)
            )
        )

        assertEquals(2, result.size)
        assertTrue(result.last().content.length <= AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGE_CHARS)
        assertTrue(result.last().content.startsWith("…"))
        assertTrue(result.sumOf { it.content.length } <= AssistantConversationContextPolicy.MAX_TOTAL_HISTORY_CHARS)
    }

    @Test
    fun dropsBlankAndUnknownRolesWithoutDroppingNewestUserTurn() {
        val result = AssistantConversationContextPolicy.compactHistory(
            listOf(
                LocalAssistantModelManager.Turn("system", "instrução"),
                LocalAssistantModelManager.Turn("user", "   "),
                LocalAssistantModelManager.Turn("assistant", "resposta"),
                LocalAssistantModelManager.Turn("user", "nova pergunta")
            )
        )

        assertEquals(listOf("resposta", "nova pergunta"), result.map { it.content })
    }
}
