package com.vaultledger.data.repository

import androidx.room.withTransaction
import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.local.dao.TransactionDao
import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val vaultDao: VaultDao,
    private val database: VaultLedgerDatabase,
) : TransactionRepository {

    override fun getTransactionsByVaultId(vaultId: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByVaultId(vaultId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTransactionById(id: String): Transaction? {
        return transactionDao.getTransactionById(id)?.toDomain()
    }

    override suspend fun createTransaction(
        vaultId: String,
        type: TransactionType,
        amount: Long,
        description: String,
    ): Transaction {
        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            type = type,
            amount = amount,
            description = description,
            createdAt = now,
        )

        database.withTransaction {
            transactionDao.insert(entity)
            val balance = transactionDao.getBalanceForVault(vaultId)
            vaultDao.updateBalance(vaultId, balance)
        }

        return entity.toDomain()
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        val entity = transaction.toEntity()

        database.withTransaction {
            transactionDao.update(entity)
            val balance = transactionDao.getBalanceForVault(transaction.vaultId)
            vaultDao.updateBalance(transaction.vaultId, balance)
        }
    }

    override suspend fun deleteTransaction(id: String) = database.withTransaction {
        val entity = transactionDao.getTransactionById(id) ?: return@withTransaction
        transactionDao.delete(entity)
        val balance = transactionDao.getBalanceForVault(entity.vaultId)
        vaultDao.updateBalance(entity.vaultId, balance)
    }

    override fun getVaultBalance(vaultId: String): Flow<Long> {
        return transactionDao.getTransactionsByVaultId(vaultId).map { transactions ->
            transactions.fold(0L) { acc, tx ->
                acc + when (tx.type) {
                    TransactionType.INFLOW -> tx.amount
                    TransactionType.OUTFLOW -> -tx.amount
                }
            }
        }
    }
}

private fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    vaultId = vaultId,
    type = type,
    amount = amount,
    description = description,
    createdAt = createdAt,
)

private fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    vaultId = vaultId,
    type = type,
    amount = amount,
    description = description,
    createdAt = createdAt,
)
