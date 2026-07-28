package com.vaultledger.data.repository

import com.vaultledger.domain.model.User
import com.vaultledger.domain.repository.AuthRepository
import com.vaultledger.domain.repository.DeleteAccountResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FakeAuthRepository : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    private val _registeredUsers = mutableMapOf<String, RegisteredUser>()

    var throwOnSignIn: Boolean = false
    var throwOnSignUp: Boolean = false
    var signInError: String? = null
    var signUpError: String? = null

    var deleteAccountResult: DeleteAccountResult = DeleteAccountResult.Success
    var reauthDeleteAccountResult: DeleteAccountResult = DeleteAccountResult.Success

    data class RegisteredUser(
        val email: String,
        val password: String,
        val id: String = UUID.randomUUID().toString(),
    )

    override fun observeAuthState(): Flow<User?> {
        return _currentUser.asStateFlow()
    }

    override suspend fun signIn(email: String, password: String) {
        if (throwOnSignIn) {
            throw RuntimeException(signInError ?: "Sign in failed")
        }
        val found = _registeredUsers.values.find { it.email == email }
            ?: throw RuntimeException("Invalid email or password.")
        if (found.password != password) {
            throw RuntimeException("Invalid email or password.")
        }
        _currentUser.value = User(
            id = found.id,
            email = found.email,
        )
    }

    override suspend fun signUp(email: String, password: String) {
        if (throwOnSignUp) {
            throw RuntimeException(signUpError ?: "Registration failed")
        }
        if (_registeredUsers.values.any { it.email == email }) {
            throw RuntimeException("An account with this email already exists.")
        }
        val user = RegisteredUser(
            email = email,
            password = password,
        )
        _registeredUsers[user.id] = user
        _currentUser.value = User(
            id = user.id,
            email = user.email,
        )
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }

    override suspend fun deleteAccount(): DeleteAccountResult = deleteAccountResult

    override suspend fun reauthenticateAndDelete(password: String): DeleteAccountResult = reauthDeleteAccountResult

    override suspend fun getCurrentUserEmail(): String? = _currentUser.value?.email

    fun setAuthenticatedUser(user: User) {
        _currentUser.value = user
    }
}
