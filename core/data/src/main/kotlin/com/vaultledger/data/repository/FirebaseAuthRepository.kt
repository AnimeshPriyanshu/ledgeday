package com.vaultledger.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.remote.FirestoreConstants
import com.vaultledger.data.repository.exception.AuthException
import com.vaultledger.data.sync.SyncManager
import com.vaultledger.domain.model.User
import com.vaultledger.domain.repository.AuthRepository
import com.vaultledger.domain.repository.DeleteAccountResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val syncManager: SyncManager? = null,
    private val database: VaultLedgerDatabase? = null,
) : AuthRepository {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun observeAuthState(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toDomain())
        }
        firebaseAuth.addAuthStateListener(listener)

        trySend(firebaseAuth.currentUser?.toDomain())

        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }.buffer(Channel.UNLIMITED)

    override suspend fun signIn(email: String, password: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(
                            task.exception?.toAuthException() ?: AuthException("Sign in failed"),
                        )
                    }
                }
        }
    }

    override suspend fun signUp(email: String, password: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(
                            task.exception?.toAuthException() ?: AuthException("Registration failed"),
                        )
                    }
                }
        }
    }

    override suspend fun signOut() {
        syncManager?.stopSyncing()
        database?.let { db ->
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
        }
        firebaseAuth.signOut()
    }

    override suspend fun getCurrentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }

    override suspend fun deleteAccount(): DeleteAccountResult {
        return try {
            syncManager?.stopSyncing()

            val user = firebaseAuth.currentUser
            if (user == null) {
                cleanupLocalData()
                return DeleteAccountResult.Success
            }

            cleanupFirestoreData(user.uid)

            suspendCancellableCoroutine<DeleteAccountResult> { cont ->
                user.delete()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            cleanupLocalData()
                            cont.resume(DeleteAccountResult.Success)
                        } else {
                            val exception = task.exception
                            if (exception is FirebaseAuthRecentLoginRequiredException) {
                                cont.resume(DeleteAccountResult.NeedsReauthentication)
                            } else {
                                cont.resume(
                                    DeleteAccountResult.Error(
                                        exception?.message ?: "Failed to delete account",
                                    ),
                                )
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("FirebaseAuthRepo", "deleteAccount failed", e)
            DeleteAccountResult.Error(e.message ?: "An unexpected error occurred")
        }
    }

    override suspend fun reauthenticateAndDelete(password: String): DeleteAccountResult {
        return try {
            val user = firebaseAuth.currentUser ?: return DeleteAccountResult.Error("No user signed in")
            val email = user.email ?: return DeleteAccountResult.Error("No email on account")

            suspendCancellableCoroutine<DeleteAccountResult> { cont ->
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential)
                    .addOnCompleteListener { reauthTask ->
                        if (reauthTask.isSuccessful) {
                            user.delete()
                                .addOnCompleteListener { deleteTask ->
                                    if (deleteTask.isSuccessful) {
                                        cleanupLocalData()
                                        cont.resume(DeleteAccountResult.Success)
                                    } else {
                                        cont.resume(
                                            DeleteAccountResult.Error(
                                                deleteTask.exception?.message ?: "Failed to delete account after reauthentication",
                                            ),
                                        )
                                    }
                                }
                        } else {
                            cont.resume(
                                DeleteAccountResult.Error(
                                    reauthTask.exception?.message?.let { msg ->
                                        if (msg.contains("password", ignoreCase = true) ||
                                            msg.contains("credential", ignoreCase = true)
                                        ) {
                                            "Incorrect password. Please try again."
                                        } else {
                                            msg
                                        }
                                    } ?: "Reauthentication failed",
                                ),
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DeleteAccountResult.Error(e.message ?: "An unexpected error occurred")
        }
    }

    private suspend fun cleanupFirestoreData(uid: String) {
        withContext(Dispatchers.IO) {
            try {
                val workspacesSnapshot = firestore
                    .collection(FirestoreConstants.COLLECTION_WORKSPACES)
                    .whereArrayContains(FirestoreConstants.FIELD_MEMBER_IDS, uid)
                    .get()
                    .await()

                for (doc in workspacesSnapshot.documents) {
                    val memberIds = doc.get(FirestoreConstants.FIELD_MEMBER_IDS) as? List<*>
                    val members = memberIds?.mapNotNull { it?.toString() } ?: emptyList()

                    if (members.size <= 1) {
                        deleteWorkspaceRecursively(doc.id)
                    } else {
                        doc.reference.update(
                            FirestoreConstants.FIELD_MEMBER_IDS,
                            members.filter { it != uid },
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("FirebaseAuthRepo", "Firestore cleanup failed, continuing", e)
            }
        }
    }

    private suspend fun deleteWorkspaceRecursively(workspaceId: String) {
        try {
            val vaultsSnapshot = firestore
                .collection(
                    "${FirestoreConstants.COLLECTION_WORKSPACES}/$workspaceId/${FirestoreConstants.COLLECTION_VAULTS}",
                )
                .get()
                .await()

            for (vaultDoc in vaultsSnapshot.documents) {
                val transactions = vaultDoc.reference
                    .collection(FirestoreConstants.COLLECTION_TRANSACTIONS)
                    .get()
                    .await()
                for (txn in transactions.documents) {
                    txn.reference.delete()
                }
                vaultDoc.reference.delete()
            }
            firestore.collection(FirestoreConstants.COLLECTION_WORKSPACES)
                .document(workspaceId)
                .delete()
        } catch (e: Exception) {
            Log.w("FirebaseAuthRepo", "Failed to delete workspace $workspaceId", e)
        }
    }

    private fun cleanupLocalData() {
        database?.let { db ->
            db.clearAllTables()
        }
    }
}

private fun FirebaseUser.toDomain(): User = User(
    id = uid,
    email = email ?: "",
    displayName = displayName,
)

private fun Exception.toAuthException(): AuthException {
    return when (this) {
        is FirebaseAuthWeakPasswordException -> AuthException(
            "Password should be at least 6 characters.",
        )
        is FirebaseAuthInvalidCredentialsException -> AuthException(
            "Invalid email or password.",
        )
        is FirebaseAuthUserCollisionException -> AuthException(
            "An account with this email already exists.",
        )
        else -> AuthException(message ?: "An authentication error occurred.")
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful && task.result != null) {
            @Suppress("UNCHECKED_CAST")
            cont.resume(task.result as T)
        } else if (task.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            cont.resume(task.result as T)
        } else {
            cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
}
