package com.vaultledger.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class InviteStatus {
    ACTIVE,
    ACCEPTED,
    EXPIRED,
}
