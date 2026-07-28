package com.vaultledger.data.repository

import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.local.dao.TransactionDao
import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.dao.WorkspaceDao
import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.remote.TransactionRemoteDataSource
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TransactionRepositoryImplSyncTest {

    private lateinit var txnDao: FakeTransactionDaoSync
    private lateinit var vaultDao: FakeVaultDaoValidation
    private lateinit var repository: TransactionRepositoryImpl

    @BeforeEach
    fun setUp() {
        txnDao = FakeTransactionDaoSync()
        vaultDao = FakeVaultDaoValidation()
        repository = TransactionRepositoryImpl(
            transactionDao = txnDao,
            vaultDao = vaultDao,
            database = FakeVaultLedgerDatabase(),
            transactionRemoteDataSource = null,
            firebaseAuth = null,
        ).also {
            it.db = null
            it.syncScope.cancel()
        }
    }

    @Test
    fun `createTransaction throws for negative amount`() = runTest {
        vaultDao.insert(VaultEntity(id = "vault-1", workspaceId = "ws-1", name = "Vault", description = "", createdAt = 1000L))
        assertThrows<IllegalArgumentException> {
            repository.createTransaction(vaultId = "vault-1", type = TransactionType.INFLOW, amount = -1, description = "test")
        }
    }

    @Test
    fun `createTransaction throws for nonexistent vault`() = runTest {
        assertThrows<IllegalArgumentException> {
            repository.createTransaction(vaultId = "no-such-vault", type = TransactionType.INFLOW, amount = 100, description = "test")
        }
    }
}

class FakeTransactionDaoSync : TransactionDao {
    val data = mutableMapOf<String, TransactionEntity>()

    override suspend fun insert(transaction: TransactionEntity) { data[transaction.id] = transaction }
    override suspend fun insertAll(transactions: List<TransactionEntity>) { transactions.forEach { insert(it) } }
    override suspend fun update(transaction: TransactionEntity) { data[transaction.id] = transaction }
    override suspend fun delete(transaction: TransactionEntity) { data.remove(transaction.id) }
    override suspend fun deleteById(id: String) { data.remove(id) }
    override fun getTransactionsByVaultId(vaultId: String): Flow<List<TransactionEntity>> = flowOf(data.values.filter { it.vaultId == vaultId }.sortedByDescending { it.createdAt })
    override suspend fun getTransactionById(id: String): TransactionEntity? = data[id]
    override suspend fun getTransactionIdsByVaultId(vaultId: String): List<String> = data.values.filter { it.vaultId == vaultId }.map { it.id }
    override suspend fun getUnsyncedTransactions(): List<TransactionEntity> = data.values.filter { !it.synced }
    override fun searchTransactions(vaultId: String, query: String): Flow<List<TransactionEntity>> = searchTransactionsInternal(vaultId, query)
    override fun searchTransactionsInternal(vaultId: String, query: String): Flow<List<TransactionEntity>> = flowOf(emptyList())
    override suspend fun getBalanceForVault(vaultId: String): Long = data.values.filter { it.vaultId == vaultId }.sumOf { if (it.type == TransactionType.INFLOW) it.amount else -it.amount }
    override fun observeBalanceForVault(vaultId: String): Flow<Long> = flowOf(data.values.filter { it.vaultId == vaultId }.sumOf { if (it.type == TransactionType.INFLOW) it.amount else -it.amount })
}

class FakeVaultLedgerDatabase : VaultLedgerDatabase() {
    override fun vaultDao(): VaultDao = error("not needed in test; db is nulled")
    override fun workspaceDao(): WorkspaceDao = error("not needed in test; db is nulled")
    override fun transactionDao(): TransactionDao = error("not needed in test; db is nulled")
    override fun createInvalidationTracker() = error("not needed in test; db is nulled")
    override fun clearAllTables() = Unit
}

class FakeVaultDaoValidation : VaultDao {
    private val data = mutableMapOf<String, VaultEntity>()

    override suspend fun insert(vault: VaultEntity) { data[vault.id] = vault }
    override suspend fun update(vault: VaultEntity) { data[vault.id] = vault }
    override suspend fun delete(vault: VaultEntity) { data.remove(vault.id) }
    override fun getVaultsByWorkspaceId(workspaceId: String): Flow<List<VaultEntity>> = flowOf(data.values.filter { it.workspaceId == workspaceId })
    override suspend fun getVaultById(id: String): VaultEntity? = data[id]
    override suspend fun getUnsyncedVaults(): List<VaultEntity> = data.values.filter { !it.synced }
    override suspend fun updateBalance(id: String, balance: Long) { data[id]?.let { data[id] = it.copy(balance = balance) } }
}
