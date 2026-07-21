package com.vaultledger.ui.util

object CurrencyFormatter {
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val dollars = abs / 100
        val centsPart = abs % 100
        val formattedDollars = dollars.toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
        return "$sign$$formattedDollars.${centsPart.toString().padStart(2, '0')}"
    }
}
