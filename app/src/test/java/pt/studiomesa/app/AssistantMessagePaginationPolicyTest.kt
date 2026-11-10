package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMessagePaginationPolicyTest {
    @Test
    fun keepsPageAndTotalLimitsWithinTheJsonResponseBudget() {
        assertEquals(100, AssistantMessagePaginationPolicy.PAGE_SIZE)
        assertEquals(5_000, AssistantMessagePaginationPolicy.MAX_MESSAGES)
        assertTrue(AssistantMessagePaginationPolicy.PAGE_SIZE * 6_000 * 6 + AssistantMessagePaginationPolicy.PAGE_SIZE * 1024 < 4 * 1024 * 1024)
    }

    @Test
    fun advancesOnlyAfterAFullPage() {
        assertEquals(
            100,
            AssistantMessagePaginationPolicy.nextOffset(
                currentOffset = 0,
                requestedLimit = 100,
                returnedRows = 100,
                totalRows = 100
            )
        )
    }

    @Test
    fun stopsAfterShortFinalPage() {
        assertNull(
            AssistantMessagePaginationPolicy.nextOffset(
                currentOffset = 100,
                requestedLimit = 100,
                returnedRows = 37,
                totalRows = 137
            )
        )
    }

    @Test
    fun stopsAtTheFiveThousandMessageSafetyCap() {
        assertNull(
            AssistantMessagePaginationPolicy.nextOffset(
                currentOffset = 4_900,
                requestedLimit = 100,
                returnedRows = 100,
                totalRows = AssistantMessagePaginationPolicy.MAX_MESSAGES
            )
        )
    }

    @Test
    fun supportsTheShortFinalPageAtTheConfiguredCap() {
        assertNull(
            AssistantMessagePaginationPolicy.nextOffset(
                currentOffset = 4_900,
                requestedLimit = 100,
                returnedRows = 99,
                totalRows = 4_999
            )
        )
    }

    @Test
    fun rejectsInvalidPaginationInputs() {
        val invalid = listOf(
            Triple(-1, 100, 100),
            Triple(0, 0, 0),
            Triple(0, 101, 101),
            Triple(0, 100, 101)
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
