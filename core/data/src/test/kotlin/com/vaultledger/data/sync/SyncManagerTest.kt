package com.vaultledger.data.sync

import app.cash.turbine.test
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
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.domain.model.Vault
import com.vaultledger.domain.model.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workspaceRemote: FakeWorkspaceRemoteDataSource
    private lateinit var vaultRemote: FakeVaultRemoteDataSource
    private lateinit var transactionRemote: FakeTransactionRemoteDataSource
    private lateinit var workspaceDao: FakeWorkspaceDao
    private lateinit var vaultDao: FakeVaultDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var syncManager: SyncManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        workspaceRemote = FakeWorkspaceRemoteDataSource()
        vaultRemote = FakeVaultRemoteDataSource()
        transactionRemote = FakeTransactionRemoteDataSource()
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

    @Test
    fun `syncs workspace to Room when workspace is emitted`() = runTest(testDispatcher) {
        val workspace = Workspace(
            id = "ws-1",
            name = "Shared",
            description = "",
            createdAt = 1000L,
            memberIds = listOf("user-1", "user-2"),
        )
        workspaceRemote.emitWorkspaces(listOf(workspace))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val stored = workspaceDao.getAllWorkspacesSuspend()
        assertEquals(1, stored.size)
        assertEquals("ws-1", stored.first().id)
        assertEquals(listOf("user-1", "user-2"), stored.first().memberIds)
    }

    @Test
    fun `syncs vaults to Room when workspace is present`() = runTest(testDispatcher) {
        val workspace = Workspace(
            id = "ws-1",
            name = "Shared",
            description = "",
            createdAt = 1000L,
            memberIds = listOf("user-1", "user-2"),
        )
        val vault = Vault(
            id = "vault-1",
            workspaceId = "ws-1",
            name = "Test Vault",
            description = "",
            createdAt = 1000L,
            color = "#006D77",
            balance = 0L,
        )

        workspaceRemote.emitWorkspaces(listOf(workspace))
        vaultRemote.emitVaults("ws-1", listOf(vault))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val stored = vaultDao.getVaultsByWorkspaceIdSuspend("ws-1")
        assertEquals(1, stored.size)
        assertEquals("vault-1", stored.first().id)
    }

    @Test
    fun `syncs transactions to Room`() = runTest(testDispatcher) {
        val workspace = Workspace(
            id = "ws-1",
            name = "Shared",
            description = "",
            createdAt = 1000L,
            memberIds = listOf("user-1", "user-2"),
        )
        val vault = Vault(
            id = "vault-1",
            workspaceId = "ws-1",
            name = "Test Vault",
            description = "",
            createdAt = 1000L,
            color = "#006D77",
        )
        val txn = Transaction(
            id = "txn-1",
            vaultId = "vault-1",
            type = TransactionType.INFLOW,
            amount = 1000L,
            description = "Test",
            createdAt = 1000L,
        )

        workspaceRemote.emitWorkspaces(listOf(workspace))
        vaultRemote.emitVaults("ws-1", listOf(vault))
        transactionRemote.emitTransactions("ws-1", "vault-1", listOf(txn))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val stored = transactionDao.getTransactionsByVaultIdSuspend("vault-1")
        assertEquals(1, stored.size)
        assertEquals("txn-1", stored.first().id)
    }

    @Test
    fun `removes transactions that are no longer in Firestore (soft delete sync)`() = runTest(testDispatcher) {
        val workspaceId = "ws-1"
        val vaultId = "vault-1"
        val workspace = Workspace(id = workspaceId, name = "Shared", description = "", createdAt = 1000L, memberIds = listOf("user-1", "user-2"))
        val vault = Vault(id = vaultId, workspaceId = workspaceId, name = "Vault", description = "", createdAt = 1000L)

        val removedTxn = Transaction(id = "removed", vaultId = vaultId, type = TransactionType.INFLOW, amount = 500L, description = "", createdAt = 1000L)
        val keptTxn = Transaction(id = "kept", vaultId = vaultId, type = TransactionType.INFLOW, amount = 1000L, description = "", createdAt = 2000L)

        workspaceRemote.emitWorkspaces(listOf(workspace))
        vaultRemote.emitVaults(workspaceId, listOf(vault))
        // First emit both transactions
        transactionRemote.emitTransactions(workspaceId, vaultId, listOf(removedTxn, keptTxn))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)

        // Second emit with only kept transaction (simulating remote soft delete on device A)
        transactionRemote.emitTransactions(workspaceId, vaultId, listOf(keptTxn))
        testDispatcher.scheduler.advanceUntilIdle()

        val stored = transactionDao.getTransactionsByVaultIdSuspend(vaultId)
        assertEquals(1, stored.size)
        assertEquals("kept", stored.first().id)
    }

    @Test
    fun `deleted transactions are not re-inserted on repeated sync`() = runTest(testDispatcher) {
        val workspaceId = "ws-1"
        val vaultId = "vault-1"
        val workspace = Workspace(id = workspaceId, name = "Shared", description = "", createdAt = 1000L, memberIds = listOf("user-1", "user-2"))
        val vault = Vault(id = vaultId, workspaceId = workspaceId, name = "Vault", description = "", createdAt = 1000L)
        val txn = Transaction(id = "tx-1", vaultId = vaultId, type = TransactionType.INFLOW, amount = 500L, description = "", createdAt = 1000L)

        workspaceRemote.emitWorkspaces(listOf(workspace))
        vaultRemote.emitVaults(workspaceId, listOf(vault))
        transactionRemote.emitTransactions(workspaceId, vaultId, listOf(txn))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)

        // Remove transaction (simulate soft delete)
        transactionRemote.emitTransactions(workspaceId, vaultId, emptyList())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)

        // Emit same empty list again (repeated sync) - should stay deleted
        transactionRemote.emitTransactions(workspaceId, vaultId, emptyList())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)
    }

    @Test
    fun `deleted transactions stay deleted after sync restart`() = runTest(testDispatcher) {
        val workspaceId = "ws-1"
        val vaultId = "vault-1"
        val workspace = Workspace(id = workspaceId, name = "Shared", description = "", createdAt = 1000L, memberIds = listOf("user-1", "user-2"))
        val vault = Vault(id = vaultId, workspaceId = workspaceId, name = "Vault", description = "", createdAt = 1000L)
        val txn = Transaction(id = "tx-1", vaultId = vaultId, type = TransactionType.INFLOW, amount = 500L, description = "", createdAt = 1000L)

        workspaceRemote.emitWorkspaces(listOf(workspace))
        vaultRemote.emitVaults(workspaceId, listOf(vault))
        transactionRemote.emitTransactions(workspaceId, vaultId, listOf(txn))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)

        // Simulate soft delete
        transactionRemote.emitTransactions(workspaceId, vaultId, emptyList())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)

        // Simulate app restart: stop and restart syncing
        syncManager.stopSyncing()
        workspaceRemote.emitWorkspaces(listOf(workspace))
        vaultRemote.emitVaults(workspaceId, listOf(vault))
        transactionRemote.emitTransactions(workspaceId, vaultId, emptyList())

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)
    }

    @Test
    fun `recomputes balance after transaction sync`() = runTest(testDispatcher) {
        val workspaceId = "ws-1"
        val vaultId = "vault-1"
        val workspace = Workspace(id = workspaceId, name = "Shared", description = "", createdAt = 1000L, memberIds = listOf("user-1", "user-2"))
        val vault = Vault(id = vaultId, workspaceId = workspaceId, name = "Vault", description = "", createdAt = 1000L)

        val inflow = Transaction(id = "in-1", vaultId = vaultId, type = TransactionType.INFLOW, amount = 2000L, description = "", createdAt = 1000L)
        val outflow = Transaction(id = "out-1", vaultId = vaultId, type = TransactionType.OUTFLOW, amount = 500L, description = "", createdAt = 2000L)

        workspaceRemote.emitWorkspaces(listOf(workspace))
        vaultRemote.emitVaults(workspaceId, listOf(vault))
        transactionRemote.emitTransactions(workspaceId, vaultId, listOf(inflow, outflow))

        syncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val balance = vaultDao.getBalanceSuspend(vaultId)
        assertEquals(1500L, balance)
    }

    @Test
    fun `sync transaction job is cleaned up on flow completion and restarts on re-emission`() = runTest(testDispatcher) {
        val workspaceId = "ws-1"
        val vaultId = "vault-1"
        val workspace = Workspace(id = workspaceId, name = "Shared", description = "", createdAt = 1000L, memberIds = listOf("user-1", "user-2"))
        val vault = Vault(id = vaultId, workspaceId = workspaceId, name = "Vault", description = "", createdAt = 1000L)
        val txn1 = Transaction(id = "tx-1", vaultId = vaultId, type = TransactionType.INFLOW, amount = 500L, description = "", createdAt = 1000L)
        val txn2 = Transaction(id = "tx-2", vaultId = vaultId, type = TransactionType.INFLOW, amount = 1000L, description = "", createdAt = 2000L)

        workspaceRemote.emitWorkspaces(listOf(workspace))
        vaultRemote.emitVaults(workspaceId, listOf(vault))

        var callCount = 0
        val completingTransactionRemote = object : TransactionRemoteDataSource() {
            override fun observeNonDeletedTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flow {
                callCount++
                val txn = if (callCount == 1) txn1 else txn2
                emit(listOf(txn))
            }
            override fun observeAllTransactions(wsId: String, vId: String): Flow<List<Transaction>> = flowOf(emptyList())
            override suspend fun createTransaction(wsId: String, vId: String, transaction: Transaction, createdBy: String) {}
            override suspend fun softDeleteTransaction(wsId: String, vId: String, transactionId: String) {}
        }

        val customSyncManager = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = vaultRemote,
            transactionRemoteDataSource = completingTransactionRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        customSyncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)
        assertEquals(1, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)
        assertEquals("tx-1", transactionDao.getTransactionsByVaultIdSuspend(vaultId).first().id)

        vaultRemote.emitVaults(workspaceId, emptyList())
        testDispatcher.scheduler.advanceUntilIdle()
        vaultRemote.emitVaults(workspaceId, listOf(vault))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, callCount)
        assertEquals(1, transactionDao.getTransactionsByVaultIdSuspend(vaultId).size)
        assertEquals("tx-2", transactionDao.getTransactionsByVaultIdSuspend(vaultId).first().id)
    }

    @Test
    fun `sync vault job is cleaned up on flow completion and restarts on workspace re-emission`() = runTest(testDispatcher) {
        val workspaceId = "ws-1"
        val vaultId = "vault-1"
        val workspace = Workspace(id = workspaceId, name = "Shared", description = "", createdAt = 1000L, memberIds = listOf("user-1", "user-2"))
        val vault = Vault(id = vaultId, workspaceId = workspaceId, name = "Vault", description = "", createdAt = 1000L)

        workspaceRemote.emitWorkspaces(listOf(workspace))

        var callCount = 0
        val completingVaultRemote = object : VaultRemoteDataSource() {
            override fun observeVaults(wsId: String): Flow<List<Vault>> = flow {
                callCount++
                emit(listOf(vault))
            }
        }

        val customSyncManager = SyncManager(
            workspaceRemoteDataSource = workspaceRemote,
            vaultRemoteDataSource = completingVaultRemote,
            transactionRemoteDataSource = transactionRemote,
            workspaceDao = workspaceDao,
            vaultDao = vaultDao,
            transactionDao = transactionDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + testDispatcher) }

        customSyncManager.startSyncing("user-1", retryUnsynced = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)

        workspaceRemote.emitWorkspaces(emptyList())
        testDispatcher.scheduler.advanceUntilIdle()
        workspaceRemote.emitWorkspaces(listOf(workspace))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, callCount)
    }

    @Test
    fun `retryUnsyncedWorkspaces creates unsynced workspace in Firestore`() = runTest(testDispatcher) {
        val entity = WorkspaceEntity(
            id = "ws-unsynced",
            name = "Offline WS",
            description = "",
            createdAt = 1000L,
            memberIds = listOf("user-1"),
            synced = false,
        )
        workspaceDao.insert(entity)

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(workspaceRemote.createWorkspaceCalled)
        val stored = workspaceDao.getWorkspaceById("ws-unsynced")
        assertTrue(stored?.synced ?: false)
    }

    @Test
    fun `retryUnsyncedWorkspaces skips deleted workspace`() = runTest(testDispatcher) {
        val entity = WorkspaceEntity(
            id = "ws-deleted",
            name = "Deleted WS",
            description = "",
            createdAt = 1000L,
            memberIds = listOf("user-1"),
            synced = false,
        )
        workspaceDao.insert(entity)
        workspaceDao.delete(entity)

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(workspaceRemote.createWorkspaceCalled)
    }

    @Test
    fun `retryUnsyncedWorkspaces compensates delete when workspace deleted during write`() = runTest(testDispatcher) {
        val entity = WorkspaceEntity(
            id = "ws-comp",
            name = "Comp WS",
            description = "",
            createdAt = 1000L,
            memberIds = listOf("user-1"),
            synced = false,
        )
        workspaceDao.insert(entity)
        workspaceRemote.onBeforeCreateWorkspace = {
            workspaceDao.delete(entity)
        }

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(workspaceRemote.createWorkspaceCalled)
        assertTrue(workspaceRemote.deleteWorkspaceCalled)
    }

    @Test
    fun `retryUnsyncedWorkspaces leaves synced false on failure`() = runTest(testDispatcher) {
        val entity = WorkspaceEntity(
            id = "ws-fail",
            name = "Fail WS",
            description = "",
            createdAt = 1000L,
            memberIds = listOf("user-1"),
            synced = false,
        )
        workspaceDao.insert(entity)
        workspaceRemote.shouldFail = true

        syncManager.retryUnsyncedWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        val stored = workspaceDao.getWorkspaceById("ws-fail")
        assertFalse(stored?.synced ?: true)
    }

    @Test
    fun `retryUnsyncedVaults creates unsynced vault in Firestore`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity(id = "ws-1", name = "WS", description = "", createdAt = 1000L, synced = true)
        workspaceDao.insert(wsEntity)
        val entity = VaultEntity(
            id = "vault-unsynced",
            workspaceId = "ws-1",
            name = "Offline Vault",
            description = "",
            createdAt = 1000L,
            color = "#006D77",
            synced = false,
        )
        vaultDao.insert(entity)

        syncManager.retryUnsyncedVaults()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vaultRemote.createVaultCalled)
        val stored = vaultDao.getVaultById("vault-unsynced")
        assertTrue(stored?.synced ?: false)
    }

    @Test
    fun `retryUnsyncedVaults skips deleted vault`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity(id = "ws-1", name = "WS", description = "", createdAt = 1000L, synced = true)
        workspaceDao.insert(wsEntity)
        val entity = VaultEntity(
            id = "vault-deleted", workspaceId = "ws-1", name = "Deleted", description = "", createdAt = 1000L, synced = false,
        )
        vaultDao.insert(entity)
        vaultDao.delete(entity)

        syncManager.retryUnsyncedVaults()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vaultRemote.createVaultCalled)
    }

    @Test
    fun `retryUnsyncedVaults compensates delete when vault deleted during write`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity(id = "ws-1", name = "WS", description = "", createdAt = 1000L, synced = true)
        workspaceDao.insert(wsEntity)
        val entity = VaultEntity(
            id = "vault-comp", workspaceId = "ws-1", name = "Comp", description = "", createdAt = 1000L, synced = false,
        )
        vaultDao.insert(entity)
        vaultRemote.onBeforeCreateVault = {
            vaultDao.delete(entity)
        }

        syncManager.retryUnsyncedVaults()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vaultRemote.createVaultCalled)
        assertTrue(vaultRemote.deleteVaultCalled)
    }

    @Test
    fun `retryUnsyncedVaults leaves synced false on failure`() = runTest(testDispatcher) {
        val wsEntity = WorkspaceEntity(id = "ws-1", name = "WS", description = "", createdAt = 1000L, synced = true)
        workspaceDao.insert(wsEntity)
        val entity = VaultEntity(
            id = "vault-fail", workspaceId = "ws-1", name = "Fail", description = "", createdAt = 1000L, synced = false,
        )
        vaultDao.insert(entity)
        vaultRemote.shouldFail = true

        syncManager.retryUnsyncedVaults()
        testDispatcher.scheduler.advanceUntilIdle()

        val stored = vaultDao.getVaultById("vault-fail")
        assertFalse(stored?.synced ?: true)
    }
}

