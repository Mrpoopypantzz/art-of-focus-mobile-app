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

    /**
     * The ONE sanctioned delete, and it is not the app deleting anything: I12 forbids the
     * app destroying a finished painting on its own, silently, or as a side effect. This is
     * the user explicitly wiping their own device copy from Profile settings, behind a sheet
     * that names how many paintings go and whether a cloud copy exists -- exactly what the
     * web app offers. Nothing else may call it, and no code path may reach it without that
     * confirmation.
     */
    @Query("DELETE FROM paintings")
    suspend fun deleteAll()

    // Touches only local sync bookkeeping, never the earned painting content itself — I12
    // protects the record, not this metadata.
    @Query("UPDATE paintings SET syncedAt = :syncedAt WHERE localId = :localId")
    suspend fun markSynced(localId: Long, syncedAt: Long)
}
