package com.vaultledger.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import com.vaultledger.data.local.VaultLedgerDatabase
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
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    private val database: VaultLedgerDatabase? = null,
) {

    @VisibleForTesting
    internal var scope: CoroutineScope = createScope()

    private val jobs = ConcurrentHashMap<String, Job>()

    private val entityRetryCount = ConcurrentHashMap<String, Int>()
    private val entityNextRetryTime = ConcurrentHashMap<String, Long>()
    private val entityPermanentlyFailed = ConcurrentHashMap<String, Boolean>()
    private var consecutiveGlobalFailures = 0
    private var circuitBreakerTrippedUntil = 0L

    companion object {
        private const val TAG = "SyncManager"
        private const val RETRY_INTERVAL_MS = 30_000L
        private const val MAX_BACKOFF_MS = 900_000L
        private const val MAX_RETRIES = 10
        private const val CIRCUIT_BREAKER_THRESHOLD = 5
        private const val CIRCUIT_BREAKER_COOLDOWN_MS = 300_000L
    }

    private fun createScope() = CoroutineScope(
        SupervisorJob() + Dispatchers.IO,
    )

    open suspend fun performSyncCycle() {
        Log.d(TAG, "performSyncCycle: starting one sync cycle")
        retryUnsyncedTransactions()
        retryUnsyncedWorkspaces()
        retryUnsyncedVaults()
        Log.d(TAG, "performSyncCycle: completed")
    }

    private fun entityKey(entity: WorkspaceEntity) = "ws:${entity.id}"
    private fun entityKey(entity: VaultEntity) = "vault:${entity.id}"
    private fun entityKey(entity: TransactionEntity) = "txn:${entity.id}"

    open fun startSyncing(uid: String, retryUnsynced: Boolean = true) {
        Log.d(TAG, "startSyncing: uid=$uid, retryUnsynced=$retryUnsynced")
        stopSyncing()
        launchSync("workspaces") { syncWorkspaces(uid) }
        if (retryUnsynced) {
            launchSync("unsynced") { retryUnsyncedLoop() }
        }
    }

    private fun launchSync(key: String, block: suspend () -> Unit): Job {
        val job = scope.launch {
            Log.d(TAG, "Job started: key=$key")
            try {
                block()
                Log.d(TAG, "Job completed normally: key=$key")
            } catch (e: CancellationException) {
                Log.d(TAG, "Job cancelled: key=$key")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Job failed with exception: key=$key, error=${e.message}", e)
            } finally {
                jobs.remove(key)
                Log.d(TAG, "Job removed from map: key=$key")
            }
        }
        jobs[key] = job
        return job
    }

    open fun stopSyncing() {
        Log.d(TAG, "stopSyncing: jobs=${jobs.keys}")
        val keys = jobs.keys.toList()
        keys.forEach { jobs.remove(it)?.cancel() }
        scope.coroutineContext.cancelChildren()
        entityRetryCount.clear()
        entityNextRetryTime.clear()
        entityPermanentlyFailed.clear()
        consecutiveGlobalFailures = 0
        circuitBreakerTrippedUntil = 0L
    }

    private fun isTransientError(e: Exception): Boolean {
        if (e is FirebaseFirestoreException) {
            return when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                FirebaseFirestoreException.Code.CANCELLED -> true
                else -> false
            }
        }
        return true
    }

    private fun isCircuitBroken(): Boolean {
        if (circuitBreakerTrippedUntil > 0L && System.currentTimeMillis() < circuitBreakerTrippedUntil) {
            Log.d(TAG, "Circuit breaker open until $circuitBreakerTrippedUntil, skipping retry")
            return true
        }
        if (circuitBreakerTrippedUntil > 0L && System.currentTimeMillis() >= circuitBreakerTrippedUntil) {
            circuitBreakerTrippedUntil = 0L
            consecutiveGlobalFailures = 0
            Log.d(TAG, "Circuit breaker reset, allowing probe")
        }
        return false
    }

    private fun recordGlobalFailure() {
        consecutiveGlobalFailures++
        if (consecutiveGlobalFailures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitBreakerTrippedUntil = System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS
            Log.w(TAG, "Circuit breaker tripped: $consecutiveGlobalFailures consecutive failures")
        }
    }

    private fun recordGlobalSuccess() {
        consecutiveGlobalFailures = 0
    }

    private fun <T> shouldSkipEntityRetry(
        key: String,
        entity: T,
        entityId: String,
    ): Boolean {
        if (entityPermanentlyFailed.getOrDefault(key, false)) {
            Log.d(TAG, "Entity $entityId permanently failed, skipping")
            return true
        }
        val retryCount = entityRetryCount.getOrDefault(key, 0)
        if (retryCount >= MAX_RETRIES) {
            Log.w(TAG, "Entity $entityId exceeded max retries ($MAX_RETRIES), marking permanently failed")
            entityPermanentlyFailed[key] = true
            return true
        }
        val nextRetry = entityNextRetryTime.getOrDefault(key, 0L)
        if (nextRetry > System.currentTimeMillis()) {
            return true
        }
        return false
    }

    private fun recordEntityRetry(key: String) {
        val count = entityRetryCount.getOrDefault(key, 0) + 1
        entityRetryCount[key] = count
        val backoffMs = minOf(
            RETRY_INTERVAL_MS * (1L shl (count - 1).coerceAtMost(5)),
            MAX_BACKOFF_MS,
        )
        entityNextRetryTime[key] = System.currentTimeMillis() + backoffMs
    }

    private fun recordEntitySuccess(key: String) {
        entityRetryCount.remove(key)
        entityNextRetryTime.remove(key)
        entityPermanentlyFailed.remove(key)
    }

    private suspend fun syncWorkspaces(uid: String) {
        Log.d(TAG, "syncWorkspaces: collecting flow for uid=$uid")
        var emissionCount = 0
        workspaceRemoteDataSource.observeWorkspacesForMember(uid).collect { workspaces ->
            emissionCount++
            Log.d(TAG, "syncWorkspaces: emission #$emissionCount, ${workspaces.size} workspace(s)")
            val incomingIds = workspaces.map { it.id }.toSet()
            val currentKeys = jobs.keys.toList()
            val syncedIds = currentKeys.filter { it.startsWith("vaults/") }
                .map { it.removePrefix("vaults/") }.toSet()

            workspaces.forEach { workspaceDao.insert(it.toWorkspaceEntity()) }

            val existing = workspaceDao.getAllWorkspaces().first()
            val removedWorkspaces = existing.filter { it.synced && it.id !in incomingIds }
            removedWorkspaces.forEach { workspaceDao.delete(it) }

            val toStart = incomingIds - syncedIds
            val toStop = syncedIds - incomingIds

            if (toStart.isNotEmpty()) {
                Log.d(TAG, "syncWorkspaces: starting vault sync for workspaceIds=$toStart")
            }
            toStart.forEach { wsId ->
                launchSync("vaults/$wsId") { syncVaults(wsId) }
            }

            if (toStop.isNotEmpty()) {
                Log.d(TAG, "syncWorkspaces: stopping vault sync for workspaceIds=$toStop")
            }
            toStop.forEach { wsId ->
                jobs.remove("vaults/$wsId")?.cancel()
                val txnKeys = currentKeys.filter { it.startsWith("txns/$wsId/") }
                txnKeys.forEach { key -> jobs.remove(key)?.cancel() }
            }
        }
    }

    private suspend fun syncVaults(workspaceId: String) {
        Log.d(TAG, "syncVaults: collecting flow for workspaceId=$workspaceId")
        var emissionCount = 0
        vaultRemoteDataSource.observeVaults(workspaceId).collect { vaults ->
            emissionCount++
            Log.d(TAG, "syncVaults: emission #$emissionCount, ${vaults.size} vault(s) for workspaceId=$workspaceId")
            val incomingIds = vaults.map { it.id }.toSet()
            val currentKeys = jobs.keys.toList()
            val syncedIds = currentKeys.filter { it.startsWith("txns/$workspaceId/") }
                .map { it.removePrefix("txns/$workspaceId/") }.toSet()

            vaults.forEach { vaultDao.insert(it.toVaultEntity()) }

            val toRestart = incomingIds.intersect(syncedIds)
            toRestart.forEach { vId ->
                val key = "txns/$workspaceId/$vId"
                val oldJob = jobs.remove(key)
                if (oldJob != null) {
                    oldJob.cancel()
                    oldJob.join()
                }
            }

            val toStart = incomingIds - syncedIds
            val toStop = syncedIds - incomingIds

            if (toStart.isNotEmpty() || toRestart.isNotEmpty()) {
                Log.d(TAG, "syncVaults: starting/restarting txn sync for vaultIds=${toStart + toRestart}")
            }
            (toStart + toRestart).forEach { vId ->
                launchSync("txns/$workspaceId/$vId") { syncTransactions(workspaceId, vId) }
            }

            if (toStop.isNotEmpty()) {
                Log.d(TAG, "syncVaults: stopping txn sync for vaultIds=$toStop")
            }
            toStop.forEach { vId ->
                jobs.remove("txns/$workspaceId/$vId")?.cancel()
            }
        }
    }

    private suspend fun syncTransactions(workspaceId: String, vaultId: String) {
        Log.d(TAG, "syncTransactions: collecting flow for vaultId=$vaultId")
        var emissionCount = 0
        transactionRemoteDataSource.observeNonDeletedTransactions(workspaceId, vaultId).collect { transactions ->
            emissionCount++
            Log.d(TAG, "syncTransactions: emission #$emissionCount, ${transactions.size} txn(s) for vaultId=$vaultId")
            val block: suspend () -> Unit = {
                val existingIds = transactionDao.getTransactionIdsByVaultId(vaultId).toSet()
                val incomingIds = transactions.map { it.id }.toSet()

                val removedIds = existingIds - incomingIds
                removedIds.forEach { transactionDao.deleteById(it) }

                transactions.forEach { transactionDao.insert(it.toTransactionEntity()) }

                val balance = transactionDao.getBalanceForVault(vaultId)
                vaultDao.updateBalance(vaultId, balance)
            }
            val db = database
            if (db != null) {
                db.withTransaction { block() }
            } else {
                block()
            }
        }
    }

    @VisibleForTesting
    internal suspend fun retryUnsyncedTransactions() {
        if (isCircuitBroken()) return
        val unsynced = transactionDao.getUnsyncedTransactions()
        if (unsynced.isNotEmpty()) {
            Log.d(TAG, "retryUnsynced: ${unsynced.size} unsynced transaction(s)")
        }
        for (entity in unsynced) {
            val key = entityKey(entity)
            if (shouldSkipEntityRetry(key, entity, entity.id)) continue

            try {
                val vault = vaultDao.getVaultById(entity.vaultId) ?: continue
                val workspaceId = vault.workspaceId

                val current = transactionDao.getTransactionById(entity.id)
                if (current == null) {
                    Log.d(TAG, "retryUnsynced: entity ${entity.id} was deleted before write, skipping")
                    recordEntitySuccess(key)
                    continue
                }

                transactionRemoteDataSource.createTransaction(
                    workspaceId, entity.vaultId, entity.toDomain(), entity.createdBy,
                )

                val stillExists = transactionDao.getTransactionById(entity.id)
                if (stillExists != null) {
                    transactionDao.insert(entity.copy(synced = true))
                    recordEntitySuccess(key)
                    recordGlobalSuccess()
                } else {
                    Log.d(TAG, "retryUnsynced: entity ${entity.id} deleted during write, compensating")
                    transactionRemoteDataSource.softDeleteTransaction(
                        workspaceId, entity.vaultId, entity.id,
                    )
                    recordEntitySuccess(key)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!isTransientError(e)) {
                    Log.w(TAG, "Permanent failure for entity ${entity.id}: ${e.message}")
                    entityPermanentlyFailed[key] = true
                } else {
                    recordEntityRetry(key)
                    recordGlobalFailure()
                }
            }
        }
    }

    @VisibleForTesting
    internal suspend fun retryUnsyncedWorkspaces() {
        if (isCircuitBroken()) return
        val unsynced = workspaceDao.getUnsyncedWorkspaces()
        if (unsynced.isNotEmpty()) {
            Log.d(TAG, "retryUnsyncedWorkspaces: ${unsynced.size} unsynced workspace(s)")
        }
        for (entity in unsynced) {
            val key = entityKey(entity)
            if (shouldSkipEntityRetry(key, entity, entity.id)) continue

            try {
                val current = workspaceDao.getWorkspaceById(entity.id)
                if (current == null) {
                    Log.d(TAG, "retryUnsyncedWorkspaces: entity ${entity.id} was deleted before write, skipping")
                    recordEntitySuccess(key)
                    continue
                }

                val creatorId = entity.memberIds.firstOrNull() ?: continue
                val workspace = Workspace(
                    id = entity.id,
                    name = entity.name,
                    description = entity.description,
                    createdAt = entity.createdAt,
                    memberIds = entity.memberIds,
                )
                workspaceRemoteDataSource.createWorkspace(workspace, creatorId)

                val stillExists = workspaceDao.getWorkspaceById(entity.id)
                if (stillExists != null) {
                    workspaceDao.insert(entity.copy(synced = true))
                    recordEntitySuccess(key)
                    recordGlobalSuccess()
                } else {
                    Log.d(TAG, "retryUnsyncedWorkspaces: entity ${entity.id} deleted during write, compensating")
                    workspaceRemoteDataSource.deleteWorkspace(entity.id)
                    recordEntitySuccess(key)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!isTransientError(e)) {
                    Log.w(TAG, "Permanent failure for workspace ${entity.id}: ${e.message}")
                    entityPermanentlyFailed[key] = true
                } else {
                    recordEntityRetry(key)
                    recordGlobalFailure()
                }
            }
        }
    }

    @VisibleForTesting
    internal suspend fun retryUnsyncedVaults() {
        if (isCircuitBroken()) return
        val unsynced = vaultDao.getUnsyncedVaults()
        if (unsynced.isNotEmpty()) {
            Log.d(TAG, "retryUnsyncedVaults: ${unsynced.size} unsynced vault(s)")
        }
        for (entity in unsynced) {
            val key = entityKey(entity)
            if (shouldSkipEntityRetry(key, entity, entity.id)) continue

            try {
                val current = vaultDao.getVaultById(entity.id)
                if (current == null) {
                    Log.d(TAG, "retryUnsyncedVaults: entity ${entity.id} was deleted before write, skipping")
                    recordEntitySuccess(key)
                    continue
                }

                val vault = Vault(
                    id = entity.id,
                    workspaceId = entity.workspaceId,
                    name = entity.name,
                    description = entity.description,
                    createdAt = entity.createdAt,
                    color = entity.color,
                )
                vaultRemoteDataSource.createVault(entity.workspaceId, vault)

                val stillExists = vaultDao.getVaultById(entity.id)
                if (stillExists != null) {
                    vaultDao.insert(entity.copy(synced = true))
                    recordEntitySuccess(key)
                    recordGlobalSuccess()
                } else {
                    Log.d(TAG, "retryUnsyncedVaults: entity ${entity.id} deleted during write, compensating")
                    vaultRemoteDataSource.deleteVault(entity.workspaceId, entity.id)
                    recordEntitySuccess(key)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!isTransientError(e)) {
                    Log.w(TAG, "Permanent failure for vault ${entity.id}: ${e.message}")
                    entityPermanentlyFailed[key] = true
                } else {
                    recordEntityRetry(key)
                    recordGlobalFailure()
                }
            }
        }
    }

    private suspend fun retryUnsyncedLoop() {
        var loopBackoff = RETRY_INTERVAL_MS
        while (true) {
            retryUnsyncedTransactions()
            retryUnsyncedWorkspaces()
            retryUnsyncedVaults()

            if (entityPermanentlyFailed.isNotEmpty() || entityRetryCount.isNotEmpty()) {
                loopBackoff = RETRY_INTERVAL_MS
            } else {
                loopBackoff = minOf((loopBackoff * 2).coerceAtLeast(RETRY_INTERVAL_MS), MAX_BACKOFF_MS)
            }
            delay(loopBackoff)
        }
    }

    @VisibleForTesting
    internal fun getRetryCount(key: String): Int = entityRetryCount.getOrDefault(key, 0)

    @VisibleForTesting
    internal fun isPermanentlyFailed(key: String): Boolean = entityPermanentlyFailed.getOrDefault(key, false)

    @VisibleForTesting
    internal fun isCircuitTripped(): Boolean = circuitBreakerTrippedUntil > System.currentTimeMillis()

    @VisibleForTesting
    internal fun resetRetryState() {
        entityNextRetryTime.clear()
        consecutiveGlobalFailures = 0
        circuitBreakerTrippedUntil = 0L
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
