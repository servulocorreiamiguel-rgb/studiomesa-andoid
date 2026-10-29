package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RestaurantAdvisorEngineTest {
    @Test
    fun flagsItemsAboveTheConfiguredDirectCostTargetAndCalculatesIndicativePrice() {
        val report = RestaurantAdvisorEngine.analyzeMenu(
            listOf(MenuEntry("Prato do dia", 10.00, 4.00)),
            "30"
        )
        val advice = report.items.single()

        assertEquals(BigDecimal("30.00"), report.targetCostPercent)
        assertEquals(MenuPriceAdviceStatus.REVIEW, advice.status)
        assertEquals(BigDecimal("40.00"), advice.directCostPercent)
        assertEquals(BigDecimal("13.33"), advice.indicativePriceAtTarget)
        assertEquals(1, report.itemsNeedingReview)
        assertTrue(advice.explanation.contains("acima do objetivo"))
    }

    @Test
    fun flagsPriceAtOrBelowRecordedUnitCostAsUrgent() {
        val advice = RestaurantAdvisorEngine.analyzeMenu(
            listOf(MenuEntry("Prato", 3.20, 3.20)),
            "30"
        ).items.single()

        assertEquals(MenuPriceAdviceStatus.URGENT_REVIEW, advice.status)
        assertEquals(BigDecimal("100.00"), advice.directCostPercent)
        assertTrue(advice.explanation.contains("não cobre o custo unitário"))
    }

    @Test
    fun recognizesItemsInsideTheConfiguredTargetWithoutClaimingTheyAreProfitable() {
        val advice = RestaurantAdvisorEngine.analyzeMenu(
            listOf(MenuEntry("Bebida", 10.00, 2.00)),
            "30"
        ).items.single()

        assertEquals(MenuPriceAdviceStatus.WITHIN_TARGET, advice.status)
        assertEquals(BigDecimal("20.00"), advice.directCostPercent)
        assertTrue(advice.explanation.contains("não garante rentabilidade"))
    }

    @Test
    fun marksMissingZeroOrInvalidValuesAsIncompleteInsteadOfGuessing() {
        val report = RestaurantAdvisorEngine.analyzeMenu(
            listOf(
                MenuEntry("Sem custo", 8.00, 0.00),
                MenuEntry("Sem preço", 0.00, 2.00),
                MenuEntry("Custo inválido", 8.00, Double.NaN),
                MenuEntry("Preço inválido", Double.POSITIVE_INFINITY, 2.00)
            ),
            "30"
        )

        assertTrue(report.items.all { it.status == MenuPriceAdviceStatus.INCOMPLETE_DATA })
        assertEquals(4, report.itemsWithIncompleteData)
        assertNull(report.items[0].indicativePriceAtTarget)
        assertEquals(BigDecimal("6.67"), report.items[1].indicativePriceAtTarget)
        assertTrue(report.items[2].directCostPercent == null)
    }

    @Test
    fun acceptsPortugueseDecimalCommaForTarget() {
        val report = RestaurantAdvisorEngine.analyzeMenu(
            listOf(MenuEntry("Prato", 10.00, 2.55)),
            "25,5"
        )

        assertEquals(BigDecimal("25.50"), report.targetCostPercent)
        assertEquals(BigDecimal("10.00"), report.items.single().indicativePriceAtTarget)
        assertEquals(MenuPriceAdviceStatus.WITHIN_TARGET, report.items.single().status)
    }

    @Test
    fun rejectsEmptyMalformedOutOfRangeAndOverlongTargets() {
        listOf("", "abc", "0", "-5", "100,01", "1000", "30.123").forEach { target ->
            assertThrows(IllegalArgumentException::class.java) {
                RestaurantAdvisorEngine.analyzeMenu(
                    listOf(MenuEntry("Prato", 10.00, 2.00)),
                    target
                )
            }
        }
    }

    @Test
    fun comparesAgainstTheExactRatioInsteadOfRoundedDisplayPercent() {
        val aboveTarget = RestaurantAdvisorEngine.analyzeMenu(
            listOf(MenuEntry("Ligeiramente acima", 100.00, 30.04)),
            "30"
        ).items.single()
        assertEquals(MenuPriceAdviceStatus.REVIEW, aboveTarget.status)
        assertEquals(BigDecimal("30.04"), aboveTarget.directCostPercent)

        val exactlyOnTarget = RestaurantAdvisorEngine.analyzeMenu(
            listOf(MenuEntry("No objetivo", 100.00, 30.00)),
            "30"
        ).items.single()
        assertEquals(MenuPriceAdviceStatus.WITHIN_TARGET, exactlyOnTarget.status)
        assertEquals(BigDecimal("30.00"), exactlyOnTarget.directCostPercent)

        val belowTarget = RestaurantAdvisorEngine.analyzeMenu(
            listOf(MenuEntry("Ligeiramente abaixo", 100.00, 29.96)),
            "30"
        ).items.single()
        assertEquals(MenuPriceAdviceStatus.WITHIN_TARGET, belowTarget.status)
        assertEquals(BigDecimal("29.96"), belowTarget.directCostPercent)
    }

    @Test
    fun keepsTheServerMenuItemIdWithItsPriceRecommendation() {
        val itemId = "12345678-1234-4234-8234-123456789abc"
        val advice = RestaurantAdvisorEngine.analyzeMenu(
            listOf(MenuEntry("Prato", 10.00, 4.00, id = itemId)),
            "30"
        ).items.single()

        assertEquals(itemId, advice.itemId)
        assertEquals(MenuPriceAdviceStatus.REVIEW, advice.status)
    }

    @Test
    fun returnsAnEmptyReportForAnEmptyMenu() {
        val report = RestaurantAdvisorEngine.analyzeMenu(emptyList(), "30")

        assertTrue(report.items.isEmpty())
        assertEquals(0, report.itemsNeedingReview)
        assertEquals(0, report.itemsWithIncompleteData)
    }

    @Test
    fun analysisDoesNotChangeAnyMenuEntry() {
        val original = listOf(
            MenuEntry("Prato A", 10.00, 4.00),
            MenuEntry("Prato B", 12.00, 2.00)
        )
        val before = original.toList()

        RestaurantAdvisorEngine.analyzeMenu(original, "30")

        assertEquals(before, original)
        assertFalse(original.isEmpty())
    }
}
