package pt.studiomesa.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ExactMathExpressionEvaluatorTest {
    @Test
    fun respectsArithmeticPrecedenceAndParentheses() {
        assertEquals("14", ExactMathExpressionEvaluator.evaluate("2 + 3 * 4").resultText())
        assertEquals("20", ExactMathExpressionEvaluator.evaluate("(2 + 3) * 4").resultText())
        assertEquals("2", ExactMathExpressionEvaluator.evaluate("18 / 3 - 4").resultText())
    }

    @Test
    fun supportsNegativeValuesAndPortugueseDecimalCommas() {
        assertEquals("-5", ExactMathExpressionEvaluator.evaluate("-(2 + 3)").resultText())
        assertEquals("3.75", ExactMathExpressionEvaluator.evaluate("1,5 + 2,25").resultText())
        assertEquals("0", ExactMathExpressionEvaluator.evaluate("-0,000").resultText())
    }

    @Test
    fun divisionUsesDocumentedTwelveDecimalPlaces() {
        assertEquals(
            "0.333333333333",
            ExactMathExpressionEvaluator.evaluate("1 / 3").resultText()
        )
    }

    @Test
    fun rejectsDivisionByZeroMalformedInputAndUnsupportedOperators() {
        listOf(
            "",
            "1 / 0",
            "2 +",
            "(2 + 3",
            "2 ^ 3",
            "System.exit(0)",
            "1 + unknown"
        ).forEach(::assertInvalid)
    }

    @Test
    fun rejectsExcessiveInputPrecisionMagnitudeAndOperationCount() {
        assertInvalid("1".repeat(ExactMathExpressionEvaluator.MAX_EXPRESSION_CHARS + 1))
        assertInvalid("1." + "2".repeat(13))
        assertInvalid("9999999999999999999")
        assertInvalid(List(70) { "1" }.joinToString("+"))
    }

    private fun assertInvalid(expression: String) {
        try {
            ExactMathExpressionEvaluator.evaluate(expression)
            throw AssertionError("A expressão devia ter sido rejeitada: $expression")
        } catch (_: IllegalArgumentException) {
            // Expected: invalid input is rejected without executing anything.
        }
    }
}
