package com.vaultledger.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.repository.exception.AuthException
import com.vaultledger.data.sync.SyncManager
import com.vaultledger.domain.model.User
import com.vaultledger.domain.repository.AuthRepository
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

    override fun observeAuthState(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            Log.d(TAG, "AuthStateListener fired: uid=${user?.uid ?: "null"}")
            trySend(user?.toDomain())
        }
        firebaseAuth.addAuthStateListener(listener)

        val currentUser = firebaseAuth.currentUser
        Log.d(TAG, "Initial emit: currentUser=${currentUser?.uid ?: "null"}")
        trySend(currentUser?.toDomain())

        awaitClose {
            Log.d(TAG, "AuthStateListener removed")
            firebaseAuth.removeAuthStateListener(listener)
        }
    }.buffer(Channel.UNLIMITED)

    override suspend fun signIn(email: String, password: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "signIn: successful for uid=${firebaseAuth.currentUser?.uid}")
                        continuation.resume(Unit)
                    } else {
                        Log.w(TAG, "signIn: failed: ${task.exception?.message}")
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
                        Log.d(TAG, "signUp: successful for uid=${firebaseAuth.currentUser?.uid}")
                        continuation.resume(Unit)
                    } else {
                        Log.w(TAG, "signUp: failed: ${task.exception?.message}")
                        continuation.resumeWithException(
                            task.exception?.toAuthException() ?: AuthException("Registration failed"),
                        )
                    }
                }
        }
    }

    override suspend fun signOut() {
        Log.d(TAG, "signOut: starting")
        syncManager?.stopSyncing()
        database?.let { db ->
            withContext(Dispatchers.IO) {
                Log.d(TAG, "signOut: clearing local database")
                db.clearAllTables()
            }
        }
        firebaseAuth.signOut()
        Log.d(TAG, "signOut: complete")
    }

    companion object {
        private const val TAG = "FirebaseAuthRepo"
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
