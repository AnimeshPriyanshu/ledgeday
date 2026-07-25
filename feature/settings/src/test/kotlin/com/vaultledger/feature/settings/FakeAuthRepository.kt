package com.vaultledger.feature.settings

import com.vaultledger.domain.model.User
import com.vaultledger.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository : AuthRepository {

    private val _authState = MutableStateFlow<User?>(null)

    var throwOnSignIn: Boolean = false
    var throwOnSignUp: Boolean = false
    var throwOnSignOut: Boolean = false
    var signInError: String? = null
    var signUpError: String? = null

    override fun observeAuthState(): Flow<User?> = _authState

    override suspend fun signIn(email: String, password: String) {
        if (throwOnSignIn) {
            throw RuntimeException(signInError ?: "Sign in failed")
        }
        _authState.value = User(
            id = "user-1",
            email = email,
            displayName = null,
        )
    }

    override suspend fun signUp(email: String, password: String) {
        if (throwOnSignUp) {
            throw RuntimeException(signUpError ?: "Registration failed")
        }
        _authState.value = User(
            id = "user-1",
            email = email,
            displayName = null,
        )
    }

    override suspend fun signOut() {
        if (throwOnSignOut) {
            throw RuntimeException("Sign out failed")
        }
        _authState.value = null
    }
}
