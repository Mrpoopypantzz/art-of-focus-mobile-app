package com.artoffocus.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * I12 — a completed painting is never destroyed. Mirrors the `paintings` table in
 * supabase/schema.sql, which deliberately has no UPDATE or DELETE RLS policy (both are
 * denied to everyone by omission). [PaintingDao] follows the same discipline: no update or
 * delete method exists.
 */
@Entity(tableName = "paintings")
data class PaintingEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val paintingKey: String, // "startedAt:seed" — idempotency key, matches unique(user_id, started_at, seed)
    val seed: Long,
    val title: String,
    val totalMinutes: Int,
    val sittingMinutes: Int,
    val pieces: Int,
    val startedAt: Long,
    val completedAt: Long,
    val syncedAt: Long? = null, // null = not yet mirrored to Supabase
    val photoUri: String? = null, // null = a generated gradient painting; set = the user's own photo
)
