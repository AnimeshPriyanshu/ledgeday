package com.vaultledger.data.repository

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
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

    companion object {
        private const val TAG = "TransactionRepo"
    }

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

        Log.d(TAG, "deleteTransaction: locally deleting $id from vault ${entity.vaultId}")
        database.withTransaction {
            transactionDao.delete(entity)
            val balance = transactionDao.getBalanceForVault(entity.vaultId)
            vaultDao.updateBalance(entity.vaultId, balance)
        }

        if (transactionRemoteDataSource != null && workspaceId != null) {
            Log.d(TAG, "deleteTransaction: soft-deleting $id in Firestore")
            try {
                transactionRemoteDataSource.softDeleteTransaction(workspaceId, entity.vaultId, id)
                Log.d(TAG, "deleteTransaction: soft-delete succeeded for $id")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val isNotFound = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND
                if (isNotFound) {
                    Log.d(TAG, "deleteTransaction: doc not found for $id (never synced), no re-insert needed")
                } else {
                    Log.e(TAG, "Failed to soft-delete transaction $id in Firestore, re-inserting into Room", e)
                    database.withTransaction {
                        transactionDao.insert(entity)
                        val balance = transactionDao.getBalanceForVault(entity.vaultId)
                        vaultDao.updateBalance(entity.vaultId, balance)
                    }
                }
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
                val current = transactionDao.getTransactionById(entity.id)
                if (current == null) {
                    Log.d(TAG, "syncToFirestore: transaction ${entity.id} was deleted, skipping sync")
                    return@launch
                }
                transactionRemoteDataSource?.createTransaction(
                    workspaceId, vaultId, entity.toDomain(), entity.createdBy,
                )
                var wasDeleted = false
                database.withTransaction {
                    val stillExists = transactionDao.getTransactionById(entity.id)
                    if (stillExists != null) {
                        transactionDao.insert(entity.copy(synced = true))
                    } else {
                        wasDeleted = true
                    }
                }
                if (wasDeleted) {
                    Log.d(TAG, "syncToFirestore: entity ${entity.id} was deleted during sync, undoing Firestore doc")
                    transactionRemoteDataSource?.softDeleteTransaction(workspaceId, vaultId, entity.id)
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
