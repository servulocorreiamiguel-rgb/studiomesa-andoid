package pt.studiomesa.app

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative automatic memory extraction.
 *
 * Only stable preferences, goals and restaurant context expressed with clear
 * first-person signals are stored. Transient questions and sensitive personal,
 * financial or authentication data are deliberately ignored.
 */
internal object AssistantAutomaticMemoryPolicy {
    const val MAX_MEMORY_CHARS = 2500
    const val MAX_CONTEXT_MEMORY_CHARS = 250
    private const val MAX_FACT_CHARS = 220

    private val extractionPatterns = listOf(
        Regex("""(?iu)^\s*(?:por favor,?\s*)?quero\s+que\s+te\s+lembres\s+que\s+(.+)$"""),
        Regex("""(?iu)^\s*(?:lembra(?:-te)?|guarda|memoriza|recorda)\s+(?:me\s+)?que\s+(.+)$"""),
        Regex("""(?iu)^\s*prefiro\s+(.+)$"""),
        Regex("""(?iu)^\s*(?:não|nao)\s+gosto\s+de\s+(.+)$"""),
        Regex("""(?iu)^\s*o\s+meu\s+objetivo\s+(?:é|e)\s+(.+)$"""),
        Regex("""(?iu)^\s*o\s+objetivo\s+(?:do\s+restaurante\s+)?(?:é|e)\s+(.+)$"""),
        Regex("""(?iu)^\s*(?:o\s+nosso|o)\s+restaurante\s+(?:é|e)\s+(.+)$"""),
        Regex("""(?iu)^\s*(?:o\s+nosso\s+)?restaurante\s+chama-se\s+(.+)$"""),
        Regex("""(?iu)^\s*a\s+nossa\s+especialidade\s+(?:é|e)\s+(.+)$"""),
        Regex("""(?iu)^\s*trabalhamos\s+com\s+(.+)$"""),
        Regex("""(?iu)^\s*responde\s+sempre\s+(?:em\s+)?(.+)$""")
    )

    private val sensitiveMarkers = listOf(
        "password", "palavra passe", "palavra-passe", "senha", "credenciais",
        "dados bancarios", "dados bancários", "iban", "cartao de credito",
        "cartão de crédito", "numero de conta", "número de conta", "cvv",
        "codigo pin", "código pin", "chave privada", "token de acesso",
        "email", "e-mail", "telemovel", "telemóvel", "telefone", "contacto",
        "morada", "endereco", "endereço", "nif", "cartao de cidadao",
        "cartão de cidadão", "dados de saude", "dados de saúde", "doenca",
        "doença", "diagnostico", "diagnóstico", "religiao", "religião",
        "orientacao sexual", "orientação sexual"
    )

    fun extractStableFact(userMessage: String): String? {
        val message = userMessage.trim()
        if (message.isBlank() || message.length > 700) return null
        if (containsSensitiveData(message)) return null

        val candidate = extractionPatterns.firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(message)?.groupValues?.getOrNull(1)
        } ?: return null

        val clean = candidate
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ';', ',', ' ')
        if (clean.length !in 8..MAX_FACT_CHARS) return null
        if (clean.contains('?') || containsSensitiveData(clean)) return null
        return clean
    }

    /**
     * Returns the updated memory, or null when no new stable fact was identified.
     */
    fun merge(existingMemory: String, userMessage: String): String? {
        val fact = extractStableFact(userMessage) ?: return null
        val current = existingMemory.trim().take(MAX_MEMORY_CHARS)
        val key = normalize(fact)
        val alreadySaved = current.lineSequence().any { line ->
            normalize(line.removePrefix("•").trim()) == key
        }
        if (alreadySaved) return null

        val entry = "• $fact"
        val updated = if (current.isBlank()) entry else "$current\n$entry"
        if (updated.length > MAX_MEMORY_CHARS) return null
        return updated
    }

    /**
     * Returns only complete, most-recent memory notes that fit the model prompt budget.
     * The full encrypted memory remains available to the user in the Memory screen.
     */
    fun contextSnippet(existingMemory: String, maxChars: Int = MAX_CONTEXT_MEMORY_CHARS): String {
        require(maxChars > 0) { "O limite de contexto da Memória tem de ser positivo." }
        val notes = existingMemory.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val selected = mutableListOf<String>()
        var used = 0
        for (note in notes.asReversed()) {
            val extra = note.length + if (selected.isEmpty()) 0 else 1
            if (used + extra > maxChars) continue
            selected.add(note)
            used += extra
        }
        return selected.asReversed().joinToString("\n")
    }

    private fun containsSensitiveData(value: String): Boolean {
        val normalized = normalize(value)
        if (sensitiveMarkers.any { normalize(it) in normalized }) return true
        if (Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE).containsMatchIn(value)) return true
        if (Regex("""\bPT\d{2}[\s-]?(?:\d[\s-]?){21}\b""", RegexOption.IGNORE_CASE).containsMatchIn(value)) return true
        if (Regex("""\b\d{9}\b""").containsMatchIn(value)) return true
        return false
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
