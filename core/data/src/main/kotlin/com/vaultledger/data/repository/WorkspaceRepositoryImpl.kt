package com.vaultledger.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.vaultledger.data.local.dao.WorkspaceDao
import com.vaultledger.data.local.entity.WorkspaceEntity
import com.vaultledger.data.remote.WorkspaceRemoteDataSource
import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepositoryImpl @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    private val workspaceRemoteDataSource: WorkspaceRemoteDataSource? = null,
    private val firebaseAuth: FirebaseAuth? = null,
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
        val currentUserId = try { firebaseAuth?.currentUser?.uid ?: "" } catch (_: Exception) { "" }
        val memberIds = if (currentUserId.isNotBlank()) listOf(currentUserId) else emptyList()
        val entity = WorkspaceEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdAt = now,
            memberIds = memberIds,
            synced = false,
            updatedAt = now,
        )
        workspaceDao.insert(entity)

        if (workspaceRemoteDataSource != null) {
            try {
                workspaceRemoteDataSource.createWorkspace(entity.toDomain(), currentUserId)
                val syncedEntity = entity.copy(synced = true)
                workspaceDao.insert(syncedEntity)
                return syncedEntity.toDomain()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Keep synced = false on remote upload error
            }
        }
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
    memberIds = memberIds,
)

private fun Workspace.toEntity(): WorkspaceEntity = WorkspaceEntity(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    memberIds = memberIds,
)
