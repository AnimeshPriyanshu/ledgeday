package com.vaultledger.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.vaultledger.data.repository.exception.FirestoreTimeoutException
import com.vaultledger.data.repository.exception.OfflineException
import com.vaultledger.domain.model.Invite
import com.vaultledger.domain.model.InviteStatus
import com.vaultledger.domain.model.Workspace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class InviteRemoteDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val secureRandom = SecureRandom()

    suspend fun createInvite(creatorId: String, creatorEmail: String): Invite {
        checkConnectivity()

        val code = generateCode()
        val now = System.currentTimeMillis()
        val expiresAt = now + FirestoreConstants.INVITE_EXPIRY_MS

        val data = mapOf(
            FirestoreConstants.FIELD_CODE to code,
            FirestoreConstants.FIELD_CREATOR_ID to creatorId,
            FirestoreConstants.FIELD_CREATOR_EMAIL to creatorEmail,
            FirestoreConstants.FIELD_CREATED_AT to now,
            FirestoreConstants.FIELD_EXPIRES_AT to expiresAt,
            FirestoreConstants.FIELD_STATUS to "active",
            FirestoreConstants.FIELD_ACCEPTED_BY to null,
        )

        try {
            withTimeout(15_000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val setTask = firestore.collection(FirestoreConstants.COLLECTION_INVITES)
                        .document(code).set(data)

                    setTask.addOnCompleteListener { task ->
                        if (cont.isActive) {
                            if (task.isSuccessful) {
                                cont.resume(Unit)
                            } else {
                                cont.resumeWithException(
                                    task.exception ?: RuntimeException("Failed to create invite"),
                                )
                            }
                        }
                    }


                }
            }
        } catch (e: TimeoutCancellationException) {
            throw FirestoreTimeoutException("Firestore write timed out after 15s")
        }

        return Invite(
            code = code,
            creatorId = creatorId,
            creatorEmail = creatorEmail,
            createdAt = now,
            expiresAt = expiresAt,
            status = InviteStatus.ACTIVE,
        )
    }

    suspend fun getInvite(code: String): Invite? = suspendCancellableCoroutine { cont ->
        firestore.collection(FirestoreConstants.COLLECTION_INVITES).document(code)
            .get()
            .addOnSuccessListener { snapshot ->
                val invite = if (snapshot.exists()) snapshot.toInvite() else null
                cont.resume(invite)
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    suspend fun acceptInvite(code: String, accepterId: String): Workspace = suspendCancellableCoroutine { cont ->
        val inviteRef = firestore.collection(FirestoreConstants.COLLECTION_INVITES).document(code)
        val workspaceRef = firestore.collection(FirestoreConstants.COLLECTION_WORKSPACES).document()
        Log.d(TAG, "acceptInvite: code=$code, accepterId=$accepterId, invitePath=${inviteRef.path}, workspacePath=${workspaceRef.path}")

        firestore.runTransaction { transaction ->
            Log.d(TAG, "transaction: calling transaction.get(inviteRef) — path=${inviteRef.path}")
            val inviteSnapshot = transaction.get(inviteRef)
            Log.d(TAG, "transaction: transaction.get succeeded, exists=${inviteSnapshot.exists()}")

            if (!inviteSnapshot.exists()) {
                throw FirebaseFirestoreException("Invite not found", FirebaseFirestoreException.Code.NOT_FOUND)
            }

            val status = inviteSnapshot.getString(FirestoreConstants.FIELD_STATUS)
            if (status != "active") {
                throw FirebaseFirestoreException("Invite is $status, not active", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
            }

            val expiresAt = inviteSnapshot.getLong(FirestoreConstants.FIELD_EXPIRES_AT) ?: 0L
            if (System.currentTimeMillis() > expiresAt) {
                throw FirebaseFirestoreException("Invite has expired", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
            }

            val creatorId = inviteSnapshot.getString(FirestoreConstants.FIELD_CREATOR_ID)
                ?: throw FirebaseFirestoreException("Invalid invite data", FirebaseFirestoreException.Code.FAILED_PRECONDITION)

            if (creatorId == accepterId) {
                throw FirebaseFirestoreException("Cannot accept your own invite", FirebaseFirestoreException.Code.FAILED_PRECONDITION)
            }

            val now = System.currentTimeMillis()
            val workspaceId = workspaceRef.id

            val workspaceData = mapOf(
                FirestoreConstants.FIELD_NAME to FirestoreConstants.DEFAULT_SHARED_WORKSPACE_NAME,
                FirestoreConstants.FIELD_DESCRIPTION to "",
                FirestoreConstants.FIELD_MEMBER_IDS to listOf(creatorId, accepterId),
                FirestoreConstants.FIELD_CREATED_BY to creatorId,
                FirestoreConstants.FIELD_CREATED_AT to now,
                FirestoreConstants.FIELD_INVITE_CODE to code,
            )

            Log.d(TAG, "transaction: calling transaction.set(workspaceRef) — path=${workspaceRef.path}, data=$workspaceData")
            transaction.set(workspaceRef, workspaceData)
            Log.d(TAG, "transaction: calling transaction.update(inviteRef) — path=${inviteRef.path}")
            transaction.update(inviteRef, FirestoreConstants.FIELD_STATUS, "accepted", FirestoreConstants.FIELD_ACCEPTED_BY, accepterId)

            Workspace(
                id = workspaceId,
                name = FirestoreConstants.DEFAULT_SHARED_WORKSPACE_NAME,
                description = "",
                createdAt = now,
                memberIds = listOf(creatorId, accepterId),
            )
        }.addOnSuccessListener { workspace ->
            Log.d(TAG, "transaction: success, workspace.id=${workspace.id}")
            cont.resume(workspace)
        }.addOnFailureListener { e ->
            val code = (e as? FirebaseFirestoreException)?.code
            Log.w(TAG, "transaction: FAILED. code=$code, message=${e.message}")
            Log.w(TAG, "transaction: stack trace:", e)
            cont.resumeWithException(e)
        }
    }

    suspend fun revokeInvite(code: String) = suspendCancellableCoroutine<Unit> { cont ->
        firestore.collection(FirestoreConstants.COLLECTION_INVITES).document(code)
            .update(FirestoreConstants.FIELD_STATUS, "expired")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) cont.resume(Unit)
                else cont.resumeWithException(task.exception ?: RuntimeException("Failed to revoke invite"))
            }
    }

    private fun checkConnectivity() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
            ?: throw OfflineException("Internet connection required to generate an invite.")
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: throw OfflineException("Internet connection required to generate an invite.")
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            throw OfflineException("Internet connection required to generate an invite.")
        }
    }

    private fun generateCode(): String {
        val chars = ALLOWED_CHARS
        val code = CharArray(FirestoreConstants.INVITE_CODE_LENGTH)
        for (i in code.indices) {
            code[i] = chars[secureRandom.nextInt(chars.size)]
        }
        return String(code)
    }

    private fun DocumentSnapshot.toInvite(): Invite? {
        return Invite(
            code = getString(FirestoreConstants.FIELD_CODE) ?: return null,
            creatorId = getString(FirestoreConstants.FIELD_CREATOR_ID) ?: return null,
            creatorEmail = getString(FirestoreConstants.FIELD_CREATOR_EMAIL) ?: return null,
            createdAt = getLong(FirestoreConstants.FIELD_CREATED_AT) ?: return null,
            expiresAt = getLong(FirestoreConstants.FIELD_EXPIRES_AT) ?: return null,
            status = parseStatus(getString(FirestoreConstants.FIELD_STATUS)),
            acceptedBy = getString(FirestoreConstants.FIELD_ACCEPTED_BY),
        )
    }

    private fun parseStatus(value: String?): InviteStatus {
        return try {
            InviteStatus.valueOf((value ?: "ACTIVE").uppercase(java.util.Locale.ROOT))
        } catch (_: IllegalArgumentException) {
            InviteStatus.ACTIVE
        }
    }

    private companion object {
        val ALLOWED_CHARS = ('A'..'Z').toList() + ('0'..'9').toList()
        private const val TAG = "InviteRemoteDS"
    }
}
