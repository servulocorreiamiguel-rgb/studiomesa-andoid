package pt.studiomesa.app

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StaffPermissionSetTest {
    @Test
    fun parsesOnlyKnownPermissionsAndRemovesDuplicates() {
        assertEquals(
            linkedSetOf("orders", "reservations"),
            StaffPermissionSet.fromJson(JSONArray("[\"orders\",\"orders\",\"reservations\",\"unknown\"]"))
        )
    }

    @Test
    fun serializesPermissionsInStableProductOrder() {
        assertEquals(
            "[\"orders\",\"tables\",\"loyalty\"]",
            StaffPermissionSet.toJsonArray(setOf("loyalty", "tables", "orders")).toString()
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
            StaffPermissionSet.toJsonArray(setOf("orders", "owner_access"))
        }
    }
}
