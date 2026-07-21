package com.vaultledger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultledger.data.local.entity.VaultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vault: VaultEntity)

    @Update
    suspend fun update(vault: VaultEntity)

    @Delete
    suspend fun delete(vault: VaultEntity)

    @Query("SELECT * FROM vaults WHERE workspaceId = :workspaceId")
    fun getVaultsByWorkspaceId(workspaceId: String): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vaults WHERE id = :id")
    suspend fun getVaultById(id: String): VaultEntity?

    @Query("UPDATE vaults SET balance = :balance WHERE id = :id")
    suspend fun updateBalance(id: String, balance: Long)
}
