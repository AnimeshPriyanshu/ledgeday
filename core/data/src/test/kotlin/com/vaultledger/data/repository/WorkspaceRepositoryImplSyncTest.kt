package com.vaultledger.data.repository

import com.vaultledger.data.local.dao.WorkspaceDao
import com.vaultledger.data.local.entity.WorkspaceEntity
import com.vaultledger.data.remote.WorkspaceRemoteDataSource
import com.vaultledger.domain.model.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WorkspaceRepositoryImplSyncTest {

    private lateinit var workspaceDao: FakeWorkspaceDaoSync
    private lateinit var workspaceRemote: FakeWorkspaceRemoteSync
    private lateinit var repository: WorkspaceRepositoryImpl

    @BeforeEach
    fun setUp() {
        workspaceDao = FakeWorkspaceDaoSync()
        workspaceRemote = FakeWorkspaceRemoteSync()
        repository = WorkspaceRepositoryImpl(
            workspaceDao = workspaceDao,
            workspaceRemoteDataSource = workspaceRemote,
        )
    }

    @Test
    fun `deleteWorkspace calls remote delete`() = runTest {
        val entity = WorkspaceEntity(id = "ws-1", name = "Test", description = "", createdAt = 1000L, memberIds = listOf("user-1"))
        workspaceDao.insert(entity)

        repository.deleteWorkspace("ws-1")

        assertTrue(workspaceRemote.deleteCalled)
        assertNull(workspaceDao.getWorkspaceById("ws-1"))
    }

    @Test
    fun `deleteWorkspace rolls back on remote failure`() = runTest {
        val entity = WorkspaceEntity(id = "ws-1", name = "Test", description = "", createdAt = 1000L, memberIds = listOf("user-1"))
        workspaceDao.insert(entity)
        workspaceRemote.shouldFail = true

        repository.deleteWorkspace("ws-1")

        val stored = workspaceDao.getWorkspaceById("ws-1")
        assertEquals("Test", stored?.name)
    }

    @Test
    fun `createWorkspace throws for blank name`() = runTest {
        assertThrows<IllegalArgumentException> {
            repository.createWorkspace(name = "  ", description = "")
        }
    }

    @Test
    fun `deleteWorkspace noops for nonexistent id`() = runTest {
        repository.deleteWorkspace("nonexistent")

        assertFalse(workspaceRemote.deleteCalled)
    }

    @Test
    fun `updateWorkspace calls remote update`() = runTest {
        val entity = WorkspaceEntity(id = "ws-1", name = "Original", description = "Original desc", createdAt = 1000L, memberIds = listOf("user-1"))
        workspaceDao.insert(entity)

        repository.updateWorkspace(Workspace(id = "ws-1", name = "Updated", description = "Updated desc", createdAt = 1000L))

        assertTrue(workspaceRemote.updateCalled)
        val stored = workspaceDao.getWorkspaceById("ws-1")
        assertEquals("Updated", stored?.name)
        assertEquals("Updated desc", stored?.description)
    }

    @Test
    fun `updateWorkspace rolls back on remote failure`() = runTest {
        val entity = WorkspaceEntity(id = "ws-1", name = "Original", description = "Original desc", createdAt = 1000L, memberIds = listOf("user-1"))
        workspaceDao.insert(entity)
        workspaceRemote.shouldFail = true

        repository.updateWorkspace(Workspace(id = "ws-1", name = "Updated", description = "Updated desc", createdAt = 1000L))

        val stored = workspaceDao.getWorkspaceById("ws-1")
        assertEquals("Original", stored?.name)
        assertEquals("Original desc", stored?.description)
    }

    @Test
    fun `updateWorkspace noops for nonexistent id`() = runTest {
        repository.updateWorkspace(Workspace(id = "nonexistent", name = "Nope", description = "", createdAt = 1000L))

        assertFalse(workspaceRemote.updateCalled)
    }
}

class FakeWorkspaceDaoSync : WorkspaceDao {
    private val workspaces = mutableMapOf<String, WorkspaceEntity>()

    override suspend fun insert(workspace: WorkspaceEntity) { workspaces[workspace.id] = workspace }
    override suspend fun insertAll(workspaces: List<WorkspaceEntity>) { workspaces.forEach { insert(it) } }
    override suspend fun update(workspace: WorkspaceEntity) { workspaces[workspace.id] = workspace }
    override suspend fun delete(workspace: WorkspaceEntity) { workspaces.remove(workspace.id) }
    override fun getAllWorkspaces(): Flow<List<WorkspaceEntity>> = flowOf(workspaces.values.toList())
    override suspend fun getWorkspaceById(id: String): WorkspaceEntity? = workspaces[id]
    override suspend fun getUnsyncedWorkspaces(): List<WorkspaceEntity> = workspaces.values.filter { !it.synced }
}

class FakeWorkspaceRemoteSync : WorkspaceRemoteDataSource() {
    var createCalled = false
    var deleteCalled = false
    var updateCalled = false
    var shouldFail = false

    override suspend fun createWorkspace(workspace: Workspace, creatorId: String) {
        createCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }

    override suspend fun deleteWorkspace(workspaceId: String) {
        deleteCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }

    override suspend fun updateWorkspace(workspace: Workspace) {
        updateCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }
}
