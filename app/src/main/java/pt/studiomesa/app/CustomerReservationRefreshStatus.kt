package pt.studiomesa.app

/**
 * Explains refresh state without hiding the last successfully received reservation list.
 */
internal object CustomerReservationRefreshStatus {
    fun message(isRefreshing: Boolean, hasFailed: Boolean): String? = when {
        isRefreshing ->
            "A atualizar as tuas reservas. A lista mantém os últimos dados recebidos até a atualização terminar."
        hasFailed ->
            "Não foi possível atualizar. A lista continua a mostrar os últimos dados recebidos; tenta novamente."
        else -> null
    }
}
