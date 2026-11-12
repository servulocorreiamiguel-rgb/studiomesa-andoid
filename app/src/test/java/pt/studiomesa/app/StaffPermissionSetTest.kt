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
            "IA", "Assistente económico", "Conta", "Reservas", "Preço Justo", "Stock", "Análise"
        )
        assertEquals(
            listOf("Início", "Pedidos", "Cozinha", "Conta", "Reservas"),
            StaffPermissionSet.filterPages(pages, setOf("orders", "reservations"), isStaff = true)
        )
    }

    @Test
    fun ownerNavigationIsNotFilteredByStaffPermissions() {
        val pages = listOf("Início", "Mesas", "Pedidos", "IA", "Assistente económico", "Conta")
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

    @Test
    fun orderCreationRequiresOrderMenuAndTableOrReservationReadAccessForStaff() {
        assertEquals(true, StaffPermissionSet.canCreateOrder(
            setOf("orders", "menu", "tables"), isStaff = true
        ))
        assertEquals(true, StaffPermissionSet.canCreateOrder(
            setOf("orders", "menu", "reservations"), isStaff = true
        ))
        assertEquals(false, StaffPermissionSet.canCreateOrder(
            setOf("orders", "tables"), isStaff = true
        ))
        assertEquals(false, StaffPermissionSet.canCreateOrder(
            setOf("orders", "menu"), isStaff = true
        ))
        assertEquals(false, StaffPermissionSet.canCreateOrder(
            setOf("menu", "tables"), isStaff = true
        ))
        assertEquals(true, StaffPermissionSet.canCreateOrder(emptySet(), isStaff = false))
    }

    @Test
    fun remoteCacheLoadsOnlyTheAreasPermittedByTheStaffMatrix() {
        assertEquals(
            setOf("tables", "orders"),
            StaffPermissionSet.cacheReadPermissions(setOf("orders", "reservations"), isStaff = true)
        )
        assertEquals(
            setOf("menu", "tables"),
            StaffPermissionSet.cacheReadPermissions(setOf("menu", "tables"), isStaff = true)
        )
        assertEquals(
            StaffPermissionSet.all.toSet(),
            StaffPermissionSet.cacheReadPermissions(emptySet(), isStaff = false)
        )
        assertEquals(
            emptySet<String>(),
            StaffPermissionSet.cacheReadPermissions(emptySet(), isStaff = true)
        )
    }
}