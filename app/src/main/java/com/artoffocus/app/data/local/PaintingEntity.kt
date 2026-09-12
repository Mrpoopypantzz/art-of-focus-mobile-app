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
    val photoUri: String? = null, // the artwork image: a local file, or a cached community image
    /**
     * ArtSource.name. Stored rather than derived from photoUri, which stopped being a reliable
     * signal once community art could also carry an image. Defaulted so a row written by
     * older code still reads back sensibly.
     */
    val source: String = "GENERATED",
    val credit: String? = null, // artist and year, for community art only
)
