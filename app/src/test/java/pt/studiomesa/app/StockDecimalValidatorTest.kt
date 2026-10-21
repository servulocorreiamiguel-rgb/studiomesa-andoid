package pt.studiomesa.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockDecimalValidatorTest {
    @Test
    fun acceptsIntegersAndWholeNumbers() {
        assertTrue(StockDecimalValidator.hasAtMostThreeDecimalPlaces("0"))
        assertTrue(StockDecimalValidator.hasAtMostThreeDecimalPlaces("123"))
    }

    @Test
    fun acceptsUpToThreeFractionalDigitsWithCommaOrDot() {
        assertTrue(StockDecimalValidator.hasAtMostThreeDecimalPlaces("1,2"))
        assertTrue(StockDecimalValidator.hasAtMostThreeDecimalPlaces("1.234"))
        assertTrue(StockDecimalValidator.hasAtMostThreeDecimalPlaces("0.001"))
    }

    @Test
    fun acceptsLeadingOrTrailingDecimalSeparatorWhenPrecisionIsValid() {
        assertTrue(StockDecimalValidator.hasAtMostThreeDecimalPlaces(".5"))
        assertTrue(StockDecimalValidator.hasAtMostThreeDecimalPlaces("1."))
    }

    @Test
    fun ignoresSurroundingWhitespace() {
        assertTrue(StockDecimalValidator.hasAtMostThreeDecimalPlaces("  1,25  "))
    }

    @Test
    fun rejectsMoreThanThreeFractionalDigits() {
        assertFalse(StockDecimalValidator.hasAtMostThreeDecimalPlaces("1.2345"))
        assertFalse(StockDecimalValidator.hasAtMostThreeDecimalPlaces("1,2345"))
    }

    @Test
    fun rejectsMultipleDecimalSeparators() {
        assertFalse(StockDecimalValidator.hasAtMostThreeDecimalPlaces("1.2.3"))
        assertFalse(StockDecimalValidator.hasAtMostThreeDecimalPlaces("1,2,3"))
    }
}
