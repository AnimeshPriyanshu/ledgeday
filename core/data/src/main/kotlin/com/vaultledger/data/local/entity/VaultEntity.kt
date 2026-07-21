package com.vaultledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vaults",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId")],
)
data class VaultEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val balance: Long = 0L,
)
