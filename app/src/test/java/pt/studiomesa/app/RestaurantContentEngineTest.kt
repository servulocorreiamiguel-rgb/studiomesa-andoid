package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantContentEngineTest {
    private val title = "Hambúrguer da casa"
    private val facts = "Pão brioche, carne de vaca e alface."

    @Test
    fun generatesAllFourSupportedContentTypesOffline() {
        val types = listOf("menu_description", "social_post", "campaign", "content_ideas")
        types.forEach { type ->
            val draft = RestaurantContentEngine.generate(type, title, facts)
            assertTrue("Draft for $type should not be blank", draft.isNotBlank())
            assertTrue("Draft for $type should include the title", draft.contains(title))
            assertTrue("Draft for $type should retain the supplied facts", draft.contains(facts))
            assertTrue("Draft for $type should stay within the output limit", draft.length <= 4000)
        }
    }

    @Test
    fun rejectsUnknownContentTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate("publish_now", title, facts)
        }
    }

    @Test
    fun rejectsTitlesOutsideBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate("social_post", "x", facts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate("social_post", "x".repeat(121), facts)
        }
    }

    @Test
    fun rejectsFactsOutsideBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate("social_post", title, "curto")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate("social_post", title, "x".repeat(1501))
        }
    }

    @Test
    fun blocksEmailsAndPortuguesePhoneNumbersFromTheInput() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate("social_post", title, "Contactar cliente@example.com para detalhes.")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate("social_post", title, "O cliente ligou do 912 345 678.")
        }
    }

    @Test
    fun detectsCustomerContactPatterns() {
        assertTrue(RestaurantContentEngine.containsCustomerContactData("contacto +351 912 345 678"))
        assertTrue(RestaurantContentEngine.containsCustomerContactData("contacto cliente@example.com"))
        assertFalse(RestaurantContentEngine.containsCustomerContactData("Pão brioche e carne de vaca"))
    }

    @Test
    fun doesNotInventPricesOrDiscounts() {
        listOf("menu_description", "social_post", "campaign", "content_ideas").forEach { type ->
            val draft = RestaurantContentEngine.generate(type, title, facts)
            assertFalse(draft.contains("€"))
            assertFalse(Regex("""\b\d+%""").containsMatchIn(draft))
            assertFalse(draft.contains("10%"))
        }
    }

    @Test
    fun contentIdeasProvideThreeClearlySeparatedSuggestions() {
        val draft = RestaurantContentEngine.generate("content_ideas", title, facts)
        assertTrue(draft.contains("1. Apresentação do artigo"))
        assertTrue(draft.contains("2. Conteúdo visual"))
        assertTrue(draft.contains("3. Pergunta à comunidade"))
    }

    @Test
    fun appendsPunctuationWithoutRemovingTheOriginalFacts() {
        val draft = RestaurantContentEngine.generate("menu_description", title, "Ingredientes confirmados: arroz, peixe")
        assertTrue(draft.contains("Ingredientes confirmados: arroz, peixe."))
    }

    @Test
    fun rejectsInputsThatExceedUtf8ByteLimitEvenWhenCharacterLimitsPass() {
        // CJK characters use three UTF-8 bytes each; character limits alone do
        // not guarantee that the combined request fits the engine's byte limit.
        val multibyteTitle = "界".repeat(120)
        val multibyteFacts = "界".repeat(1500)
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate("social_post", multibyteTitle, multibyteFacts)
        }
    }

    @Test
    fun trimsOuterWhitespaceBeforeGeneratingAndValidating() {
        val draft = RestaurantContentEngine.generate(
            "menu_description",
            "  Hambúrguer da casa  ",
            "  Pão brioche, carne de vaca e alface.  "
        )
        assertTrue(draft.contains("Hambúrguer da casa"))
        assertTrue(draft.contains("Pão brioche, carne de vaca e alface."))
        assertFalse(draft.contains("  Pão brioche"))
    }

    @Test
    fun rejectsContactPatternsInTheTitleAsWellAsFacts() {
        assertThrows(IllegalArgumentException::class.java) {
            RestaurantContentEngine.generate(
                "social_post",
                "Ligar para 912 345 678",
                "Prato preparado com ingredientes confirmados."
            )
        }
    }
}
