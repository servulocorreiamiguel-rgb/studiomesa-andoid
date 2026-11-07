package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCapabilitiesPolicyTest {

    private val standardFeatures = AssistantCapabilitiesPolicy.Features(
        hasReservationsAndPricing = false,
        isPremium = false,
        localModelInstalled = false
    )

    @Test
    fun recognizesNaturalQuestionsAboutItsCapabilities() {
        val answer = AssistantCapabilitiesPolicy.answer(
            "O que consegues fazer e como me podes ajudar?",
            standardFeatures
        )
        assertNotNull(answer)
        assertTrue(answer.orEmpty().contains("Sou o Assistente StudioMesa"))
        assertTrue(answer.orEmpty().contains("carta, custos por dose, margens e preços"))
        assertTrue(answer.orEmpty().contains("modelo generativo local ainda não está instalado"))
    }

    @Test
    fun describesFeaturesOnlyWhenTheyAreAvailable() {
        val answer = AssistantCapabilitiesPolicy.answer(
            "Que funcionalidades tens?",
            AssistantCapabilitiesPolicy.Features(
                hasReservationsAndPricing = true,
                isPremium = true,
                localModelInstalled = true
            )
        ).orEmpty()
        assertTrue(answer.contains("reservas e referências de preço"))
        assertTrue(answer.contains("Analisar stock"))
        assertTrue(answer.contains("modelo generativo local está instalado"))
    }

    @Test
    fun recognisesQuestionsAboutWhatTheUserCanAsk() {
        val answer = AssistantCapabilitiesPolicy.answer(
            "Que tipo de perguntas posso fazer?",
            standardFeatures
        )
        assertNotNull(answer)
        assertTrue(answer.orEmpty().contains("Sou o Assistente StudioMesa"))
    }

    @Test
    fun doesNotInterceptNormalRestaurantQuestions() {
        assertNull(AssistantCapabilitiesPolicy.answer("Como posso melhorar a margem dos pratos?", standardFeatures))
        assertNull(AssistantCapabilitiesPolicy.answer("Quantas reservas tenho hoje?", standardFeatures))
        assertFalse(AssistantCapabilitiesPolicy.answer("O que consegues fazer?", standardFeatures).isNullOrBlank())
    }
}
