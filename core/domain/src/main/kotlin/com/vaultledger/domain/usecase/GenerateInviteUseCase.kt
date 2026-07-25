package com.vaultledger.domain.usecase

import com.vaultledger.domain.model.Invite
import com.vaultledger.domain.repository.InviteRepository
import javax.inject.Inject

class GenerateInviteUseCase @Inject constructor(
    private val inviteRepository: InviteRepository,
) {
    suspend operator fun invoke(): Invite {
        return inviteRepository.createInvite()
    }
}
