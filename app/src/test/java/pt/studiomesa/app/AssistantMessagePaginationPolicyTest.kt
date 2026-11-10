package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMessagePaginationPolicyTest {
    @Test
    fun keepsPageAndTotalLimitsWithinTheJsonResponseBudget() {
        assertEquals(200, AssistantMessagePaginationPolicy.PAGE_SIZE)
        assertEquals(5_000, AssistantMessagePaginationPolicy.MAX_MESSAGES)
        assertTrue(AssistantMessagePaginationPolicy.PAGE_SIZE * 6_000 < 2 * 1024 * 1024)
    }

    @Test
    fun advancesOnlyAfterAFullPage() {
        assertEquals(
            200,
            AssistantMessagePaginationPolicy.nextOffset(
                currentOffset = 0,
                requestedLimit = 200,
                returnedRows = 200,
                totalRows = 200
            )
        )
    }

    @Test
    fun stopsAfterShortFinalPage() {
        assertNull(
            AssistantMessagePaginationPolicy.nextOffset(
                currentOffset = 200,
                requestedLimit = 200,
                returnedRows = 37,
                totalRows = 237
            )
        )
    }

    @Test
    fun stopsAtTheFiveThousandMessageSafetyCap() {
        assertNull(
            AssistantMessagePaginationPolicy.nextOffset(
                currentOffset = 4_800,
                requestedLimit = 200,
                returnedRows = 200,
                totalRows = AssistantMessagePaginationPolicy.MAX_MESSAGES
            )
        )
    }

    @Test
    fun supportsTheShortFinalPageAtTheConfiguredCap() {
        assertNull(
            AssistantMessagePaginationPolicy.nextOffset(
                currentOffset = 4_800,
                requestedLimit = 200,
                returnedRows = 199,
                totalRows = 4_999
            )
        )
    }

    @Test
    fun rejectsInvalidPaginationInputs() {
        val invalid = listOf(
            Triple(-1, 200, 200),
            Triple(0, 0, 0),
            Triple(0, 201, 201),
            Triple(0, 200, 201)
        )
        invalid.forEach { (offset, limit, rows) ->
            try {
                AssistantMessagePaginationPolicy.nextOffset(
                    currentOffset = offset,
                    requestedLimit = limit,
                    returnedRows = rows,
                    totalRows = 0
                )
                throw AssertionError("A paginação devia rejeitar offset=$offset, limit=$limit, rows=$rows")
            } catch (_: IllegalArgumentException) {
                // Expected: no invalid offset/limit can start another request.
            }
        }
    }
}
