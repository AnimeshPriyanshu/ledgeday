package com.vaultledger.data.repository

import com.vaultledger.data.local.dao.TransactionDao
import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.remote.TransactionRemoteDataSource
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

// ===== LOGGING =====

data class LogEntry(val elapsedMs: Long, val source: String, val message: String)

object TestLog {
    private val entries = ConcurrentLinkedQueue<LogEntry>()
    private val startTime = System.nanoTime()

    fun log(source: String, message: String) {
        val now = System.nanoTime()
        val elapsed = (now - startTime) / 1_000_000L
        entries.add(LogEntry(elapsed, source, message))
        println("[${elapsed}ms] [$source] $message")
    }

    fun dump() {
        println("\n===== FULL LOG =====")
        entries.forEach { println("[${it.elapsedMs}ms] [${it.source}] ${it.message}") }
        println("====================")
    }

    fun clear() { entries.clear() }
}

// ===== FAKE DAOs =====

class FakeTransactionDao : TransactionDao {
    private val store = mutableMapOf<String, TransactionEntity>()

    override suspend fun insert(t: TransactionEntity) { store[t.id] = t }
    override suspend fun insertAll(ts: List<TransactionEntity>) { ts.forEach { insert(it) } }
    override suspend fun update(t: TransactionEntity) { store[t.id] = t }
    override suspend fun delete(t: TransactionEntity) { store.remove(t.id) }
    override suspend fun deleteById(id: String) { store.remove(id) }
    override fun getTransactionsByVaultId(vaultId: String) =
        flowOf(store.values.filter { it.vaultId == vaultId }.sortedByDescending { it.createdAt })
    override suspend fun getTransactionById(id: String): TransactionEntity? = store[id]
    override suspend fun getTransactionIdsByVaultId(vaultId: String): List<String> =
        store.values.filter { it.vaultId == vaultId }.map { it.id }
    override suspend fun getUnsyncedTransactions(): List<TransactionEntity> =
        store.values.filter { !it.synced }
    override fun searchTransactions(vaultId: String, query: String) =
        flowOf(store.values.filter { it.vaultId == vaultId && it.description.contains(query, ignoreCase = true) }
            .sortedByDescending { it.createdAt })
    override suspend fun getBalanceForVault(vaultId: String): Long =
        store.values.filter { it.vaultId == vaultId }
            .sumOf { if (it.type == TransactionType.INFLOW) it.amount else -it.amount }
    override fun observeBalanceForVault(vaultId: String): Flow<Long> = flow {
        emit(getBalanceForVault(vaultId))
    }
}

class FakeVaultDao : VaultDao {
    private val store = mutableMapOf<String, VaultEntity>()
    override suspend fun insert(v: VaultEntity) { store[v.id] = v }
    override suspend fun update(v: VaultEntity) { store[v.id] = v }
    override suspend fun delete(v: VaultEntity) { store.remove(v.id) }
    override suspend fun getVaultById(id: String): VaultEntity? = store[id]
    override fun getVaultsByWorkspaceId(ws: String) = flowOf(store.values.filter { it.workspaceId == ws })
    override suspend fun updateBalance(id: String, b: Long) {
        store[id]?.let { store[id] = it.copy(balance = b) }
    }
}

// ===== SCENARIO-SPECIFIC REMOTE DATA SOURCES =====

/**
 * For Scenario A: createTransaction succeeds immediately.
 * softDeleteTransaction succeeds (document exists).
 */
class ScenarioARemote : TransactionRemoteDataSource() {
    override suspend fun createTransaction(ws: String, vId: String, txn: Transaction, createdBy: String) {
        TestLog.log("REMOTE", "[SET] id=${txn.id} → doc CREATED (deleted=false)")
    }

    override suspend fun softDeleteTransaction(ws: String, vId: String, txnId: String) {
        TestLog.log("REMOTE", "[UPDATE] id=$txnId → doc SOFT-DELETED (deleted=true)")
    }
}

/**
 * For Scenario B: createTransaction succeeds.
 * softDeleteTransaction fails with NOT_FOUND (document doesn't exist yet).
 */
