package pt.studiomesa.app

import android.content.Context
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Creates SHA-256-derived preference keys for retry state. Raw payloads can contain
 * customer names, reservation contacts or notes, so the raw payload is never persisted
 * here. A digest is not encryption; it is used only to avoid storing those values directly.
 */
internal object PendingRequestKey {
    fun from(operation: String, accountId: String, payloadSignature: String): String {
        require(operation.matches(Regex("^[a-z0-9_-]{1,40}$"))) {
            "Identificador de operação inválido."
        }
        require(accountId.isNotBlank()) { "A conta autenticada não está identificada." }

        val digest = MessageDigest.getInstance("SHA-256")
        listOf(operation, accountId, payloadSignature).forEach { component ->
            val bytes = component.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return "request_" + digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}

/**
 * Persists request UUIDs before sending remote writes. If the Android process is killed
 * after the server commits but before the UI receives the response, retrying the same
 * operation with the same account/payload reuses its idempotency key.
 *
 * The completed marker is written before removing a key. If removal fails, the next
 * attempt sees "D|" and creates a fresh UUID rather than reusing a successful operation.
 */
internal class PendingRequestIds(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "studiomesa_pending_requests",
        Context.MODE_PRIVATE
    )

    fun getOrCreate(operation: String, accountId: String, payloadSignature: String): String =
        synchronized(lock) {
            val key = PendingRequestKey.from(operation, accountId, payloadSignature)
            val existing = preferences.getString(key, null)
            if (existing != null && existing.startsWith("P|")) {
                val requestId = existing.removePrefix("P|")
                if (isUuid(requestId)) return@synchronized requestId
            }

            val requestId = UUID.randomUUID().toString()
            if (!preferences.edit().putString(key, "P|$requestId").commit()) {
                throw IllegalStateException("Não foi possível guardar a identificação segura da operação. Tenta novamente.")
            }
            requestId
        }

    fun complete(operation: String, accountId: String, payloadSignature: String, requestId: String) {
        synchronized(lock) {
            val key = PendingRequestKey.from(operation, accountId, payloadSignature)
            if (preferences.getString(key, null) != "P|$requestId") return
            if (!preferences.edit().putString(key, "D|$requestId").commit()) return
            // A failed removal is harmless: getOrCreate will not reuse a D| marker.
            preferences.edit().remove(key).commit()
        }
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

    private companion object {
        val lock = Any()
    }
}
