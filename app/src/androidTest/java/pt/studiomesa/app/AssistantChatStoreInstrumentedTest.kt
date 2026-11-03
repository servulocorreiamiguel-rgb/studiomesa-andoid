package pt.studiomesa.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AssistantChatStoreInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun historyAndMemoryPersistAcrossStoreInstancesAndStayAccountScoped() {
        val store = AssistantChatStore(context)
        val scope = "test:${UUID.randomUUID()}"
        val otherScope = "test:${UUID.randomUUID()}"

        try {
            val conversationId = store.appendTurn(
                scope,
                null,
                "user",
                "Quero melhorar a margem dos pratos."
            )
            store.appendTurn(
                scope,
                conversationId,
                "assistant",
                "Começa por confirmar os custos por dose."
            )
            store.saveMemory(scope, "Restaurante de peixe; objetivo: reduzir desperdício.")

            val reopenedStore = AssistantChatStore(context)
            val conversations = reopenedStore.listConversations(scope)
            val turns = reopenedStore.readTurns(scope, conversationId)

            assertEquals(1, conversations.size)
            assertEquals("Quero melhorar a margem dos pratos.", conversations.single().title)
            assertEquals(2, turns.size)
            assertEquals("user", turns.first().role)
            assertEquals("assistant", turns.last().role)
            assertEquals(
                "Restaurante de peixe; objetivo: reduzir desperdício.",
                reopenedStore.readMemory(scope)
            )
            assertTrue(reopenedStore.listConversations(scope, "desperdício").isEmpty())
            assertTrue(reopenedStore.listConversations(scope, "margem").isNotEmpty())
            assertTrue(reopenedStore.listConversations(otherScope).isEmpty())
            assertFalse(reopenedStore.readMemory(otherScope).isNotBlank())

            reopenedStore.deleteConversation(scope, conversationId)
            assertTrue(reopenedStore.listConversations(scope).isEmpty())
            assertEquals(
                "Restaurante de peixe; objetivo: reduzir desperdício.",
                reopenedStore.readMemory(scope)
            )
        } finally {
            runCatching { store.clearScope(scope) }
            runCatching { store.clearScope(otherScope) }
        }
    }

    @Test
    fun assistantCannotCreateAnOrphanConversation() {
        val store = AssistantChatStore(context)
        val scope = "test:${UUID.randomUUID()}"
        try {
            try {
                store.appendTurn(scope, null, "assistant", "Resposta sem pergunta.")
                throw AssertionError("Era esperada a rejeição de uma resposta sem conversa.")
            } catch (_: IllegalArgumentException) {
                // Expected: a conversation must start with a user message.
            }
            assertTrue(store.listConversations(scope).isEmpty())
        } finally {
            runCatching { store.clearScope(scope) }
        }
    }
}