// ===== Fake Implementations =====

class FakeWorkspaceRemoteDataSource : WorkspaceRemoteDataSource() {
    private val _workspaces = MutableSharedFlow<List<Workspace>>(replay = 1, extraBufferCapacity = 1)
    var createWorkspaceCalled = false
    var deleteWorkspaceCalled = false
    var updateWorkspaceCalled = false
    var shouldFail = false
    var onBeforeCreateWorkspace: suspend () -> Unit = {}

    fun emitWorkspaces(workspaces: List<Workspace>) {
        _workspaces.tryEmit(workspaces)
    }

    override fun observeWorkspacesForMember(memberId: String): Flow<List<Workspace>> {
        return _workspaces
    }

    override suspend fun createWorkspace(workspace: Workspace, creatorId: String) {
        onBeforeCreateWorkspace()
        createWorkspaceCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }

    override suspend fun deleteWorkspace(workspaceId: String) {
        deleteWorkspaceCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }

    override suspend fun updateWorkspace(workspace: Workspace) {
        updateWorkspaceCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }
}

class FakeVaultRemoteDataSource : VaultRemoteDataSource() {
    private val streams = mutableMapOf<String, MutableSharedFlow<List<Vault>>>()
    var createVaultCalled = false
    var deleteVaultCalled = false
    var shouldFail = false
    var onBeforeCreateVault: suspend () -> Unit = {}

