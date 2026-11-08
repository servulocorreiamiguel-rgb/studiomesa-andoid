package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantQuestionRouterTest {
    @Test
    fun currentBusinessAdviceUsesPublicSearch() {
        assertTrue(AssistantQuestionRouter.requiresWebSearch("Dá-me dicas de negócio atualizadas para 2026"))
    }

    @Test
    fun currentNewsAndRegulationUsePublicSearch() {
        assertTrue(AssistantQuestionRouter.requiresWebSearch("Quais são as últimas notícias sobre restauração?"))
        assertTrue(AssistantQuestionRouter.requiresWebSearch("Qual é a legislação em vigor hoje?"))
    }

    @Test
    fun ordinaryGeneralQuestionsDoNotTriggerSearch() {
        assertFalse(AssistantQuestionRouter.requiresWebSearch("Explica a fotossíntese."))
        assertFalse(AssistantQuestionRouter.requiresWebSearch("Ajuda-me a escrever um email."))
    }

    @Test
    fun accentsAndUppercaseAreNormalized() {
        assertTrue(AssistantQuestionRouter.requiresWebSearch("PREÇOS ATUAIS do azeite"))
    }
}
