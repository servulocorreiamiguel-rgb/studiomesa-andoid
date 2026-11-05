package pt.studiomesa.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local encrypted conversation history and editable assistant memory.
 *
 * Data is partitioned by the caller-provided account/restaurant scope. The key is
 * kept in Android Keystore and the encrypted payload in private app preferences.
 * It is deliberately not presented as cloud-synchronised history.
 */
class AssistantChatStore(context: Context) {
    data class Conversation(
        val id: String,
        val title: String,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
        val messageCount: Int
    )

    data class Turn(
        val role: String,
        val content: String,
        val createdAtMillis: Long,
        val id: String = ""
    )

    data class SyncConversation(
        val id: String,
        val title: String,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
        val turns: List<Turn>
    )

    private val preferences = context.applicationContext
        .getSharedPreferences("studiomesa_assistant_history_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun listConversations(scope: String, query: String = ""): List<Conversation> {
        val root = load(scope)
        val normalizedQuery = query.trim().lowercase()
        val conversations = root.optJSONArray("conversations") ?: JSONArray()
        val result = mutableListOf<Conversation>()
        for (index in 0 until conversations.length()) {
            val item = conversations.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = item.optString("title", "Nova conversa")
            val messages = item.optJSONArray("messages") ?: JSONArray()
            val searchable = buildString {
                append(title.lowercase())
                for (messageIndex in 0 until messages.length()) {
                    append(' ')
                    append(messages.optJSONObject(messageIndex)?.optString("content").orEmpty().lowercase())
                }
            }
            if (normalizedQuery.isNotEmpty() && !searchable.contains(normalizedQuery)) continue
            result += Conversation(
                id = id,
                title = title,
                createdAtMillis = item.optLong("created_at"),
                updatedAtMillis = item.optLong("updated_at"),
                messageCount = messages.length()
            )
        }
        return result.sortedByDescending { it.updatedAtMillis }.take(MAX_CONVERSATIONS)
    }

    @Synchronized
    fun readTurns(scope: String, conversationId: String): List<Turn> {
        val conversations = load(scope).optJSONArray("conversations") ?: JSONArray()
        val item = findConversation(conversations, conversationId) ?: return emptyList()
        val messages = item.optJSONArray("messages") ?: JSONArray()
        return (0 until messages.length()).mapNotNull { index ->
            val message = messages.optJSONObject(index) ?: return@mapNotNull null
            val role = message.optString("role")
            val content = message.optString("content")
            if (role !in ALLOWED_ROLES || content.isBlank()) null else Turn(
                role = role,
                content = content,
                createdAtMillis = message.optLong("created_at"),
                id = message.optString("id").ifBlank {
                    UUID.nameUUIDFromBytes(
                        "$conversationId|$role|$content|${message.optLong("created_at")}".toByteArray(Charsets.UTF_8)
                    ).toString()
                }
            )
        }
    }

    /**
     * Appends a user or assistant turn. A new conversation is created only when
     * the first user turn is supplied with a null conversation ID.
     */
    @Synchronized
    fun appendTurn(
        scope: String,
        conversationId: String?,
        role: String,
        content: String
    ): String {
        require(role in ALLOWED_ROLES) { "Tipo de mensagem inválido." }
        val cleanContent = content.trim().take(MAX_MESSAGE_CHARS)
        require(cleanContent.isNotBlank()) { "A mensagem está vazia." }

        val root = load(scope)
        val conversations = root.optJSONArray("conversations") ?: JSONArray()
        val now = System.currentTimeMillis()
        var id = conversationId?.takeIf { it.isNotBlank() }
        var item = id?.let { findConversation(conversations, it) }

        if (item == null) {
            require(role == "user") { "Inicia uma conversa com uma mensagem tua." }
            id = UUID.randomUUID().toString()
            item = JSONObject()
                .put("id", id)
                .put("title", cleanContent.replace(Regex("\\s+"), " ").take(TITLE_CHARS))
                .put("created_at", now)
                .put("updated_at", now)
                .put("messages", JSONArray())
            conversations.put(item)
        }

        val messages = item.optJSONArray("messages") ?: JSONArray().also {
            item.put("messages", it)
        }
        messages.put(
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("role", role)
                .put("content", cleanContent)
                .put("created_at", now)
        )
        while (messages.length() > MAX_TURNS) messages.remove(0)
        item.put("updated_at", now)
        root.put("conversations", conversations)

        while (conversations.length() > MAX_CONVERSATIONS) {
            val oldestIndex = oldestConversationIndex(conversations, id.orEmpty())
            if (oldestIndex < 0) break
            conversations.remove(oldestIndex)
        }
        save(scope, root)
        return id ?: throw IllegalStateException("Não foi possível identificar a conversa.")
    }

    @Synchronized
    fun readMemory(scope: String): String = load(scope).optString("memory", "").take(MAX_MEMORY_CHARS)

    @Synchronized
    fun saveMemory(scope: String, memory: String) {
        val root = load(scope)
        root.put("memory", memory.trim().take(MAX_MEMORY_CHARS))
        root.put("memory_updated_at", System.currentTimeMillis())
        save(scope, root)
    }

    @Synchronized
    fun readMemoryUpdatedAtMillis(scope: String): Long =
        load(scope).optLong("memory_updated_at", 0L)

    @Synchronized
    fun mergeRemoteMemory(scope: String, memory: String, updatedAtMillis: Long): Boolean {
        val root = load(scope)
        val localTimestamp = root.optLong("memory_updated_at", 0L)
        if (updatedAtMillis <= localTimestamp) return false
        root.put("memory", memory.trim().take(MAX_MEMORY_CHARS))
        root.put("memory_updated_at", updatedAtMillis)
        save(scope, root)
        return true
    }