    fun emitVaults(workspaceId: String, vaults: List<Vault>) {
        streams.getOrPut(workspaceId) { MutableSharedFlow<List<Vault>>(replay = 1, extraBufferCapacity = 1) }
            .tryEmit(vaults)
    }

    override fun observeVaults(workspaceId: String): Flow<List<Vault>> {
        return streams.getOrPut(workspaceId) { MutableSharedFlow<List<Vault>>(replay = 1, extraBufferCapacity = 1) }
    }

    override suspend fun createVault(workspaceId: String, vault: Vault) {
        onBeforeCreateVault()
        createVaultCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }

    override suspend fun deleteVault(workspaceId: String, vaultId: String) {
        deleteVaultCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }
}

class FakeTransactionRemoteDataSource : TransactionRemoteDataSource() {
    private val streams = mutableMapOf<String, MutableStateFlow<List<Transaction>>>()

    fun emitTransactions(workspaceId: String, vaultId: String, transactions: List<Transaction>) {
        val key = "$workspaceId/$vaultId"
        streams.getOrPut(key) { MutableStateFlow(emptyList()) }.value = transactions
    }

    override fun observeNonDeletedTransactions(workspaceId: String, vaultId: String): Flow<List<Transaction>> {
        val key = "$workspaceId/$vaultId"
        return streams.getOrPut(key) { MutableStateFlow(emptyList()) }
    }

