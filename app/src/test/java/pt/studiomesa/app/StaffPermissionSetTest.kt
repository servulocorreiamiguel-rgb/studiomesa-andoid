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
    fun rejectsUnknownPermissionBeforeSendingItToTheServer() {
        assertThrows(IllegalArgumentException::class.java) {
            StaffPermissionSet.toJsonArray(setOf("orders", "owner_access"))
        }
    }
}
