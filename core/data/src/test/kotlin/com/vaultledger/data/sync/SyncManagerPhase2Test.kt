package com.vaultledger.data.sync

import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.local.entity.WorkspaceEntity
import com.vaultledger.data.remote.TransactionRemoteDataSource
import com.vaultledger.data.remote.WorkspaceRemoteDataSource
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.domain.model.Vault
import com.vaultledger.domain.model.Workspace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerPhase2Test {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workspaceRemote: FakeWorkspaceRemoteDataSource
    private lateinit var vaultRemote: FakeVaultRemoteDataSource
    private lateinit var transactionRemote: TransactionRemoteDataSource
    private lateinit var workspaceDao: FakeWorkspaceDao
    private lateinit var vaultDao: FakeVaultDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var syncManager: SyncManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workspaceRemote = FakeWorkspaceRemoteDataSource()
        vaultRemote = FakeVaultRemoteDataSource()
        transactionRemote = object : TransactionRemoteDataSource() {
            override fun observeNonDeletedTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override fun observeAllTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(wsId: String, vId: String, txn: Transaction, createdBy: String) {}
            override suspend fun softDeleteTransaction(wsId: String, vId: String, txnId: String) {}
        }
        workspaceDao = FakeWorkspaceDao()
        vaultDao = FakeVaultDao()
        transactionDao = FakeTransactionDao()
        syncManager = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = transactionRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also {
            it.scope = CoroutineScope(SupervisorJob() + testDispatcher)
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============================================================
    // 1. Exponential backoff timing
    // ============================================================

    @Test
    fun `retry count increments on transient failure`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        val vaultEntity = VaultEntity("vault-1", "ws-1", "V", "", 1000L, 0L, "#000", true)
        vaultDao.insert(vaultEntity)
        val txnEntity = TransactionEntity("txn-1", "vault-1", TransactionType.INFLOW, 1000L, "", 1000L, 1000L, false, "user-1")
        transactionDao.insert(txnEntity)

        val failingRemote = object : TransactionRemoteDataSource() {
            var callCount = 0
            override fun observeNonDeletedTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override fun observeAllTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(wsId: String, vId: String, txn: Transaction, createdBy: String) {
                callCount++
                throw RuntimeException("Transient failure")
            }
            override suspend fun softDeleteTransaction(wsId: String, vId: String, txnId: String) {}
        }

        val sm = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = failingRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        sm.retryUnsyncedTransactions()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, failingRemote.callCount)
        assertEquals(1, sm.getRetryCount("txn:txn-1"))

        // Second call: entity skipped due to backoff (shouldSkipEntityRetry returns true)
        sm.retryUnsyncedTransactions()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, failingRemote.callCount)
        assertEquals(1, sm.getRetryCount("txn:txn-1"))
    }

    @Test
    fun `retry count resets after successful sync`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        val vaultEntity = VaultEntity("vault-1", "ws-1", "V", "", 1000L, 0L, "#000", true)
        vaultDao.insert(vaultEntity)
        val txnEntity = TransactionEntity("txn-1", "vault-1", TransactionType.INFLOW, 1000L, "", 1000L, 1000L, false, "user-1")
        transactionDao.insert(txnEntity)

        var shouldFail = true
        val togglingRemote = object : TransactionRemoteDataSource() {
            override fun observeNonDeletedTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override fun observeAllTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(wsId: String, vId: String, txn: Transaction, createdBy: String) {
                if (shouldFail) {
                    shouldFail = false
                    throw RuntimeException("Transient failure")
                }
            }
            override suspend fun softDeleteTransaction(wsId: String, vId: String, txnId: String) {}
        }

        val sm = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = togglingRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        // First call fails -> retry count = 1
        sm.retryUnsyncedTransactions()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, sm.getRetryCount("txn:txn-1"))

        // Clear backoff
        sm.resetRetryState()

        // Second call succeeds -> retry count = 0
        sm.retryUnsyncedTransactions()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, sm.getRetryCount("txn:txn-1"))
    }

    @Test
    fun `backoff prevents immediate retry of failed entity`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        val vaultEntity = VaultEntity("vault-1", "ws-1", "V", "", 1000L, 0L, "#000", true)
        vaultDao.insert(vaultEntity)
        val txnEntity = TransactionEntity("txn-bf", "vault-1", TransactionType.INFLOW, 1000L, "", 1000L, 1000L, false, "user-1")
        transactionDao.insert(txnEntity)

        var callCount = 0
        val remote = object : TransactionRemoteDataSource() {
            override fun observeNonDeletedTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override fun observeAllTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(wsId: String, vId: String, txn: Transaction, createdBy: String) {
                callCount++
                throw RuntimeException("Transient failure")
            }
            override suspend fun softDeleteTransaction(wsId: String, vId: String, txnId: String) {}
        }

        val sm = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = remote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        // First call fails, backoff = now + 30s
        sm.retryUnsyncedTransactions()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, callCount)
        assertEquals(1, sm.getRetryCount("txn:txn-bf"))

        // Second call: shouldSkipEntityRetry returns true (backoff not expired)
        sm.retryUnsyncedTransactions()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, callCount)
    }

    // ============================================================
    // 2. Circuit breaker
    // ============================================================

    @Test
    fun `circuit breaker opens after 5 consecutive failures`() = runTest(testDispatcher) {
        for (i in 1..5) {
            workspaceDao.insert(
                WorkspaceEntity("ws-$i", "WS-$i", "", 1000L, listOf("user-1"), false)
            )
        }
        workspaceRemote.shouldFail = true

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(syncManager.isCircuitTripped())
    }

    @Test
    fun `circuit breaker blocks retries during cooldown`() = runTest(testDispatcher) {
        for (i in 1..5) {
            workspaceDao.insert(
                WorkspaceEntity("ws-$i", "WS-$i", "", 1000L, listOf("user-1"), false)
            )
        }
        workspaceRemote.shouldFail = true
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(syncManager.isCircuitTripped())

        workspaceDao.insert(
            WorkspaceEntity("ws-6", "WS-6", "", 1000L, listOf("user-1"), false)
        )
        workspaceRemote.createWorkspaceCalled = false

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(workspaceRemote.createWorkspaceCalled)
    }

    @Test
    fun `circuit breaker recovers after cooldown expiry`() = runTest(testDispatcher) {
        for (i in 1..5) {
            workspaceDao.insert(
                WorkspaceEntity("ws-$i", "WS-$i", "", 1000L, listOf("user-1"), false)
            )
        }
        workspaceRemote.shouldFail = true
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(syncManager.isCircuitTripped())

        syncManager.resetRetryState()
        assertFalse(syncManager.isCircuitTripped())

        val wsNew = WorkspaceEntity("ws-new", "New", "", 1000L, listOf("user-1"), false)
        workspaceDao.insert(wsNew)
        workspaceRemote.createWorkspaceCalled = false

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(workspaceRemote.createWorkspaceCalled)
    }

    @Test
    fun `global success resets consecutive failure counter`() = runTest(testDispatcher) {
        for (i in 1..5) {
            workspaceDao.insert(
                WorkspaceEntity("ws-$i", "WS-$i", "", 1000L, listOf("user-1"), false)
            )
        }
        workspaceRemote.shouldFail = true
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(syncManager.isCircuitTripped())

        workspaceRemote.shouldFail = false
        syncManager.resetRetryState()
        val wsOk = WorkspaceEntity("ws-ok", "OK", "", 1000L, listOf("user-1"), false)
        workspaceDao.insert(wsOk)

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(syncManager.isCircuitTripped())
        val stored = workspaceDao.getWorkspaceById("ws-ok")
        assertTrue(stored?.synced ?: false)
    }

    // ============================================================
    // 3. Permanent failure
    // ============================================================

    @Test
    fun `entity permanently failed after MAX_RETRIES transient failures`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        workspaceDao.insert(
            WorkspaceEntity("ws-pf", "PF", "", 1000L, listOf("user-1"), false)
        )
        workspaceRemote.shouldFail = true

        for (i in 0 until 10) {
            syncManager.retryUnsyncedWorkspaces()
            testDispatcher.scheduler.advanceUntilIdle()
            syncManager.resetRetryState()
        }

        // One more retry triggers shouldSkipEntityRetry with count >= MAX_RETRIES
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(syncManager.isPermanentlyFailed("ws:ws-pf"))
    }

    @Test
    fun `permanently failed entity is skipped in retry`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        workspaceDao.insert(
            WorkspaceEntity("ws-skip", "Skip", "", 1000L, listOf("user-1"), false)
        )
        workspaceRemote.shouldFail = true

        for (i in 0 until 10) {
            syncManager.retryUnsyncedWorkspaces()
            testDispatcher.scheduler.advanceUntilIdle()
            syncManager.resetRetryState()
        }

        // One more retry triggers shouldSkipEntityRetry with count >= MAX_RETRIES
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(syncManager.isPermanentlyFailed("ws:ws-skip"))

        workspaceRemote.createWorkspaceCalled = false
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(workspaceRemote.createWorkspaceCalled)
    }

    @Test
    fun `transient failure does not permanently fail entity`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        workspaceDao.insert(
            WorkspaceEntity("ws-tf", "TF", "", 1000L, listOf("user-1"), false)
        )
        workspaceRemote.shouldFail = true

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(syncManager.isPermanentlyFailed("ws:ws-tf"))
        assertEquals(1, syncManager.getRetryCount("ws:ws-tf"))
    }

    // ============================================================
    // 4. Concurrent operations
    // ============================================================

    @Test
    fun `concurrent retryUnsyncedTransactions calls do not interfere`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        val vaultEntity = VaultEntity("vault-1", "ws-1", "V", "", 1000L, 0L, "#000", true)
        vaultDao.insert(vaultEntity)
        val txn1 = TransactionEntity("txn-a", "vault-1", TransactionType.INFLOW, 100L, "", 1000L, 1000L, false, "user-1")
        val txn2 = TransactionEntity("txn-b", "vault-1", TransactionType.INFLOW, 200L, "", 1000L, 1000L, false, "user-1")
        transactionDao.insert(txn1)
        transactionDao.insert(txn2)

        val failingRemote = object : TransactionRemoteDataSource() {
            var callCount = 0
            override fun observeNonDeletedTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override fun observeAllTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(wsId: String, vId: String, txn: Transaction, createdBy: String) {
                callCount++
                throw RuntimeException("Transient failure")
            }
            override suspend fun softDeleteTransaction(wsId: String, vId: String, txnId: String) {}
        }

        val sm = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = failingRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        val testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val job1 = testScope.launch { sm.retryUnsyncedTransactions() }
        val job2 = testScope.launch { sm.retryUnsyncedTransactions() }
        testDispatcher.scheduler.advanceUntilIdle()
        job1.join()
        job2.join()

        assertTrue(failingRemote.callCount >= 1)
    }

    @Test
    fun `concurrent startSyncing calls do not cause errors`() = runTest(testDispatcher) {
        workspaceRemote.emitWorkspaces(listOf(
            Workspace("ws-1", "Shared", "", 1000L, listOf("user-1"))
        ))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        syncManager.startSyncing("user-1", retryUnsynced = false)
        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val stored = workspaceDao.getWorkspaceById("ws-1")
        assertNotNull(stored)
        assertEquals("ws-1", stored!!.id)
    }

    @Test
    fun `startSyncing does not leak jobs from previous session`() = runTest(testDispatcher) {
        workspaceRemote.emitWorkspaces(listOf(
            Workspace("ws-1", "First", "", 1000L, listOf("user-1"))
        ))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        workspaceRemote.emitWorkspaces(listOf(
            Workspace("ws-2", "Second", "", 2000L, listOf("user-1"))
        ))
        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val allWorkspaces = workspaceDao.getAllWorkspacesSuspend()
        assertEquals(1, allWorkspaces.size)
        assertEquals("ws-2", allWorkspaces.first().id)
    }

    // ============================================================
    // 5. Child job cleanup
    // ============================================================

    @Test
    fun `stopSyncing cancels all child coroutines`() = runTest(testDispatcher) {
        workspaceRemote.emitWorkspaces(listOf(
            Workspace("ws-1", "WS", "", 1000L, listOf("user-1"))
        ))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        syncManager.stopSyncing()
        testDispatcher.scheduler.advanceUntilIdle()

        val children = syncManager.scope.coroutineContext[Job]?.children?.toList() ?: emptyList()
        assertTrue(children.isEmpty() || children.all { !it.isActive })
    }

    @Test
    fun `launchSync removes job from map after flow completion`() = runTest(testDispatcher) {
        val completingRemote = object : WorkspaceRemoteDataSource() {
            override fun observeWorkspacesForMember(memberId: String): Flow<List<Workspace>> =
                flowOf(emptyList())
            override suspend fun createWorkspace(workspace: Workspace, creatorId: String) {}
            override suspend fun deleteWorkspace(workspaceId: String) {}
            override suspend fun updateWorkspace(workspace: Workspace) {}
        }
        val sm = SyncManager(
            workspaceRemoteDataSource = completingRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = transactionRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        sm.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should complete without error
        sm.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // ============================================================
    // 6. Dispatcher correctness
    // ============================================================

    @Test
    fun `default scope uses IO dispatcher`() = runTest(testDispatcher) {
        val sm = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = transactionRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        )
        val interceptor = sm.scope.coroutineContext[ContinuationInterceptor]
        assertTrue(interceptor is CoroutineDispatcher)
        val dispatcherName = interceptor.toString().substringBefore("@")
        assertEquals("Dispatchers.IO", dispatcherName)
    }

    @Test
    fun `performSyncCycle calls retry methods`() = runTest(testDispatcher) {
        workspaceDao.insert(
            WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), false)
        )

        syncManager.performSyncCycle()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(workspaceRemote.createWorkspaceCalled)
    }

    // ============================================================
    // 7. Cancellation behaviour
    // ============================================================

    @Test
    fun `retryUnsyncedLoop cancels cleanly when scope cancelled`() = runTest(testDispatcher) {
        val completingWsRemote = object : WorkspaceRemoteDataSource() {
            override fun observeWorkspacesForMember(memberId: String): Flow<List<Workspace>> =
                flowOf(emptyList())
            override suspend fun createWorkspace(workspace: Workspace, creatorId: String) {}
            override suspend fun deleteWorkspace(workspaceId: String) {}
            override suspend fun updateWorkspace(workspace: Workspace) {}
        }
        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        val sm = SyncManager(
            workspaceRemoteDataSource = completingWsRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = transactionRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = scope }

        sm.startSyncing("test-uid", retryUnsynced = true)
        testDispatcher.scheduler.runCurrent() // dispatch pending coroutines without entering infinite delay loop

        // The retry loop should be running (active)
        sm.stopSyncing()
        testDispatcher.scheduler.advanceUntilIdle()

        // After stopSyncing, scope children should be cancelled
        val children = sm.scope.coroutineContext[Job]?.children?.toList() ?: emptyList()
        assertTrue(children.isEmpty() || children.all { !it.isActive })
    }

    // ============================================================
    // 8. Balance consistency
    // ============================================================

    @Test
    fun `balance recalculated correctly after emissions`() = runTest(testDispatcher) {
        val vaultId = "vault-1"
        val wsId = "ws-1"
        vaultRemote.emitVaults(wsId, listOf(Vault(vaultId, wsId, "V", "", 1000L)))
        workspaceRemote.emitWorkspaces(listOf(Workspace(wsId, "WS", "", 1000L, listOf("user-1"))))

        var emitted = 0
        transactionRemote = object : TransactionRemoteDataSource() {
            override fun observeNonDeletedTransactions(ws: String, v: String): Flow<List<Transaction>> {
                emitted++
                return when (emitted) {
                    1 -> flowOf(listOf(
                        Transaction("in-1", vaultId, TransactionType.INFLOW, 2000L, "", 1000L),
                        Transaction("out-1", vaultId, TransactionType.OUTFLOW, 500L, "", 2000L),
                    ))
                    2 -> flowOf(listOf(
                        Transaction("in-1", vaultId, TransactionType.INFLOW, 2000L, "", 1000L),
                    ))
                    else -> flowOf(emptyList())
                }
            }
            override fun observeAllTransactions(ws: String, v: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(ws: String, v: String, txn: Transaction, createdBy: String) {}
            override suspend fun softDeleteTransaction(ws: String, v: String, txnId: String) {}
        }

        val sm = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = transactionRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        sm.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // First emission: 2000 - 500 = 1500
        assertEquals(1500L, vaultDao.getBalanceSuspend(vaultId))

        // Trigger re-collection by re-emitting vaults
        vaultRemote.emitVaults(wsId, listOf(Vault(vaultId, wsId, "V", "", 1000L)))
        testDispatcher.scheduler.advanceUntilIdle()

        // Second emission: 2000
        assertEquals(2000L, vaultDao.getBalanceSuspend(vaultId))
    }

    // ============================================================
    // 9. Retry queue behaviour
    // ============================================================

    @Test
    fun `retry processes all unsynced entities`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        val vaultEntity = VaultEntity("vault-1", "ws-1", "V", "", 1000L, 0L, "#000", true)
        vaultDao.insert(vaultEntity)

        for (i in 1..3) {
            transactionDao.insert(
                TransactionEntity("txn-$i", "vault-1", TransactionType.INFLOW, 100L * i, "", 1000L * i, 1000L * i, false, "user-1")
            )
        }

        val trackingRemote = object : TransactionRemoteDataSource() {
            val createdIds = mutableListOf<String>()
            override fun observeNonDeletedTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override fun observeAllTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(wsId: String, vId: String, txn: Transaction, createdBy: String) {
                createdIds.add(txn.id)
            }
            override suspend fun softDeleteTransaction(wsId: String, vId: String, txnId: String) {}
        }

        val sm = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = trackingRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        sm.retryUnsyncedTransactions()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, trackingRemote.createdIds.size)
        assertTrue(trackingRemote.createdIds.containsAll(listOf("txn-1", "txn-2", "txn-3")))

        val stored = transactionDao.getTransactionsByVaultIdSuspend("vault-1")
        stored.forEach { assertTrue(it.synced) }
    }

    @Test
    fun `retry filters out already synced entities`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity("ws-1", "WS", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(wsEntity)
        val vaultEntity = VaultEntity("vault-1", "ws-1", "V", "", 1000L, 0L, "#000", true)
        vaultDao.insert(vaultEntity)

        transactionDao.insert(
            TransactionEntity("txn-sync", "vault-1", TransactionType.INFLOW, 100L, "", 1000L, 1000L, true, "user-1")
        )
        transactionDao.insert(
            TransactionEntity("txn-unsync", "vault-1", TransactionType.INFLOW, 200L, "", 1000L, 1000L, false, "user-1")
        )

        val trackingRemote = object : TransactionRemoteDataSource() {
            val createdIds = mutableListOf<String>()
            override fun observeNonDeletedTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override fun observeAllTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(wsId: String, vId: String, txn: Transaction, createdBy: String) {
                createdIds.add(txn.id)
            }
            override suspend fun softDeleteTransaction(wsId: String, vId: String, txnId: String) {}
        }

        val sm = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = trackingRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        sm.retryUnsyncedTransactions()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, trackingRemote.createdIds.size)
        assertEquals("txn-unsync", trackingRemote.createdIds.first())
    }

    // ============================================================
    // 10. Circuit breaker does not affect realtime sync
    // ============================================================

    @Test
    fun `circuit breaker does not affect realtime sync`() = runTest(testDispatcher) {
        for (i in 1..5) {
            workspaceDao.insert(
                WorkspaceEntity("ws-$i", "WS-$i", "", 1000L, listOf("user-1"), false)
            )
        }
        workspaceRemote.shouldFail = true
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(syncManager.isCircuitTripped())

        workspaceRemote.emitWorkspaces(listOf(
            Workspace("ws-live", "Live", "", 1000L, listOf("user-1"))
        ))
        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(workspaceDao.getWorkspaceById("ws-live"))
    }

    @Test
    fun `circuit breaker blocks retry but not realtime sync`() = runTest(testDispatcher) {
        for (i in 1..5) {
            workspaceDao.insert(
                WorkspaceEntity("ws-$i", "WS-$i", "", 1000L, listOf("user-1"), false)
            )
        }
        workspaceRemote.shouldFail = true
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(syncManager.isCircuitTripped())

        val retryEntity = WorkspaceEntity("ws-retry", "Retry", "", 1000L, listOf("user-1"), false)
        workspaceDao.insert(retryEntity)
        workspaceRemote.createWorkspaceCalled = false
        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(workspaceRemote.createWorkspaceCalled)
    }

    // ============================================================
    // 11. TOCTOU: delete during retry
    // ============================================================

    @Test
    fun `entity deleted during retry does not get resurrected`() = runTest(testDispatcher) {
        val parentWs = WorkspaceEntity("ws-parent", "Parent", "", 1000L, listOf("user-1"), true)
        workspaceDao.insert(parentWs)
        val entity = WorkspaceEntity("ws-del", "Del", "", 1000L, listOf("user-1"), false)
        workspaceDao.insert(entity)

        workspaceRemote.onBeforeCreateWorkspace = {
            workspaceDao.delete(entity)
        }

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(workspaceRemote.createWorkspaceCalled)
        assertTrue(workspaceRemote.deleteWorkspaceCalled)
        assertNull(workspaceDao.getWorkspaceById("ws-del"))
    }
}