    override fun observeAllTransactions(workspaceId: String, vaultId: String): Flow<List<Transaction>> {
        val key = "$workspaceId/$vaultId"
        return streams.getOrPut(key) { MutableStateFlow(emptyList()) }
    }

    override suspend fun createTransaction(workspaceId: String, vaultId: String, transaction: Transaction, createdBy: String) {}
    override suspend fun softDeleteTransaction(workspaceId: String, vaultId: String, transactionId: String) {}
}

class FakeWorkspaceDao : WorkspaceDao {
    private val workspaces = mutableMapOf<String, WorkspaceEntity>()

    override suspend fun insert(workspace: WorkspaceEntity) { workspaces[workspace.id] = workspace }
    override suspend fun insertAll(workspaces: List<WorkspaceEntity>) { workspaces.forEach { insert(it) } }
    override suspend fun update(workspace: WorkspaceEntity) { workspaces[workspace.id] = workspace }
    override suspend fun delete(workspace: WorkspaceEntity) { workspaces.remove(workspace.id) }
    override fun getAllWorkspaces(): Flow<List<WorkspaceEntity>> = flowOf(workspaces.values.toList())
    override suspend fun getWorkspaceById(id: String): WorkspaceEntity? = workspaces[id]
    override suspend fun getUnsyncedWorkspaces(): List<WorkspaceEntity> = workspaces.values.filter { !it.synced }

