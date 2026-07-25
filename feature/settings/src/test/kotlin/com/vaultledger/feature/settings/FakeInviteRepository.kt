package com.vaultledger.feature.settings

import com.vaultledger.domain.model.Invite
import com.vaultledger.domain.model.InviteStatus
import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.InviteRepository
import java.util.UUID

class FakeInviteRepository : InviteRepository {

    private val invites = mutableMapOf<String, Invite>()
    var throwOnCreate: Boolean = false
    var throwOnAccept: Boolean = false
    var throwOnRevoke: Boolean = false
    var createError: String? = null
    var acceptError: String? = null
    var revokeError: String? = null

    override suspend fun createInvite(): Invite {
        if (throwOnCreate) {
            throw RuntimeException(createError ?: "Failed to create invite")
        }
        val code = UUID.randomUUID().toString().take(8).uppercase()
        val now = System.currentTimeMillis()
        val invite = Invite(
            code = code,
            creatorId = "creator-1",
            creatorEmail = "creator@test.com",
            createdAt = now,
            expiresAt = now + 86_400_000L,
            status = InviteStatus.ACTIVE,
        )
        invites[code] = invite
        return invite
    }

    override suspend fun getInvite(code: String): Invite? {
        return invites[code]
    }

    override suspend fun acceptInvite(code: String): Workspace {
        if (throwOnAccept) {
            throw RuntimeException(acceptError ?: "Failed to accept invite")
        }
        val invite = invites[code] ?: throw RuntimeException("Invite not found")
        invites[code] = invite.copy(status = InviteStatus.ACCEPTED, acceptedBy = "accepter-1")
        val now = System.currentTimeMillis()
        return Workspace(
            id = UUID.randomUUID().toString(),
            name = "Shared Workspace",
            description = "",
            createdAt = now,
            memberIds = listOf(invite.creatorId, "accepter-1"),
        )
    }

    override suspend fun revokeInvite(code: String) {
        if (throwOnRevoke) {
            throw RuntimeException(revokeError ?: "Failed to revoke invite")
        }
        invites[code]?.let { invites[code] = it.copy(status = InviteStatus.EXPIRED) }
    }
}
