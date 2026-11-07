package pt.studiomesa.app

import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantGeneralConversationPolicyTest {

    @Test
    fun instructionClearlySupportsGeneralTopicsBeyondRestaurantManagement() {
        val instruction = AssistantGeneralConversationPolicy.SYSTEM_INSTRUCTION.lowercase()
        assertTrue(instruction.contains("assistente conversacional de uso geral"))
        assertTrue(instruction.contains("não te limites a temas de restauração"))
        assertTrue(instruction.contains("programação"))
        assertTrue(instruction.contains("matemática e ciências"))
        assertTrue(instruction.contains("traduções"))
    }

    @Test
    fun instructionHandlesNaturalLanguageAndConversationFollowUps() {
        val instruction = AssistantGeneralConversationPolicy.SYSTEM_INSTRUCTION.lowercase()
        assertTrue(instruction.contains("erros ortográficos"))
        assertTrue(instruction.contains("contexto das mensagens anteriores"))
        assertTrue(instruction.contains("faz uma pergunta curta para esclarecer"))
    }

    @Test
    fun conversationHistoryIsExpandedWithoutExceedingTheCurrentContextBudget() {
        assertTrue(AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGES > 4)
        assertTrue(AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGE_CHARS > 300)
        assertTrue(AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGES *
            AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGE_CHARS <= 4000)
    }
}
