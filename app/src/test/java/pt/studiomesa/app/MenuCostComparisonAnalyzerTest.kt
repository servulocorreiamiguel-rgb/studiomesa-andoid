package pt.studiomesa.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MenuCostComparisonAnalyzerTest {
    @Test
    fun ranksItemsByHighestRecordedCostShareFirst() {
        val result = MenuCostComparisonAnalyzer.analyze(
            listOf(
                MenuCostComparisonInput("Prato A", BigDecimal("10.00"), BigDecimal("2.00")),
                MenuCostComparisonInput("Prato B", BigDecimal("8.00"), BigDecimal("4.00")),
                MenuCostComparisonInput("Prato C", BigDecimal("12.00"), BigDecimal("3.00"))
            ),
            targetPercent = BigDecimal("30")
        )

        assertEquals(listOf("Prato B", "Prato C", "Prato A"), result.rankedItems.map { it.itemName })
        assertEquals(BigDecimal("50.0"), result.rankedItems.first().costToPricePercent)
    }

    @Test
    fun flagsItemsAboveTheOwnerDefinedTarget() {
        val result = MenuCostComparisonAnalyzer.analyze(
            listOf(
                MenuCostComparisonInput("Dentro", BigDecimal("10"), BigDecimal("2.5")),
                MenuCostComparisonInput("Acima", BigDecimal("10"), BigDecimal("3.1"))
            ),
            targetPercent = BigDecimal("30")
        )

        assertEquals(1, result.aboveTargetCount)
        assertTrue(result.rankedItems.first { it.itemName == "Acima" }.aboveTarget)
        assertFalse(result.rankedItems.first { it.itemName == "Dentro" }.aboveTarget)
    }

    @Test
    fun keepsCostAboveSellingPriceVisibleAsNegativeAmount() {
        val result = MenuCostComparisonAnalyzer.analyze(
            listOf(MenuCostComparisonInput("Prato", BigDecimal("5.00"), BigDecimal("6.00"))),
            targetPercent = null
        )

        assertEquals(BigDecimal("120.0"), result.rankedItems.single().costToPricePercent)
        assertEquals(BigDecimal("-1.00"), result.rankedItems.single().amountAfterRecordedCost)
        assertEquals(1, result.costAboveSellingPriceCount)
    }

    @Test
    fun acceptsAnExplicitZeroCostAsKnown() {
        val result = MenuCostComparisonAnalyzer.analyze(
            listOf(MenuCostComparisonInput("Água", BigDecimal("1.50"), BigDecimal.ZERO)),
            targetPercent = BigDecimal("30")
        )

        assertEquals(BigDecimal("0.0"), result.rankedItems.single().costToPricePercent)
        assertEquals(BigDecimal("1.50"), result.rankedItems.single().amountAfterRecordedCost)
        assertEquals(0, result.excludedMissingCost)
    }

    @Test
    fun excludesMissingCostsAndInvalidOrZeroSellingPrices() {
        val result = MenuCostComparisonAnalyzer.analyze(
            listOf(
                MenuCostComparisonInput("Sem custo", BigDecimal("5.00"), null),
                MenuCostComparisonInput("Preço zero", BigDecimal.ZERO, BigDecimal("1.00")),
                MenuCostComparisonInput("Preço ausente", null, BigDecimal("1.00")),
                MenuCostComparisonInput("Válido", BigDecimal("5.00"), BigDecimal("1.00"))
            ),
            targetPercent = null
        )

        assertEquals(1, result.rankedItems.size)
        assertEquals(1, result.excludedMissingCost)
        assertEquals(2, result.excludedInvalidPrice)
    }

    @Test
    fun rejectsNegativeCostAndInvalidTarget() {
        assertEquals(
            1,
            MenuCostComparisonAnalyzer.analyze(
                listOf(MenuCostComparisonInput("Custo inválido", BigDecimal("5"), BigDecimal("-1"))),
                targetPercent = null
            ).excludedInvalidCost
        )
        assertThrows(IllegalArgumentException::class.java) {
            MenuCostComparisonAnalyzer.analyze(emptyList(), targetPercent = BigDecimal.ZERO)
        }
    }

    @Test
    fun rejectsTooManyMenuItemsAndInvalidNames() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuCostComparisonAnalyzer.analyze(
                (1..501).map { MenuCostComparisonInput("Prato $it", BigDecimal.ONE, BigDecimal.ZERO) },
                targetPercent = null
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuCostComparisonAnalyzer.analyze(
                listOf(MenuCostComparisonInput(" ", BigDecimal.ONE, BigDecimal.ZERO)),
                targetPercent = null
            )
        }
    }
}
