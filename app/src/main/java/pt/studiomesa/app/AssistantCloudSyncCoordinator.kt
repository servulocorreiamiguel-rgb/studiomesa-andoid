package pt.studiomesa.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal data class AssistantCloudSyncResult(
    val conversationsUploaded: Int,
    val messagesUploaded: Int,
    val messagesDownloaded: Int,
    val memoryDownloaded: Boolean
)

/**
 * Two-way owner-only sync. Message IDs make retries idempotent; remote data is merged
 * into the encrypted local store. The Supabase copy is protected by RLS, not E2E encrypted.
 */
internal class AssistantCloudSyncCoordinator {
    fun sync(
        token: String,
        userId: String,
        restaurantId: String,
        scope: String,
        store: AssistantChatStore
    ): AssistantCloudSyncResult {
        require(token.isNotBlank() && userId.isNotBlank() && restaurantId.isNotBlank() && scope.isNotBlank()) {
            "Inicia sessão e confirma o restaurante antes de sincronizar."
        }

        val localConversations = store.listConversationsForSync(scope)
        val remoteConversationsBefore = SupabaseApi.listAssistantConversations(token, restaurantId)
        val remoteById = buildMap<String, JSONObject> {
            for (index in 0 until remoteConversationsBefore.length()) {
                val row = remoteConversationsBefore.optJSONObject(index) ?: continue
                val id = row.optString("id")
                if (id.isNotBlank()) put(id, row)
            }
        }

        val conversationRows = JSONArray()
        localConversations.forEach { local ->
            val remote = remoteById[local.id]
            val remoteUpdated = remote?.optString("updated_at")?.let(::parseTimestamp) ?: 0L
            if (remote == null || local.updatedAtMillis >= remoteUpdated) {
                conversationRows.put(
                    JSONObject()
                        .put("id", local.id)
                        .put("restaurant_id", restaurantId)
                        .put("title", local.title.take(120))
                        .put("created_by", userId)
                        .put("created_at", iso(local.createdAtMillis))
                        .put("updated_at", iso(local.updatedAtMillis))
                )
            }
        }
        SupabaseApi.upsertAssistantConversations(token, conversationRows)

        val messageRows = JSONArray()
        var messagesToUpload = 0
        localConversations.forEach { conversation ->
            conversation.turns.forEach { turn ->
                if (turn.id.isNotBlank() && turn.content.isNotBlank() && turn.role in setOf("user", "assistant")) {
                    messageRows.put(
                        JSONObject()
                            .put("id", turn.id)
                            .put("restaurant_id", restaurantId)
                            .put("conversation_id", conversation.id)
                            .put("role", turn.role)
                            .put("content", turn.content.take(6000))
                            .put("created_by", userId)
                            .put("created_at", iso(turn.createdAtMillis))
                    )
                    messagesToUpload++
                }
            }
        }
        for (start in 0 until messageRows.length() step MESSAGE_BATCH_SIZE) {
            val batch = JSONArray()
            for (index in start until minOf(start + MESSAGE_BATCH_SIZE, messageRows.length())) {
                batch.put(messageRows.getJSONObject(index))
            }
            SupabaseApi.upsertAssistantMessages(token, batch)
        }

        val remoteConversations = SupabaseApi.listAssistantConversations(token, restaurantId)
        val remoteMessages = SupabaseApi.listAssistantMessages(token, restaurantId)
        val groupedMessages = mutableMapOf<String, MutableList<AssistantChatStore.Turn>>()
        for (index in 0 until remoteMessages.length()) {
            val row = remoteMessages.optJSONObject(index) ?: continue
            val conversationId = row.optString("conversation_id")
            val id = row.optString("id")
            val role = row.optString("role")
            val content = row.optString("content")
            if (conversationId.isBlank() || id.isBlank() || role !in setOf("user", "assistant") || content.isBlank()) continue
            groupedMessages.getOrPut(conversationId) { mutableListOf() } += AssistantChatStore.Turn(
                id = id,
                role = role,
                content = content.take(6000),
                createdAtMillis = parseTimestamp(row.optString("created_at")).coerceAtLeast(1L)
            )
        }

        var downloaded = 0
        for (index in 0 until remoteConversations.length()) {
            val row = remoteConversations.optJSONObject(index) ?: continue
            val id = row.optString("id")
            if (id.isBlank()) continue
            val turns = groupedMessages[id].orEmpty().sortedBy { it.createdAtMillis }
            downloaded += store.mergeRemoteConversation(
                scope = scope,
                conversationId = id,
                title = row.optString("title", "Conversa sincronizada"),
                turns = turns,
                remoteCreatedAtMillis = parseTimestamp(row.optString("created_at")),
                remoteUpdatedAtMillis = parseTimestamp(row.optString("updated_at"))
            )
        }

        var memoryDownloaded = false
        val remoteMemory = SupabaseApi.getAssistantMemory(token, restaurantId)
        val localMemory = store.readMemory(scope)
        val localMemoryUpdated = store.readMemoryUpdatedAtMillis(scope)
        if (remoteMemory == null) {
            if (localMemory.isNotBlank()) {
                SupabaseApi.upsertAssistantMemory(
                    token,
                    JSONObject()
                        .put("restaurant_id", restaurantId)
                        .put("content", localMemory.take(6000))
                        .put("updated_by", userId)
                        .put("updated_at", iso(localMemoryUpdated.takeIf { it > 0L } ?: System.currentTimeMillis()))
                )
            }
        } else {
            val remoteMemoryText = remoteMemory.optString("content")
            val remoteMemoryUpdated = parseTimestamp(remoteMemory.optString("updated_at"))
            if (remoteMemoryUpdated > localMemoryUpdated) {
                memoryDownloaded = store.mergeRemoteMemory(scope, remoteMemoryText, remoteMemoryUpdated)
            } else if (localMemoryUpdated > remoteMemoryUpdated) {
                SupabaseApi.upsertAssistantMemory(
                    token,
                    JSONObject()
                        .put("restaurant_id", restaurantId)
                        .put("content", localMemory.take(6000))
                        .put("updated_by", userId)
                        .put("updated_at", iso(localMemoryUpdated))
                )
            }
        }

        return AssistantCloudSyncResult(
            conversationsUploaded = conversationRows.length(),
            messagesUploaded = messagesToUpload,
            messagesDownloaded = downloaded,
            memoryDownloaded = memoryDownloaded
        )
    }

    private fun iso(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis.coerceAtLeast(1L)).toString()

    private fun parseTimestamp(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private companion object {
        const val MESSAGE_BATCH_SIZE = 100
    }
}
