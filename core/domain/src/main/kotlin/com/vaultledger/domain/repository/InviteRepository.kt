package com.vaultledger.domain.repository

import com.vaultledger.domain.model.Invite
import com.vaultledger.domain.model.Workspace

interface InviteRepository {
    suspend fun createInvite(): Invite
    suspend fun getInvite(code: String): Invite?
    suspend fun acceptInvite(code: String): Workspace
    suspend fun revokeInvite(code: String)
}
