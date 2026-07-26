package com.vaultledger.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.vaultledger.domain.model.Workspace
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class WorkspaceRemoteDataSource @Inject constructor() {

    private val TAG = "WorkspaceRemoteDS"

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    open fun observeWorkspacesForMember(memberId: String): Flow<List<Workspace>> = callbackFlow {
        Log.d(TAG, "Creating listener: collection=workspaces, filter=whereArrayContains(memberIds, $memberId)")
        val registration = firestore.collection(FirestoreConstants.COLLECTION_WORKSPACES)
            .whereArrayContains(FirestoreConstants.FIELD_MEMBER_IDS, memberId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error: ${error.message}")
                    if (error is FirebaseFirestoreException) {
                        Log.w(TAG, "Error code: ${error.code}")
                    }
                    close(error)
                    return@addSnapshotListener
                }
                val docCount = snapshot?.documents?.size ?: 0
                Log.d(TAG, "First callback: docs=$docCount")
                if (snapshot != null) {
                    val workspaces = snapshot.documents.mapNotNull { it.toWorkspace() }
                    trySend(workspaces)
                }
            }
        awaitClose {
            Log.d(TAG, "Listener removed: memberId=$memberId")
            registration.remove()
        }
    }.retryFirestoreTransient()

    open suspend fun createWorkspace(workspace: Workspace, creatorId: String) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        val data = mapOf(
            FirestoreConstants.FIELD_NAME to workspace.name,
            FirestoreConstants.FIELD_DESCRIPTION to workspace.description,
            FirestoreConstants.FIELD_MEMBER_IDS to listOf(creatorId),
            FirestoreConstants.FIELD_CREATED_BY to creatorId,
            FirestoreConstants.FIELD_CREATED_AT to workspace.createdAt,
        )
        firestore.collection(FirestoreConstants.COLLECTION_WORKSPACES)
            .document(workspace.id)
            .set(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) cont.resume(kotlin.Unit)
                else cont.resumeWithException(task.exception ?: RuntimeException("Failed to create workspace"))
            }
    }

    private fun DocumentSnapshot.toWorkspace(): Workspace? {
        if (!exists()) return null
        return Workspace(
            id = id,
            name = getString(FirestoreConstants.FIELD_NAME) ?: return null,
            description = getString(FirestoreConstants.FIELD_DESCRIPTION) ?: "",
            createdAt = getLong(FirestoreConstants.FIELD_CREATED_AT) ?: return null,
            memberIds = extractMemberIds(get(FirestoreConstants.FIELD_MEMBER_IDS)),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractMemberIds(raw: Any?): List<String> {
        return when (raw) {
            is List<*> -> raw.mapNotNull { it?.toString() }
            else -> emptyList()
        }
    }
}

