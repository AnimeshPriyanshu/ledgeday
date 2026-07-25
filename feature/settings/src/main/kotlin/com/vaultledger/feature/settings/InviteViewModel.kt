package com.vaultledger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.data.remote.FirestoreErrorMapper
import com.vaultledger.domain.model.Invite
import com.vaultledger.domain.usecase.GenerateInviteUseCase
import com.vaultledger.domain.usecase.RevokeInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface InviteUiState {
    data object Idle : InviteUiState
    data object Generating : InviteUiState
    data class Generated(val invite: Invite) : InviteUiState
    data class Error(val message: String, val isRetryable: Boolean) : InviteUiState
    data object Revoked : InviteUiState
}

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val generateInviteUseCase: GenerateInviteUseCase,
    private val revokeInviteUseCase: RevokeInviteUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<InviteUiState>(InviteUiState.Idle)
    val state: StateFlow<InviteUiState> = _state.asStateFlow()

    fun generateInvite() {
        viewModelScope.launch {
            _state.value = InviteUiState.Generating
            try {
                val invite = generateInviteUseCase()
                _state.value = InviteUiState.Generated(invite)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val mapped = FirestoreErrorMapper.map(e)
                _state.value = InviteUiState.Error(mapped.userMessage, mapped.isRetryable)
            }
        }
    }

    fun revokeInvite(code: String) {
        viewModelScope.launch {
            try {
                revokeInviteUseCase(code)
                _state.value = InviteUiState.Revoked
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val mapped = FirestoreErrorMapper.map(e)
                _state.value = InviteUiState.Error(mapped.userMessage, mapped.isRetryable)
            }
        }
    }

    fun reset() {
        _state.value = InviteUiState.Idle
    }
}
