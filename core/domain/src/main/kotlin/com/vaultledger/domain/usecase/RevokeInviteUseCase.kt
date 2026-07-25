package com.vaultledger.domain.usecase

import com.vaultledger.domain.repository.InviteRepository
import javax.inject.Inject

class RevokeInviteUseCase @Inject constructor(
    private val inviteRepository: InviteRepository,
) {
    suspend operator fun invoke(code: String) {
        inviteRepository.revokeInvite(code)
    }
}
