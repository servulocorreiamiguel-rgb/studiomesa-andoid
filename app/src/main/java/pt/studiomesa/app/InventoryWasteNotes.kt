package pt.studiomesa.app

internal object InventoryWasteNotes {
    const val PREFIX = "Desperdício · "

    val reasons: List<String> = listOf(
        "Validade expirada",
        "Deterioração",
        "Erro de preparação",
        "Produção excedente",
        "Queda ou acidente",
        "Outro"
    )

    fun build(reason: String, details: String): String {
        require(reason in reasons) { "Seleciona um motivo de desperdício válido." }
        val normalizedDetails = details.trim().replace(Regex("\\s+"), " ")
        require(normalizedDetails.length <= 350) {
            "A observação pode ter no máximo 350 caracteres."
        }
        val note = buildString {
            append(PREFIX).append(reason)
            if (normalizedDetails.isNotEmpty()) append(" · ").append(normalizedDetails)
        }
        require(note.length <= 500) { "O registo de desperdício excede o limite permitido." }
        return note
    }

    fun isWaste(note: String): Boolean = note.startsWith(PREFIX)
}
