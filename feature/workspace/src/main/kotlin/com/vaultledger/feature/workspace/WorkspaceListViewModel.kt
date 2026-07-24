package com.vaultledger.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.WorkspaceRepository
import com.vaultledger.ui.common.UiOperation
import com.vaultledger.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class WorkspaceListViewModel @Inject constructor(
    private val repository: WorkspaceRepository,
) : ViewModel() {

    private val operation = UiOperation<List<Workspace>>(viewModelScope)
    val uiState: StateFlow<UiState<List<Workspace>>> = operation.state

    init {
        loadWorkspaces()
    }

    private fun loadWorkspaces() {
        operation.observe(
            provide = { repository.getAllWorkspaces() },
            map = { workspaces ->
                if (workspaces.isEmpty()) UiState.Empty else UiState.Success(workspaces)
            },
        )
    }

    fun createWorkspace(name: String) {
        operation.launch {
            repository.createWorkspace(name = name, description = "")
        }
    }

    fun deleteWorkspace(id: String) {
        operation.launch {
            repository.deleteWorkspace(id)
        }
    }

    fun retry() = operation.retry()
}
