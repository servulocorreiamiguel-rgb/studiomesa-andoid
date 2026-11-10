package pt.studiomesa.app

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads bounded HTTP response bodies.
 *
 * JSON callers use [read]; binary Storage callers use [readBytes] with their
 * own strict limit. Content-Length is only an early rejection hint: the byte
 * reader enforces the limit even when the header is missing or incorrect.
 */
internal object ResponseBodyReader {
    private const val DEFAULT_MAX_BYTES = 2 * 1024 * 1024
    private const val BUFFER_SIZE = 8 * 1024
    private const val TOO_LARGE_MESSAGE = "A resposta do servidor excede o limite permitido."

    fun read(inputStream: InputStream?, maxBytes: Int = DEFAULT_MAX_BYTES): String =
        String(readBytes(inputStream, maxBytes), Charsets.UTF_8)

    fun readBytes(inputStream: InputStream?, maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "O limite da resposta não pode ser negativo." }
        if (inputStream == null) return ByteArray(0)

        return inputStream.use { input ->
            val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes = 0

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    // Defensive fallback for non-conforming InputStreams: avoid a busy
                    // loop if a bulk read repeatedly reports zero bytes without EOF.
                    val singleByte = input.read()
                    if (singleByte < 0) break
                    if (totalBytes >= maxBytes) {
                        throw IllegalStateException(TOO_LARGE_MESSAGE)
                    }
                    output.write(singleByte)
                    totalBytes++
                    continue
                }

                if (count > maxBytes - totalBytes) {
                    throw IllegalStateException(TOO_LARGE_MESSAGE)
                }

                output.write(buffer, 0, count)
                totalBytes += count
            }

            output.toByteArray()
        }
    }

    /**
     * Reject a response early when the server declares an excessive size.
     * A negative Content-Length (normally -1) means that the size is unknown
     * and must still be checked by [readBytes].
     */
    fun requireDeclaredLengthWithinLimit(declaredLength: Long, maxBytes: Int) {
        require(maxBytes >= 0) { "O limite da resposta não pode ser negativo." }
        if (declaredLength > maxBytes.toLong()) {
            throw IllegalStateException(TOO_LARGE_MESSAGE)
        }
    }
}
