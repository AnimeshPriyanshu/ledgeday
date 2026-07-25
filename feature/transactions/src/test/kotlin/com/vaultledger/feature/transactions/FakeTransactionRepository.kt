package com.vaultledger.feature.transactions

import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import java.util.UUID

class FakeTransactionRepository : TransactionRepository {

    private val transactions = mutableMapOf<String, Transaction>()

    private val versionFlow = MutableStateFlow(0)

    var throwOnGetTransactions: Boolean = false
    var throwOnGetBalance: Boolean = false
    var throwOnDelete: Boolean = false
    var throwOnCreate: Boolean = false
    var throwOnGetById: Boolean = false
    var throwOnUpdate: Boolean = false

    override fun getTransactionsByVaultId(vaultId: String): Flow<List<Transaction>> {
        if (throwOnGetTransactions) {
            return flow { throw RuntimeException("Failed to load transactions") }
        }
        return versionFlow.map {
            transactions.values
                .filter { it.vaultId == vaultId }
                .sortedByDescending { it.createdAt }
        }
    }

    override suspend fun getTransactionById(id: String): Transaction? {
        if (throwOnGetById) throw RuntimeException("Failed to load transaction")
        return transactions[id]
    }

    override suspend fun createTransaction(
        vaultId: String,
        type: TransactionType,
        amount: Long,
        description: String,
    ): Transaction {
        if (throwOnCreate) throw RuntimeException("Failed to create transaction")
        val txn = Transaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            type = type,
            amount = amount,
            description = description,
            createdAt = System.currentTimeMillis(),
        )
        transactions[txn.id] = txn
        versionFlow.value++
        return txn
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        if (throwOnUpdate) throw RuntimeException("Failed to update transaction")
        transactions[transaction.id] = transaction
        versionFlow.value++
    }

    override suspend fun deleteTransaction(id: String) {
        if (throwOnDelete) throw RuntimeException("Failed to delete transaction")
        transactions.remove(id)
        versionFlow.value++
    }

    override fun getVaultBalance(vaultId: String): Flow<Long> {
        if (throwOnGetBalance) {
            return flow { throw RuntimeException("Failed to load balance") }
        }
        return versionFlow.map {
            transactions.values
                .filter { it.vaultId == vaultId }
                .sumOf { txn ->
                    when (txn.type) {
                        TransactionType.INFLOW -> txn.amount
                        TransactionType.OUTFLOW -> -txn.amount
                    }
                }
        }
    }

    fun addTransaction(txn: Transaction) {
        transactions[txn.id] = txn
        versionFlow.value++
    }
}
