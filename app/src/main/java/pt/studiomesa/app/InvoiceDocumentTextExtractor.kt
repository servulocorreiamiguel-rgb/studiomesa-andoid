package pt.studiomesa.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Extracts document text on-device. No document bytes or recognized text are
 * uploaded to Google, Supabase or another remote service.
 *
 * Image OCR uses the bundled Latin ML Kit model (Portuguese/Latin script).
 * PDF pages are rendered locally with PdfRenderer before OCR.
 */
object InvoiceDocumentTextExtractor {
    data class Result(
        val text: String,
        val pagesProcessed: Int,
        val truncated: Boolean,
        val method: String
    )

    private const val MAX_DOCUMENT_BYTES = 12 * 1024 * 1024
    private const val MAX_TEXT_DOCUMENT_BYTES = 2 * 1024 * 1024
    private const val MAX_PDF_PAGES = 10
    private const val MAX_TEXT_CHARS = 20_000
    private const val MAX_RENDER_DIMENSION = 1_800
    private const val OCR_TIMEOUT_SECONDS = 60L
    private val directExecutor = Executor { command -> command.run() }

    fun extract(context: Context, uri: Uri, mimeType: String): Result {
        return when (mimeType.lowercase()) {
            "application/pdf" -> extractPdf(context, uri)
            "image/jpeg", "image/png", "image/webp" -> extractImage(context, uri)
            "text/plain" -> extractPlainText(context, uri)
            "application/xml", "text/xml" -> extractXmlText(context, uri)
            else -> throw IllegalArgumentException(
                "Este formato pode ser guardado, mas ainda não tem leitura automática de texto."
            )
        }
    }

    private fun extractImage(context: Context, uri: Uri): Result {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, uri)
            val text = recognize(recognizer, image)
            return Result(
                text = text.take(MAX_TEXT_CHARS),
                pagesProcessed = 1,
                truncated = text.length > MAX_TEXT_CHARS,
                method = "OCR local"
            )
        } finally {
            recognizer.close()
        }
    }

    private fun extractPdf(context: Context, uri: Uri): Result {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Não foi possível abrir o PDF selecionado.")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(renderer.pageCount > 0) { "O PDF não contém páginas." }
                    val pagesToProcess = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    val output = StringBuilder()
                    var truncated = renderer.pageCount > MAX_PDF_PAGES
                    var processed = 0

                    for (pageIndex in 0 until pagesToProcess) {
                        if (output.length >= MAX_TEXT_CHARS) {
                            truncated = true
                            break
                        }
                        renderer.openPage(pageIndex).use { page ->
                            val longestSide = maxOf(page.width, page.height).coerceAtLeast(1)
                            val scale = minOf(
                                2.0f,
                                MAX_RENDER_DIMENSION.toFloat() / longestSide.toFloat()
                            )
                            val bitmapWidth = (page.width * scale).toInt().coerceAtLeast(1)
                            val bitmapHeight = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(
                                bitmapWidth,
                                bitmapHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                val transform = Matrix().apply {
                                    setScale(
                                        bitmapWidth.toFloat() / page.width.toFloat(),
                                        bitmapHeight.toFloat() / page.height.toFloat()
                                    )
                                }
                                page.render(
                                    bitmap,
                                    null,
                                    transform,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )
                                val pageText = recognize(
                                    recognizer,
                                    InputImage.fromBitmap(bitmap, 0)
                                )
                                if (pageText.isNotBlank()) {
                                    if (output.isNotEmpty()) output.append("\n\n")
                                    output.append("Página ").append(pageIndex + 1).append("\n")
                                    output.append(pageText)
                                }
                                processed += 1
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }

                    val fullText = output.toString()
                    if (fullText.length > MAX_TEXT_CHARS) truncated = true
                    require(fullText.isNotBlank()) {
                        "Não foi possível reconhecer texto neste PDF. Confirma se as páginas têm texto legível."
                    }
                    return Result(
                        text = fullText.take(MAX_TEXT_CHARS),
                        pagesProcessed = processed,
                        truncated = truncated,
                        method = "OCR local em PDF"
                    )
                }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun extractPlainText(context: Context, uri: Uri): Result {
        val bytes = readBounded(context, uri, MAX_TEXT_DOCUMENT_BYTES)
        val text = String(bytes, Charsets.UTF_8)
            .removePrefix("\uFEFF")
            .replace("\u0000", "")
            .trim()
        require(text.isNotBlank()) { "O ficheiro de texto não contém conteúdo legível." }
        return Result(
            text = text.take(MAX_TEXT_CHARS),
            pagesProcessed = 1,
            truncated = text.length > MAX_TEXT_CHARS,
            method = "leitura de texto local"
        )
    }

    private fun extractXmlText(context: Context, uri: Uri): Result {
        val bytes = readBounded(context, uri, MAX_TEXT_DOCUMENT_BYTES)
        val xml = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(StringReader(xml))
        val output = StringBuilder()
        var currentTag = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT && output.length < MAX_TEXT_CHARS) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> currentTag = parser.name.orEmpty()
                XmlPullParser.TEXT -> {
                    val value = parser.text.orEmpty().trim()
                    if (value.isNotBlank()) {
                        if (output.isNotEmpty()) output.append('\n')
                        if (currentTag.isNotBlank()) output.append(currentTag).append(": ")
                        output.append(value)
                    }
                }
            }
            parser.next()
        }
        val extracted = output.toString()
        require(extracted.isNotBlank()) { "O XML não contém campos de texto reconhecíveis." }
        return Result(
            text = extracted.take(MAX_TEXT_CHARS),
            pagesProcessed = 1,
            truncated = extracted.length > MAX_TEXT_CHARS,
            method = "leitura XML local"
        )
    }

    private fun recognize(recognizer: TextRecognizer, image: InputImage): String {
        val latch = CountDownLatch(1)
        var result: String? = null
        var failure: Exception? = null

        recognizer.process(image)
            .addOnSuccessListener(directExecutor) { text ->
                result = text.text
                latch.countDown()
            }
            .addOnFailureListener(directExecutor) { error ->
                failure = error
                latch.countDown()
            }

        if (!latch.await(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw TimeoutException("O reconhecimento demorou demasiado tempo. Tenta uma imagem mais nítida ou um PDF mais curto.")
        }
        failure?.let { throw IllegalStateException("O reconhecimento de texto falhou.", it) }
        return result.orEmpty()
    }

    private fun readBounded(context: Context, uri: Uri, limit: Int): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) {
                    "O ficheiro excede o limite de leitura de texto."
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalStateException("Não foi possível abrir o ficheiro selecionado.")
    }
}
