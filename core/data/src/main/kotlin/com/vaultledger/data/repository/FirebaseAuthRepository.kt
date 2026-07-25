package com.vaultledger.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.vaultledger.data.repository.exception.AuthException
import com.vaultledger.domain.model.User
import com.vaultledger.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor() : AuthRepository {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun observeAuthState(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toDomain())
        }
        firebaseAuth.addAuthStateListener(listener)

        // Emit current state immediately
        trySend(firebaseAuth.currentUser?.toDomain())

        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

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
        firebaseAuth.signOut()
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
