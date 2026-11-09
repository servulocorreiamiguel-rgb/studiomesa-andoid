package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantAutomaticMemoryPolicyTest {

    @Test
    fun remembersExplicitStablePreferencesAndGoals() {
        assertEquals(
            "respostas curtas e em português de Portugal",
            AssistantAutomaticMemoryPolicy.extractStableFact("Prefiro respostas curtas e em português de Portugal.")
        )
        assertEquals(
            "reduzir o desperdício no restaurante",
            AssistantAutomaticMemoryPolicy.extractStableFact("O meu objetivo é reduzir o desperdício no restaurante.")
        )
        assertEquals(
            "o restaurante serve comida vegetariana",
            AssistantAutomaticMemoryPolicy.extractStableFact("Lembra-te que o restaurante serve comida vegetariana.")
        )
    }

    @Test
    fun ignoresOrdinaryQuestionsAndTransientStatements() {
        assertNull(AssistantAutomaticMemoryPolicy.extractStableFact("Quanto custa produzir uma sopa?"))
        assertNull(AssistantAutomaticMemoryPolicy.extractStableFact("Hoje vendemos muitos cafés."))
    }

    @Test
    fun neverStoresSensitiveContactOrAuthenticationDetails() {
        assertNull(AssistantAutomaticMemoryPolicy.extractStableFact("Guarda que o meu email é miguel@example.com."))
        assertNull(AssistantAutomaticMemoryPolicy.extractStableFact("Lembra-te que a minha palavra-passe é segredo123."))
        assertNull(AssistantAutomaticMemoryPolicy.extractStableFact("Prefiro usar o meu IBAN PT50000000000000000000000."))
        assertNull(AssistantAutomaticMemoryPolicy.extractStableFact("Guarda que o meu telefone é 912345678."))
    }

    @Test
    fun promptMemoryKeepsRecentCompleteNotesWithinItsBudget() {
        val memory = (1..8).joinToString("\n") { "• preferência estável número $it" }
        val snippet = AssistantAutomaticMemoryPolicy.contextSnippet(memory, 70)

        assertTrue(snippet.length <= 70)
        assertTrue(snippet.contains("número 8"))
        assertFalse(snippet.contains("número 1"))
        assertTrue(snippet.lineSequence().all { it.startsWith("• ") })
    }

    @Test
    fun mergesNewFactsWithoutReplacingExistingNotesOrDuplicatingThem() {
        val existing = "Restaurante de peixe"
        val updated = AssistantAutomaticMemoryPolicy.merge(
            existing,
            "Prefiro respostas diretas e sem rodeios."
        )
        assertEquals(
            "Restaurante de peixe\n• respostas diretas e sem rodeios",
            updated
        )
        assertNull(
            AssistantAutomaticMemoryPolicy.merge(
                updated.orEmpty(),
                "Prefiro respostas diretas e sem rodeios."
            )
        )
    }

    @Test
    fun doesNotDiscardExistingMemoryWhenTheLimitWouldBeExceeded() {
        val fullMemory = "x".repeat(AssistantAutomaticMemoryPolicy.MAX_MEMORY_CHARS)
        assertNull(
            AssistantAutomaticMemoryPolicy.merge(
                fullMemory,
                "Prefiro respostas curtas e objetivas."
            )
        )
        assertEquals(AssistantAutomaticMemoryPolicy.MAX_MEMORY_CHARS, fullMemory.length)
    }
}
