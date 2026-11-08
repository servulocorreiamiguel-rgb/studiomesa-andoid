package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioMesaNotificationDestinationTest {
    @Test
    fun customerReservationAndRewardOpenTheirOwnAreas() {
        assertEquals(
            "Reservas",
            StudioMesaNotificationDestination.pageFor(true, "customer:reservation:123")
        )
        assertEquals(
            "Cartão",
            StudioMesaNotificationDestination.pageFor(true, "customer:reward:456")
        )
    }

    @Test
    fun restaurantNotificationOnlyOpensVisibleAuthorizedPages() {
        val visible = setOf("Cozinha", "Reservas", "Pontos")
        assertEquals(
            "Cozinha",
            StudioMesaNotificationDestination.pageFor(false, "restaurant:order:1", visible)
        )
        assertEquals(
            "Reservas",
            StudioMesaNotificationDestination.pageFor(false, "restaurant:reservation:2", visible)
        )
        assertEquals(
            "Pontos",
            StudioMesaNotificationDestination.pageFor(false, "restaurant:redemption:3", visible)
        )
    }

    @Test
    fun staffCannotNavigateToAnAreaExcludedByTheOwnerPermissions() {
        val visible = setOf("Cozinha")
        assertNull(StudioMesaNotificationDestination.pageFor(false, "restaurant:reservation:2", visible))
        assertNull(StudioMesaNotificationDestination.pageFor(false, "restaurant:redemption:3", visible))
    }

    @Test
    fun unknownEventsNeverChooseAnArbitraryPage() {
        assertNull(StudioMesaNotificationDestination.pageFor(true, "restaurant:order:1"))
        assertNull(StudioMesaNotificationDestination.pageFor(false, "unknown:event", setOf("Início")))
    }
}