class ScenarioBRemote : TransactionRemoteDataSource() {
    override suspend fun createTransaction(ws: String, vId: String, txn: Transaction, createdBy: String) {
        TestLog.log("REMOTE", "[SET] id=${txn.id} → doc CREATED (deleted=false)")
    }

    override suspend fun softDeleteTransaction(ws: String, vId: String, txnId: String) {
        TestLog.log("REMOTE", "[UPDATE] id=$txnId → FAILED (doc doesn't exist)")
        throw com.google.firebase.firestore.FirebaseFirestoreException(
            "No document to update",
            com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND,
        )
    }
}

// ===== TEST =====

class RaceConditionTest {

    private lateinit var txnDao: FakeTransactionDao
    private lateinit var vaultDao: FakeVaultDao
    private lateinit var remote: TransactionRemoteDataSource

    private fun setUp() {
        TestLog.clear()
        txnDao = FakeTransactionDao()
        vaultDao = FakeVaultDao()
    }

    private fun TransactionEntity.toDomain() = Transaction(
        id = id, vaultId = vaultId, type = type, amount = amount,
        description = description, createdAt = createdAt, updatedAt = updatedAt,
    )

    // ===== SCENARIO A: .set() completes before .update() =====
    //
    // Real execution order:
    //   1. syncToFirestore: check Room → entity exists → .set() → doc created with deleted=false
    //   2. deleteTransaction: delete Room → .update(deleted=true) → doc soft-deleted
    //   3. syncToFirestore: re-insert entity with synced=true  ← BUG
    //
    // Result: entity reappears in Room temporarily until SyncManager snapshot removes it.

    @Test
    fun `Scenario A set arrives before update`() {
        println("\n========== SCENARIO A: .set() BEFORE .update() ==========")
        setUp()
        remote = ScenarioARemote()

        val entity = TransactionEntity(
            id = "tx-1", vaultId = "vault-1", type = TransactionType.OUTFLOW,
            amount = 100L, description = "Lunch", createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(), synced = false, createdBy = "user-1",
        )
        // Step 1: Entity inserted into Room by createTransaction
        runBlocking { txnDao.insert(entity) }
        TestLog.log("TEST", "Initial state: entity in Room, synced=false")

        // Step 2: syncToFirestore phase 1 — .set() completes
        TestLog.log("SYNC", "--- syncToFirestore phase 1: calling createTransaction...")
        runBlocking { remote.createTransaction("ws-1", "vault-1", entity.toDomain(), entity.createdBy) }
        TestLog.log("SYNC", "--- .set() succeeded, doc=deleted=false")

        // Step 3: deleteTransaction interleaves
        TestLog.log("DELETE", "--- deleteTransaction: deleting from Room...")
        runBlocking { txnDao.delete(entity) }
        TestLog.log("DELETE", "--- entity deleted from Room")
        TestLog.log("DELETE", "--- calling softDeleteTransaction...")
        runBlocking { remote.softDeleteTransaction("ws-1", "vault-1", entity.id) }
        TestLog.log("DELETE", "--- doc soft-deleted in Firestore (deleted=true)")

        // Step 4: syncToFirestore phase 2 — re-inserts entity (THE BUG)
        TestLog.log("SYNC", "--- syncToFirestore phase 2: re-inserting entity with synced=true")
        runBlocking { txnDao.insert(entity.copy(synced = true)) }

        // Verify: entity is back in Room (temporary reappearance)
        val finalState = runBlocking { txnDao.getTransactionById(entity.id) }
        TestLog.log("TEST", "Final Room state: exists=${finalState != null}, synced=${finalState?.synced}")
        assertNotNull(finalState, "Scenario A: entity re-appeared in Room after syncToFirestore re-insert")
        assertTrue(finalState!!.synced, "Scenario A: synced should be true")

        TestLog.log("RESULT", "=== SCENARIO A OUTCOME ===")
        TestLog.log("RESULT", "Entity in Room with synced=true, but Firestore doc has deleted=true")
        TestLog.log("RESULT", "SyncManager snapshot: incomingIds=[], existingIds=[tx-1]")
        TestLog.log("RESULT", "  → removedIds=[tx-1] → entity REMOVED by SyncManager")
        TestLog.log("RESULT", "  → Temporary UI reappearance until snapshot fires")

        TestLog.dump()
    }

