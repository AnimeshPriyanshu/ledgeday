package com.vaultledger.data.repository

import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.remote.VaultRemoteDataSource
import com.vaultledger.domain.model.Vault
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

class VaultRepositoryImplSyncTest {

    private lateinit var vaultDao: FakeVaultDaoSync
    private lateinit var vaultRemote: FakeVaultRemoteSync
    private lateinit var repository: VaultRepositoryImpl

    @BeforeEach
    fun setUp() {
        vaultDao = FakeVaultDaoSync()
        vaultRemote = FakeVaultRemoteSync()
        repository = VaultRepositoryImpl(
            vaultDao = vaultDao,
            vaultRemoteDataSource = vaultRemote,
        )
    }

    @Test
    fun `deleteVault calls remote delete`() = runTest {
        val entity = VaultEntity(id = "vault-1", workspaceId = "ws-1", name = "Test", description = "", createdAt = 1000L)
        vaultDao.insert(entity)

        repository.deleteVault("vault-1")

        assertTrue(vaultRemote.deleteCalled)
        assertNull(vaultDao.getVaultById("vault-1"))
    }

    @Test
    fun `deleteVault rolls back on remote failure`() = runTest {
        val entity = VaultEntity(id = "vault-1", workspaceId = "ws-1", name = "Test", description = "", createdAt = 1000L)
        vaultDao.insert(entity)
        vaultRemote.shouldFail = true

        repository.deleteVault("vault-1")

        val stored = vaultDao.getVaultById("vault-1")
        assertEquals("Test", stored?.name)
    }

    @Test
    fun `createVault throws for blank name`() = runTest {
        assertThrows<IllegalArgumentException> {
            repository.createVault(workspaceId = "ws-1", name = "  ", description = "")
        }
    }

    @Test
    fun `createVault throws for blank workspaceId`() = runTest {
        assertThrows<IllegalArgumentException> {
            repository.createVault(workspaceId = "", name = "Test", description = "")
        }
    }

    @Test
    fun `deleteVault noops for nonexistent id`() = runTest {
        repository.deleteVault("nonexistent")

        assertFalse(vaultRemote.deleteCalled)
    }
}

class FakeVaultDaoSync : VaultDao {
    private val vaults = mutableMapOf<String, VaultEntity>()

    override suspend fun insert(vault: VaultEntity) { vaults[vault.id] = vault }
    override suspend fun update(vault: VaultEntity) { vaults[vault.id] = vault }
    override suspend fun delete(vault: VaultEntity) { vaults.remove(vault.id) }
    override fun getVaultsByWorkspaceId(workspaceId: String): Flow<List<VaultEntity>> {
        return flowOf(vaults.values.filter { it.workspaceId == workspaceId })
    }
    override suspend fun getVaultById(id: String): VaultEntity? = vaults[id]
    override suspend fun getUnsyncedVaults(): List<VaultEntity> = vaults.values.filter { !it.synced }
    override suspend fun updateBalance(id: String, balance: Long) { vaults[id]?.let { vaults[id] = it.copy(balance = balance) } }
}

class FakeVaultRemoteSync : VaultRemoteDataSource() {
    var createCalled = false
    var deleteCalled = false
    var shouldFail = false

    override suspend fun createVault(workspaceId: String, vault: Vault) {
        createCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }

    override suspend fun deleteVault(workspaceId: String, vaultId: String) {
        deleteCalled = true
        if (shouldFail) throw RuntimeException("Simulated failure")
    }
}
