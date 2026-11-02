package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StaffPermissionSetTest {
    @Test
    fun parsesOnlyKnownPermissionsAndRemovesDuplicates() {
        assertEquals(
            linkedSetOf("orders", "reservations"),
            StaffPermissionSet.fromValues(listOf("orders", "orders", "reservations", "unknown"))
        )
    }

    @Test
    fun serializesPermissionsInStableProductOrder() {
        assertEquals(
            "[\"orders\",\"tables\",\"loyalty\"]",
            StaffPermissionSet.orderedValues(setOf("loyalty", "tables", "orders"))
        )
    }

    @Test
    fun staffOnlySeeOperationalPagesForEnabledAreas() {
        val pages = listOf(
            "Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos",
            "Assistente", "Conta", "Reservas", "Preço Justo", "Stock", "Análise"
        )
        assertEquals(
            listOf("Início", "Pedidos", "Cozinha", "Conta", "Reservas"),
            StaffPermissionSet.filterPages(pages, setOf("orders", "reservations"), isStaff = true)
        )
    }

    @Test
    fun ownerNavigationIsNotFilteredByStaffPermissions() {
        val pages = listOf("Início", "Mesas", "Pedidos", "Assistente", "Conta")
        assertEquals(
            pages,
            StaffPermissionSet.filterPages(pages, emptySet(), isStaff = false)
        )
    }

    @Test
    fun rejectsUnknownPermissionBeforeSendingItToTheServer() {
        assertThrows(IllegalArgumentException::class.java) {
            StaffPermissionSet.orderedValues(setOf("orders", "owner_access"))
        }
    }
}
