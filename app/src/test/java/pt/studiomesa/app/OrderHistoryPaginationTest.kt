package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderHistoryPaginationTest {
    @Test
    fun fetchesPagesUntilTheFirstShortPageAndPreservesOrder() {
        val offsets = mutableListOf<Pair<Int, Int>>()
        val result = OrderHistoryPagination.collect(pageSize = 2, maxPages = 4) { offset, limit ->
            offsets += offset to limit
            when (offset) {
                0 -> listOf("a", "b")
                2 -> listOf("c", "d")
                4 -> listOf("e")
                else -> error("Unexpected page offset: $offset")
            }
        }

        assertEquals(listOf("a", "b", "c", "d", "e"), result)
        assertEquals(listOf(0 to 2, 2 to 2, 4 to 2), offsets)
    }

    @Test
    fun exactMaximumHistoryUsesAnEmptyProbeInsteadOfFailing() {
        val offsets = mutableListOf<Int>()
        val result = OrderHistoryPagination.collect(pageSize = 2, maxPages = 2) { offset, _ ->
            offsets += offset
            when (offset) {
                0 -> listOf("a", "b")
                2 -> listOf("c", "d")
                4 -> emptyList()
                else -> error("Unexpected page offset: $offset")
            }
        }

        assertEquals(listOf("a", "b", "c", "d"), result)
        assertEquals(listOf(0, 2, 4), offsets)
    }

    @Test
    fun refusesToReturnPartialHistoryWhenMoreRowsRemainAfterTheLimit() {
        val offsets = mutableListOf<Int>()
        val error = assertThrows(IllegalStateException::class.java) {
            OrderHistoryPagination.collect(pageSize = 2, maxPages = 2) { offset, _ ->
                offsets += offset
                when (offset) {
                    0 -> listOf("a", "b")
                    2 -> listOf("c", "d")
                    4 -> listOf("e")
                    else -> emptyList()
                }
            }
        }

        assertTrue(error.message.orEmpty().contains("mais de 4 pedidos"))
        assertTrue(error.message.orEmpty().contains("evitar apresentar uma análise parcial"))
        assertEquals(listOf(0, 2, 4), offsets)
    }

    @Test
    fun emptyHistoryReturnsImmediately() {
        var requestedPages = 0
        val result = OrderHistoryPagination.collect(pageSize = 500, maxPages = 20) { _, _ ->
            requestedPages++
            emptyList<String>()
        }
        assertTrue(result.isEmpty())
        assertEquals(1, requestedPages)
    }

    @Test
    fun rejectsInvalidLimitsAndUnexpectedOversizedPage() {
        assertThrows(IllegalArgumentException::class.java) {
            OrderHistoryPagination.collect(pageSize = 0, maxPages = 1) { _, _ -> emptyList<String>() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrderHistoryPagination.collect(pageSize = 1, maxPages = 0) { _, _ -> emptyList<String>() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrderHistoryPagination.collect(pageSize = 1, maxPages = 1) { _, _ -> listOf("a", "b") }
        }
    }
}
