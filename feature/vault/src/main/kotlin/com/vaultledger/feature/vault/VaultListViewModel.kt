package com.vaultledger.feature.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.model.Vault
import com.vaultledger.domain.repository.VaultRepository
import com.vaultledger.ui.common.UiOperation
import com.vaultledger.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class VaultListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
) : ViewModel() {

    private val workspaceId: String = requireNotNull(savedStateHandle[ARG_WORKSPACE_ID]) {
        "Missing workspaceId navigation argument"
    }

    private val operation = UiOperation<List<Vault>>(viewModelScope)
    val uiState: StateFlow<UiState<List<Vault>>> = operation.state

    init {
        observeVaults()
    }

    private fun observeVaults() {
        operation.observe(
            provide = { repository.getVaultsByWorkspaceId(workspaceId) },
            map = { vaults ->
                if (vaults.isEmpty()) UiState.Empty else UiState.Success(vaults)
            },
        )
    }

    fun createVault(name: String, description: String, color: String) {
        operation.launch {
            repository.createVault(workspaceId = workspaceId, name = name, description = description, color = color)
        }
    }

    fun deleteVault(id: String) {
        operation.launch {
            repository.deleteVault(id)
        }
    }

    fun retry() = operation.retry()

    companion object {
        private const val ARG_WORKSPACE_ID = "workspaceId"
    }
}
