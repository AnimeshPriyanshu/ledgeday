package com.vaultledger.data.di

import android.content.Context
import androidx.room.Room
import com.vaultledger.data.local.VaultLedgerDatabase
import com.vaultledger.data.local.dao.TransactionDao
import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.dao.WorkspaceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaultLedgerDatabase {
        return Room.databaseBuilder(
            context,
            VaultLedgerDatabase::class.java,
            "vault-ledger-db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideWorkspaceDao(database: VaultLedgerDatabase): WorkspaceDao {
        return database.workspaceDao()
    }

    @Provides
    fun provideVaultDao(database: VaultLedgerDatabase): VaultDao {
        return database.vaultDao()
    }

    @Provides
    fun provideTransactionDao(database: VaultLedgerDatabase): TransactionDao {
        return database.transactionDao()
    }
}
