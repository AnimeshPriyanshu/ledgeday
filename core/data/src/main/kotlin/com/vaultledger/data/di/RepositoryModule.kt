package com.vaultledger.data.di

import com.vaultledger.data.repository.FirebaseAuthRepository
import com.vaultledger.data.repository.FirebaseInviteRepository
import com.vaultledger.data.repository.TransactionRepositoryImpl
import com.vaultledger.data.repository.VaultRepositoryImpl
import com.vaultledger.data.repository.WorkspaceRepositoryImpl
import com.vaultledger.domain.repository.AuthRepository
import com.vaultledger.domain.repository.InviteRepository
import com.vaultledger.domain.repository.TransactionRepository
import com.vaultledger.domain.repository.VaultRepository
import com.vaultledger.domain.repository.WorkspaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWorkspaceRepository(
        impl: WorkspaceRepositoryImpl,
    ): WorkspaceRepository

    @Binds
    @Singleton
    abstract fun bindVaultRepository(
        impl: VaultRepositoryImpl,
    ): VaultRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl,
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: FirebaseAuthRepository,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindInviteRepository(
        impl: FirebaseInviteRepository,
    ): InviteRepository
}
