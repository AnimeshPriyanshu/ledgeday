package com.vaultledger.domain.repository

import com.vaultledger.domain.model.User
import kotlinx.coroutines.flow.Flow

sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult
    data object NeedsReauthentication : DeleteAccountResult
    data class Error(val message: String) : DeleteAccountResult
}

interface AuthRepository {
    fun observeAuthState(): Flow<User?>
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun signOut()
    suspend fun deleteAccount(): DeleteAccountResult
    suspend fun reauthenticateAndDelete(password: String): DeleteAccountResult
    suspend fun getCurrentUserEmail(): String?
}
