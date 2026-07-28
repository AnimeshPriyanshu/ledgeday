package com.vaultledger.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private fun transactionsPath(workspaceId: String, vaultId: String): String =
        "${FirestoreConstants.COLLECTION_WORKSPACES}/$workspaceId/${FirestoreConstants.COLLECTION_VAULTS}/$vaultId/${FirestoreConstants.COLLECTION_TRANSACTIONS}"

    open fun observeNonDeletedTransactions(workspaceId: String, vaultId: String): Flow<List<Transaction>> = callbackFlow {
        val registration = firestore
            .collection(transactionsPath(workspaceId, vaultId))
            .whereEqualTo(FirestoreConstants.FIELD_DELETED, false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val transactions = snapshot.documents.mapNotNull { it.toTransaction(vaultId) }
                    trySend(transactions)
                }
            }
        awaitClose {
            registration.remove()
        }
    }.retryFirestoreTransient()

    open fun observeAllTransactions(workspaceId: String, vaultId: String): Flow<List<Transaction>> = callbackFlow {
        val registration = firestore
            .collection(transactionsPath(workspaceId, vaultId))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val transactions = snapshot.documents.mapNotNull { it.toTransaction(vaultId) }
                    trySend(transactions)
                }
            }
        awaitClose {
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
                    cont.resume(Unit)
                } else {
                    cont.resumeWithException(task.exception ?: RuntimeException("Failed to delete transaction"))
                }
            }
    }

    private fun DocumentSnapshot.toTransaction(vaultId: String): Transaction? {
        if (!exists()) return null
        val type = parseType(getString(FirestoreConstants.FIELD_TYPE)) ?: return null
        return Transaction(
            id = id,
            vaultId = vaultId,
            type = type,
            amount = getLong(FirestoreConstants.FIELD_AMOUNT) ?: return null,
            description = getString(FirestoreConstants.FIELD_DESCRIPTION) ?: "",
            createdAt = getLong(FirestoreConstants.FIELD_CREATED_AT) ?: return null,
            updatedAt = getLong(FirestoreConstants.FIELD_UPDATED_AT),
        )
    }

    private fun parseType(value: String?): TransactionType? {
        if (value == null) return null
        return try {
            TransactionType.valueOf(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
