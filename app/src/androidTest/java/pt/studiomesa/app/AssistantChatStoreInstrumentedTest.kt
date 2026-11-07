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
            assertEquals(listOf(conversationId), reopenedStore.listPendingConversationDeletions(scope))

            // A stale cloud copy must not resurrect a conversation while its deletion is pending.
            val staleMerge = reopenedStore.mergeRemoteConversation(
                scope = scope,
                conversationId = conversationId,
                title = "Resumo mensal",
                turns = turns,
                remoteCreatedAtMillis = 1_700_000_000_000L,
                remoteUpdatedAtMillis = 1_700_000_001_000L
            )
            assertEquals(0, staleMerge)
            assertTrue(reopenedStore.listConversations(scope).isEmpty())

            reopenedStore.acknowledgeConversationDeletion(scope, conversationId)
            assertTrue(reopenedStore.listPendingConversationDeletions(scope).isEmpty())
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
    fun clearStableFactsAreAutomaticallySavedWithUserMessagesAndRemainAccountScoped() {
        val store = AssistantChatStore(context)
        val scope = "test:${UUID.randomUUID()}"
        val otherScope = "test:${UUID.randomUUID()}"
        try {
            val conversationId = store.appendTurn(
                scope,
                null,
                "user",
                "Prefiro respostas diretas e em português de Portugal."
            )

            assertEquals(
                "• respostas diretas e em português de Portugal",
                store.readMemory(scope)
            )
            assertTrue(store.readMemoryUpdatedAtMillis(scope) > 0L)

            // An ordinary question is kept in history but does not become a memory note.
            store.appendTurn(scope, conversationId, "user", "Quanto custa produzir uma sopa?")
            assertEquals(
                "• respostas diretas e em português de Portugal",
                store.readMemory(scope)
            )
            assertEquals("", store.readMemory(otherScope))
        } finally {
            runCatching { store.clearScope(scope) }
            runCatching { store.clearScope(otherScope) }
        }
    }

    @Test
    fun remoteDeletionRemovesLocalConversationAndAcknowledgesPendingDelete() {
        val store = AssistantChatStore(context)
        val scope = "test:" + UUID.randomUUID()
        try {
            val conversationId = store.appendTurn(
                scope,
                null,
                "user",
                "Conversa que será eliminada noutro dispositivo."
            )
            store.deleteConversation(scope, conversationId)
            assertEquals(listOf(conversationId), store.listPendingConversationDeletions(scope))

            // A tombstone downloaded from Supabase on another device is authoritative.
            store.applyRemoteConversationDeletion(scope, conversationId)

            assertTrue(store.listConversations(scope).isEmpty())
            assertTrue(store.listPendingConversationDeletions(scope).isEmpty())
        } finally {
            runCatching { store.clearScope(scope) }
        }
    }

    @Test
    fun remoteConversationMergeIsIdempotentAndPreservesServerTimestamps() {
        val store = AssistantChatStore(context)
        val scope = "test:" + UUID.randomUUID()
        val conversationId = UUID.randomUUID().toString()
        val turns = listOf(
            AssistantChatStore.Turn("user", "Quanto gastei este mês?", 1_700_000_000_000L, "message-1"),
            AssistantChatStore.Turn("assistant", "O total confirmado é 100 euros.", 1_700_000_001_000L, "message-2")
        )
        try {
            val firstMerge = store.mergeRemoteConversation(
                scope = scope,
                conversationId = conversationId,
                title = "Resumo mensal",
                turns = turns,
                remoteCreatedAtMillis = 1_700_000_000_000L,
                remoteUpdatedAtMillis = 1_700_000_001_000L
            )
            val secondMerge = store.mergeRemoteConversation(
                scope = scope,
                conversationId = conversationId,
                title = "Resumo mensal",
                turns = turns,
                remoteCreatedAtMillis = 1_700_000_000_000L,
                remoteUpdatedAtMillis = 1_700_000_001_000L
            )
            assertEquals(2, firstMerge)
            assertEquals(0, secondMerge)
            assertEquals(2, store.readTurns(scope, conversationId).size)
            val conversation = store.listConversations(scope).single()
            assertEquals(1_700_000_000_000L, conversation.createdAtMillis)
            assertEquals(1_700_000_001_000L, conversation.updatedAtMillis)
        } finally {
            runCatching { store.clearScope(scope) }
        }
    }

    @Test
    fun remoteMemoryUsesNewestTimestampAndIgnoresOlderCopies() {
        val store = AssistantChatStore(context)
        val scope = "test:" + UUID.randomUUID()
        try {
            assertTrue(store.mergeRemoteMemory(scope, "Memória mais recente", 2_000L))
            assertFalse(store.mergeRemoteMemory(scope, "Memória antiga", 1_000L))
            assertEquals("Memória mais recente", store.readMemory(scope))
            assertEquals(2_000L, store.readMemoryUpdatedAtMillis(scope))
        } finally {
            runCatching { store.clearScope(scope) }
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
