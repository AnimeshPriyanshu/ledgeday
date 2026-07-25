package com.vaultledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vaultledger.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = VaultEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("vaultId"),
        Index(
            value = ["vaultId", "createdAt"],
            orders = [Index.Order.ASC, Index.Order.DESC],
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val vaultId: String,
    val type: TransactionType,
    val amount: Long,
    val description: String,
    val createdAt: Long,
)
