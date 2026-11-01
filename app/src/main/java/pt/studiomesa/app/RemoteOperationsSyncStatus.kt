package pt.studiomesa.app

/**
 * User-facing status for the restaurant operations cache.
 *
 * A successful timestamp is shown only as the last confirmed server fetch;
 * it is never presented as proof of live connectivity.
 */
internal object RemoteOperationsSyncStatus {
    fun label(
        isSyncing: Boolean,
        hasFailed: Boolean,
        lastSuccessfulSync: String?
    ): String {
        if (isSyncing) return "A sincronizar mesas, carta e pedidos…"

        val lastSync = lastSuccessfulSync?.takeIf { it.isNotBlank() }
        if (hasFailed) {
            return lastSync?.let {
                "Falha ao atualizar · a usar dados recebidos às $it"
            } ?: "Falha ao sincronizar · sem dados confirmados neste dispositivo"
        }

        return lastSync?.let {
            "Última sincronização concluída às $it"
        } ?: "Restaurante configurado · ainda sem sincronização concluída"
    }
}
