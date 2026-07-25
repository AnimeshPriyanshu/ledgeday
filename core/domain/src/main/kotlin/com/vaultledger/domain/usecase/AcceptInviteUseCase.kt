package com.vaultledger.domain.usecase

import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.InviteRepository
import javax.inject.Inject

class AcceptInviteUseCase @Inject constructor(
    private val inviteRepository: InviteRepository,
) {
    suspend operator fun invoke(code: String): Workspace {
        return inviteRepository.acceptInvite(code)
    }
}
