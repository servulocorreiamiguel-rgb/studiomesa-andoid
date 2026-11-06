package pt.studiomesa.app

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Locale

internal data class ProcessedInvoiceDocument(
    val mimeType: String,
    val sha256: String,
    val byteSize: Long,
    val pageCount: Int,
    val extractedText: String
)

/** OCR offline de PDFs e imagens. Executar sempre num worker thread. */
internal class InvoiceDocumentProcessor(private val context: Context) {
    fun process(uri: Uri): ProcessedInvoiceDocument {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("Não foi possível identificar o tipo do documento.")
        require(mime in ALLOWED_MIME_TYPES) {
            "Formato não suportado. Escolhe um PDF, JPG, PNG ou WebP."
        }
        val bytes = readDocumentBytes(resolver, uri)
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "O documento excede 25 MB." }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val (text, pages) = if (mime == "application/pdf") {
                extractPdfText(uri, recognizer)
            } else {
                Tasks.await(recognizer.process(InputImage.fromFilePath(context, uri))).text to 1
            }
            require(text.isNotBlank()) {
                "Não foi possível reconhecer texto. Experimenta uma fotografia mais nítida ou introduz os valores manualmente."
            }
            ProcessedInvoiceDocument(mime, hash, bytes.size.toLong(), pages, text.take(MAX_OCR_CHARS))
        } finally {
            recognizer.close()
        }
    }

    fun readVerifiedDocumentBytes(uri: Uri, expectedSha256: String): ByteArray {
        require(expectedSha256.matches(Regex("^[a-f0-9]{64}$"))) {
            "O identificador de integridade do documento é inválido."
        }
        val bytes = readDocumentBytes(context.contentResolver, uri)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        require(digest.equals(expectedSha256, ignoreCase = true)) {
            "O documento mudou depois da extração OCR. Importa-o novamente."
        }
        return bytes
    }

    private fun extractPdfText(uri: Uri, recognizer: TextRecognizer): Pair<String, Int> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("Não foi possível abrir o PDF.")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                require(renderer.pageCount in 1..MAX_PDF_PAGES) {
                    "O PDF tem ${renderer.pageCount} páginas. Nesta versão são aceites até $MAX_PDF_PAGES páginas."
                }
                val output = StringBuilder()
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = minOf(1.0f, MAX_RENDER_DIMENSION.toFloat() / maxOf(page.width, page.height))
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                            if (output.isNotEmpty()) output.append("\n\n--- Página ").append(index + 1).append(" ---\n")
                            output.append(recognized.text)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    if (output.length >= MAX_OCR_CHARS) break
                }
                return output.toString().take(MAX_OCR_CHARS) to renderer.pageCount
            }
        }
    }

    private fun readDocumentBytes(resolver: ContentResolver, uri: Uri): ByteArray {
        val output = ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_DOCUMENT_BYTES) { "O documento excede 25 MB." }
                output.write(buffer, 0, count)
            }
        } ?: throw FileNotFoundException("Não foi possível ler o documento selecionado.")
        return output.toByteArray()
    }

    companion object {
        const val MAX_DOCUMENT_BYTES = 25 * 1024 * 1024
        const val MAX_PDF_PAGES = 10
        const val MAX_RENDER_DIMENSION = 1800
        const val MAX_OCR_CHARS = 50_000
        val ALLOWED_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png", "image/webp")
    }
}
