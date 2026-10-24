package pt.studiomesa.app

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads small JSON API responses without allowing a server or proxy response
 * to allocate an unbounded amount of memory in the app process.
 */
internal object ResponseBodyReader {
    private const val DEFAULT_MAX_BYTES = 2 * 1024 * 1024
    private const val BUFFER_SIZE = 8 * 1024

    fun read(inputStream: InputStream?, maxBytes: Int = DEFAULT_MAX_BYTES): String {
        require(maxBytes >= 0) { "O limite da resposta não pode ser negativo." }
        if (inputStream == null) return ""

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
                        throw IllegalStateException("A resposta do servidor excede o limite permitido.")
                    }
                    output.write(singleByte)
                    totalBytes++
                    continue
                }

                if (count > maxBytes - totalBytes) {
                    throw IllegalStateException("A resposta do servidor excede o limite permitido.")
                }

                output.write(buffer, 0, count)
                totalBytes += count
            }

            String(output.toByteArray(), Charsets.UTF_8)
        }
    }
}
