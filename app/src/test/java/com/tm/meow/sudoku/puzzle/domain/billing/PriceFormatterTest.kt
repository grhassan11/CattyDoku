package com.tm.meow.sudoku.puzzle.domain.billing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PriceFormatterTest {

    private fun p(text: String, micros: Long = 0L, code: String = "") = ProductPrice("id", text, micros, code)
    private fun norm(s: String) = s.replace('\u00A0', ' ').replace('\u202F', ' ')

    @Test
    fun inrWholeAmountDropsFraction() {
        val out = PriceFormatter.trimZeroFraction(p("₹560.00", 560_000_000L, "INR"), Locale.forLanguageTag("en-IN"))
        assertEquals("₹560", out)
    }

    @Test
    fun usdWholeAmount() {
        assertEquals("$5", PriceFormatter.trimZeroFraction(p("$5.00", 5_000_000L, "USD"), Locale.US))
    }

    @Test
    fun usdFractionUntouched() {
        assertEquals("$5.49", PriceFormatter.trimZeroFraction(p("$5.49", 5_490_000L, "USD"), Locale.US))
    }

    @Test
    fun commaDecimalLocale() {
        val out = PriceFormatter.trimZeroFraction(p("4,00 €", 4_000_000L, "EUR"), Locale.GERMANY)
        assertEquals("4 €", norm(out))
    }

    @Test
    fun jpyZeroDigitCurrency() {
        val out = PriceFormatter.trimZeroFraction(p("¥500", 500_000_000L, "JPY"), Locale.JAPAN)
        assertEquals("500", out.filter { it.isDigit() })
    }

    @Test
    fun threeDigitCurrencyFractionKept() {
        assertEquals("KWD 5.500", PriceFormatter.trimZeroFraction(p("KWD 5.500", 5_500_000L, "KWD"), Locale.US))
    }

    @Test
    fun fallbackRegexStripsDotZeros() {
        assertEquals("Rs 270", PriceFormatter.trimZeroFraction(p("Rs 270.00")))
        assertEquals("$5", PriceFormatter.trimZeroFraction(p("$5.00")))
    }

    @Test
    fun fallbackRegexStripsCommaZerosEmbedded() {
        assertEquals("4 €", PriceFormatter.trimZeroFraction(p("4,00 €")))
        assertEquals("1.000 €", PriceFormatter.trimZeroFraction(p("1.000,00 €")))
    }

    @Test
    fun fallbackLeavesFractionsAndThousandGroups() {
        assertEquals("$5.49", PriceFormatter.trimZeroFraction(p("$5.49")))
        assertEquals("$1,000", PriceFormatter.trimZeroFraction(p("$1,000")))
        assertEquals("KWD 5.000", PriceFormatter.trimZeroFraction(p("KWD 5.000")))
    }

    @Test
    fun badCurrencyCodeFallsBackToRegex() {
        assertEquals("5 X", PriceFormatter.trimZeroFraction(p("5.00 X", 5_000_000L, "??"), Locale.US))
    }
}
