package com.vaultledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Invite(
    val code: String,
    val creatorId: String,
    val creatorEmail: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: InviteStatus,
    val acceptedBy: String? = null,
)
