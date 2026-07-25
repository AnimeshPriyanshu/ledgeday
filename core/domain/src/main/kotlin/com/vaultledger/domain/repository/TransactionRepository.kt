package com.vaultledger.domain.repository

import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getTransactionsByVaultId(vaultId: String): Flow<List<Transaction>>
    suspend fun getTransactionById(id: String): Transaction?
    suspend fun createTransaction(
        vaultId: String,
        type: TransactionType,
        amount: Long,
        description: String,
    ): Transaction
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: String)
    fun getVaultBalance(vaultId: String): Flow<Long>
    fun searchTransactions(vaultId: String, query: String): Flow<List<Transaction>>
}
