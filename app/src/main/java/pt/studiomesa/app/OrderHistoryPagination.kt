package pt.studiomesa.app

/**
 * Loads an ordered collection in deterministic pages.
 *
 * The final empty-page probe distinguishes an exactly-full supported history from
 * a larger history that would otherwise be returned silently as a partial result.
 */
internal object OrderHistoryPagination {
    const val DEFAULT_PAGE_SIZE = 500
    const val DEFAULT_MAX_PAGES = 20
    const val MAX_SUPPORTED_ROWS = DEFAULT_PAGE_SIZE * DEFAULT_MAX_PAGES

    fun <T> collect(
        pageSize: Int = DEFAULT_PAGE_SIZE,
        maxPages: Int = DEFAULT_MAX_PAGES,
        fetchPage: (offset: Int, limit: Int) -> List<T>
    ): List<T> {
        require(pageSize in 1..1_000) { "O tamanho da página tem de estar entre 1 e 1000." }
        require(maxPages > 0) { "O número máximo de páginas tem de ser superior a zero." }
        require(maxPages <= Int.MAX_VALUE / pageSize) {
            "O limite de páginas excede o intervalo suportado."
        }
        val maximumRows = pageSize * maxPages

        val collected = ArrayList<T>()
        for (pageIndex in 0 until maxPages) {
            val page = fetchPage(pageIndex * pageSize, pageSize)
            require(page.size <= pageSize) { "O servidor devolveu mais linhas do que o limite pedido." }
            collected.addAll(page)
            if (page.size < pageSize) return collected
        }

        val overflowProbe = fetchPage(maxPages * pageSize, pageSize)
        require(overflowProbe.size <= pageSize) { "O servidor devolveu mais linhas do que o limite pedido." }
        if (overflowProbe.isEmpty()) return collected

        throw IllegalStateException(
            "O histórico tem mais de $maximumRows pedidos. " +
                "O carregamento foi interrompido para evitar apresentar uma análise parcial; contacta o suporte."
        )
    }
}
