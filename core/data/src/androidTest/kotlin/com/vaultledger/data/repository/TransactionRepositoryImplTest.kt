package com.vaultledger.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.local.entity.WorkspaceEntity
import com.vaultledger.data.remote.TransactionRemoteDataSource
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionRepositoryImplTest {

    private lateinit var database: VaultLedgerDatabase
    private lateinit var repository: TransactionRepositoryImpl

    private val vaultId = "test-vault"

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, VaultLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            vaultDao = database.vaultDao(),
            database = database,
        )
        seedVault()
    }

    private suspend fun seedVault() {
        database.workspaceDao().insert(
            WorkspaceEntity(
                id = "test-workspace",
                name = "Test Workspace",
                description = "",
                createdAt = System.currentTimeMillis(),
            ),
        )
        database.vaultDao().insert(
            VaultEntity(
                id = vaultId,
                workspaceId = "test-workspace",
                name = "Test Vault",
                description = "",
                createdAt = System.currentTimeMillis(),
                balance = 0L,
                color = "#006D77",
            ),
        )
    }

    @After
    fun tearDown() {
        if (::repository.isInitialized) {
            repository.syncScope.cancel()
        }
        database.close()
    }

    @Test
    fun createTransaction_createsInflowAndUpdatesBalance() = runBlocking {
        val txn = repository.createTransaction(
            vaultId = vaultId,
            type = TransactionType.INFLOW,
            amount = 1000L,
            description = "Deposit",
        )

        assertNotNull(txn.id)
        assertEquals(TransactionType.INFLOW, txn.type)
        assertEquals(1000L, txn.amount)
        assertEquals("Deposit", txn.description)

        val balance = repository.getVaultBalance(vaultId).first()
        assertEquals(1000L, balance)
    }

    @Test
    fun createTransaction_createsOutflowAndUpdatesBalance() = runBlocking {
        repository.createTransaction(
            vaultId = vaultId,
            type = TransactionType.OUTFLOW,
            amount = 500L,
            description = "Withdrawal",
        )

        val balance = repository.getVaultBalance(vaultId).first()
        assertEquals(-500L, balance)
    }

    @Test
    fun getTransactionsByVaultId_returnsTransactionsInReverseChronologicalOrder() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "First")
        repository.createTransaction(vaultId, TransactionType.INFLOW, 200L, "Second")

        val transactions = repository.getTransactionsByVaultId(vaultId).first()
        assertEquals(2, transactions.size)
        assertEquals("Second", transactions[0].description)
        assertEquals("First", transactions[1].description)
    }

    @Test
    fun getTransactionById_returnsCreatedTransaction() = runBlocking {
        val created = repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "Test")

        val retrieved = repository.getTransactionById(created.id)

        assertNotNull(retrieved)
        assertEquals(created.id, retrieved!!.id)
        assertEquals(100L, retrieved.amount)
    }

    @Test
    fun getTransactionById_returnsNullForNonExistentId() = runBlocking {
        val result = repository.getTransactionById("non-existent")
        assertNull(result)
    }

    @Test
    fun updateTransaction_modifiesTransactionAndRecalculatesBalance() = runBlocking {
        val txn = repository.createTransaction(vaultId, TransactionType.INFLOW, 500L, "Original")

        repository.updateTransaction(
            txn.copy(amount = 1000L, description = "Updated"),
        )

        val retrieved = repository.getTransactionById(txn.id)
        assertEquals(1000L, retrieved!!.amount)
        assertEquals("Updated", retrieved.description)

        val balance = repository.getVaultBalance(vaultId).first()
        assertEquals(1000L, balance)
    }

    @Test
    fun deleteTransaction_removesTransactionAndRecalculatesBalance() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 1000L, "Deposit")

        val secondId = repository.createTransaction(
            vaultId, TransactionType.OUTFLOW, 300L, "Withdrawal",
        ).id

        repository.deleteTransaction(secondId)

        val transactions = repository.getTransactionsByVaultId(vaultId).first()
        assertEquals(1, transactions.size)

        val balance = repository.getVaultBalance(vaultId).first()
        assertEquals(1000L, balance)
    }

    @Test
    fun balanceCacheConsistency_withMultipleInserts() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 1000L, "Salary")
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 200L, "Groceries")
        repository.createTransaction(vaultId, TransactionType.INFLOW, 500L, "Freelance")

        val balance = repository.getVaultBalance(vaultId).first()
        assertEquals(1300L, balance)
    }

    @Test
    fun balanceCacheConsistency_withUpdate() = runBlocking {
        val txn = repository.createTransaction(vaultId, TransactionType.INFLOW, 500L, "Initial")

        assertEquals(500L, repository.getVaultBalance(vaultId).first())

        repository.updateTransaction(txn.copy(amount = 1000L, type = TransactionType.INFLOW))

        assertEquals(1000L, repository.getVaultBalance(vaultId).first())
    }

    @Test
    fun balanceCacheConsistency_withDelete() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 1000L, "A")
        val txnB = repository.createTransaction(vaultId, TransactionType.OUTFLOW, 400L, "B")
        repository.createTransaction(vaultId, TransactionType.INFLOW, 200L, "C")

        assertEquals(800L, repository.getVaultBalance(vaultId).first())

        repository.deleteTransaction(txnB.id)

        assertEquals(1200L, repository.getVaultBalance(vaultId).first())
    }

    @Test
    fun getTransactionsByVaultId_isEmptyForNonExistentVault() = runBlocking {
        val transactions = repository.getTransactionsByVaultId("non-existent").first()
        assertTrue(transactions.isEmpty())
    }

    @Test
    fun balanceIsZero_forVaultWithNoTransactions() = runBlocking {
        val balance = repository.getVaultBalance(vaultId).first()
        assertEquals(0L, balance)
    }

    @Test
    fun createTransaction_returnsImmediately_whenFirestoreIsOffline() = runBlocking {
        val neverCompletingRemote = object : TransactionRemoteDataSource() {
            override suspend fun createTransaction(
                workspaceId: String,
                vaultId: String,
                transaction: Transaction,
                createdBy: String,
            ) {
                awaitCancellation()
            }
        }

        val offlineRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            vaultDao = database.vaultDao(),
            database = database,
            transactionRemoteDataSource = neverCompletingRemote,
            firebaseAuth = null,
        )

        val result = withTimeoutOrNull(2000) {
            offlineRepository.createTransaction(
                vaultId = vaultId,
                type = TransactionType.INFLOW,
                amount = 1000L,
                description = "Offline test",
            )
        }

        assertNotNull("createTransaction must return immediately even when Firestore is down", result)
        assertEquals(1000L, result!!.amount)

        val balance = offlineRepository.getVaultBalance(vaultId).first()
        assertEquals(1000L, balance)

        offlineRepository.syncScope.cancel()
    }

    @Test
    fun createTransaction_createsWithSyncedFalse() = runBlocking {
        val txn = repository.createTransaction(
            vaultId = vaultId,
            type = TransactionType.INFLOW,
            amount = 500L,
            description = "Test synced flag",
        )

        val entity = database.transactionDao().getTransactionById(txn.id)
        assertNotNull(entity)
        assertFalse("New transaction must have synced = false", entity!!.synced)
    }

    @Test
    fun updateTransaction_preservesSyncedState() = runBlocking {
        val txn = repository.createTransaction(
            vaultId = vaultId,
            type = TransactionType.INFLOW,
            amount = 500L,
            description = "Original",
        )

        repository.updateTransaction(
            txn.copy(amount = 1000L, description = "Updated"),
        )

        val entity = database.transactionDao().getTransactionById(txn.id)
        assertNotNull(entity)
        assertFalse("Updated transaction must have synced = false until Firestore confirms", entity!!.synced)
    }

    @Test
    fun searchTransactions_emptyQuery_returnsAll() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "Alpha")
        repository.createTransaction(vaultId, TransactionType.INFLOW, 200L, "Beta")

        val results = repository.searchTransactions(vaultId, "").first()

        assertEquals(2, results.size)
    }

    @Test
    fun searchTransactions_matchesDescription() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "groceries")
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 50L, "gas bill")

        val results = repository.searchTransactions(vaultId, "groceries").first()

        assertEquals(1, results.size)
        assertEquals("groceries", results[0].description)
    }

    @Test
    fun searchTransactions_caseInsensitive() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "Groceries")

        val results = repository.searchTransactions(vaultId, "groceries").first()

        assertEquals(1, results.size)
    }

    @Test
    fun searchTransactions_matchesPartialDescription() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "weekly groceries")

        val results = repository.searchTransactions(vaultId, "grocer").first()

        assertEquals(1, results.size)
    }

    @Test
    fun searchTransactions_matchesAmount() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 15000L, "Salary")

        val results = repository.searchTransactions(vaultId, "15000").first()

        assertEquals(1, results.size)
    }

    @Test
    fun searchTransactions_whitespaceIgnored() = runBlocking {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "Groceries")

        val results = repository.searchTransactions(vaultId, "  groceries  ").first()

        assertEquals(1, results.size)
    }
}
