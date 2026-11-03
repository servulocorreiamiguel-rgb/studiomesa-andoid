package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerReservationRefreshStatusTest {
    @Test fun refreshShowsThatExistingListRemainsAvailable() {
        assertEquals(
            "A atualizar as tuas reservas. A lista mantém os últimos dados recebidos até a atualização terminar.",
            CustomerReservationRefreshStatus.message(isRefreshing = true, hasFailed = false)
        )
    }

    @Test fun refreshFailureExplainsThatCachedReservationsRemainVisible() {
        assertEquals(
            "Não foi possível atualizar. A lista continua a mostrar os últimos dados recebidos; tenta novamente.",
            CustomerReservationRefreshStatus.message(isRefreshing = false, hasFailed = true)
        )
    }

    @Test fun successfulIdleStateDoesNotAddAnUnnecessaryNotice() {
        assertNull(CustomerReservationRefreshStatus.message(isRefreshing = false, hasFailed = false))
    }
}
