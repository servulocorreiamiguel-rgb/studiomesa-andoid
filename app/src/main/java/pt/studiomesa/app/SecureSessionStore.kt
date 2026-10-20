package pt.studiomesa.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores Supabase access and refresh tokens encrypted with an AES-GCM key held by Android Keystore.
 * Migrates tokens from the earlier private SharedPreferences file the first time they are read.
 */
class SecureSessionStore(context: Context) {
    private val securePrefs = context.applicationContext
        .getSharedPreferences("studiomesa_secure_session", Context.MODE_PRIVATE)
    private val legacyPrefs = context.applicationContext
        .getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)

    @Synchronized
    fun accessToken(): String = getToken("access_token")

    @Synchronized
    fun refreshToken(): String = getToken("refresh_token")

    @Synchronized
    fun save(accessToken: String, refreshToken: String) {
        val editor = securePrefs.edit()
        if (accessToken.isBlank()) {
            editor.remove("access_token")
        } else {
            editor.putString("access_token", encrypt(accessToken))
        }
        if (refreshToken.isBlank()) {
            editor.remove("refresh_token")
        } else {
            editor.putString("refresh_token", encrypt(refreshToken))
        }
        if (!editor.commit()) {
            throw IllegalStateException("Não foi possível guardar a sessão cifrada neste dispositivo.")
        }
        legacyPrefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }

    @Synchronized
    fun clear() {
        securePrefs.edit().remove("access_token").remove("refresh_token").commit()
        legacyPrefs.edit().remove("access_token").remove("refresh_token").apply()
    }

    /**
     * Stores a pending PKCE verifier encrypted with the same Android Keystore key as session tokens.
     * Only one auth-code flow is allowed at a time in this single-activity demo app.
     */
    @Synchronized
    fun savePendingCodeVerifier(verifier: String, flowType: String) {
        require(flowType == "oauth" || flowType == "recovery") {
            "Tipo de autenticação pendente inválido."
        }
        val editor = securePrefs.edit()
        if (verifier.isBlank()) {
            editor.remove("pending_code_verifier")
            editor.remove("pending_code_flow")
        } else {
            editor.putString("pending_code_verifier", encrypt(verifier))
                .putString("pending_code_flow", flowType)
        }
        if (!editor.commit()) {
            throw IllegalStateException("Não foi possível guardar a verificação segura da autenticação.")
        }
    }

    @Synchronized
    fun pendingCodeVerifier(): String {
        val encrypted = securePrefs.getString("pending_code_verifier", null)
        if (encrypted.isNullOrBlank()) return ""
        return try {
            decrypt(encrypted)
        } catch (_: Exception) {
            clearPendingCodeVerifier()
            ""
        }
    }

    @Synchronized
    fun pendingCodeFlowType(): String =
        securePrefs.getString("pending_code_flow", "").orEmpty()

    @Synchronized
    fun clearPendingCodeVerifier() {
        if (!securePrefs.edit()
                .remove("pending_code_verifier")
                .remove("pending_code_flow")
                .commit()) {
            throw IllegalStateException("Não foi possível limpar a autenticação pendente.")
        }
    }

    private fun getToken(key: String): String {
        val encrypted = securePrefs.getString(key, null)
        if (!encrypted.isNullOrBlank()) {
            legacyPrefs.edit().remove(key).apply()
            return try {
                decrypt(encrypted)
            } catch (_: Exception) {
                clear()
                ""
            }
        }

        // One-time migration from the app's previous private preferences file.
        val oldAccessToken = legacyPrefs.getString("access_token", "").orEmpty()
        val oldRefreshToken = legacyPrefs.getString("refresh_token", "").orEmpty()
        if (oldAccessToken.isNotBlank() || oldRefreshToken.isNotBlank()) {
            return try {
                save(oldAccessToken, oldRefreshToken)
                if (key == "access_token") oldAccessToken else oldRefreshToken
            } catch (_: Exception) {
                clear()
                ""
            }
        }
        return ""
    }

    private fun encrypt(plainText: String): String {
        require(plainText.isNotBlank()) { "A sessão não pode estar vazia." }
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
        if (combined.isEmpty()) throw IllegalArgumentException("A sessão cifrada está vazia.")
        val ivLength = combined[0].toInt() and 0xff
        if (ivLength !in 12..16 || combined.size <= 1 + ivLength) {
            throw IllegalArgumentException("O formato da sessão cifrada não é válido.")
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
        const val KEY_ALIAS = "pt.studiomesa.session.tokens.v1"
    }
}
