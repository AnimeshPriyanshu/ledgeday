package com.vaultledger.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatter {
    private val formatter = DateTimeFormatter
        .ofPattern("MMM d, yyyy")
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    fun format(epochMillis: Long): String {
        return formatter.format(Instant.ofEpochMilli(epochMillis))
    }
}
