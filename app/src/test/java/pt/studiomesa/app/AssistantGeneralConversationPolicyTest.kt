package pt.studiomesa.app

import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantGeneralConversationPolicyTest {

    @Test
    fun instructionClearlySupportsGeneralTopicsBeyondRestaurantManagement() {
        val instruction = AssistantGeneralConversationPolicy.SYSTEM_INSTRUCTION.lowercase()
        assertTrue(instruction.contains("assistente conversacional geral"))
        assertTrue(instruction.contains("não te limites à restauração"))
        assertTrue(instruction.contains("matemática, ciências"))
        assertTrue(instruction.contains("programação"))
        assertTrue(instruction.contains("línguas"))
        assertTrue(instruction.contains("assuntos gerais"))
    }

    @Test
    fun instructionHandlesNaturalLanguageAndConversationFollowUps() {
        val instruction = AssistantGeneralConversationPolicy.SYSTEM_INSTRUCTION.lowercase()
        assertTrue(instruction.contains("erros ortográficos"))
        assertTrue(instruction.contains("histórico recente"))
        assertTrue(instruction.contains("pergunta quando faltar informação essencial"))
    }

    @Test
    fun instructionRequiresCrossCheckingAndCarefulFactLabels() {
        val instruction = AssistantGeneralConversationPolicy.SYSTEM_INSTRUCTION.lowercase()
        assertTrue(instruction.contains("duas fontes independentes"))
        assertTrue(instruction.contains("não verificado"))
        assertTrue(instruction.contains("não prova que algo é falso"))
        assertTrue(instruction.contains("documentos primários"))
    }

    @Test
    fun conversationHistoryIsExpandedWithoutExceedingTheCurrentContextBudget() {
        assertTrue(AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGES > 4)
        assertTrue(AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGE_CHARS > 300)
        assertTrue(AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGES *
            AssistantGeneralConversationPolicy.MAX_HISTORY_MESSAGE_CHARS <= 4000)
        assertTrue(AssistantGeneralConversationPolicy.SYSTEM_INSTRUCTION.length <= 2200)
        assertTrue(AssistantConversationContextPolicy.MAX_TOTAL_HISTORY_CHARS <= 2000)
    }

    @Test
    fun instructionDescribesTheOwnerOnlyReadOnlyEconomicBridge() {
        val instruction = AssistantGeneralConversationPolicy.SYSTEM_INSTRUCTION.lowercase()
        assertTrue(instruction.contains("consult_economic_assistant"))
        assertTrue(instruction.contains("está disponível apenas para o proprietário autenticado"))
        assertTrue(instruction.contains("nunca tentes aprovar"))
        assertTrue(instruction.contains("não alegues que viste pdfs ou ocr"))
    }

}
