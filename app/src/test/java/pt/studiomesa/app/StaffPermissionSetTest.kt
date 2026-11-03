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
            listOf("orders", "tables", "loyalty"),
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
    fun emptyMatrixLeavesOnlyHomeAccountAndDoesNotExposeOperationalPages() {
        val pages = listOf(
            "Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos",
            "Assistente", "Conta", "Reservas", "Preço Justo", "Stock", "Análise"
        )
        assertEquals(
            listOf("Início", "Conta"),
            StaffPermissionSet.filterPages(pages, emptySet(), isStaff = true)
        )
    }

    @Test
    fun staffNeverReceiveOwnerOnlyEconomicAndAnalysisPages() {
        val pages = listOf(
            "Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos",
            "Assistente", "Conta", "Reservas", "Preço Justo", "Stock", "Análise"
        )
        assertEquals(
            listOf("Início", "Mesas", "Pedidos", "Cozinha", "Carta", "Pontos", "Conta", "Reservas", "Stock"),
            StaffPermissionSet.filterPages(pages, StaffPermissionSet.all.toSet(), isStaff = true)
        )
    }

    @Test
    fun eachOperationalPermissionControlsItsMatchingPage() {
        val pages = listOf("Mesas", "Pedidos", "Cozinha", "Carta", "Pontos", "Reservas", "Stock")
        val cases = listOf(
            setOf("tables") to listOf("Mesas"),
            setOf("orders") to listOf("Pedidos", "Cozinha"),
            setOf("menu") to listOf("Carta"),
            setOf("loyalty") to listOf("Pontos"),
            setOf("reservations") to listOf("Reservas"),
            setOf("inventory") to listOf("Stock")
        )
        cases.forEach { (permissions, expected) ->
            assertEquals(expected, StaffPermissionSet.filterPages(pages, permissions, isStaff = true))
        }
    }

    @Test
    fun bottomNavigationOmitsAreasNotGrantedToStaff() {
        assertEquals(
            listOf("Início", "Pedidos"),
            StaffPermissionSet.bottomNavigationPages(listOf("Início", "Pedidos", "Conta"))
        )
    }

    @Test
    fun bottomNavigationKeepsAllThreeDestinationsWhenAvailable() {
        assertEquals(
            listOf("Início", "Mesas", "Pedidos"),
            StaffPermissionSet.bottomNavigationPages(
                listOf("Início", "Mesas", "Pedidos", "Conta")
            )
        )
    }

    @Test
    fun onlyOwnersCanManageRestaurantJoinCodes() {
        assertEquals(true, StaffPermissionSet.canManageRestaurantCodes("owner"))
        assertEquals(false, StaffPermissionSet.canManageRestaurantCodes("staff"))
        assertEquals(false, StaffPermissionSet.canManageRestaurantCodes(null))
        assertEquals(false, StaffPermissionSet.canManageRestaurantCodes(""))
    }

    @Test
    fun rejectsUnknownPermissionBeforeSendingItToTheServer() {
        assertThrows(IllegalArgumentException::class.java) {
            StaffPermissionSet.orderedValues(setOf("orders", "owner_access"))
        }
    }
}
