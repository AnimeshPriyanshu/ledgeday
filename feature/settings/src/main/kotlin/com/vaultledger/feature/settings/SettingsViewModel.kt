package com.vaultledger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.repository.AuthRepository
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    private val _partnerStatus = MutableStateFlow<PartnerUiState>(PartnerUiState.Loading)
    val partnerStatus: StateFlow<PartnerUiState> = _partnerStatus.asStateFlow()

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
                // Sign out errors are non-blocking
            }
        }
    }
}
