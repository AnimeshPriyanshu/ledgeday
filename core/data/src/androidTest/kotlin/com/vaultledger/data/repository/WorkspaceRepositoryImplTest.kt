package com.vaultledger.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.local.entity.VaultEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceRepositoryImplTest {

    private lateinit var database: VaultLedgerDatabase
    private lateinit var repository: WorkspaceRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, VaultLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkspaceRepositoryImpl(
            workspaceDao = database.workspaceDao(),
            database = database,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createWorkspace_storesAndReturnsWorkspace() = runBlocking {
        val workspace = repository.createWorkspace("Test", "Description")

        assertNotNull(workspace.id)
        assertEquals("Test", workspace.name)
        assertEquals("Description", workspace.description)
    }

    @Test
    fun getWorkspaceById_returnsCreatedWorkspace() = runBlocking {
        val created = repository.createWorkspace("Test", "Description")

        val retrieved = repository.getWorkspaceById(created.id)

        assertNotNull(retrieved)
        assertEquals(created.id, retrieved!!.id)
        assertEquals("Test", retrieved.name)
    }

    @Test
    fun getWorkspaceById_returnsNullForNonExistentId() = runBlocking {
        val result = repository.getWorkspaceById("non-existent")
        assertNull(result)
    }

    @Test
    fun updateWorkspace_modifiesExistingWorkspace() = runBlocking {
        val created = repository.createWorkspace("Original", "Original desc")

        repository.updateWorkspace(created.copy(name = "Updated", description = "Updated desc"))

        val retrieved = repository.getWorkspaceById(created.id)
        assertEquals("Updated", retrieved!!.name)
        assertEquals("Updated desc", retrieved.description)
    }

    @Test
    fun deleteWorkspace_removesWorkspace() = runBlocking {
        val workspace = repository.createWorkspace("Test", "Description")

        repository.deleteWorkspace(workspace.id)

        assertNull(repository.getWorkspaceById(workspace.id))
    }

    @Test
    fun getAllWorkspaces_emitsEmptyListInitially() = runBlocking {
        val workspaces = repository.getAllWorkspaces().first()
        assertTrue(workspaces.isEmpty())
    }

    @Test
    fun getAllWorkspaces_emitsNewlyCreatedWorkspace() = runBlocking {
        repository.createWorkspace("Test", "Description")

        val workspaces = repository.getAllWorkspaces().first()
        assertEquals(1, workspaces.size)
        assertEquals("Test", workspaces[0].name)
    }

    @Test
    fun getAllWorkspaces_reflectsDeletion() = runBlocking {
        val w1 = repository.createWorkspace("One", "")
        repository.createWorkspace("Two", "")

        repository.deleteWorkspace(w1.id)

        val workspaces = repository.getAllWorkspaces().first()
        assertEquals(1, workspaces.size)
        assertEquals("Two", workspaces[0].name)
    }

    @Test
    fun deleteWorkspace_cascadesToVaults() = runBlocking {
        val workspace = repository.createWorkspace("Test", "")
        val vaultDao = database.vaultDao()

        vaultDao.insert(
            VaultEntity(
                id = "vault-1",
                workspaceId = workspace.id,
                name = "Vault",
                description = "",
                createdAt = System.currentTimeMillis(),
                balance = 0L,
                color = "#006D77",
            ),
        )

        repository.deleteWorkspace(workspace.id)

        val vaults = vaultDao.getVaultsByWorkspaceId(workspace.id).first()
        assertTrue(vaults.isEmpty())
    }
}
