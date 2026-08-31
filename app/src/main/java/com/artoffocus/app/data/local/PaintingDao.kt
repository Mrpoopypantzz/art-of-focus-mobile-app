package com.artoffocus.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * I12 — deliberately exposes no update or delete method. A completed painting can only ever
 * be inserted (idempotently, keyed by [PaintingEntity.paintingKey]) or read.
 */
@Dao
interface PaintingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(painting: PaintingEntity): Long

    @Query("SELECT * FROM paintings ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<PaintingEntity>>

    @Query("SELECT * FROM paintings ORDER BY completedAt DESC")
    suspend fun getAll(): List<PaintingEntity>

    @Query("SELECT * FROM paintings WHERE syncedAt IS NULL")
    suspend fun getUnsynced(): List<PaintingEntity>

    // Touches only local sync bookkeeping, never the earned painting content itself — I12
    // protects the record, not this metadata.
    @Query("UPDATE paintings SET syncedAt = :syncedAt WHERE localId = :localId")
    suspend fun markSynced(localId: Long, syncedAt: Long)
}
