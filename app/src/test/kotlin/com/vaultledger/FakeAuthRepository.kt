package com.vaultledger

import com.vaultledger.domain.model.User
import com.vaultledger.domain.repository.AuthRepository
import com.vaultledger.domain.repository.DeleteAccountResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository : AuthRepository {

    private val _authState = MutableStateFlow<User?>(null)

    var deleteAccountResult: DeleteAccountResult = DeleteAccountResult.Success
    var reauthDeleteAccountResult: DeleteAccountResult = DeleteAccountResult.Success

    override fun observeAuthState(): Flow<User?> = _authState

    override suspend fun signIn(email: String, password: String) {
        _authState.value = User(
            id = "user-1",
            email = email,
            displayName = null,
        )
    }

    override suspend fun signUp(email: String, password: String) {
        _authState.value = User(
            id = "user-1",
            email = email,
            displayName = null,
        )
    }

    override suspend fun signOut() {
        _authState.value = null
    }

    override suspend fun deleteAccount(): DeleteAccountResult = deleteAccountResult

    override suspend fun reauthenticateAndDelete(password: String): DeleteAccountResult = reauthDeleteAccountResult

    override suspend fun getCurrentUserEmail(): String? = _authState.value?.email
}
