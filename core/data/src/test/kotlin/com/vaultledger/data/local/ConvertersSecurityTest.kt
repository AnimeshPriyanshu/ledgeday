package com.vaultledger.data.local

import com.vaultledger.domain.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConvertersSecurityTest {

    private val converters = Converters()

    @Test
    fun `safe TransactionType conversion falls back to INFLOW for unknown values`() {
        assertEquals(TransactionType.INFLOW, converters.toTransactionType("INFLOW"))
        assertEquals(TransactionType.OUTFLOW, converters.toTransactionType("OUTFLOW"))
        assertEquals(TransactionType.INFLOW, converters.toTransactionType("UNKNOWN"))
        assertEquals(TransactionType.INFLOW, converters.toTransactionType(""))
        assertEquals(TransactionType.INFLOW, converters.toTransactionType("inflow"))
        assertEquals(TransactionType.INFLOW, converters.toTransactionType(null as String? ?: ""))
    }

    @Test
    fun `safe TransactionType round-trip preserves known values`() {
        assertEquals("INFLOW", converters.fromTransactionType(TransactionType.INFLOW))
        assertEquals("OUTFLOW", converters.fromTransactionType(TransactionType.OUTFLOW))
    }
}
