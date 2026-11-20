package com.tm.meow.sudoku.puzzle.domain.billing

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Display formatting for Remove Ads prices: drops an all-zero fraction ("560.00" -> "560", "4,00 €" -> "4 €").
 * Never changes a price that has a non-zero fraction. Pure java.text / java.util (no Android).
 * The value shown is still Play's price; only the zero fraction is trimmed.
 */
object PriceFormatter {

    /** Zero fraction group "[.,]00" after a digit, not followed by another digit (so "1,000" / "5.000" are untouched). */
    private val ZERO_FRACTION = Regex("(?<=\\d)[.,]00(?!\\d)")

    /**
     * ISO code -> short local symbol, used when Play / the device locale prints the bare ISO code ("PKR 560").
     * Only codes whose local symbol is unambiguous are listed. Dollar-family codes (USD, CAD, AUD...) are
     * left alone on purpose: "$" would be ambiguous outside the home locale.
     */
    private val SYMBOLS: Map<String, String> = mapOf(
        "PKR" to "Rs", "LKR" to "Rs", "NPR" to "Rs", "INR" to "₹", "BDT" to "৳",
        "IDR" to "Rp", "PHP" to "₱", "VND" to "₫", "THB" to "฿", "MYR" to "RM",
        "KRW" to "₩", "ILS" to "₪", "TRY" to "₺", "RUB" to "₽", "UAH" to "₴",
        "NGN" to "₦", "KES" to "KSh", "ZAR" to "R", "EGP" to "E£", "GHS" to "GH₵",
        "PLN" to "zł", "CZK" to "Kč", "SEK" to "kr", "NOK" to "kr", "DKK" to "kr",
        "BRL" to "R$", "MXN" to "MX$", "JPY" to "¥", "CNY" to "¥", "EUR" to "€", "GBP" to "£",
    )

    fun trimZeroFraction(price: ProductPrice, locale: Locale = Locale.getDefault()): String =
        localizeCurrencySymbol(trimZero(price, locale), price.currencyCode)

    /** Replaces the bare ISO [code] (whole token, not part of a longer word) with its short symbol. */
    private fun localizeCurrencySymbol(text: String, code: String): String {
        val symbol = SYMBOLS[code] ?: return text
        return text.replace(Regex("(?<![A-Za-z])" + Regex.escape(code) + "(?![A-Za-z])"), symbol)
    }

    private fun trimZero(price: ProductPrice, locale: Locale): String {
        val micros = price.priceMicros
        if (micros > 0L && price.currencyCode.isNotEmpty()) {
            // Fractional amount: keep Play's string untouched.
            if (micros % 1_000_000L != 0L) return price.formattedPrice
            try {
                val nf = NumberFormat.getCurrencyInstance(locale)
                nf.currency = Currency.getInstance(price.currencyCode)
                nf.minimumFractionDigits = 0
                nf.maximumFractionDigits = 0
                return nf.format(micros / 1_000_000L)
            } catch (_: IllegalArgumentException) {
                // unknown currency code -> regex fallback below
            }
        }
        return price.formattedPrice.replace(ZERO_FRACTION, "")
    }
}
