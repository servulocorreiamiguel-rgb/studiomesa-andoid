package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerativeAssistantPrivacyFilterTest {
    @Test fun allowsGeneralRestaurantAdviceWithoutPersonalOrFinancialDetails() {
        assertTrue(GenerativeAssistantPrivacyFilter.isSafeToSend(
            listOf("Como posso melhorar a experiência no restaurante?")
        ))
        assertTrue(GenerativeAssistantPrivacyFilter.isSafeToSend(
            listOf("Quero criar um plano de fidelização.", "Que ideias recomendas?")
        ))
    }

    @Test fun keepsInvoicesFiscalAndNumericBusinessDataOnDevice() {
        listOf(
            "Analisa esta fatura.",
            "Quanto IVA paguei?",
            "O meu NIF é 123456789.",
            "Tenho 1200 euros de despesas.",
            "Fornecedor X",
            "Fornecedor: Distribuidor Exemplo",
            "O email é teste@example.com",
            "Liga para +351 912 345 678"
        ).forEach { assertFalse(it, GenerativeAssistantPrivacyFilter.isSafeToSend(listOf(it))) }
    }

    @Test fun rejectsEmptyOversizedOrExcessiveContext() {
        assertFalse(GenerativeAssistantPrivacyFilter.isSafeToSend(emptyList()))
        assertFalse(GenerativeAssistantPrivacyFilter.isSafeToSend(List(11) { "Olá" }))
        assertFalse(GenerativeAssistantPrivacyFilter.isSafeToSend(listOf("x".repeat(12001))))
    }
}
