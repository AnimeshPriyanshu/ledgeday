package com.vaultledger.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultledger.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM transactions WHERE vaultId = :vaultId ORDER BY createdAt DESC")
    fun getTransactionsByVaultId(vaultId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query("SELECT id FROM transactions WHERE vaultId = :vaultId")
    suspend fun getTransactionIdsByVaultId(vaultId: String): List<String>

    @Query("SELECT * FROM transactions WHERE synced = 0")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    fun searchTransactions(vaultId: String, query: String): Flow<List<TransactionEntity>> {
        val trimmed = query.trim()
        return searchTransactionsInternal(vaultId, trimmed)
    }

    @Query(
        """
        SELECT t.* FROM transactions t
        LEFT JOIN vaults v ON t.vaultId = v.id
        WHERE t.vaultId = :vaultId
        AND (
            t.description LIKE '%' || :query || '%'
            OR CAST(t.amount AS TEXT) LIKE '%' || :query || '%'
            OR v.name LIKE '%' || :query || '%'
        )
        ORDER BY t.createdAt DESC
        """
    )
    fun searchTransactionsInternal(vaultId: String, query: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT COALESCE(
            SUM(CASE WHEN type = 'INFLOW' THEN amount ELSE -amount END),
            0
        ) FROM transactions WHERE vaultId = :vaultId
        """
    )
    suspend fun getBalanceForVault(vaultId: String): Long

    @Query(
        """
        SELECT COALESCE(
            SUM(CASE WHEN type = 'INFLOW' THEN amount ELSE -amount END),
            0
        ) FROM transactions WHERE vaultId = :vaultId
        """
    )
    fun observeBalanceForVault(vaultId: String): Flow<Long>
}
