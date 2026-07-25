package com.vaultledger.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private val MIGRATION_1_2 = Migration(1, 2) { db ->
        db.execSQL("ALTER TABLE vaults ADD COLUMN color TEXT NOT NULL DEFAULT '#006D77'")
    }

    private val MIGRATION_2_3 = Migration(2, 3) { db ->
        db.execSQL("ALTER TABLE workspaces ADD COLUMN memberIds TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE workspaces ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workspaces ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vaults ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vaults ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
    }

    private val MIGRATION_3_4 = Migration(3, 4) { db ->
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_description ON transactions(description)")
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaultLedgerDatabase {
        return Room.databaseBuilder(
            context,
            VaultLedgerDatabase::class.java,
            "vault-ledger-db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
