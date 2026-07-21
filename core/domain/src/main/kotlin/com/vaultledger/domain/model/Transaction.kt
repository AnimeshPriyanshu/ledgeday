package com.vaultledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val id: String,
    val vaultId: String,
    val type: TransactionType,
    val amount: Long,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long? = null,
)
