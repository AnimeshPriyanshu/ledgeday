package com.vaultledger.feature.vault

import com.vaultledger.domain.model.Vault
import com.vaultledger.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeVaultRepository : VaultRepository {

    private val _vaults = MutableStateFlow(mutableMapOf<String, Vault>())
    var throwOnGetAll: Boolean = false
    var throwOnCreate: Boolean = false
    var throwOnDelete: Boolean = false

    override fun getVaultsByWorkspaceId(workspaceId: String): Flow<List<Vault>> {
        if (throwOnGetAll) {
            return kotlinx.coroutines.flow.flow {
                throw RuntimeException("Failed to load vaults")
            }
        }
        return _vaults.map { map ->
            map.values
                .filter { it.workspaceId == workspaceId }
                .sortedBy { it.createdAt }
        }
    }

    override suspend fun getVaultById(id: String): Vault? {
        return _vaults.value[id]
    }

    override suspend fun createVault(
        workspaceId: String,
        name: String,
        description: String,
        color: String,
    ): Vault {
        if (throwOnCreate) {
            throw RuntimeException("Failed to create vault")
        }
        val vault = Vault(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            name = name,
            description = description,
            createdAt = System.currentTimeMillis(),
            color = color,
        )
        _vaults.value = _vaults.value.toMutableMap().apply { put(vault.id, vault) }
        return vault
    }

    override suspend fun updateVault(vault: Vault) {
        _vaults.value = _vaults.value.toMutableMap().apply { put(vault.id, vault) }
    }

    override suspend fun updateBalance(id: String, balance: Long) {
        _vaults.value = _vaults.value.toMutableMap().apply {
            this[id]?.let { put(id, it.copy(balance = balance)) }
        }
    }

    override suspend fun deleteVault(id: String) {
        if (throwOnDelete) {
            throw RuntimeException("Failed to delete vault")
        }
        _vaults.value = _vaults.value.toMutableMap().apply { remove(id) }
    }
}
