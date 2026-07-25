package com.vaultledger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.data.remote.FirestoreErrorMapper
import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.usecase.AcceptInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AcceptInviteUiState {
    data object Idle : AcceptInviteUiState
    data object Accepting : AcceptInviteUiState
    data class Success(val workspace: Workspace) : AcceptInviteUiState
    data class Error(val message: String, val isRetryable: Boolean) : AcceptInviteUiState
}

@HiltViewModel
class AcceptInviteViewModel @Inject constructor(
    private val acceptInviteUseCase: AcceptInviteUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<AcceptInviteUiState>(AcceptInviteUiState.Idle)
    val state: StateFlow<AcceptInviteUiState> = _state.asStateFlow()

    fun acceptInvite(code: String) {
        viewModelScope.launch {
            _state.value = AcceptInviteUiState.Accepting
            try {
                val workspace = acceptInviteUseCase(code)
                _state.value = AcceptInviteUiState.Success(workspace)
            } catch (e: Exception) {
                val mapped = FirestoreErrorMapper.map(e)
                _state.value = AcceptInviteUiState.Error(mapped.userMessage, mapped.isRetryable)
            }
        }
    }

    fun reset() {
        _state.value = AcceptInviteUiState.Idle
    }
}
