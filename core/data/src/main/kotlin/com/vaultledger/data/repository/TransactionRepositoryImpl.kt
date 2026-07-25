package com.vaultledger.data.repository

import androidx.annotation.VisibleForTesting
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    @VisibleForTesting
    internal var syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            syncToFirestore(workspaceId, vaultId, entity)
        }

        return entity.toDomain()
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        val existing = transactionDao.getTransactionById(transaction.id)
        val entity = transaction.toEntity(
            synced = false,
            createdBy = existing?.createdBy ?: "",
        )

        database.withTransaction {
            transactionDao.update(entity)
            val balance = transactionDao.getBalanceForVault(transaction.vaultId)
            vaultDao.updateBalance(transaction.vaultId, balance)
        }

        if (transactionRemoteDataSource != null) {
            val vault = vaultDao.getVaultById(transaction.vaultId)
            val workspaceId = vault?.workspaceId
            if (workspaceId != null) {
                syncToFirestore(workspaceId, transaction.vaultId, entity)
            }
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

    override fun searchTransactions(vaultId: String, query: String): Flow<List<Transaction>> {
        return transactionDao.searchTransactions(vaultId, query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getVaultBalance(vaultId: String): Flow<Long> {
        return transactionDao.observeBalanceForVault(vaultId)
    }

    private fun syncToFirestore(workspaceId: String, vaultId: String, entity: TransactionEntity) {
        syncScope.launch {
            try {
                transactionRemoteDataSource?.createTransaction(
                    workspaceId, vaultId, entity.toDomain(), entity.createdBy,
                )
                database.withTransaction {
                    transactionDao.insert(entity.copy(synced = true))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // synced stays false; SyncManager will retry
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
    updatedAt = updatedAt,
)

private fun Transaction.toEntity(
    synced: Boolean = false,
    createdBy: String = "",
): TransactionEntity = TransactionEntity(
    id = id,
    vaultId = vaultId,
    type = type,
    amount = amount,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt ?: createdAt,
    synced = synced,
    createdBy = createdBy,
)
