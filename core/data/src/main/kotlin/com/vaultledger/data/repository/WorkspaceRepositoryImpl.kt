package com.vaultledger.data.repository

import com.vaultledger.data.local.dao.WorkspaceDao
import com.vaultledger.data.local.entity.WorkspaceEntity
import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepositoryImpl @Inject constructor(
    private val workspaceDao: WorkspaceDao,
) : WorkspaceRepository {

    override fun getAllWorkspaces(): Flow<List<Workspace>> {
        return workspaceDao.getAllWorkspaces().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getWorkspaceById(id: String): Workspace? {
        return workspaceDao.getWorkspaceById(id)?.toDomain()
    }

    override suspend fun createWorkspace(name: String, description: String): Workspace {
        val now = System.currentTimeMillis()
        val entity = WorkspaceEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdAt = now,
        )
        workspaceDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun updateWorkspace(workspace: Workspace) {
        workspaceDao.update(workspace.toEntity())
    }

    override suspend fun deleteWorkspace(id: String) {
        val entity = workspaceDao.getWorkspaceById(id) ?: return
        workspaceDao.delete(entity)
    }
}

private fun WorkspaceEntity.toDomain(): Workspace = Workspace(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
)

private fun Workspace.toEntity(): WorkspaceEntity = WorkspaceEntity(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
)
