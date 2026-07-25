package com.vaultledger.data.repository

import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.remote.VaultRemoteDataSource
import com.vaultledger.domain.model.Vault
import com.vaultledger.domain.repository.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val vaultDao: VaultDao,
    private val vaultRemoteDataSource: VaultRemoteDataSource? = null,
) : VaultRepository {

    override fun getVaultsByWorkspaceId(workspaceId: String): Flow<List<Vault>> {
        return vaultDao.getVaultsByWorkspaceId(workspaceId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getVaultById(id: String): Vault? {
        return vaultDao.getVaultById(id)?.toDomain()
    }

    override suspend fun createVault(
        workspaceId: String,
        name: String,
        description: String,
        color: String,
    ): Vault {
        val now = System.currentTimeMillis()
        val entity = VaultEntity(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            name = name,
            description = description,
            createdAt = now,
            balance = 0L,
            color = color,
            synced = false,
            updatedAt = now,
        )
        vaultDao.insert(entity)

        if (vaultRemoteDataSource != null) {
            try {
                vaultRemoteDataSource.createVault(workspaceId, entity.toDomain())
                val syncedEntity = entity.copy(synced = true)
                vaultDao.insert(syncedEntity)
                return syncedEntity.toDomain()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Keep synced = false on failure
            }
        }
        return entity.toDomain()
    }

    override suspend fun updateVault(vault: Vault) {
        vaultDao.update(vault.toEntity())
    }

    override suspend fun updateBalance(id: String, balance: Long) {
        vaultDao.updateBalance(id, balance)
    }

    override suspend fun deleteVault(id: String) {
        val entity = vaultDao.getVaultById(id) ?: return
        vaultDao.delete(entity)
    }
}

private fun VaultEntity.toDomain(): Vault = Vault(
    id = id,
    workspaceId = workspaceId,
    name = name,
    description = description,
    createdAt = createdAt,
    balance = balance,
    color = color,
)

private fun Vault.toEntity(): VaultEntity = VaultEntity(
    id = id,
    workspaceId = workspaceId,
    name = name,
    description = description,
    createdAt = createdAt,
    balance = balance,
    color = color,
)
