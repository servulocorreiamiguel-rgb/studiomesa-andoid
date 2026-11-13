package pt.studiomesa.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SaftSalesStoreImportResult(
    val importedInvoices: Int,
    val updatedExistingInvoices: Int,
    val repeatedFile: Boolean
)

/**
 * Persistent owner-scoped archive of SAF-T sales invoice summaries and product lines.
 * Each invoice payload is AES-GCM encrypted with a key held in Android Keystore.
 * No customer identity or loyalty-points information is stored.
 */
internal class SaftSalesInvoiceStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sales_invoices (
                scope_key TEXT NOT NULL,
                invoice_id TEXT NOT NULL,
                issue_date TEXT NOT NULL,
                encrypted_payload TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                cloud_synced_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(scope_key, invoice_id)
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE sales_invoice_import_files (
                scope_key TEXT NOT NULL,
                file_sha256 TEXT NOT NULL,
                imported_at INTEGER NOT NULL,
                PRIMARY KEY(scope_key, file_sha256)
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX sales_invoices_scope_date_idx ON sales_invoices(scope_key, issue_date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2 && newVersion >= 2) {
            db.execSQL("ALTER TABLE sales_invoices ADD COLUMN cloud_synced_at INTEGER NOT NULL DEFAULT 0")
        }
        if (newVersion != DATABASE_VERSION) {
            throw IllegalStateException("A versão do arquivo SAF-T não é compatível; os dados não foram eliminados.")
        }
    }

    @Synchronized
    fun import(scope: String, parsed: SaftSalesImport): SaftSalesStoreImportResult {
        require(scope.isNotBlank()) { "A conta do arquivo de vendas não está identificada." }
        val scopeKey = scopeKey(scope)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val priorFile = db.query(
                "sales_invoice_import_files",
                arrayOf("file_sha256"),
                "scope_key = ? AND file_sha256 = ?",
                arrayOf(scopeKey, parsed.fileSha256),
                null, null, null, "1"
            ).use { it.moveToFirst() }
            if (priorFile) {
                db.setTransactionSuccessful()
                return SaftSalesStoreImportResult(0, 0, repeatedFile = true)
            }

            var imported = 0
            var updated = 0
            parsed.acceptedInvoices.forEach { invoice ->
                val exists = db.query(
                    "sales_invoices",
                    arrayOf("invoice_id"),
                    "scope_key = ? AND invoice_id = ?",
                    arrayOf(scopeKey, invoice.id),
                    null, null, null, "1"
                ).use { it.moveToFirst() }
                val values = ContentValues().apply {
                    put("scope_key", scopeKey)
                    put("invoice_id", invoice.id)
                    put("issue_date", invoice.issueDate)
                    put("encrypted_payload", encrypt(invoice.toJson().toString()))
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict(
                    "sales_invoices",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (exists) updated++ else imported++
            }
            db.insertOrThrow(
                "sales_invoice_import_files",
                null,
                ContentValues().apply {
                    put("scope_key", scopeKey)
                    put("file_sha256", parsed.fileSha256)
                    put("imported_at", System.currentTimeMillis())
                }
            )
            db.setTransactionSuccessful()
            return SaftSalesStoreImportResult(imported, updated, repeatedFile = false)
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun list(scope: String): List<SaftSalesInvoice> {
        require(scope.isNotBlank()) { "A conta do arquivo de vendas não está identificada." }
        val result = mutableListOf<SaftSalesInvoice>()
        readableDatabase.query(
            "sales_invoices",
            arrayOf("encrypted_payload"),
            "scope_key = ?",
            arrayOf(scopeKey(scope)),
            null, null,
            "issue_date ASC, invoice_id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val raw = decrypt(cursor.getString(0))
                result += JSONObject(raw).toInvoice()
            }
        }
        return result
    }


    /**
     * Returns only locally changed invoices. Payloads are decrypted in memory for
     * transport over TLS; records remain AES-GCM encrypted in the local database.
     */
    @Synchronized
    fun pendingCloudRows(scope: String, limit: Int = 250): List<JSONObject> {
        require(scope.isNotBlank())
        require(limit in 1..500)
        val rows = mutableListOf<JSONObject>()
        readableDatabase.query(
            "sales_invoices",
            arrayOf("invoice_id", "issue_date", "encrypted_payload", "updated_at"),
            "scope_key = ? AND updated_at > cloud_synced_at",
            arrayOf(scopeKey(scope)),
            null, null,
            "updated_at ASC, invoice_id ASC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += JSONObject()
                    .put("invoice_id", cursor.getString(0))
                    .put("issue_date", cursor.getString(1))
                    .put("payload", JSONObject(decrypt(cursor.getString(2))))
                    .put("updated_at_ms", cursor.getLong(3))
            }
        }
        return rows
    }

    /** Mark only the exact versions acknowledged by the server as synchronized. */
    @Synchronized
    fun markCloudRowsSynced(scope: String, rows: List<JSONObject>) {
        require(scope.isNotBlank())
        if (rows.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            rows.forEach { row ->
                val id = row.optString("invoice_id")
                val updatedAt = row.optLong("updated_at_ms", 0L)
                require(id.matches(Regex("^[a-f0-9]{64}$")) && updatedAt > 0L)
                db.execSQL(
                    "UPDATE sales_invoices SET cloud_synced_at = ? WHERE scope_key = ? AND invoice_id = ? AND updated_at = ?",
                    arrayOf<Any>(updatedAt, scopeKey(scope), id, updatedAt)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Merge a server page without replacing a newer local edit. Incoming payloads
     * are validated before they are re-encrypted with the current device key.
     */
    @Synchronized
    fun mergeCloudRows(scope: String, rows: List<JSONObject>): Int {
        require(scope.isNotBlank())
        require(rows.size <= 1_000)
        if (rows.isEmpty()) return 0
        val db = writableDatabase
        var merged = 0
        db.beginTransaction()
        try {
            rows.forEach { row ->
                val invoiceId = row.optString("invoice_id")
                val issueDate = row.optString("issue_date")
                val updatedAt = row.optLong("updated_at_ms", 0L)
                val payload = row.optJSONObject("payload")
                    ?: throw IllegalArgumentException("O servidor devolveu uma fatura SAF-T inválida.")
                require(invoiceId.matches(Regex("^[a-f0-9]{64}$")) && updatedAt > 0L)
                val invoice = payload.toInvoice()
                require(invoice.id == invoiceId && invoice.issueDate == issueDate) {
                    "A identidade ou a data da fatura SAF-T não corresponde ao conteúdo recebido."
                }
                val existingUpdatedAt = db.query(
                    "sales_invoices",
                    arrayOf("updated_at"),
                    "scope_key = ? AND invoice_id = ?",
                    arrayOf(scopeKey(scope), invoiceId),
                    null, null, null, "1"
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
                if (updatedAt <= existingUpdatedAt) {
                    if (updatedAt == existingUpdatedAt) {
                        db.execSQL(
                            "UPDATE sales_invoices SET cloud_synced_at = MAX(cloud_synced_at, ?) WHERE scope_key = ? AND invoice_id = ?",
                            arrayOf<Any>(updatedAt, scopeKey(scope), invoiceId)
                        )
                    }
                    return@forEach
                }
                db.insertWithOnConflict(
                    "sales_invoices",
                    null,
                    ContentValues().apply {
                        put("scope_key", scopeKey(scope))
                        put("invoice_id", invoiceId)
                        put("issue_date", issueDate)
                        put("encrypted_payload", encrypt(payload.toString()))
                        put("updated_at", updatedAt)
                        put("cloud_synced_at", updatedAt)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                merged++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return merged
    }

    @Synchronized
    fun count(scope: String): Int {
        require(scope.isNotBlank())
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM sales_invoices WHERE scope_key = ?",
            arrayOf(scopeKey(scope))
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun SaftSalesInvoice.toJson() = JSONObject()
        .put("id", id)
        .put("invoice_number", invoiceNumber)
        .put("issue_date", issueDate)
        .put("invoice_type", invoiceType)
        .put("invoice_status", invoiceStatus)
        .put("net_total", netTotal.toPlainString())
        .put("gross_total", grossTotal.toPlainString())
        .put("lines", JSONArray().apply {
            lines.forEach { line ->
                put(
                    JSONObject()
                        .put("product_code", line.productCode)
                        .put("description", line.description)
                        .put("quantity", line.quantity.toPlainString())
                        .put("net_amount", line.netAmount.toPlainString())
                )
            }
        })

    private fun JSONObject.toInvoice(): SaftSalesInvoice {
        val rows = optJSONArray("lines") ?: JSONArray()
        val lines = (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val quantity = row.optString("quantity").toBigDecimalOrNull() ?: return@mapNotNull null
            val amount = row.optString("net_amount").toBigDecimalOrNull() ?: return@mapNotNull null
            SaftSalesInvoiceLine(
                productCode = row.optString("product_code"),
                description = row.optString("description"),
                quantity = quantity,
                netAmount = amount
            )
        }
        return SaftSalesInvoice(
            id = getString("id"),
            invoiceNumber = getString("invoice_number"),
            issueDate = getString("issue_date"),
            invoiceType = getString("invoice_type"),
            invoiceStatus = getString("invoice_status"),
            netTotal = getString("net_total").toBigDecimal(),
            grossTotal = getString("gross_total").toBigDecimal(),
            lines = lines
        )
    }

    private fun scopeKey(scope: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

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
        val encrypted = bytes.copyOfRange(1 + ivSize, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
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
        const val DATABASE_NAME = "studiomesa_saft_sales_v1.db"
        const val DATABASE_VERSION = 2
        const val KEY_ALIAS = "pt.studiomesa.assistant.saft.sales.v1"
    }
}
