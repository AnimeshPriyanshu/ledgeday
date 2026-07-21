package com.vaultledger.domain.usecase

import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType

class CalculateBalanceUseCase {

    operator fun invoke(transactions: List<Transaction>): Long {
        var balance = 0L
        for (tx in transactions) {
            when (tx.type) {
                TransactionType.INFLOW -> balance += tx.amount
                TransactionType.OUTFLOW -> balance -= tx.amount
            }
        }
        return balance
    }
}
