package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteOperationsSyncStatusTest {
    @Test fun syncingStatusTakesPriorityOverPreviousResult() {
        assertEquals(
            "A sincronizar mesas, carta e pedidos…",
            RemoteOperationsSyncStatus.label(true, true, "10:35")
        )
    }

    @Test fun successfulSyncShowsConfirmedTime() {
        assertEquals(
            "Última sincronização concluída às 10:35",
            RemoteOperationsSyncStatus.label(false, false, "10:35")
        )
    }

    @Test fun failedSyncExplainsThatCachedDataIsBeingUsed() {
        assertEquals(
            "Falha ao atualizar · a usar dados recebidos às 10:35",
            RemoteOperationsSyncStatus.label(false, true, "10:35")
        )
    }

    @Test fun failedFirstSyncDoesNotImplyThatDataWasReceived() {
        assertEquals(
            "Falha ao sincronizar · sem dados confirmados neste dispositivo",
            RemoteOperationsSyncStatus.label(false, true, null)
        )
    }

    @Test fun uninitialisedSyncDoesNotClaimThatServerDataIsCurrent() {
        assertEquals(
            "Restaurante configurado · ainda sem sincronização concluída",
            RemoteOperationsSyncStatus.label(false, false, null)
        )
    }
}
