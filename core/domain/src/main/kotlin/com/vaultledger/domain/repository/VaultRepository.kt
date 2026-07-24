package com.vaultledger.domain.repository

import com.vaultledger.domain.model.Vault
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun getVaultsByWorkspaceId(workspaceId: String): Flow<List<Vault>>
    suspend fun getVaultById(id: String): Vault?
    suspend fun createVault(workspaceId: String, name: String, description: String, color: String = "#006D77"): Vault
    suspend fun updateVault(vault: Vault)
    suspend fun updateBalance(id: String, balance: Long)
    suspend fun deleteVault(id: String)
}
