package com.vaultledger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.repository.AuthRepository
import com.vaultledger.domain.repository.DeleteAccountResult
import com.vaultledger.domain.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PartnerUiState {
    data object Loading : PartnerUiState
    data object NoPartner : PartnerUiState
    data object Connected : PartnerUiState
}

sealed interface DeleteAccountUiState {
    data object Idle : DeleteAccountUiState
    data object ConfirmDeletion : DeleteAccountUiState
    data object NeedsPassword : DeleteAccountUiState
    data object Deleting : DeleteAccountUiState
    data class Success(val message: String) : DeleteAccountUiState
    data class Error(val message: String) : DeleteAccountUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    private val _partnerStatus = MutableStateFlow<PartnerUiState>(PartnerUiState.Loading)
    val partnerStatus: StateFlow<PartnerUiState> = _partnerStatus.asStateFlow()

    private val _deleteAccountState = MutableStateFlow<DeleteAccountUiState>(DeleteAccountUiState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountUiState> = _deleteAccountState.asStateFlow()

    init {
        viewModelScope.launch {
            workspaceRepository.getAllWorkspaces().collect { workspaces ->
                val hasPartner = workspaces.any { it.memberIds.size > 1 }
                _partnerStatus.value = if (hasPartner) PartnerUiState.Connected else PartnerUiState.NoPartner
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun requestDeleteAccount() {
        _deleteAccountState.value = DeleteAccountUiState.ConfirmDeletion
    }

    fun confirmDeleteAccount() {
        viewModelScope.launch {
            _deleteAccountState.value = DeleteAccountUiState.Deleting
            when (val result = authRepository.deleteAccount()) {
                is DeleteAccountResult.Success -> {
                    _deleteAccountState.value = DeleteAccountUiState.Success("Account deleted successfully")
                }
                is DeleteAccountResult.NeedsReauthentication -> {
                    _deleteAccountState.value = DeleteAccountUiState.NeedsPassword
                }
                is DeleteAccountResult.Error -> {
                    _deleteAccountState.value = DeleteAccountUiState.Error(result.message)
                }
            }
        }
    }

    fun reauthenticateAndDelete(password: String) {
        viewModelScope.launch {
            _deleteAccountState.value = DeleteAccountUiState.Deleting
            when (val result = authRepository.reauthenticateAndDelete(password)) {
                is DeleteAccountResult.Success -> {
                    _deleteAccountState.value = DeleteAccountUiState.Success("Account deleted successfully")
                }
                is DeleteAccountResult.NeedsReauthentication -> {
                    _deleteAccountState.value = DeleteAccountUiState.NeedsPassword
                }
                is DeleteAccountResult.Error -> {
                    _deleteAccountState.value = DeleteAccountUiState.Error(result.message)
                }
            }
        }
    }

    fun dismissDeleteAccount() {
        _deleteAccountState.value = DeleteAccountUiState.Idle
    }
}
