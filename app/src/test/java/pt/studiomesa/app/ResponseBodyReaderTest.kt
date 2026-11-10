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

    @Test
    fun readsBinaryResponseAtTheConfiguredLimit() {
        val bytes = byteArrayOf(0, 1, 2, 3)

        assertEquals(
            listOf<Byte>(0, 1, 2, 3),
            ResponseBodyReader.readBytes(ByteArrayInputStream(bytes), maxBytes = bytes.size).toList()
        )
    }

    @Test
    fun rejectsBinaryResponseThatExceedsTheConfiguredLimit() {
        val error = assertThrows(IllegalStateException::class.java) {
            ResponseBodyReader.readBytes(
                ByteArrayInputStream(byteArrayOf(0, 1, 2, 3)),
                maxBytes = 3
            )
        }

        assertEquals("A resposta do servidor excede o limite permitido.", error.message)
    }

    @Test
    fun handlesAnAbsentBinaryResponseStream() {
        assertEquals(0, ResponseBodyReader.readBytes(null, maxBytes = 0).size)
    }

    @Test
    fun makesProgressWhenBinaryBulkReadReturnsZero() {
        val bytes = byteArrayOf(5, 4, 3, 2, 1)
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

        assertEquals(bytes.toList(), ResponseBodyReader.readBytes(input, bytes.size).toList())
    }

    @Test
    fun rejectsNegativeBinaryResponseLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            ResponseBodyReader.readBytes(ByteArrayInputStream(byteArrayOf(1)), maxBytes = -1)
        }
    }

    @Test
    fun rejectsDeclaredContentLengthAboveTheConfiguredLimit() {
        val error = assertThrows(IllegalStateException::class.java) {
            ResponseBodyReader.requireDeclaredLengthWithinLimit(
                declaredLength = 5L,
                maxBytes = 4
            )
        }

        assertEquals("A resposta do servidor excede o limite permitido.", error.message)
    }

    @Test
    fun allowsUnknownOrWithinLimitDeclaredContentLength() {
        ResponseBodyReader.requireDeclaredLengthWithinLimit(declaredLength = -1L, maxBytes = 4)
        ResponseBodyReader.requireDeclaredLengthWithinLimit(declaredLength = 4L, maxBytes = 4)
    }

}
