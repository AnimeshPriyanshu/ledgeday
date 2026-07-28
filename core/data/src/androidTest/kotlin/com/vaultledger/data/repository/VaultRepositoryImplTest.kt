package com.vaultledger.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.local.entity.WorkspaceEntity
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
class VaultRepositoryImplTest {

    private lateinit var database: VaultLedgerDatabase
    private lateinit var repository: VaultRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, VaultLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VaultRepositoryImpl(
            vaultDao = database.vaultDao(),
            database = database,
        )
        seedWorkspace()
    }

    private suspend fun seedWorkspace() {
        database.workspaceDao().insert(
            WorkspaceEntity(
                id = "test-workspace",
                name = "Test Workspace",
                description = "",
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createVault_storesAndReturnsVaultWithZeroBalance() = runBlocking {
        val vault = repository.createVault(
            workspaceId = "test-workspace",
            name = "My Vault",
            description = "Test vault",
            color = "#006D77",
        )

        assertNotNull(vault.id)
        assertEquals("My Vault", vault.name)
        assertEquals("Test vault", vault.description)
        assertEquals("#006D77", vault.color)
        assertEquals(0L, vault.balance)
    }

    @Test
    fun getVaultById_returnsCreatedVault() = runBlocking {
        val created = repository.createVault(
            workspaceId = "test-workspace",
            name = "My Vault",
            description = "",
        )

        val retrieved = repository.getVaultById(created.id)

        assertNotNull(retrieved)
        assertEquals(created.id, retrieved!!.id)
        assertEquals("My Vault", retrieved.name)
    }

    @Test
    fun getVaultById_returnsNullForNonExistentId() = runBlocking {
        val result = repository.getVaultById("non-existent")
        assertNull(result)
    }

    @Test
    fun updateVault_modifiesVaultFields() = runBlocking {
        val created = repository.createVault(
            workspaceId = "test-workspace",
            name = "Original",
            description = "",
        )

        repository.updateVault(created.copy(name = "Updated", description = "Updated desc"))

        val retrieved = repository.getVaultById(created.id)
        assertEquals("Updated", retrieved!!.name)
        assertEquals("Updated desc", retrieved.description)
    }

    @Test
    fun deleteVault_removesVault() = runBlocking {
        val vault = repository.createVault(
            workspaceId = "test-workspace",
            name = "To Delete",
            description = "",
        )

        repository.deleteVault(vault.id)

        assertNull(repository.getVaultById(vault.id))
    }

    @Test
    fun getVaultsByWorkspaceId_returnsOnlyVaultsForGivenWorkspace() = runBlocking {
        database.workspaceDao().insert(
            WorkspaceEntity(
                id = "other-workspace",
                name = "Other",
                description = "",
                createdAt = System.currentTimeMillis(),
            ),
        )

        repository.createVault("test-workspace", "Vault 1", "")
        repository.createVault("test-workspace", "Vault 2", "")
        repository.createVault("other-workspace", "Other Vault", "")

        val vaults = repository.getVaultsByWorkspaceId("test-workspace").first()
        assertEquals(2, vaults.size)
        assertTrue(vaults.all { it.workspaceId == "test-workspace" })
    }

    @Test
    fun getVaultsByWorkspaceId_returnsEmptyListForWorkspaceWithNoVaults() = runBlocking {
        database.workspaceDao().insert(
            WorkspaceEntity(
                id = "empty-workspace",
                name = "Empty",
                description = "",
                createdAt = System.currentTimeMillis(),
            ),
        )

        val vaults = repository.getVaultsByWorkspaceId("empty-workspace").first()
        assertTrue(vaults.isEmpty())
    }

    @Test
    fun updateBalance_modifiesVaultBalance() = runBlocking {
        val vault = repository.createVault("test-workspace", "Test", "")

        repository.updateBalance(vault.id, 500L)

        val retrieved = repository.getVaultById(vault.id)
        assertEquals(500L, retrieved!!.balance)
    }

    @Test
    fun deleteVault_doesNotAffectOtherVaultsInSameWorkspace() = runBlocking {
        val v1 = repository.createVault("test-workspace", "Vault 1", "")
        val v2 = repository.createVault("test-workspace", "Vault 2", "")

        repository.deleteVault(v1.id)

        val vaults = repository.getVaultsByWorkspaceId("test-workspace").first()
        assertEquals(1, vaults.size)
        assertEquals(v2.id, vaults[0].id)
    }
}
