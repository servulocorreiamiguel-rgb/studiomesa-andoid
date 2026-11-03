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
        val createdAtMillis: Long
    )

    data class Invoice(
        val id: String,
        val supplier: String,
        val number: String,
        val invoiceDate: String,
        val invoiceType: String,
        val netAmount: String,
        val taxAmount: String,
        val totalAmount: String,
        val notes: String,
        val attachmentId: String?,
        val attachmentName: String?,
        val attachmentMimeType: String?,
        val createdAtMillis: Long
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
                createdAtMillis = message.optLong("created_at")
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
        save(scope, root)
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
    fun listInvoices(scope: String, query: String = ""): List<Invoice> {
        val invoices = load(scope).optJSONArray("invoices") ?: JSONArray()
        val normalizedQuery = query.trim().lowercase()
        return (0 until invoices.length()).mapNotNull { index ->
            val item = invoices.optJSONObject(index) ?: return@mapNotNull null
            invoiceFromJson(item)
        }.filter { invoice ->
            normalizedQuery.isBlank() || listOf(
                invoice.supplier, invoice.number, invoice.invoiceDate, invoice.notes,
                invoice.attachmentName.orEmpty()
            ).any { it.lowercase().contains(normalizedQuery) }
        }.sortedWith(compareByDescending<Invoice> { it.invoiceDate }.thenByDescending { it.createdAtMillis })
    }

    @Synchronized
    fun saveInvoice(scope: String, invoice: Invoice) {
        require(invoice.id.isNotBlank()) { "O identificador da fatura não é válido." }
        require(invoice.supplier.trim().length in 2..120) { "Indica o nome do fornecedor." }
        require(invoice.number.trim().isNotBlank() && invoice.number.length <= 80) {
            "Indica o número do documento (até 80 caracteres)."
        }
        require(invoice.invoiceDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            "A data da fatura deve usar o formato AAAA-MM-DD."
        }
        val strictDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply {
            isLenient = false
        }
        val parsedDate = runCatching { strictDateFormat.parse(invoice.invoiceDate) }.getOrNull()
        require(parsedDate != null && strictDateFormat.format(parsedDate) == invoice.invoiceDate) {
            "A data da fatura não existe no calendário."
        }
        require(invoice.invoiceType in setOf("invoice", "credit_note")) { "Tipo de documento inválido." }
        require(invoice.notes.length <= 4000) { "As notas da fatura são demasiado longas." }
        fun validateAmount(value: String, label: String) {
            val amount = runCatching { java.math.BigDecimal(value) }.getOrNull()
                ?: throw IllegalArgumentException("O valor de $label não é válido.")
            require(amount.signum() >= 0 && amount <= java.math.BigDecimal("999999999.99") &&
                amount.scale().coerceAtLeast(0) <= 2) { "O valor de $label tem de ser não negativo e ter até duas casas decimais." }
        }
        validateAmount(invoice.netAmount, "base tributável")
        validateAmount(invoice.taxAmount, "IVA")
        validateAmount(invoice.totalAmount, "total")
        require(java.math.BigDecimal(invoice.totalAmount).signum() > 0) {
            "O total da fatura tem de ser superior a zero."
        }

        val root = load(scope)
        val invoices = root.optJSONArray("invoices") ?: JSONArray().also { root.put("invoices", it) }
        val normalizedSupplier = invoice.supplier.trim().lowercase()
        val normalizedNumber = invoice.number.trim().uppercase()
        val duplicate = (0 until invoices.length()).mapNotNull { invoices.optJSONObject(it) }
            .firstOrNull { item ->
                item.optString("id") != invoice.id &&
                    item.optString("supplier").trim().lowercase() == normalizedSupplier &&
                    item.optString("number").trim().uppercase() == normalizedNumber &&
                    item.optString("invoice_date") == invoice.invoiceDate &&
                    normalizedNumber.isNotBlank()
            }
        require(duplicate == null) {
            "Já existe um documento deste fornecedor com o mesmo número e data. Confirma se não é uma duplicação."
        }

        val json = JSONObject()
            .put("id", invoice.id)
            .put("supplier", invoice.supplier.trim())
            .put("number", invoice.number.trim())
            .put("invoice_date", invoice.invoiceDate)
            .put("invoice_type", invoice.invoiceType)
            .put("net_amount", java.math.BigDecimal(invoice.netAmount).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
            .put("tax_amount", java.math.BigDecimal(invoice.taxAmount).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
            .put("total_amount", java.math.BigDecimal(invoice.totalAmount).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
            .put("notes", invoice.notes.trim())
            .put("attachment_id", invoice.attachmentId ?: JSONObject.NULL)
            .put("attachment_name", invoice.attachmentName ?: JSONObject.NULL)
            .put("attachment_mime_type", invoice.attachmentMimeType ?: JSONObject.NULL)
            .put("created_at", invoice.createdAtMillis)
        var replaced = false
        for (index in 0 until invoices.length()) {
            val existing = invoices.optJSONObject(index) ?: continue
            if (existing.optString("id") == invoice.id) {
                invoices.put(index, json)
                replaced = true
                break
            }
        }
        if (!replaced) invoices.put(json)
        root.put("invoices", invoices)
        while (invoices.length() > MAX_INVOICES) invoices.remove(0)
        save(scope, root)
    }

    @Synchronized
    fun updateInvoiceAttachment(
        scope: String,
        invoiceId: String,
        attachmentId: String,
        attachmentName: String,
        attachmentMimeType: String
    ) {
        require(attachmentId.matches(Regex("^[A-Za-z0-9-]{16,80}$"))) { "O identificador do anexo não é válido." }
        require(attachmentName.isNotBlank() && attachmentName.length <= 180) { "O nome do anexo não é válido." }
        require(attachmentMimeType.length <= 100) { "O tipo do anexo não é válido." }
        val root = load(scope)
        val invoices = root.optJSONArray("invoices") ?: JSONArray()
        var found = false
        for (index in 0 until invoices.length()) {
            val item = invoices.optJSONObject(index) ?: continue
            if (item.optString("id") == invoiceId) {
                item.put("attachment_id", attachmentId)
                item.put("attachment_name", attachmentName)
                item.put("attachment_mime_type", attachmentMimeType)
                found = true
                break
            }
        }
        require(found) { "A fatura já não existe no histórico." }
        root.put("invoices", invoices)
        save(scope, root)
    }

    @Synchronized
    fun clearInvoiceAttachment(scope: String, invoiceId: String) {
        val root = load(scope)
        val invoices = root.optJSONArray("invoices") ?: JSONArray()
        var found = false
        for (index in 0 until invoices.length()) {
            val item = invoices.optJSONObject(index) ?: continue
            if (item.optString("id") == invoiceId) {
                item.put("attachment_id", JSONObject.NULL)
                item.put("attachment_name", JSONObject.NULL)
                item.put("attachment_mime_type", JSONObject.NULL)
                found = true
                break
            }
        }
        require(found) { "A fatura já não existe no histórico." }
        root.put("invoices", invoices)
        save(scope, root)
    }

    @Synchronized
    fun deleteInvoice(scope: String, invoiceId: String): String? {
        val root = load(scope)
        val invoices = root.optJSONArray("invoices") ?: JSONArray()
        var attachmentId: String? = null
        for (index in invoices.length() - 1 downTo 0) {
            val item = invoices.optJSONObject(index) ?: continue
            if (item.optString("id") == invoiceId) {
                attachmentId = item.optString("attachment_id").takeIf { it.isNotBlank() && it != "null" }
                invoices.remove(index)
            }
        }
        root.put("invoices", invoices)
        save(scope, root)
        return attachmentId
    }

    @Synchronized
    fun readEconomicInputs(scope: String): Map<String, String> {
        val stored = load(scope).optJSONObject("economic_inputs") ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        val keys = stored.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = stored.optString(key)
            if (key.matches(Regex("^[A-Za-z][A-Za-z0-9_]{0,40}$")) && value.length <= 4000) {
                result[key] = value
            }
        }
        return result
    }

    @Synchronized
    fun saveEconomicState(scope: String, enabled: Boolean, values: Map<String, String>) {
        val stored = JSONObject()
        values.entries.take(80).forEach { (key, value) ->
            if (key.matches(Regex("^[A-Za-z][A-Za-z0-9_]{0,40}$"))) {
                stored.put(key, value.take(4000))
            }
        }
        val root = load(scope)
        root.put("economic_manager_enabled", enabled)
        root.put("economic_inputs", stored)
        save(scope, root)
    }

    @Synchronized
    fun isEconomicManagerEnabled(scope: String): Boolean =
        load(scope).optBoolean("economic_manager_enabled", false)

    private fun invoiceFromJson(item: JSONObject): Invoice? {
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        val supplier = item.optString("supplier").trim().takeIf { it.length in 2..120 } ?: return null
        val number = item.optString("number")
        val date = item.optString("invoice_date")
        val type = item.optString("invoice_type", "invoice")
        val net = item.optString("net_amount")
        val tax = item.optString("tax_amount")
        val total = item.optString("total_amount")
        if (!date.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) ||
            type !in setOf("invoice", "credit_note") ||
            runCatching { java.math.BigDecimal(net); java.math.BigDecimal(tax); java.math.BigDecimal(total) }.isFailure
        ) return null
        return Invoice(
            id = id,
            supplier = supplier,
            number = number,
            invoiceDate = date,
            invoiceType = type,
            netAmount = net,
            taxAmount = tax,
            totalAmount = total,
            notes = item.optString("notes").take(4000),
            attachmentId = item.optString("attachment_id").takeIf { it.isNotBlank() && it != "null" },
            attachmentName = item.optString("attachment_name").takeIf { it.isNotBlank() && it != "null" },
            attachmentMimeType = item.optString("attachment_mime_type").takeIf { it.isNotBlank() && it != "null" },
            createdAtMillis = item.optLong("created_at", 0L)
        )
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
        const val MAX_INVOICES = 500
        const val MAX_TURNS = 100
        const val MAX_MESSAGE_CHARS = 6000
        const val MAX_MEMORY_CHARS = 2500
        const val TITLE_CHARS = 52
        val ALLOWED_ROLES = setOf("user", "assistant")
    }
}
