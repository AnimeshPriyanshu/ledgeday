package com.vaultledger

import com.vaultledger.data.local.dao.TransactionDao
import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.dao.WorkspaceDao
import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.local.entity.WorkspaceEntity
import com.vaultledger.data.remote.TransactionRemoteDataSource
import com.vaultledger.data.remote.VaultRemoteDataSource
import com.vaultledger.data.remote.WorkspaceRemoteDataSource
import com.vaultledger.data.sync.SyncManager
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.Vault
import com.vaultledger.domain.model.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var syncManager: FakeSyncManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
        syncManager = FakeSyncManager()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AppViewModel(authRepository, syncManager)

    @Test
    fun `isAuthenticated starts as null`() = runTest(testDispatcher) {
        val vm = createViewModel()

        assertNull(vm.isAuthenticated.value)
    }

    @Test
    fun `isAuthenticated becomes true when user signs in`() = runTest(testDispatcher) {
        val vm = createViewModel()

        authRepository.signIn("test@example.com", "password123")
        advanceUntilIdle()

        assertTrue(vm.isAuthenticated.value == true)
    }

    @Test
    fun `isAuthenticated becomes false when user signs out`() = runTest(testDispatcher) {
        authRepository.signIn("test@example.com", "password123")
        advanceUntilIdle()

        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.isAuthenticated.value == true)

        authRepository.signOut()
        advanceUntilIdle()

        assertTrue(vm.isAuthenticated.value == false)
    }

    @Test
    fun `isAuthenticated stays null when no auth state emission`() = runTest(testDispatcher) {
        val vm = createViewModel()

        assertNull(vm.isAuthenticated.value)
    }
}

// ===== Stub implementations =====

private class FakeWorkspaceRemoteDataSource : WorkspaceRemoteDataSource() {
    override fun observeWorkspacesForMember(memberId: String): Flow<List<Workspace>> = flowOf(emptyList())
}

private class FakeVaultRemoteDataSource : VaultRemoteDataSource() {
    override fun observeVaults(workspaceId: String): Flow<List<Vault>> = flowOf(emptyList())
}

private class FakeTransactionRemoteDataSource : TransactionRemoteDataSource() {
    override fun observeNonDeletedTransactions(workspaceId: String, vaultId: String): Flow<List<Transaction>> = flowOf(emptyList())
    override fun observeAllTransactions(workspaceId: String, vaultId: String): Flow<List<Transaction>> = flowOf(emptyList())
    override suspend fun createTransaction(workspaceId: String, vaultId: String, transaction: Transaction, createdBy: String) {}
    override suspend fun softDeleteTransaction(workspaceId: String, vaultId: String, transactionId: String) {}
}

private class FakeWorkspaceDao : WorkspaceDao {
    private val data = mutableMapOf<String, WorkspaceEntity>()
    override suspend fun insert(workspace: WorkspaceEntity) { data[workspace.id] = workspace }
    override suspend fun insertAll(workspaces: List<WorkspaceEntity>) { workspaces.forEach { insert(it) } }
    override suspend fun update(workspace: WorkspaceEntity) { data[workspace.id] = workspace }
    override suspend fun delete(workspace: WorkspaceEntity) { data.remove(workspace.id) }
    override fun getAllWorkspaces(): Flow<List<WorkspaceEntity>> = flowOf(data.values.toList())
    override suspend fun getWorkspaceById(id: String): WorkspaceEntity? = data[id]
    override suspend fun getUnsyncedWorkspaces(): List<WorkspaceEntity> = data.values.filter { !it.synced }
    override suspend fun getWorkspaceIdsWithEmptyMemberIds(emptyList: String): List<String> = data.values.filter { it.memberIds.isEmpty() }.map { it.id }
}

private class FakeVaultDao : VaultDao {
    private val data = mutableMapOf<String, VaultEntity>()
    override suspend fun insert(vault: VaultEntity) { data[vault.id] = vault }
    override suspend fun update(vault: VaultEntity) { data[vault.id] = vault }
    override suspend fun delete(vault: VaultEntity) { data.remove(vault.id) }
    override fun getVaultsByWorkspaceId(workspaceId: String): Flow<List<VaultEntity>> = flowOf(data.values.filter { it.workspaceId == workspaceId })
    override suspend fun getVaultById(id: String): VaultEntity? = data[id]
    override suspend fun updateBalance(id: String, balance: Long) { data[id]?.let { data[id] = it.copy(balance = balance) } }
}

private class FakeTransactionDao : TransactionDao {
    private val data = mutableMapOf<String, TransactionEntity>()
    override suspend fun insert(transaction: TransactionEntity) { data[transaction.id] = transaction }
    override suspend fun insertAll(transactions: List<TransactionEntity>) { transactions.forEach { insert(it) } }
    override suspend fun update(transaction: TransactionEntity) { data[transaction.id] = transaction }
    override suspend fun delete(transaction: TransactionEntity) { data.remove(transaction.id) }
    override suspend fun deleteById(id: String) { data.remove(id) }
    override fun getTransactionsByVaultId(vaultId: String): Flow<List<TransactionEntity>> = flowOf(data.values.filter { it.vaultId == vaultId })
    override suspend fun getTransactionById(id: String): TransactionEntity? = data[id]
    override suspend fun getTransactionIdsByVaultId(vaultId: String): List<String> = data.values.filter { it.vaultId == vaultId }.map { it.id }
    override suspend fun getUnsyncedTransactions(): List<TransactionEntity> = data.values.filter { !it.synced }
    override fun searchTransactions(vaultId: String, query: String): Flow<List<TransactionEntity>> {
        val lowerQuery = query.lowercase()
        return flowOf(data.values.filter { it.vaultId == vaultId }
            .filter { it.description.lowercase().contains(lowerQuery) || it.amount.toString().contains(lowerQuery) }
            .sortedByDescending { it.createdAt })
    }
    override suspend fun getBalanceForVault(vaultId: String): Long = data.values.filter { it.vaultId == vaultId }.sumOf { if (it.type == com.vaultledger.domain.model.TransactionType.INFLOW) it.amount else -it.amount }
    override fun observeBalanceForVault(vaultId: String): Flow<Long> = flowOf(getBalanceForVaultSuspend(vaultId))
    private fun getBalanceForVaultSuspend(vaultId: String): Long = data.values.filter { it.vaultId == vaultId }.sumOf { if (it.type == com.vaultledger.domain.model.TransactionType.INFLOW) it.amount else -it.amount }
}

class FakeSyncManager(
    workspaceRemoteDataSource: WorkspaceRemoteDataSource = FakeWorkspaceRemoteDataSource(),
    vaultRemoteDataSource: VaultRemoteDataSource = FakeVaultRemoteDataSource(),
    transactionRemoteDataSource: TransactionRemoteDataSource = FakeTransactionRemoteDataSource(),
    workspaceDao: WorkspaceDao = FakeWorkspaceDao(),
    vaultDao: VaultDao = FakeVaultDao(),
    transactionDao: TransactionDao = FakeTransactionDao(),
) : SyncManager(
    workspaceRemoteDataSource,
    vaultRemoteDataSource,
    transactionRemoteDataSource,
    workspaceDao,
    vaultDao,
    transactionDao,
) {
    var syncing = false
    var uid: String? = null

    override fun startSyncing(uid: String, retryUnsynced: Boolean) {
        syncing = true
        this.uid = uid
    }

    override fun stopSyncing() {
        syncing = false
        uid = null
    }
}
