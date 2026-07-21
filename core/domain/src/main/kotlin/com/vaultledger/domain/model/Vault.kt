package com.vaultledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Vault(
    val id: String,
    val workspaceId: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val transactionCount: Int = 0,
    val balance: Long = 0L,
)
