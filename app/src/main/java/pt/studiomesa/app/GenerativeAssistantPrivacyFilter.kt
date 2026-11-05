package pt.studiomesa.app

import java.text.Normalizer
import java.util.Locale

/**
 * Guard for the free Gemini tier. Keeps identifiable, fiscal and numeric business
 * data on-device; this is a conservative heuristic, not a data-loss prevention system.
 */
internal object GenerativeAssistantPrivacyFilter {
    private val blocked = Regex(
        """\b(?:faturas?|facturas?|invoices?|iva|nif|iban|fornecedor(?:es)?|compras?|despesas?|gastos?|salarios?|ordenados?|lucros?|margens?|tesouraria|numeros? fiscais?|palavras? passe|passwords?|tokens?|contactos?|telefones?|emails?|moradas?|enderecos?|dados pessoais|cartoes?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val digitsOrAmounts = Regex("""\d|€|\bEUR\b""", RegexOption.IGNORE_CASE)

    fun isSafeToSend(messages: List<String>): Boolean {
        if (messages.isEmpty() || messages.size > 10) return false
        val combined = messages.joinToString("\n") { it.trim() }
        if (combined.isBlank() || combined.length > 12_000) return false
        val normalized = Normalizer.normalize(combined.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        if (blocked.containsMatchIn(normalized) || digitsOrAmounts.containsMatchIn(normalized)) return false
        if (Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE).containsMatchIn(combined)) return false
        if (Regex("""\+?\d[\d ().-]{7,}""").containsMatchIn(combined)) return false
        return true
    }
}
