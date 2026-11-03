package pt.studiomesa.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InvoiceDocumentTextExtractorInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun extractsPlainTextLocallyAndMarksLongTextAsTruncated() {
        val file = File.createTempFile("studiomesa-invoice-", ".txt", context.cacheDir)
        try {
            file.writeText("Fornecedor: Distribuidor Alimentar\nTotal: 123,00 EUR", Charsets.UTF_8)
            val result = InvoiceDocumentTextExtractor.extract(
                context,
                Uri.fromFile(file),
                "text/plain"
            )
            assertTrue(result.text.contains("Distribuidor Alimentar"))
            assertTrue(result.text.contains("123,00 EUR"))
            assertEquals(1, result.pagesProcessed)
            assertFalse(result.truncated)

            file.writeText("X".repeat(InvoiceDocumentTextExtractorTestLimits.MAX_TEXT_CHARS + 100), Charsets.UTF_8)
            val longResult = InvoiceDocumentTextExtractor.extract(
                context,
                Uri.fromFile(file),
                "text/plain"
            )
            assertEquals(InvoiceDocumentTextExtractorTestLimits.MAX_TEXT_CHARS, longResult.text.length)
            assertTrue(longResult.truncated)
        } finally {
            file.delete()
        }
    }

    @Test
    fun extractsXmlFieldsWithoutUploadingTheDocument() {
        val file = File.createTempFile("studiomesa-invoice-", ".xml", context.cacheDir)
        try {
            file.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<Invoice>
  <Supplier>Distribuidor Alimentar</Supplier>
  <InvoiceNo>FT 2026/104</InvoiceNo>
  <InvoiceTotal>123.00</InvoiceTotal>
</Invoice>""".trimIndent(),
                Charsets.UTF_8
            )
            val result = InvoiceDocumentTextExtractor.extract(
                context,
                Uri.fromFile(file),
                "application/xml"
            )
            assertTrue(result.text.contains("Supplier: Distribuidor Alimentar"))
            assertTrue(result.text.contains("InvoiceNo: FT 2026/104"))
            assertTrue(result.text.contains("InvoiceTotal: 123.00"))
            assertEquals("leitura XML local", result.method)
            assertFalse(result.truncated)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsUnsupportedFormatsInsteadOfPretendingToReadThem() {
        val file = File.createTempFile("studiomesa-invoice-", ".bin", context.cacheDir)
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            try {
                InvoiceDocumentTextExtractor.extract(
                    context,
                    Uri.fromFile(file),
                    "application/octet-stream"
                )
                throw AssertionError("A leitura de um tipo MIME não suportado devia ser rejeitada.")
            } catch (_: IllegalArgumentException) {
                // Expected: unknown binary files are stored as attachments, not parsed as text.
            }
        } finally {
            file.delete()
        }
    }
}

private object InvoiceDocumentTextExtractorTestLimits {
    const val MAX_TEXT_CHARS = 20_000
}
