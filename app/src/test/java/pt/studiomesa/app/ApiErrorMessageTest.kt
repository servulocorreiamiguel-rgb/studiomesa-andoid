package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorMessageTest {
    @Test
    fun collapsesControlCharactersIntoOneReadableLine() {
        assertEquals(
            "Autenticação falhou. Tenta novamente.",
            ApiErrorMessage.sanitize("Autenticação falhou.\n\tTenta novamente.", 401)
        )
    }

    @Test
    fun usesStatusCodeWhenServerMessageIsAbsent() {
        assertEquals("Erro de ligação (502).", ApiErrorMessage.sanitize(null, 502))
        assertEquals("Erro de ligação (403).", ApiErrorMessage.sanitize("  ", 403))
    }

    @Test
    fun truncatesUnexpectedlyLongMessages() {
        val result = ApiErrorMessage.sanitize("x".repeat(10_000), 429)

        assertEquals(400, result.length)
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun doesNotSplitSurrogatePairAtTruncationBoundary() {
        val candidate = "a".repeat(398) + "😀" + "b".repeat(10)
        val result = ApiErrorMessage.sanitize(candidate, 500)

        assertEquals("a".repeat(398) + "…", result)
        assertFalse(Character.isHighSurrogate(result.last()))
    }
}
