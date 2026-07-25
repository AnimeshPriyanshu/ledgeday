package com.vaultledger.data.sync

import com.vaultledger.data.local.dao.TransactionDao
import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.dao.WorkspaceDao
import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.local.entity.WorkspaceEntity
import com.vaultledger.data.remote.TransactionRemoteDataSource
import com.vaultledger.data.remote.VaultRemoteDataSource
import com.vaultledger.data.remote.WorkspaceRemoteDataSource
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.Vault
import com.vaultledger.domain.model.Workspace
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SyncManager @Inject constructor(
    private val workspaceRemoteDataSource: WorkspaceRemoteDataSource,
    private val vaultRemoteDataSource: VaultRemoteDataSource,
    private val transactionRemoteDataSource: TransactionRemoteDataSource,
    private val workspaceDao: WorkspaceDao,
    private val vaultDao: VaultDao,
    private val transactionDao: TransactionDao,
) {

    @VisibleForTesting
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val RETRY_INTERVAL_MS = 30_000L
    }

    open fun startSyncing(uid: String, retryUnsynced: Boolean = true) {
        stopSyncing()
        launchSync("workspaces") { syncWorkspaces(uid) }
        if (retryUnsynced) {
            launchSync("unsynced") { retryUnsyncedTransactionsLoop() }
        }
    }

    private fun launchSync(key: String, block: suspend () -> Unit): Job {
        val job = scope.launch {
            try {
                block()
            } finally {
                jobs.remove(key)
            }
        }
        jobs[key] = job
        return job
    }

    open fun stopSyncing() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.coroutineContext.cancelChildren()
    }

    private suspend fun syncWorkspaces(uid: String) {
        workspaceRemoteDataSource.observeWorkspacesForMember(uid).collect { workspaces ->
            val incomingIds = workspaces.map { it.id }.toSet()
            val syncedIds = jobs.keys.filter { it.startsWith("vaults/") }
                .map { it.removePrefix("vaults/") }.toSet()

            workspaces.forEach { workspaceDao.insert(it.toWorkspaceEntity()) }

            val toStart = incomingIds - syncedIds
            val toStop = syncedIds - incomingIds

            toStart.forEach { wsId ->
                launchSync("vaults/$wsId") { syncVaults(wsId) }
            }

            toStop.forEach { wsId ->
                jobs.remove("vaults/$wsId")?.cancel()
                jobs.keys.filter { it.startsWith("txns/$wsId/") }.toList().forEach { key ->
                    jobs.remove(key)?.cancel()
                }
            }
        }
    }

    private suspend fun syncVaults(workspaceId: String) {
        vaultRemoteDataSource.observeVaults(workspaceId).collect { vaults ->
            val incomingIds = vaults.map { it.id }.toSet()
            val syncedIds = jobs.keys.filter { it.startsWith("txns/$workspaceId/") }
                .map { it.removePrefix("txns/$workspaceId/") }.toSet()

            vaults.forEach { vaultDao.insert(it.toVaultEntity()) }

            val toStart = incomingIds - syncedIds
            val toStop = syncedIds - incomingIds

            toStart.forEach { vId ->
                launchSync("txns/$workspaceId/$vId") { syncTransactions(workspaceId, vId) }
            }

            toStop.forEach { vId ->
                jobs.remove("txns/$workspaceId/$vId")?.cancel()
            }
        }
    }

    private suspend fun syncTransactions(workspaceId: String, vaultId: String) {
        transactionRemoteDataSource.observeNonDeletedTransactions(workspaceId, vaultId).collect { transactions ->
            val existingIds = transactionDao.getTransactionIdsByVaultId(vaultId).toSet()
            val incomingIds = transactions.map { it.id }.toSet()

            val removedIds = existingIds - incomingIds
            removedIds.forEach { transactionDao.deleteById(it) }

            transactions.forEach { transactionDao.insert(it.toTransactionEntity()) }

            val balance = transactionDao.getBalanceForVault(vaultId)
            vaultDao.updateBalance(vaultId, balance)
        }
    }

    private suspend fun retryUnsyncedTransactions() {
        val unsynced = transactionDao.getUnsyncedTransactions()
        for (entity in unsynced) {
            try {
                val vault = vaultDao.getVaultById(entity.vaultId) ?: continue
                val workspaceId = vault.workspaceId
                transactionRemoteDataSource.createTransaction(
                    workspaceId, entity.vaultId, entity.toDomain(), entity.createdBy,
                )
                transactionDao.insert(entity.copy(synced = true))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private suspend fun retryUnsyncedTransactionsLoop() {
        while (true) {
            retryUnsyncedTransactions()
            delay(RETRY_INTERVAL_MS)
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

    private fun Workspace.toWorkspaceEntity() = WorkspaceEntity(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        memberIds = memberIds,
        synced = true,
        updatedAt = createdAt,
    )

    private fun Vault.toVaultEntity() = VaultEntity(
        id = id,
        workspaceId = workspaceId,
        name = name,
        description = description,
        createdAt = createdAt,
        balance = balance,
        color = color,
        synced = true,
        updatedAt = createdAt,
    )

    private fun Transaction.toTransactionEntity() = TransactionEntity(
        id = id,
        vaultId = vaultId,
        type = type,
        amount = amount,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt ?: createdAt,
        synced = true,
    )
}
