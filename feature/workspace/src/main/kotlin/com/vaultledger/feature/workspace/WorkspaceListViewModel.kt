package com.vaultledger.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.WorkspaceRepository
import com.vaultledger.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkspaceListViewModel @Inject constructor(
    private val repository: WorkspaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Workspace>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Workspace>>> = _uiState

    init {
        observeWorkspaces()
    }

    private fun observeWorkspaces() {
        viewModelScope.launch {
            repository.getAllWorkspaces()
                .map { workspaces ->
                    if (workspaces.isEmpty()) UiState.Empty else UiState.Success(workspaces)
                }
                .catch { e ->
                    _uiState.value = UiState.Error(e.message ?: "Failed to load workspaces")
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun createWorkspace(name: String) {
        viewModelScope.launch {
            try {
                repository.createWorkspace(name = name, description = "")
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to create workspace")
            }
        }
    }

    fun deleteWorkspace(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteWorkspace(id)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to delete workspace")
            }
        }
    }
}