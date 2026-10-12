package com.sypos.mobile.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyUtils {
    /**
     * Formats an amount according to the provided currency string.
     * Uses France locale grouping (space separator) by default.
     */
    fun Double.formatAmount(currency: String): String {
        val symbols = DecimalFormatSymbols(Locale.FRANCE)
        val hasDecimals = this % 1.0 != 0.0
        
        // Some currencies conventionally use symbols before the amount or have different formats,
        // but for simplicity and uniformity across the POS app, we append it.
        val pattern = if (hasDecimals) {
            "#,##0.00"
        } else {
            "#,##0"
        }
        
        val df = DecimalFormat(pattern, symbols)
        return "${df.format(this)} $currency"
    }
}
