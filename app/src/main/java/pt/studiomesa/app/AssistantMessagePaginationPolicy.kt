package pt.studiomesa.app

/** Bounded pagination for owner-only assistant history downloads. */
internal object AssistantMessagePaginationPolicy {
    const val PAGE_SIZE = 100
    const val MAX_MESSAGES = 5_000

    /**
     * Returns the next PostgREST offset, or null when the result is complete/capped.
     * [totalRows] is the number already collected after appending the current page.
     */
    fun nextOffset(
        currentOffset: Int,
        requestedLimit: Int,
        returnedRows: Int,
        totalRows: Int
    ): Int? {
        require(currentOffset >= 0) { "O offset não pode ser negativo." }
        require(requestedLimit in 1..PAGE_SIZE) { "O tamanho da página não é válido." }
        require(returnedRows in 0..requestedLimit) { "O número de linhas devolvidas não é válido." }
        require(totalRows >= 0) { "O total de linhas não pode ser negativo." }

        if (returnedRows < requestedLimit || totalRows >= MAX_MESSAGES) return null
        return Math.addExact(currentOffset, returnedRows)
    }
}
