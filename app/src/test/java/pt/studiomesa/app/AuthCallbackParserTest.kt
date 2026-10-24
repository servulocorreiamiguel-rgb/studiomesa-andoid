package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthCallbackParserTest {
    @Test
    fun parsesCodesAndEncodedErrorText() {
        val params = AuthCallbackParser.parse("code=abc_123-xyz&error_description=login+failed%3A+retry")

        assertEquals("abc_123-xyz", params["code"])
        assertEquals("login failed: retry", params["error_description"])
    }

    @Test
    fun ignoresMalformedComponentsWithoutDiscardingValidCode() {
        val params = AuthCallbackParser.parse("%ZZ=broken&code=valid_code&error=bad%2Gvalue")

        assertEquals("valid_code", params["code"])
        assertFalse(params.containsKey("%ZZ"))
        assertFalse(params.containsKey("error"))
    }

    @Test
    fun ignoresComponentsWithoutEqualsAndEmptyKeys() {
        val params = AuthCallbackParser.parse("code=valid&orphan&=empty-key&")

        assertEquals(mapOf("code" to "valid"), params)
    }

    @Test
    fun oversizedCallbackIsRejectedBeforeParsing() {
        val raw = "code=" + "x".repeat(8192)

        assertEquals(emptyMap<String, String>(), AuthCallbackParser.parse(raw))
    }

    @Test
    fun callbackWithTooManyParametersIsRejected() {
        val raw = (0 until 33).joinToString("&") { "p$it=value" }

        assertEquals(emptyMap<String, String>(), AuthCallbackParser.parse(raw))
    }

    @Test
    fun oversizedParameterIsIgnored() {
        val raw = "code=" + "x".repeat(4092)

        assertEquals(emptyMap<String, String>(), AuthCallbackParser.parse(raw))
    }
    @Test
    fun nullOrEmptyInputReturnsNoParameters() {
        assertEquals(emptyMap<String, String>(), AuthCallbackParser.parse(null))
        assertEquals(emptyMap<String, String>(), AuthCallbackParser.parse(""))
    }
}
