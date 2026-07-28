package com.vaultledger.data.repository

import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.remote.TransactionRemoteDataSource
import com.vaultledger.data.sync.FakeTransactionDao
import com.vaultledger.data.sync.FakeVaultDao
import com.vaultledger.data.sync.FakeWorkspaceDao
import com.vaultledger.data.sync.FakeWorkspaceRemoteDataSource
import com.vaultledger.data.sync.FakeVaultRemoteDataSource
import com.vaultledger.data.sync.SyncManager
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RaceConditionTest {

    // ===================================================================
    // TEST 1: retryUnsyncedTransactions() does not resurrect
    //
    // Verifies the post-check pattern inside retryUnsyncedTransactions:
    // Firestore write → post-write check → skip insert if deleted.
    // ===================================================================

    @Test
    fun `retryUnsyncedTransactions does not resurrect entity deleted during retry`() = runBlocking {
        val txnDao = FakeTransactionDao()
        val vaultDao = FakeVaultDao()
        val wsDao = FakeWorkspaceDao()

        val createStarted = CountDownLatch(1)
        val canProceed = CountDownLatch(1)

        val remote = object : TransactionRemoteDataSource() {

            override suspend fun createTransaction(
                workspaceId: String, vaultId: String, transaction: Transaction, createdBy: String,
            ) {
                createStarted.countDown()
                canProceed.await(10, TimeUnit.SECONDS)
            }

            override suspend fun softDeleteTransaction(
                workspaceId: String, vaultId: String, transactionId: String,
            ) = Unit

            override fun observeNonDeletedTransactions(
                workspaceId: String, vaultId: String,
            ) = flowOf<List<Transaction>>(emptyList())

            override fun observeAllTransactions(
                workspaceId: String, vaultId: String,
            ) = flowOf<List<Transaction>>(emptyList())
        }

        vaultDao.insert(VaultEntity(
            id = "vault-1", workspaceId = "ws-1", name = "Vault",
            description = "", createdAt = 1000L,
        ))

        val entity = TransactionEntity(
            id = "tx-1", vaultId = "vault-1", type = TransactionType.OUTFLOW,
            amount = 100L, description = "test", createdAt = 2000L,
            updatedAt = 2000L, synced = false, createdBy = "user-1",
        )
        txnDao.insert(entity)

        val syncManager = SyncManager(
            workspaceRemoteDataSource = FakeWorkspaceRemoteDataSource(),
            vaultRemoteDataSource = FakeVaultRemoteDataSource(),
            transactionRemoteDataSource = remote,
            workspaceDao = wsDao,
            vaultDao = vaultDao,
            transactionDao = txnDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }

        CoroutineScope(Dispatchers.Default).launch {
            syncManager.retryUnsyncedTransactions()
        }

        assert(createStarted.await(5, TimeUnit.SECONDS)) { "Timed out waiting for createTransaction" }

        txnDao.delete(entity)

        canProceed.countDown()
        delay(500)

        val final = txnDao.getTransactionById(entity.id)
        assertNull(final) { "retryUnsyncedTransactions resurrected entity that was deleted during retry" }
    }

    // ===================================================================
    // TEST 2: Compensating soft-delete
    //
    // Verifies that when the entity is deleted during the Firestore write,
    // retryUnsyncedTransactions calls softDeleteTransaction to undo the doc.
    // ===================================================================

    @Test
    fun `compensating softDelete is called when entity deleted during retry`() = runBlocking {
        val txnDao = FakeTransactionDao()
        val vaultDao = FakeVaultDao()
        val wsDao = FakeWorkspaceDao()

        val createStarted = CountDownLatch(1)
        val canProceed = CountDownLatch(1)
        val compensatingDeleteCalled = AtomicBoolean(false)

        val remote = object : TransactionRemoteDataSource() {

            override suspend fun createTransaction(
                workspaceId: String, vaultId: String, transaction: Transaction, createdBy: String,
            ) {
                createStarted.countDown()
                canProceed.await(10, TimeUnit.SECONDS)
            }

            override suspend fun softDeleteTransaction(
                workspaceId: String, vaultId: String, transactionId: String,
            ) {
                compensatingDeleteCalled.set(true)
            }

            override fun observeNonDeletedTransactions(
                workspaceId: String, vaultId: String,
            ) = flowOf<List<Transaction>>(emptyList())

            override fun observeAllTransactions(
                workspaceId: String, vaultId: String,
            ) = flowOf<List<Transaction>>(emptyList())
        }

        vaultDao.insert(VaultEntity(
            id = "vault-1", workspaceId = "ws-1", name = "Vault",
            description = "", createdAt = 1000L,
        ))

        txnDao.insert(TransactionEntity(
            id = "tx-1", vaultId = "vault-1", type = TransactionType.OUTFLOW,
            amount = 100L, description = "test", createdAt = 2000L,
            updatedAt = 2000L, synced = false, createdBy = "user-1",
        ))

        val syncManager = SyncManager(
            workspaceRemoteDataSource = FakeWorkspaceRemoteDataSource(),
            vaultRemoteDataSource = FakeVaultRemoteDataSource(),
            transactionRemoteDataSource = remote,
            workspaceDao = wsDao,
            vaultDao = vaultDao,
            transactionDao = txnDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }

        CoroutineScope(Dispatchers.Default).launch {
            syncManager.retryUnsyncedTransactions()
        }

        assert(createStarted.await(5, TimeUnit.SECONDS)) { "Timed out waiting for createTransaction" }
        txnDao.deleteById("tx-1")
        canProceed.countDown()
        delay(500)

        assert(compensatingDeleteCalled.get()) { "compensating softDeleteTransaction was NOT called" }
        assertNull(txnDao.getTransactionById("tx-1")) { "entity was resurrected after compensating delete" }
    }

    // ===================================================================
    // TEST 3: Pre-check skips already-deleted entities
    //
    // If the entity is deleted BEFORE retryUnsyncedTransactions calls
    // createTransaction, the pre-check should skip it entirely.
    // ===================================================================

    @Test
    fun `pre-check skips entity deleted before createTransaction`() = runBlocking {
        val txnDao = FakeTransactionDao()
        val vaultDao = FakeVaultDao()
        val wsDao = FakeWorkspaceDao()

        var createTransactionCalled = AtomicBoolean(false)

        val remote = object : TransactionRemoteDataSource() {

            override suspend fun createTransaction(
                workspaceId: String, vaultId: String, transaction: Transaction, createdBy: String,
            ) {
                createTransactionCalled.set(true)
            }

            override suspend fun softDeleteTransaction(
                workspaceId: String, vaultId: String, transactionId: String,
            ) = Unit

            override fun observeNonDeletedTransactions(
                workspaceId: String, vaultId: String,
            ) = flowOf<List<Transaction>>(emptyList())

            override fun observeAllTransactions(
                workspaceId: String, vaultId: String,
            ) = flowOf<List<Transaction>>(emptyList())
        }

        vaultDao.insert(VaultEntity(
            id = "vault-1", workspaceId = "ws-1", name = "Vault",
            description = "", createdAt = 1000L,
        ))

        txnDao.insert(TransactionEntity(
            id = "tx-1", vaultId = "vault-1", type = TransactionType.OUTFLOW,
            amount = 100L, description = "test", createdAt = 2000L,
            updatedAt = 2000L, synced = false, createdBy = "user-1",
        ))

        val syncManager = SyncManager(
            workspaceRemoteDataSource = FakeWorkspaceRemoteDataSource(),
            vaultRemoteDataSource = FakeVaultRemoteDataSource(),
            transactionRemoteDataSource = remote,
            workspaceDao = wsDao,
            vaultDao = vaultDao,
            transactionDao = txnDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }

        // Delete entity BEFORE retry runs
        txnDao.deleteById("tx-1")

        syncManager.retryUnsyncedTransactions()

        assertNull(txnDao.getTransactionById("tx-1")) { "entity was resurrected despite pre-check" }
    }

    // ===================================================================
    // TEST 4: Normal retry succeeds when entity is not deleted
    //
    // When no concurrent deletion occurs, the retry should succeed and
    // mark the entity as synced.
    // ===================================================================

    @Test
    fun `normal retry succeeds when entity is not deleted`() = runBlocking {
        val txnDao = FakeTransactionDao()
        val vaultDao = FakeVaultDao()
        val wsDao = FakeWorkspaceDao()

        val remote = object : TransactionRemoteDataSource() {
            override suspend fun createTransaction(
                workspaceId: String, vaultId: String, transaction: Transaction, createdBy: String,
            ) = Unit

            override suspend fun softDeleteTransaction(
                workspaceId: String, vaultId: String, transactionId: String,
            ) = Unit

            override fun observeNonDeletedTransactions(
                workspaceId: String, vaultId: String,
            ) = flowOf<List<Transaction>>(emptyList())

            override fun observeAllTransactions(
                workspaceId: String, vaultId: String,
            ) = flowOf<List<Transaction>>(emptyList())
        }

        vaultDao.insert(VaultEntity(
            id = "vault-1", workspaceId = "ws-1", name = "Vault",
            description = "", createdAt = 1000L,
        ))

        txnDao.insert(TransactionEntity(
            id = "tx-1", vaultId = "vault-1", type = TransactionType.OUTFLOW,
            amount = 100L, description = "test", createdAt = 2000L,
            updatedAt = 2000L, synced = false, createdBy = "user-1",
        ))

        val syncManager = SyncManager(
            workspaceRemoteDataSource = FakeWorkspaceRemoteDataSource(),
            vaultRemoteDataSource = FakeVaultRemoteDataSource(),
            transactionRemoteDataSource = remote,
            workspaceDao = wsDao,
            vaultDao = vaultDao,
            transactionDao = txnDao,
        ).also { it.scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }

        syncManager.retryUnsyncedTransactions()

        val final = txnDao.getTransactionById("tx-1")
        assert(final != null && final.synced) { "entity should be synced after successful retry" }
    }
}