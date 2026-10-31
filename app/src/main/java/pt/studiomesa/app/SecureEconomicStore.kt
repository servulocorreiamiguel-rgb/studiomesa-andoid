package pt.studiomesa.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Explicitly opt-in, device-local storage for owner-entered economic inputs.
 *
 * Files are app-private, excluded from Android Auto Backup and encrypted with
 * an AES-256-GCM key held by Android Keystore. A scope hash separates owners
 * and restaurants without exposing their identifiers in filenames.
 */
internal class SecureEconomicStore(context: Context) {
    private val appContext = context.applicationContext

    fun hasSaved(scope: String): Boolean = scopedFile(scope).isFile

    fun save(scope: String, values: Map<String, String>) {
        require(scope.isNotBlank() && scope.length <= 512) {
            "A identificação local da conta não é válida."
        }
        val inputObject = JSONObject()
        values.forEach { (key, value) ->
            if (key in ALLOWED_INPUT_KEYS && value.isNotBlank()) {
                require(value.length <= MAX_VALUE_LENGTH) {
                    "Um dos campos económicos excede o limite permitido."
                }
                inputObject.put(key, value)
            }
        }
        require(inputObject.length() > 0) {
            "Preenche pelo menos um campo antes de guardar os valores."
        }
        val payload = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("scope", scope)
            .put("saved_at", System.currentTimeMillis())
            .put("inputs", inputObject)
            .toString()
            .toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_PAYLOAD_BYTES) {
            "Os dados económicos excedem o espaço de armazenamento permitido."
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv ?: throw IllegalStateException("O Android não gerou o vetor de cifragem.")
        val encrypted = cipher.doFinal(payload)
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(FILE_MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeInt(iv.size)
                output.write(iv)
                output.write(encrypted)
            }
            buffer.toByteArray()
        }
        writeAtomically(scopedFile(scope), bytes)
    }

    fun load(scope: String): Map<String, String> {
        require(scope.isNotBlank() && scope.length <= 512) {
            "A identificação local da conta não é válida."
        }
        val file = scopedFile(scope)
        if (!file.isFile) return emptyMap()
        require(file.length() in 1..(MAX_PAYLOAD_BYTES + GCM_TAG_BYTES + MAX_HEADER_BYTES).toLong()) {
            "A cópia económica local tem um tamanho inválido."
        }

        val (iv, encryptedPayload) = DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == FILE_MAGIC) {
                "O formato da cópia económica não é reconhecido."
            }
            require(input.readInt() == FORMAT_VERSION) {
                "A versão da cópia económica não é compatível."
            }
            val ivLength = input.readInt()
            require(ivLength in MIN_GCM_IV_BYTES..MAX_GCM_IV_BYTES) {
                "A cópia económica contém dados de cifragem inválidos."
            }
            val decodedIv = ByteArray(ivLength)
            input.readFully(decodedIv)
            decodedIv to input.readBytes()
        }
        require(encryptedPayload.isNotEmpty() && encryptedPayload.size <= MAX_PAYLOAD_BYTES + GCM_TAG_BYTES) {
            "A cópia económica está incompleta ou excede o limite permitido."
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(encryptedPayload)
        val document = JSONObject(String(plaintext, Charsets.UTF_8))
        require(document.optInt("version", -1) == FORMAT_VERSION) {
            "A versão dos dados económicos não é compatível."
        }
        if (document.optString("scope") != scope) return emptyMap()

        val savedInputs = document.optJSONObject("inputs") ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        val keys = savedInputs.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in ALLOWED_INPUT_KEYS) {
                val value = savedInputs.optString(key, "")
                if (value.isNotBlank()) result[key] = value
            }
        }
        return result
    }

    fun delete(scope: String): Boolean {
        val file = scopedFile(scope)
        if (!file.exists()) return true
        AtomicFile(file).delete()
        return !file.exists()
    }

    private fun scopedFile(scope: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
        val suffix = digest.take(16).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return File(appContext.noBackupFilesDir, "studiomesa-economic-$suffix.bin")
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val atomicFile = AtomicFile(file)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { atomicFile.failWrite(it) }
            throw IllegalStateException("Não foi possível guardar a cópia económica cifrada.", error)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pt.studiomesa.economic-store.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FILE_MAGIC = 0x534D4543 // SMEC
        const val FORMAT_VERSION = 1
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val MIN_GCM_IV_BYTES = 12
        const val MAX_GCM_IV_BYTES = 16
        const val MAX_VALUE_LENGTH = 8_192
        const val MAX_PAYLOAD_BYTES = 64 * 1_024
        const val MAX_HEADER_BYTES = 4 + 4 + 4 + MAX_GCM_IV_BYTES
        val ALLOWED_INPUT_KEYS = setOf(
            "sales", "food", "payroll", "fixed", "variable", "foodTarget",
            "waste", "cash", "receipts", "due", "weeks",
            "scenarioSalesChange", "scenarioFoodChange", "scenarioPayrollChange",
            "scenarioVariableChange", "breakEvenDays", "breakEvenTicket",
            "recipe", "reorder", "wasteLog", "supplierQuotes", "budgetComparison",
            "usageVariance", "labourPlan"
        )
    }
}
