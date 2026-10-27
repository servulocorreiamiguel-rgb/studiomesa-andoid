package pt.studiomesa.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device-runtime tests for encrypted session-token storage and legacy-token migration. */
class SecureSessionStoreSmokeTest {
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val securePrefs =
        context.getSharedPreferences("studiomesa_secure_session", Context.MODE_PRIVATE)
    private val legacyPrefs =
        context.getSharedPreferences("studiomesa_backend", Context.MODE_PRIVATE)
    private val store = SecureSessionStore(context)

    @After
    fun clearTestSession() {
        store.clear()
    }

    @Test
    fun sessionTokensAreEncryptedAndClearRemovesThem() {
        store.clear()
        val access = "access-token-test-not-a-real-credential"
        val refresh = "refresh-token-test-not-a-real-credential"

        store.save(access, refresh)

        assertEquals(access, store.accessToken())
        assertEquals(refresh, store.refreshToken())
        assertTrue(securePrefs.getString("access_token", null) != access)
        assertTrue(securePrefs.getString("refresh_token", null) != refresh)
        assertFalse(legacyPrefs.contains("access_token"))
        assertFalse(legacyPrefs.contains("refresh_token"))

        store.clear()

        assertEquals("", store.accessToken())
        assertEquals("", store.refreshToken())
        assertFalse(securePrefs.contains("access_token"))
        assertFalse(securePrefs.contains("refresh_token"))
    }

    @Test
    fun migratesLegacyPlainTextTokensIntoEncryptedStorage() {
        store.clear()
        val access = "legacy-access-token-test"
        val refresh = "legacy-refresh-token-test"
        assertTrue(
            legacyPrefs.edit()
                .putString("access_token", access)
                .putString("refresh_token", refresh)
                .commit()
        )

        assertEquals(access, store.accessToken())
        assertEquals(refresh, store.refreshToken())
        assertTrue(securePrefs.getString("access_token", null) != access)
        assertTrue(securePrefs.getString("refresh_token", null) != refresh)
        assertFalse(legacyPrefs.contains("access_token"))
        assertFalse(legacyPrefs.contains("refresh_token"))
    }

    @Test
    fun rejectsTamperedCiphertextAndClearsTheSession() {
        store.clear()
        store.save("access-token-test", "refresh-token-test")
        assertTrue(
            securePrefs.edit()
                .putString("access_token", "not-valid-base64-ciphertext")
                .commit()
        )

        assertEquals("", store.accessToken())
        assertEquals("", store.refreshToken())
        assertFalse(securePrefs.contains("access_token"))
        assertFalse(securePrefs.contains("refresh_token"))
        assertFalse(legacyPrefs.contains("access_token"))
        assertFalse(legacyPrefs.contains("refresh_token"))
    }
}
