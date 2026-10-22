package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PendingRequestKeyTest {
    @Test
    fun sameOperationAccountAndPayloadProduceTheSameKey() {
        assertEquals(
            PendingRequestKey.from("order_create", "account-1", "{\"table\":2}"),
            PendingRequestKey.from("order_create", "account-1", "{\"table\":2}")
        )
    }

    @Test
    fun accountAndOperationArePartOfTheKey() {
        val original = PendingRequestKey.from("order_create", "account-1", "{\"table\":2}")
        assertNotEquals(original, PendingRequestKey.from("order_create", "account-2", "{\"table\":2}"))
        assertNotEquals(original, PendingRequestKey.from("reservation_create", "account-1", "{\"table\":2}"))
        assertNotEquals(original, PendingRequestKey.from("order_create", "account-1", "{\"table\":3}"))
    }

    @Test
    fun keyDoesNotPersistRawPayloadText() {
        val sensitivePayload = "{\"guest_name\":\"Test Customer\",\"phone\":\"+351910000000\"}"
        val key = PendingRequestKey.from("reservation_create", "account-1", sensitivePayload)
        assertFalse(key.contains("Test Customer"))
        assertFalse(key.contains("910000000"))
        assertEquals(72, key.length) // "request_" + a 64-character SHA-256 digest.
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankAccountIsRejected() {
        PendingRequestKey.from("order_create", " ", "{}")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidOperationIsRejected() {
        PendingRequestKey.from("Order Create", "account-1", "{}")
    }
}
