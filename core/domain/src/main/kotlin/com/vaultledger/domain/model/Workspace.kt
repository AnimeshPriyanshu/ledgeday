package com.vaultledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val memberIds: List<String> = emptyList(),
    val vaultCount: Int = 0,
)
