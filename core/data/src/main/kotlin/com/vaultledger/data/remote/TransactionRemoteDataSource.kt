package com.vaultledger.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
open class TransactionRemoteDataSource @Inject constructor() {

    private val TAG = "TxnRemoteDS"

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private fun transactionsPath(workspaceId: String, vaultId: String): String =
        "${FirestoreConstants.COLLECTION_WORKSPACES}/$workspaceId/${FirestoreConstants.COLLECTION_VAULTS}/$vaultId/${FirestoreConstants.COLLECTION_TRANSACTIONS}"

    open fun observeNonDeletedTransactions(workspaceId: String, vaultId: String): Flow<List<Transaction>> = callbackFlow {
        val path = transactionsPath(workspaceId, vaultId)
        Log.d(TAG, "Creating listener: collection=$path, filter=whereEqualTo(deleted, false)")
        val registration = firestore
            .collection(path)
            .whereEqualTo(FirestoreConstants.FIELD_DELETED, false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Non-deleted txn error: ${error.message}")
                    if (error is FirebaseFirestoreException) {
                        Log.w(TAG, "Error code: ${error.code}")
                    }
                    close(error)
                    return@addSnapshotListener
                }
                val docCount = snapshot?.documents?.size ?: 0
                Log.d(TAG, "First callback: docs=$docCount for vaultId=$vaultId")
                if (snapshot != null) {
                    val transactions = snapshot.documents.mapNotNull { it.toTransaction(vaultId) }
                    trySend(transactions)
                }
            }
        awaitClose {
            Log.d(TAG, "Listener removed: vaultId=$vaultId")
            registration.remove()
        }
    }.retryFirestoreTransient()

    open fun observeAllTransactions(workspaceId: String, vaultId: String): Flow<List<Transaction>> = callbackFlow {
        val path = transactionsPath(workspaceId, vaultId)
        Log.d(TAG, "Creating listener: collection=$path (no filters, all transactions)")
        val registration = firestore
            .collection(path)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "All txn error: ${error.message}")
                    if (error is FirebaseFirestoreException) {
                        Log.w(TAG, "Error code: ${error.code}")
                    }
                    close(error)
                    return@addSnapshotListener
                }
                val docCount = snapshot?.documents?.size ?: 0
                Log.d(TAG, "First callback: docs=$docCount for vaultId=$vaultId (all)")
                if (snapshot != null) {
                    val transactions = snapshot.documents.mapNotNull { it.toTransaction(vaultId) }
                    trySend(transactions)
                }
            }
        awaitClose {
            Log.d(TAG, "Listener removed: vaultId=$vaultId (all)")
            registration.remove()
        }
    }.retryFirestoreTransient()

    open suspend fun createTransaction(
        workspaceId: String,
        vaultId: String,
        transaction: Transaction,
        createdBy: String,
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val data = mapOf(
            FirestoreConstants.FIELD_VAULT_ID to vaultId,
            FirestoreConstants.FIELD_TYPE to transaction.type.name,
            FirestoreConstants.FIELD_AMOUNT to transaction.amount,
            FirestoreConstants.FIELD_DESCRIPTION to transaction.description,
            FirestoreConstants.FIELD_CREATED_BY to createdBy,
            FirestoreConstants.FIELD_CREATED_AT to transaction.createdAt,
            FirestoreConstants.FIELD_UPDATED_AT to (transaction.updatedAt ?: transaction.createdAt),
            FirestoreConstants.FIELD_DELETED to false,
        )
        firestore
            .collection(transactionsPath(workspaceId, vaultId))
            .document(transaction.id)
            .set(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) cont.resume(Unit)
                else cont.resumeWithException(task.exception ?: RuntimeException("Failed to create transaction"))
            }
    }

    open suspend fun softDeleteTransaction(workspaceId: String, vaultId: String, transactionId: String) = suspendCancellableCoroutine<Unit> { cont ->
        Log.d(TAG, "softDeleteTransaction: calling .update() on $transactionId in vault $vaultId")
        firestore
            .collection(transactionsPath(workspaceId, vaultId))
            .document(transactionId)
            .update(
                mapOf(
                    FirestoreConstants.FIELD_DELETED to true,
                    FirestoreConstants.FIELD_UPDATED_AT to System.currentTimeMillis(),
                ),
            )
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "softDeleteTransaction: .update() succeeded for $transactionId")
                    cont.resume(Unit)
                } else {
                    Log.w(TAG, "softDeleteTransaction: .update() FAILED for $transactionId: ${task.exception?.message}")
                    cont.resumeWithException(task.exception ?: RuntimeException("Failed to delete transaction"))
                }
            }
    }

    private fun DocumentSnapshot.toTransaction(vaultId: String): Transaction? {
        if (!exists()) return null
        return Transaction(
            id = id,
            vaultId = vaultId,
            type = parseType(getString(FirestoreConstants.FIELD_TYPE)),
            amount = getLong(FirestoreConstants.FIELD_AMOUNT) ?: return null,
            description = getString(FirestoreConstants.FIELD_DESCRIPTION) ?: "",
            createdAt = getLong(FirestoreConstants.FIELD_CREATED_AT) ?: return null,
            updatedAt = getLong(FirestoreConstants.FIELD_UPDATED_AT),
        )
    }

    private fun parseType(value: String?): TransactionType {
        return try {
            TransactionType.valueOf(value ?: "INFLOW")
        } catch (_: IllegalArgumentException) {
            TransactionType.INFLOW
        }
    }
}
