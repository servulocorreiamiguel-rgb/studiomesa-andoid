package pt.studiomesa.app

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ResponseBodyReaderTest {
    @Test
    fun acceptsUtf8ResponseAtTheConfiguredLimit() {
        val response = "ação"
        val bytes = response.toByteArray(Charsets.UTF_8)

        assertEquals(
            response,
            ResponseBodyReader.read(ByteArrayInputStream(bytes), maxBytes = bytes.size)
        )
    }

    @Test
    fun rejectsResponseThatExceedsTheConfiguredLimit() {
        val error = assertThrows(IllegalStateException::class.java) {
            ResponseBodyReader.read(
                ByteArrayInputStream("abcd".toByteArray(Charsets.UTF_8)),
                maxBytes = 3
            )
        }

        assertEquals("A resposta do servidor excede o limite permitido.", error.message)
    }

    @Test
    fun handlesAnAbsentResponseStream() {
        assertEquals("", ResponseBodyReader.read(null, maxBytes = 0))
    }

    @Test
    fun makesProgressWhenBulkReadReturnsZero() {
        val bytes = "resposta".toByteArray(Charsets.UTF_8)
        val input = object : ByteArrayInputStream(bytes) {
            private var returnZeroOnce = true

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZeroOnce) {
                    returnZeroOnce = false
                    return 0
                }
                return super.read(buffer, offset, length)
            }
        }

        assertEquals("resposta", ResponseBodyReader.read(input, maxBytes = bytes.size))
    }
}
