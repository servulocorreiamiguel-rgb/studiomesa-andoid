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

internal data class SupplierInvoiceRecord(
    val id: String,
    val supplierName: String,
    val supplierVatNumber: String,
    val invoiceNumber: String,
    val issueDate: String,
    val dueDate: String,
    val netAmount: String?,
    val vatAmount: String?,
    val totalAmount: String,
    val currency: String,
    val mimeType: String,
    val byteSize: Long,
    val documentSha256: String,
    val documentUri: String?,
    val documentPath: String?,
    val sourceType: String,
    val reviewStatus: String,
    val warnings: List<String>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDraft(): SupplierInvoiceDraft = SupplierInvoiceDraft(
        supplierName = supplierName,
        supplierVatNumber = supplierVatNumber,
        invoiceNumber = invoiceNumber,
        issueDate = issueDate,
        dueDate = dueDate,
        netAmount = netAmount?.toBigDecimalOrNull(),
        vatAmount = vatAmount?.toBigDecimalOrNull(),
        totalAmount = totalAmount.toBigDecimalOrNull(),
        currency = currency,
        source = sourceType,
        warnings = warnings
    )
}

/** Encrypted device-local copy of invoice metadata. OCR text is never persisted. */
internal class SupplierInvoiceStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("studiomesa_supplier_invoice_index_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun list(scope: String): List<SupplierInvoiceRecord> {
        val array = load(scope).optJSONArray("invoices") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            runCatching { array.getJSONObject(index).toRecord() }.getOrNull()
        }.sortedByDescending { it.updatedAtMillis }.take(MAX_RECORDS)
    }

    @Synchronized
    fun get(scope: String, id: String): SupplierInvoiceRecord? =
        list(scope).firstOrNull { it.id == id }

    @Synchronized
    fun save(scope: String, record: SupplierInvoiceRecord) {
        require(scope.isNotBlank())
        val root = load(scope)
        val array = root.optJSONArray("invoices") ?: JSONArray()
        val replacement = record.toJson()
        var found = false
        for (index in 0 until array.length()) {
            if (array.optJSONObject(index)?.optString("id") == record.id) {
                array.put(index, replacement)
                found = true
                break
            }
        }
        if (!found) array.put(replacement)
        while (array.length() > MAX_RECORDS) array.remove(0)
        root.put("invoices", array)
        persist(scope, root)
    }

    @Synchronized
    fun delete(scope: String, id: String) {
        val root = load(scope)
        val array = root.optJSONArray("invoices") ?: JSONArray()
        for (index in array.length() - 1 downTo 0) {
            if (array.optJSONObject(index)?.optString("id") == id) array.remove(index)
        }
        root.put("invoices", array)
        persist(scope, root)
    }

    @Synchronized
    fun clearScope(scope: String) {
        check(preferences.edit().remove(storageKey(scope)).commit()) {
            "Não foi possível eliminar o índice local de faturas."
        }
    }

    private fun load(scope: String): JSONObject {
        require(scope.isNotBlank()) { "A conta do arquivo de faturas não está identificada." }
        val value = preferences.getString(storageKey(scope), null)
            ?: return JSONObject().put("version", FORMAT_VERSION).put("invoices", JSONArray())
        return try {
            val root = JSONObject(decrypt(value))
            require(root.optInt("version") == FORMAT_VERSION)
            root
        } catch (error: Exception) {
            throw IllegalStateException("Não foi possível abrir o arquivo local cifrado. Os dados não foram apagados.", error)
        }
    }

    private fun persist(scope: String, root: JSONObject) {
        val encrypted = encrypt(root.toString())
        check(preferences.edit().putString(storageKey(scope), encrypted).commit()) {
            "Não foi possível guardar o índice local cifrado."
        }
    }

    private fun storageKey(scope: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
        return "scope_" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun JSONObject.toRecord() = SupplierInvoiceRecord(
        id = getString("id"),
        supplierName = optString("supplier_name"),
        supplierVatNumber = optString("supplier_vat_number"),
        invoiceNumber = optString("invoice_number"),
        issueDate = optString("issue_date"),
        dueDate = optString("due_date"),
        netAmount = optNullableString("net_amount"),
        vatAmount = optNullableString("vat_amount"),
        totalAmount = optString("total_amount"),
        currency = optString("currency", "EUR"),
        mimeType = optString("mime_type"),
        byteSize = optLong("byte_size"),
        documentSha256 = optString("document_sha256"),
        documentUri = optNullableString("document_uri"),
        documentPath = optNullableString("document_path"),
        sourceType = optString("source_type"),
        reviewStatus = optString("review_status", "pending_review"),
        warnings = (optJSONArray("warnings") ?: JSONArray()).let { values ->
            (0 until values.length()).map { values.optString(it) }.filter { it.isNotBlank() }
        },
        createdAtMillis = optLong("created_at"),
        updatedAtMillis = optLong("updated_at")
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun SupplierInvoiceRecord.toJson() = JSONObject()
        .put("id", id)
        .put("supplier_name", supplierName)
        .put("supplier_vat_number", supplierVatNumber)
        .put("invoice_number", invoiceNumber)
        .put("issue_date", issueDate)
        .put("due_date", dueDate)
        .put("net_amount", netAmount ?: JSONObject.NULL)
        .put("vat_amount", vatAmount ?: JSONObject.NULL)
        .put("total_amount", totalAmount)
        .put("currency", currency)
        .put("mime_type", mimeType)
        .put("byte_size", byteSize)
        .put("document_sha256", documentSha256)
        .put("document_uri", documentUri ?: JSONObject.NULL)
        .put("document_path", documentPath ?: JSONObject.NULL)
        .put("source_type", sourceType)
        .put("review_status", reviewStatus)
        .put("warnings", JSONArray(warnings))
        .put("created_at", createdAtMillis)
        .put("updated_at", updatedAtMillis)

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 1)
        val ivSize = bytes[0].toInt() and 0xff
        require(ivSize in 12..16 && bytes.size > ivSize + 1)
        val iv = bytes.copyOfRange(1, 1 + ivSize)
        val payload = bytes.copyOfRange(1 + ivSize, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "pt.studiomesa.assistant.invoices.v1"
        const val FORMAT_VERSION = 1
        const val MAX_RECORDS = 200
    }
}
