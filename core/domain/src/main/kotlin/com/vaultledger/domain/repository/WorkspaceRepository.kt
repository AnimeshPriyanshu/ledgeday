package com.vaultledger.domain.repository

import com.vaultledger.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    fun getAllWorkspaces(): Flow<List<Workspace>>
    suspend fun getWorkspaceById(id: String): Workspace?
    suspend fun createWorkspace(name: String, description: String): Workspace
    suspend fun updateWorkspace(workspace: Workspace)
    suspend fun deleteWorkspace(id: String)
}
