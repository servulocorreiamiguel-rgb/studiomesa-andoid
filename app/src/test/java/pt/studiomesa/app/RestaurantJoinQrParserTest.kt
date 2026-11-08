package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantJoinQrParserTest {
    @Test
    fun extractsValidRestaurantInvitationCode() {
        assertEquals(
            "ABC123XY",
            RestaurantJoinQrParser.extractCode("STUDIOMESA-JOIN:abc123xy")
        )
    }

    @Test
    fun acceptsCaseInsensitivePrefixAndSurroundingWhitespace() {
        assertEquals(
            "ABC123",
            RestaurantJoinQrParser.extractCode("studiomesa-join: abc123 ")
        )
    }

    @Test
    fun rejectsLoyaltyQrAndUnrelatedPayloads() {
        assertFalse(RestaurantJoinQrParser.hasPrefix("STUDIOMESA-LOYALTY:00000000-0000-0000-0000-000000000000"))
        assertNull(RestaurantJoinQrParser.extractCode("STUDIOMESA-LOYALTY:00000000-0000-0000-0000-000000000000"))
        assertNull(RestaurantJoinQrParser.extractCode("https://example.com"))
    }

    @Test
    fun rejectsInvalidInvitationCodes() {
        assertNull(RestaurantJoinQrParser.extractCode("STUDIOMESA-JOIN:ABC"))
        assertNull(RestaurantJoinQrParser.extractCode("STUDIOMESA-JOIN:ABC123!"))
        assertNull(RestaurantJoinQrParser.extractCode("STUDIOMESA-JOIN:ABCDEFGHIJKLM"))
    }
}