    fun getAllWorkspacesSuspend(): List<WorkspaceEntity> = workspaces.values.toList()
}

class FakeVaultDao : VaultDao {
    private val vaults = mutableMapOf<String, VaultEntity>()
    private var balanceMap = mutableMapOf<String, Long>()

    override suspend fun insert(vault: VaultEntity) { vaults[vault.id] = vault }
    override suspend fun update(vault: VaultEntity) { vaults[vault.id] = vault }
    override suspend fun delete(vault: VaultEntity) { vaults.remove(vault.id) }
    override fun getVaultsByWorkspaceId(workspaceId: String): Flow<List<VaultEntity>> {
        return flowOf(vaults.values.filter { it.workspaceId == workspaceId })
    }
    override suspend fun getVaultById(id: String): VaultEntity? = vaults[id]
    override suspend fun getUnsyncedVaults(): List<VaultEntity> = vaults.values.filter { !it.synced }
    override suspend fun updateBalance(id: String, balance: Long) { vaults[id]?.let { vaults[id] = it.copy(balance = balance) }; balanceMap[id] = balance }

    fun getVaultsByWorkspaceIdSuspend(workspaceId: String): List<VaultEntity> = vaults.values.filter { it.workspaceId == workspaceId }
    fun getBalanceSuspend(id: String): Long = balanceMap[id] ?: vaults[id]?.balance ?: 0L
}

