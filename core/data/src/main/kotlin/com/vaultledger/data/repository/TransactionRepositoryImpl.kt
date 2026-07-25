package com.vaultledger.data.repository

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.local.dao.TransactionDao
import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.remote.TransactionRemoteDataSource
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.domain.repository.TransactionRepository
import kotlinx.coroutines.CancellationException
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
    private val transactionRemoteDataSource: TransactionRemoteDataSource? = null,
    private val firebaseAuth: FirebaseAuth? = null,
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
        val vault = vaultDao.getVaultById(vaultId)
        val workspaceId = vault?.workspaceId
        val currentUserId = try { firebaseAuth?.currentUser?.uid ?: "" } catch (_: Exception) { "" }

        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            type = type,
            amount = amount,
            description = description,
            createdAt = now,
            updatedAt = now,
            synced = false,
            createdBy = currentUserId,
        )

        database.withTransaction {
            transactionDao.insert(entity)
            val balance = transactionDao.getBalanceForVault(vaultId)
            vaultDao.updateBalance(vaultId, balance)
        }

        if (transactionRemoteDataSource != null && workspaceId != null) {
            try {
                transactionRemoteDataSource.createTransaction(workspaceId, vaultId, entity.toDomain(), currentUserId)
                val syncedEntity = entity.copy(synced = true)
                database.withTransaction {
                    transactionDao.insert(syncedEntity)
                }
                return syncedEntity.toDomain()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Failure keeps synced = false
            }
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

    override suspend fun deleteTransaction(id: String) {
        val entity = transactionDao.getTransactionById(id) ?: return
        val vault = vaultDao.getVaultById(entity.vaultId)
        val workspaceId = vault?.workspaceId

        database.withTransaction {
            transactionDao.delete(entity)
            val balance = transactionDao.getBalanceForVault(entity.vaultId)
            vaultDao.updateBalance(entity.vaultId, balance)
        }

        if (transactionRemoteDataSource != null && workspaceId != null) {
            try {
                transactionRemoteDataSource.softDeleteTransaction(workspaceId, entity.vaultId, id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    override fun getVaultBalance(vaultId: String): Flow<Long> {
        return transactionDao.observeBalanceForVault(vaultId)
    }
}

private fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    vaultId = vaultId,
    type = type,
    amount = amount,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    vaultId = vaultId,
    type = type,
    amount = amount,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt ?: createdAt,
)
