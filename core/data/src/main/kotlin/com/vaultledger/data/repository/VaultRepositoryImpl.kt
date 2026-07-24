package com.vaultledger.data.repository

import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.domain.model.Vault
import com.vaultledger.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val vaultDao: VaultDao,
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
        )
        vaultDao.insert(entity)
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
