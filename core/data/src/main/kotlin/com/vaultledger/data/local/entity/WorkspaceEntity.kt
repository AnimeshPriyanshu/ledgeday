package com.vaultledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val createdAt: Long,
    val memberIds: List<String> = emptyList(),
    val synced: Boolean = false,
    val updatedAt: Long = 0L,
)
