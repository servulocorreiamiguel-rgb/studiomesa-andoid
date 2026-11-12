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
    fun unknownRemoteRolesAreRestrictedButLocalAndOwnerNavigationIsPreserved() {
        assertEquals(true, StaffPermissionSet.shouldRestrictNavigation(true, "staff"))
        assertEquals(true, StaffPermissionSet.shouldRestrictNavigation(true, "unknown"))
        assertEquals(false, StaffPermissionSet.shouldRestrictNavigation(true, "owner"))
        assertEquals(false, StaffPermissionSet.shouldRestrictNavigation(false, ""))
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
            setOf("orders", "menu", "tables"), role = "staff"
        ))
        assertEquals(true, StaffPermissionSet.canCreateOrder(
            setOf("orders", "menu", "reservations"), role = "staff"
        ))
        assertEquals(false, StaffPermissionSet.canCreateOrder(
            setOf("orders", "tables"), role = "staff"
        ))
        assertEquals(false, StaffPermissionSet.canCreateOrder(
            setOf("orders", "menu"), role = "staff"
        ))
        assertEquals(false, StaffPermissionSet.canCreateOrder(
            setOf("menu", "tables"), role = "staff"
        ))
        assertEquals(true, StaffPermissionSet.canCreateOrder(emptySet(), role = "owner"))
        assertEquals(false, StaffPermissionSet.canCreateOrder(StaffPermissionSet.all.toSet(), role = "unknown"))
    }

    @Test
    fun remoteCacheLoadsOnlyTheAreasPermittedByTheStaffMatrix() {
        assertEquals(
            setOf("tables", "orders"),
            StaffPermissionSet.cacheReadPermissions("staff", setOf("orders", "reservations"))
        )
        assertEquals(
            setOf("menu", "tables"),
            StaffPermissionSet.cacheReadPermissions("staff", setOf("menu", "tables"))
        )
        assertEquals(
            StaffPermissionSet.all.toSet(),
            StaffPermissionSet.cacheReadPermissions("owner", emptySet())
        )
        assertEquals(
            emptySet<String>(),
            StaffPermissionSet.cacheReadPermissions("staff", emptySet())
        )
        assertEquals(
            emptySet<String>(),
            StaffPermissionSet.cacheReadPermissions("unknown", StaffPermissionSet.all.toSet())
        )
    }
}