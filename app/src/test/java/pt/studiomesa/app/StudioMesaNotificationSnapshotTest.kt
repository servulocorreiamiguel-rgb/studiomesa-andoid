package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMesaNotificationSnapshotTest {
    @Test
    fun firstSnapshotOnlyEstablishesBaseline() {
        val event = StudioMesaNotificationEvent(
            key = "order:1",
            state = "aberto",
            title = "Novo pedido",
            message = "Foi recebido um novo pedido.",
            notifyOnNew = true
        )

        val result = StudioMesaNotificationSnapshot.compare(emptyMap(), listOf(event), initialized = false)

        assertTrue(result.notifications.isEmpty())
        assertEquals("aberto", result.snapshot["order:1"])
    }

    @Test
    fun newOrderAfterBaselineTriggersOnce() {
        val event = StudioMesaNotificationEvent(
            key = "order:2",
            state = "aberto",
            title = "Novo pedido",
            message = "Foi recebido um novo pedido.",
            notifyOnNew = true
        )

        val first = StudioMesaNotificationSnapshot.compare(
            mapOf("order:1" to "pronto"),
            listOf(event),
            initialized = true
        )
        val second = StudioMesaNotificationSnapshot.compare(
            first.snapshot,
            listOf(event),
            initialized = true
        )

        assertEquals(1, first.notifications.size)
        assertTrue(second.notifications.isEmpty())
    }

    @Test
    fun customerReservationOnlyNotifiesOnRelevantTransition() {
        val event = StudioMesaNotificationEvent(
            key = "reservation:7",
            state = "Confirmada",
            title = "Atualização da reserva",
            message = "O estado da tua reserva foi atualizado.",
            notifyWhenStateIs = setOf("confirmada", "cancelada")
        )

        val result = StudioMesaNotificationSnapshot.compare(
            mapOf("reservation:7" to "pendente"),
            listOf(event),
            initialized = true
        )

        assertEquals(1, result.notifications.size)
        assertEquals("Confirmada", result.notifications.single().state)
    }

    @Test
    fun unrelatedStateChangesDoNotNotify() {
        val event = StudioMesaNotificationEvent(
            key = "reservation:9",
            state = "Concluída",
            title = "Atualização da reserva",
            message = "O estado da tua reserva foi atualizado.",
            notifyWhenStateIs = setOf("confirmada", "cancelada")
        )

        val result = StudioMesaNotificationSnapshot.compare(
            mapOf("reservation:9" to "confirmada"),
            listOf(event),
            initialized = true
        )

        assertTrue(result.notifications.isEmpty())
    }

    @Test
    fun snapshotIsBounded() {
        val events = (1..6).map {
            StudioMesaNotificationEvent(
                key = "event:$it",
                state = "open",
                title = "Aviso",
                message = "Teste"
            )
        }

        val result = StudioMesaNotificationSnapshot.compare(
            emptyMap(),
            events,
            initialized = false,
            maxEntries = 3
        )

        assertEquals(3, result.snapshot.size)
        assertTrue("event:4" in result.snapshot)
        assertTrue("event:6" in result.snapshot)
    }
}
