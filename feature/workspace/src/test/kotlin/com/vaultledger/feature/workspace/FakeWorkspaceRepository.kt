package com.vaultledger.feature.workspace

import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeWorkspaceRepository : WorkspaceRepository {

    private val _workspaces = MutableStateFlow(mutableMapOf<String, Workspace>())

    override fun getAllWorkspaces(): Flow<List<Workspace>> {
        return _workspaces.map { it.values.sortedBy { w -> w.createdAt } }
    }

    override suspend fun getWorkspaceById(id: String): Workspace? {
        return _workspaces.value[id]
    }

    override suspend fun createWorkspace(name: String, description: String): Workspace {
        val workspace = Workspace(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdAt = System.currentTimeMillis(),
        )
        _workspaces.value = _workspaces.value.toMutableMap().apply { put(workspace.id, workspace) }
        return workspace
    }

    override suspend fun updateWorkspace(workspace: Workspace) {
        _workspaces.value = _workspaces.value.toMutableMap().apply { put(workspace.id, workspace) }
    }

    override suspend fun deleteWorkspace(id: String) {
        _workspaces.value = _workspaces.value.toMutableMap().apply { remove(id) }
    }
}