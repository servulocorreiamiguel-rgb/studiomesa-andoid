package pt.studiomesa.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted, app-private storage for supplier invoice attachments.
 * Plaintext is held in bounded memory only; persisted files are AES-GCM ciphertext.
 */
class AssistantInvoiceFileStore(context: Context) {
    data class Attachment(
        val id: String,
        val fileName: String,
        val mimeType: String,
        val byteCount: Long
    )

    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "assistant_invoices")

    @Synchronized
    fun importFromUri(uri: Uri): Attachment {
        val resolver = appContext.contentResolver
        val metadata = readMetadata(resolver, uri)
        require(metadata.first.isNotBlank()) { "Não foi possível identificar o nome do ficheiro." }
        val mimeType = (resolver.getType(uri) ?: "application/octet-stream").lowercase()
        require(mimeType in ALLOWED_MIME_TYPES) {
            "Formato não suportado. Importa PDF, XML, TXT, JPEG, PNG ou WebP."
        }
        val plain = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BYTES) { "O documento excede o limite de 12 MB." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IllegalStateException("Não foi possível abrir o documento selecionado.")
        require(plain.isNotEmpty()) { "O documento está vazio." }

        if (mimeType == "application/pdf") {
            require(plain.size >= 5 && String(plain, 0, minOf(5, plain.size), Charsets.US_ASCII) == "%PDF-") {
                "O ficheiro selecionado não parece ser um PDF válido."
            }
        }
        if (mimeType == "application/xml" || mimeType == "text/xml") {
            val prefix = String(plain, 0, minOf(256, plain.size), Charsets.UTF_8)
            require(prefix.contains("<") && !prefix.contains("\u0000")) {
                "O ficheiro XML não parece ser válido."
            }
        }

        val id = UUID.randomUUID().toString()
        val plainByteCount = plain.size.toLong()
        val encrypted = encrypt(plain)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Não foi possível criar o arquivo privado de documentos.")
        }
        val target = fileFor(id)
        val temporary = File(directory, "$id.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(encrypted)
                stream.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                throw IllegalStateException("Não foi possível guardar o documento cifrado.")
            }
        } catch (error: Exception) {
            temporary.delete()
            target.delete()
            throw error
        } finally {
            plain.fill(0)
            encrypted.fill(0)
        }
        return Attachment(id, metadata.first.take(180), mimeType, plainByteCount)
    }

    @Synchronized
    fun exportToUri(id: String, destination: Uri) {
        val encrypted = readEncrypted(fileFor(id))
        val plain = decrypt(encrypted)
        try {
            appContext.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                output.write(plain)
                output.flush()
            } ?: throw IllegalStateException("Não foi possível escrever no destino escolhido.")
        } finally {
            plain.fill(0)
            encrypted.fill(0)
        }
    }

    @Synchronized
    fun delete(id: String) {
        val file = fileFor(id)
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Não foi possível eliminar o anexo cifrado.")
        }
    }

    @Synchronized
    fun exists(id: String): Boolean = fileFor(id).isFile

    private fun readMetadata(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        require(size <= MAX_BYTES) { "O documento excede o limite de 12 MB." }
        return name to size
    }

    private fun fileFor(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
            "O identificador do anexo não é válido."
        }
        val root = directory.canonicalFile
        val file = File(root, "$id.enc").canonicalFile
        require(file.parentFile == root) { "O caminho do anexo não é válido." }
        return file
    }

    private fun readEncrypted(file: File): ByteArray {
        require(file.isFile && file.length() in 1..(MAX_BYTES + 64).toLong()) {
            "O anexo não existe ou está danificado."
        }
        return FileInputStream(file).use { it.readBytes() }
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > 1) { "O documento cifrado está vazio." }
        val ivLength = payload[0].toInt() and 0xff
        require(ivLength in 12..16 && payload.size > 1 + ivLength) {
            "O formato do documento cifrado não é válido."
        }
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val encrypted = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
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
        const val KEY_ALIAS = "pt.studiomesa.assistant.invoice.v1"
        const val MAX_BYTES = 12 * 1024 * 1024
        val ALLOWED_MIME_TYPES = setOf(
            "application/pdf",
            "application/xml",
            "text/xml",
            "text/plain",
            "image/jpeg",
            "image/png",
            "image/webp"
        )
    }
}
