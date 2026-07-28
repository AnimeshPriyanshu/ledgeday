package com.vaultledger.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.vaultledger.domain.model.Vault
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class VaultRemoteDataSource @Inject constructor() {

    private val TAG = "VaultRemoteDS"

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    open fun observeVaults(workspaceId: String): Flow<List<Vault>> = callbackFlow {
        val path = "${FirestoreConstants.COLLECTION_WORKSPACES}/$workspaceId/${FirestoreConstants.COLLECTION_VAULTS}"
        Log.d(TAG, "Creating listener: collection=$path (no filters)")
        val registration = firestore.collection(path).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error: ${error.message}")
                    if (error is FirebaseFirestoreException) {
                        Log.w(TAG, "Error code: ${error.code}")
                    }
                    close(error)
                    return@addSnapshotListener
                }
                val docCount = snapshot?.documents?.size ?: 0
                Log.d(TAG, "First callback: docs=$docCount for workspaceId=$workspaceId")
                if (snapshot != null) {
                    val vaults = snapshot.documents.mapNotNull { it.toVault(workspaceId) }
                    trySend(vaults)
                }
            }
        awaitClose {
            Log.d(TAG, "Listener removed: workspaceId=$workspaceId")
            registration.remove()
        }
    }.retryFirestoreTransient()

    open suspend fun createVault(workspaceId: String, vault: Vault) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        val data = mapOf(
            FirestoreConstants.FIELD_NAME to vault.name,
            FirestoreConstants.FIELD_DESCRIPTION to vault.description,
            FirestoreConstants.FIELD_CREATED_AT to vault.createdAt,
            FirestoreConstants.FIELD_COLOR to vault.color,
        )
        firestore.collection("${FirestoreConstants.COLLECTION_WORKSPACES}/$workspaceId/${FirestoreConstants.COLLECTION_VAULTS}")
            .document(vault.id)
            .set(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) cont.resume(Unit)
                else cont.resumeWithException(task.exception ?: RuntimeException("Failed to create vault"))
            }
    }

    open suspend fun deleteVault(workspaceId: String, vaultId: String) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        firestore.collection("${FirestoreConstants.COLLECTION_WORKSPACES}/$workspaceId/${FirestoreConstants.COLLECTION_VAULTS}")
            .document(vaultId)
            .delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) cont.resume(Unit)
                else cont.resumeWithException(task.exception ?: RuntimeException("Failed to delete vault"))
            }
    }

    private fun DocumentSnapshot.toVault(workspaceId: String): Vault? {
        if (!exists()) return null
        return Vault(
            id = id,
            workspaceId = workspaceId,
            name = getString(FirestoreConstants.FIELD_NAME) ?: return null,
            description = getString(FirestoreConstants.FIELD_DESCRIPTION) ?: "",
            createdAt = getLong(FirestoreConstants.FIELD_CREATED_AT) ?: return null,
            color = getString(FirestoreConstants.FIELD_COLOR) ?: FirestoreConstants.DEFAULT_VAULT_COLOR,
        )
    }
}

