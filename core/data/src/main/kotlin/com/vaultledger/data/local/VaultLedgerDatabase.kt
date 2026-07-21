package com.vaultledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vaultledger.data.local.dao.TransactionDao
import com.vaultledger.data.local.dao.VaultDao
import com.vaultledger.data.local.dao.WorkspaceDao
import com.vaultledger.data.local.entity.TransactionEntity
import com.vaultledger.data.local.entity.VaultEntity
import com.vaultledger.data.local.entity.WorkspaceEntity

@Database(
    entities = [WorkspaceEntity::class, VaultEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VaultLedgerDatabase : RoomDatabase() {

    abstract fun workspaceDao(): WorkspaceDao

    abstract fun vaultDao(): VaultDao

    abstract fun transactionDao(): TransactionDao
}
