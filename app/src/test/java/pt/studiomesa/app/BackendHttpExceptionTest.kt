package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendHttpExceptionTest {
    @Test
    fun preservesStatusEndpointAndSanitizedDisplayMessage() {
        val error = BackendHttpException(
            statusCode = 403,
            endpointPath = "/rest/v1/restaurant_orders",
            message = "Acesso recusado."
        )

        assertEquals(403, error.statusCode)
        assertEquals("/rest/v1/restaurant_orders", error.endpointPath)
        assertEquals("Acesso recusado.", error.message)
    }
}
