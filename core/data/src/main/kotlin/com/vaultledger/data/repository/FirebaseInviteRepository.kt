package com.vaultledger.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.vaultledger.data.remote.InviteRemoteDataSource
import com.vaultledger.data.repository.exception.AuthException
import com.vaultledger.domain.model.Invite
import com.vaultledger.domain.model.Workspace
import com.vaultledger.domain.repository.InviteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseInviteRepository @Inject constructor(
    private val remoteDataSource: InviteRemoteDataSource,
    private val firebaseAuth: FirebaseAuth,
) : InviteRepository {

    override suspend fun createInvite(): Invite {
        val user = firebaseAuth.currentUser
        if (user == null) {
            throw AuthException("You must be signed in to create an invite")
        }
        return remoteDataSource.createInvite(
            creatorId = user.uid,
            creatorEmail = user.email ?: "",
        )
    }

    override suspend fun getInvite(code: String): Invite? {
        return remoteDataSource.getInvite(code)
    }

    override suspend fun acceptInvite(code: String): Workspace {
        val user = firebaseAuth.currentUser ?: throw AuthException("You must be signed in to accept an invite")
        return remoteDataSource.acceptInvite(code, accepterId = user.uid)
    }

    override suspend fun revokeInvite(code: String) {
        remoteDataSource.revokeInvite(code)
    }
}
