package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioMesaNotificationDestinationPolicyTest {
    @Test
    fun customerDestinationsAreAllowListed() {
        assertEquals("Reservas", StudioMesaNotificationDestinationPolicy.forCustomer("Reservas"))
        assertEquals("Cartão", StudioMesaNotificationDestinationPolicy.forCustomer("Cartão"))
        assertNull(StudioMesaNotificationDestinationPolicy.forCustomer("Marketing"))
        assertNull(StudioMesaNotificationDestinationPolicy.forCustomer("Conta;Stock"))
    }

    @Test
    fun restaurantDestinationMustBeKnownAndCurrentlyPermitted() {
        val permitted = setOf("Início", "Pedidos", "Cozinha")

        assertEquals(
            "Cozinha",
            StudioMesaNotificationDestinationPolicy.forRestaurant("Cozinha", permitted)
        )
        assertNull(StudioMesaNotificationDestinationPolicy.forRestaurant("Reservas", permitted))
        assertNull(StudioMesaNotificationDestinationPolicy.forRestaurant("Marketing", permitted))
    }

    @Test
    fun invalidOrBlankDestinationsAreRejected() {
        assertNull(StudioMesaNotificationDestinationPolicy.forCustomer(null))
        assertNull(StudioMesaNotificationDestinationPolicy.forCustomer(" "))
        assertNull(StudioMesaNotificationDestinationPolicy.forRestaurant("Cozinha", emptySet()))
    }
}
