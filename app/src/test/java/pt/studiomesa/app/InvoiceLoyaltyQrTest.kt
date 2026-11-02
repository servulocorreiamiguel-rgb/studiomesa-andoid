package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceLoyaltyQrTest {
    private val token = "8d4caa12-824f-4fd6-8f7c-8c0c7d3b6e21"

    @Test
    fun payloadRoundTripsCanonicalToken() {
        val payload = InvoiceLoyaltyQr.payloadForToken(token)
        assertEquals("STUDIOMESA-LOYALTY-INVOICE:$token", payload)
        assertEquals(token, InvoiceLoyaltyQr.parseToken(payload))
    }

    @Test
    fun parserRejectsFiscalOrUnrelatedQrPayloads() {
        assertNull(InvoiceLoyaltyQr.parseToken("https://example.com/fatura"))
        assertNull(InvoiceLoyaltyQr.parseToken("PT:ATCUD:123"))
        assertNull(InvoiceLoyaltyQr.parseToken(null))
    }

    @Test
    fun parserRejectsMalformedTokens() {
        assertNull(InvoiceLoyaltyQr.parseToken("STUDIOMESA-LOYALTY-INVOICE:not-a-uuid"))
        assertNull(InvoiceLoyaltyQr.parseToken("STUDIOMESA-LOYALTY-INVOICE:$token-extra"))
    }

    @Test
    fun payloadGeneratorRequiresAUuid() {
        val rejected = runCatching { InvoiceLoyaltyQr.payloadForToken("not-a-uuid") }.isFailure
        assertTrue(rejected)
    }
}