    @Synchronized
    fun listConversationsForSync(scope: String): List<SyncConversation> =
        listConversations(scope).map { conversation ->
            SyncConversation(
                id = conversation.id,
                title = conversation.title,
                createdAtMillis = conversation.createdAtMillis,
                updatedAtMillis = conversation.updatedAtMillis,
                turns = readTurns(scope, conversation.id)
            )
        }

    @Synchronized
    fun mergeRemoteConversation(
        scope: String,
        conversationId: String,
        title: String,
        turns: List<Turn>
    ): Int {
        require(conversationId.isNotBlank()) { "A conversa remota não tem identificador." }
        val root = load(scope)
        val conversations = root.optJSONArray("conversations") ?: JSONArray()
        val now = System.currentTimeMillis()
        var item = findConversation(conversations, conversationId)
        if (item == null) {
            item = JSONObject()
                .put("id", conversationId)
                .put("title", title.trim().take(TITLE_CHARS).ifBlank { "Conversa sincronizada" })
                .put("created_at", now)
                .put("updated_at", now)
                .put("messages", JSONArray())
            conversations.put(item)
        }
        val messages = item.optJSONArray("messages") ?: JSONArray().also { item.put("messages", it) }
        val knownIds = (0 until messages.length()).mapNotNull { index ->
            messages.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
        }.toMutableSet()
        var added = 0
        turns.forEach { turn ->
            require(turn.role in ALLOWED_ROLES)
            val stableId = turn.id.ifBlank {
                UUID.nameUUIDFromBytes(
                    "$conversationId|${turn.role}|${turn.content}|${turn.createdAtMillis}".toByteArray(Charsets.UTF_8)
                ).toString()
            }
            if (stableId !in knownIds && turn.content.isNotBlank()) {
                messages.put(
                    JSONObject()
                        .put("id", stableId)
                        .put("role", turn.role)
                        .put("content", turn.content.trim().take(MAX_MESSAGE_CHARS))
                        .put("created_at", turn.createdAtMillis.coerceAtLeast(1L))
                )
                knownIds += stableId
                added++
            }
        }
        while (messages.length() > MAX_TURNS) messages.remove(0)
        item.put("updated_at", maxOf(item.optLong("updated_at", 0L), now))
        root.put("conversations", conversations)
        while (conversations.length() > MAX_CONVERSATIONS) {
            val oldestIndex = oldestConversationIndex(conversations, conversationId)
            if (oldestIndex < 0) break
            conversations.remove(oldestIndex)
        }
        save(scope, root)
        return added
    }

    @Synchronized
    fun deleteConversation(scope: String, conversationId: String) {
        val root = load(scope)
        val conversations = root.optJSONArray("conversations") ?: JSONArray()
        for (index in conversations.length() - 1 downTo 0) {
            if (conversations.optJSONObject(index)?.optString("id") == conversationId) {
                conversations.remove(index)
            }
        }
        root.put("conversations", conversations)
        save(scope, root)
    }

    @Synchronized
    fun clearScope(scope: String) {
        if (!preferences.edit().remove(storageKey(scope)).commit()) {
            throw IllegalStateException("Não foi possível eliminar o histórico local.")
        }
    }

    private fun load(scope: String): JSONObject {
        require(scope.isNotBlank()) { "A conta do histórico não está identificada." }
        val encrypted = preferences.getString(storageKey(scope), null)
            ?: return JSONObject().put("version", FORMAT_VERSION)
                .put("memory", "")
                .put("conversations", JSONArray())
        return try {
            val parsed = JSONObject(decrypt(encrypted))
            if (parsed.optInt("version") != FORMAT_VERSION) {
                throw IllegalStateException("A versão do histórico local não é compatível.")
            }
            parsed
        } catch (error: Exception) {
            throw IllegalStateException(
                "Não foi possível abrir o histórico cifrado. Os dados não foram apagados.",
                error
            )
        }
    }

    private fun save(scope: String, root: JSONObject) {
        val encrypted = encrypt(root.toString())
        if (!preferences.edit().putString(storageKey(scope), encrypted).commit()) {
            throw IllegalStateException("Não foi possível guardar o histórico cifrado.")
        }
    }

    private fun storageKey(scope: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
        return "scope_" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun findConversation(conversations: JSONArray, id: String): JSONObject? {
        for (index in 0 until conversations.length()) {
            val item = conversations.optJSONObject(index) ?: continue
            if (item.optString("id") == id) return item
        }
        return null
    }

    private fun oldestConversationIndex(conversations: JSONArray, keepId: String): Int {
        var selectedIndex = -1
        var oldestTimestamp = Long.MAX_VALUE
        for (index in 0 until conversations.length()) {
            val item = conversations.optJSONObject(index) ?: continue
            if (item.optString("id") == keepId) continue
            val timestamp = item.optLong("updated_at", Long.MAX_VALUE)
            if (timestamp < oldestTimestamp) {
                oldestTimestamp = timestamp
                selectedIndex = index
            }
        }
        return selectedIndex
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > 1) { "O histórico cifrado está vazio." }
        val ivLength = combined[0].toInt() and 0xff
        require(ivLength in 12..16 && combined.size > 1 + ivLength) {
            "O formato do histórico cifrado não é válido."
        }
        val iv = combined.copyOfRange(1, 1 + ivLength)
        val ciphertext = combined.copyOfRange(1 + ivLength, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "pt.studiomesa.assistant.history.v1"
        const val FORMAT_VERSION = 1
        const val MAX_CONVERSATIONS = 50
        const val MAX_TURNS = 100
        const val MAX_MESSAGE_CHARS = 6000
        const val MAX_MEMORY_CHARS = 2500
        const val TITLE_CHARS = 52
        val ALLOWED_ROLES = setOf("user", "assistant")
    }
}
