package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantThinkingModePolicyTest {

    @Test
    fun standardModeKeepsTheBaseInstructionAndNormalLimits() {
        val base = "Responde com rigor e não inventes dados."
        assertEquals(
            base,
            AssistantThinkingModePolicy.buildSystemInstruction(base, false, 500)
        )
        assertEquals(1024, AssistantThinkingModePolicy.maxOutputTokens(false))
        assertEquals(0.3, AssistantThinkingModePolicy.temperature(false), 0.0001)
    }

    @Test
    fun deepModeAddsVerificationDirectiveAndUsesLargerOutputBudget() {
        val result = AssistantThinkingModePolicy.buildSystemInstruction(
            "Responde em português europeu.",
            true,
            1000
        )
        assertTrue(result.startsWith("Responde em português europeu."))
        assertTrue(result.contains("verifica novamente cálculos e unidades"))
        assertTrue(result.contains("sem revelar raciocínio interno privado"))
        assertEquals(1400, AssistantThinkingModePolicy.maxOutputTokens(true))
        assertEquals(0.2, AssistantThinkingModePolicy.temperature(true), 0.0001)
    }

    @Test
    fun deepDirectiveIsNotLostWhenBaseInstructionIsLong() {
        val result = AssistantThinkingModePolicy.buildSystemInstruction(
            "A".repeat(20_000),
            true,
            1200
        )
        assertTrue(result.length <= 1200)
        assertTrue(result.contains("MODO PENSAR MAIS"))
        assertTrue(result.endsWith("sem revelar raciocínio interno privado."))
        assertFalse(result.startsWith("A".repeat(20_000)))
    }

    @Test
    fun standardInstructionIsBoundedWithoutChangingItsMeaningWhenItFits() {
        val base = "Contexto de restaurante com dados confirmados."
        val result = AssistantThinkingModePolicy.buildSystemInstruction(base, false, 12)
        assertEquals(base.take(12), result)
    }
}
