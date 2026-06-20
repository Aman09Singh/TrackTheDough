package com.example.t1.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Query("UPDATE transactions SET category = :category, is_manual_cat = :isManual WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String, isManual: Boolean)

    @Query("SELECT COUNT(*) > 0 FROM transactions WHERE sms_id = :smsId")
    suspend fun existsBySmsId(smsId: String): Boolean
}