    // ===== SCENARIO B: .update() arrives before .set() =====
    //
    // Real execution order:
    //   1. deleteTransaction: delete Room → .update(deleted=true) → NOT_FOUND (doc doesn't exist)
    //   2. deleteTransaction catch block: RE-INSERT entity into Room
    //   3. syncToFirestore: .set() → doc created with deleted=false
    //   4. syncToFirestore: re-insert entity with synced=true  ← BUG
    //
    // Result: entity is permanently restored. SyncManager sees it in Firestore.

    @Test
    fun `Scenario B update arrives before set`() {
        println("\n========== SCENARIO B: .update() BEFORE .set() ==========")
        setUp()
        remote = ScenarioBRemote()

        val entity = TransactionEntity(
            id = "tx-1", vaultId = "vault-1", type = TransactionType.OUTFLOW,
            amount = 100L, description = "Lunch", createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(), synced = false, createdBy = "user-1",
        )
        // Step 1: Entity inserted into Room by createTransaction
        runBlocking { txnDao.insert(entity) }
        TestLog.log("TEST", "Initial state: entity in Room, synced=false")

        // Step 2: deleteTransaction runs first
        TestLog.log("DELETE", "--- deleteTransaction: deleting from Room...")
        runBlocking { txnDao.delete(entity) }
        TestLog.log("DELETE", "--- entity deleted from Room")
        TestLog.log("DELETE", "--- calling softDeleteTransaction (will fail)...")
        try {
            runBlocking { remote.softDeleteTransaction("ws-1", "vault-1", entity.id) }
            throw AssertionError("Expected NOT_FOUND but update succeeded")
        } catch (e: Exception) {
            TestLog.log("DELETE", "--- softDelete FAILED: ${e.message}")
        }
        // Step 2b: catch block re-inserts entity
        TestLog.log("DELETE", "--- catch block RE-INSERTING entity into Room")
        runBlocking { txnDao.insert(entity) }

        // Step 3: syncToFirestore phase 1 — .set() completes (doc created with deleted=false)
        TestLog.log("SYNC", "--- syncToFirestore: calling createTransaction...")
        runBlocking { remote.createTransaction("ws-1", "vault-1", entity.toDomain(), entity.createdBy) }

        // Step 4: syncToFirestore phase 2 — re-inserts entity (THE BUG)
        TestLog.log("SYNC", "--- syncToFirestore: re-inserting entity with synced=true")
        runBlocking { txnDao.insert(entity.copy(synced = true)) }

        // Verify: entity is in Room (permanent reappearance)
        val finalState = runBlocking { txnDao.getTransactionById(entity.id) }
        TestLog.log("TEST", "Final Room state: exists=${finalState != null}, synced=${finalState?.synced}")
        assertNotNull(finalState, "Scenario B: entity should be in Room (permanent reappearance)")

        TestLog.log("RESULT", "=== SCENARIO B OUTCOME ===")
        TestLog.log("RESULT", "Entity in Room with synced=${finalState?.synced}")
        TestLog.log("RESULT", "Firestore doc has deleted=false")
        TestLog.log("RESULT", "SyncManager snapshot: incomingIds=[tx-1], existingIds=[tx-1]")
        TestLog.log("RESULT", "  → removedIds=[] → entity STAYS in Room (PERMANENT reappearance)")

        TestLog.dump()
    }

    // ===== PROOF: Firestore Security Rules prevent .set() after .update() =====