class FakeTransactionDao : TransactionDao {
    private val transactions = mutableMapOf<String, TransactionEntity>()

    override suspend fun insert(transaction: TransactionEntity) { transactions[transaction.id] = transaction }
    override suspend fun insertAll(transactions: List<TransactionEntity>) { transactions.forEach { insert(it) } }
    override suspend fun update(transaction: TransactionEntity) { transactions[transaction.id] = transaction }
    override suspend fun delete(transaction: TransactionEntity) { transactions.remove(transaction.id) }
    override suspend fun deleteById(id: String) { transactions.remove(id) }
    override fun getTransactionsByVaultId(vaultId: String): Flow<List<TransactionEntity>> {
        return flowOf(transactions.values.filter { it.vaultId == vaultId }.sortedByDescending { it.createdAt })
    }
    override suspend fun getTransactionById(id: String): TransactionEntity? = transactions[id]
    override suspend fun getTransactionIdsByVaultId(vaultId: String): List<String> {
        return transactions.values.filter { it.vaultId == vaultId }.map { it.id }
    }
    override suspend fun getBalanceForVault(vaultId: String): Long {
        return transactions.values.filter { it.vaultId == vaultId }
            .sumOf { if (it.type == TransactionType.INFLOW) it.amount else -it.amount }
    }
    override suspend fun getUnsyncedTransactions(): List<TransactionEntity> {
        return transactions.values.filter { !it.synced }
    }

    override fun searchTransactions(vaultId: String, query: String): Flow<List<TransactionEntity>> = searchTransactionsInternal(vaultId, query.trim())
    override fun searchTransactionsInternal(vaultId: String, query: String): Flow<List<TransactionEntity>> {
        val lowerQuery = query.lowercase()
        return flowOf(transactions.values.filter { it.vaultId == vaultId }
            .filter { it.description.lowercase().contains(lowerQuery) || it.amount.toString().contains(lowerQuery) }
            .sortedByDescending { it.createdAt })
    }

    override fun observeBalanceForVault(vaultId: String): Flow<Long> {
        return flowOf(getBalanceForVaultSuspend(vaultId))
    }

    fun getTransactionsByVaultIdSuspend(vaultId: String): List<TransactionEntity> =
        transactions.values.filter { it.vaultId == vaultId }

    private fun getBalanceForVaultSuspend(vaultId: String): Long {
        return transactions.values.filter { it.vaultId == vaultId }
            .sumOf { if (it.type == TransactionType.INFLOW) it.amount else -it.amount }
    }
}