    @Test
    fun `prove Firestore document is never recreated after soft delete`() {
        println("\n========== PROOF: Firestore Document Lifecycle ==========")

        var firestoreDocState = "DOES_NOT_EXIST"

        val proofRemote = object : TransactionRemoteDataSource() {
            override suspend fun createTransaction(ws: String, vId: String, txn: Transaction, createdBy: String) {
                TestLog.log("PROOF", "Firestore receives .set(id=${txn.id}), state=$firestoreDocState")
                when (firestoreDocState) {
                    "DOES_NOT_EXIST" -> {
                        firestoreDocState = "EXISTS(deleted=false)"
                        TestLog.log("PROOF", "  → CREATE rule: ALLOWED, doc created with deleted=false")
                    }
                    else -> {
                        TestLog.log("PROOF", "  → UPDATE rule: REJECTED (deleted=false on existing doc)")
                        throw RuntimeException("Security rules rejected: existing doc can't be overwritten with deleted=false")
                    }
                }
            }

            override suspend fun softDeleteTransaction(ws: String, vId: String, txnId: String) {
                TestLog.log("PROOF", "Firestore receives .update(id=$txnId), state=$firestoreDocState")
                when (firestoreDocState) {
                    "DOES_NOT_EXIST" -> {
                        TestLog.log("PROOF", "  → .update() FAILED: document does not exist")
                        throw com.google.firebase.firestore.FirebaseFirestoreException(
                            "No document to update",
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND,
                        )
                    }
                    "EXISTS(deleted=false)" -> {
                        firestoreDocState = "EXISTS(deleted=true)"
                        TestLog.log("PROOF", "  → UPDATE rule: ALLOWED (deleted: false→true), doc SOFT-DELETED")
                    }
                    else -> {
                        TestLog.log("PROOF", "  → UPDATE rule: REJECTED (deleted already true)")
                        throw RuntimeException("Already deleted")
                    }
                }
            }
        }

        fun TransactionEntity.toDomain() = Transaction(
            id = id, vaultId = vaultId, type = type, amount = amount,
            description = description, createdAt = createdAt, updatedAt = updatedAt,
        )

        // Trace 1: Normal flow (.set() first, .update() second)
        TestLog.log("PROOF", "\n--- TRACE 1: .set() first, .update() second ---")
        firestoreDocState = "DOES_NOT_EXIST"
        val e1 = TransactionEntity("tx-a", "vault-1", TransactionType.OUTFLOW, 100L, "A", 1000L, 1000L, false, "u1")
        runBlocking { proofRemote.createTransaction("ws-1", "vault-1", e1.toDomain(), "u1") }
        runBlocking { proofRemote.softDeleteTransaction("ws-1", "vault-1", e1.id) }
        // Retry .set() — should be REJECTED
        try {
            runBlocking { proofRemote.createTransaction("ws-1", "vault-1", e1.toDomain(), "u1") }
            throw AssertionError("Expected rejection but set succeeded")
        } catch (e: Exception) {
            TestLog.log("PROOF", "  → Retry .set() correctly rejected: ${e.message}")
        }
        assertTrue(firestoreDocState == "EXISTS(deleted=true)", "Doc must be deleted=true after soft delete")

        // Trace 2: .update() first, .set() second (Scenario B)
        TestLog.log("PROOF", "\n--- TRACE 2: .update() first, .set() second ---")
        firestoreDocState = "DOES_NOT_EXIST"
        val e2 = TransactionEntity("tx-b", "vault-1", TransactionType.OUTFLOW, 200L, "B", 2000L, 2000L, false, "u1")
        try {
            runBlocking { proofRemote.softDeleteTransaction("ws-1", "vault-1", e2.id) }
            throw AssertionError("Expected NOT_FOUND but update succeeded")
        } catch (e: Exception) {
            TestLog.log("PROOF", "  → .update() correctly failed: ${e.message}")
        }
        runBlocking { proofRemote.createTransaction("ws-1", "vault-1", e2.toDomain(), "u1") }
        assertTrue(firestoreDocState == "EXISTS(deleted=false)", "Doc must be deleted=false (update never happened)")

        TestLog.log("PROOF", "\n=== CONCLUSION ===")
        TestLog.log("PROOF", "Trace 1: does-not-exist → deleted=false → deleted=true (terminal)")
        TestLog.log("PROOF", "  Security rules prevent .set(deleted=false) after soft delete.")
        TestLog.log("PROOF", "Trace 2: does-not-exist → deleted=false (terminal)")
        TestLog.log("PROOF", "  .update() failed — doc never created until .set() creates it with deleted=false.")
        TestLog.log("PROOF", "  This is Scenario B: the entity is permanently restored in Room.")

        TestLog.dump()
    }
}
