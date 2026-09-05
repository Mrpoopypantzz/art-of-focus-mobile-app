package com.artoffocus.app.data.repository

import com.artoffocus.app.data.local.PaintingDao
import com.artoffocus.app.data.local.PaintingEntity
import com.artoffocus.app.domain.focus.assignPainting
import com.artoffocus.app.domain.museum.CompletedPainting
import com.artoffocus.app.domain.museum.paintingKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Mirrors museumStorage.ts. I12 — a completed painting is written here before the active
 * slot is cleared (by the caller); if this write fails the painting must stay active. One
 * malformed record must not cost the whole collection, so reads map defensively and drop
 * anything that fails to convert, individually, rather than failing the whole list.
 */
class MuseumRepository(private val dao: PaintingDao) {

    /** Idempotent by [paintingKey] — a duplicate insert (e.g. a retried sync) is a no-op. */
    suspend fun recordCompletion(record: CompletedPainting) {
        dao.insert(
            PaintingEntity(
                paintingKey = paintingKey(record),
                seed = record.painting.seed,
                title = record.painting.title,
                totalMinutes = record.totalMinutes,
                sittingMinutes = record.sittingMinutes,
                pieces = record.pieces,
                startedAt = record.startedAt,
                completedAt = record.completedAt,
                // Null for a gradient painting; the user's picture for a photo-backed one.
                photoUri = record.painting.imageRef,
            ),
        )
    }

    fun observeAll(): Flow<List<CompletedPainting>> = dao.observeAll().map { entities ->
        entities.mapNotNull { it.toDomainOrNull() }
    }

    suspend fun getAll(): List<CompletedPainting> = dao.getAll().mapNotNull { it.toDomainOrNull() }

    /** Explicit user-initiated wipe only — see PaintingDao.deleteAll. */
    suspend fun clearAll() = dao.deleteAll()

    /** localId alongside the record — the sync layer needs it to mark rows pushed. */
    suspend fun allWithLocalIds(): List<Pair<Long, CompletedPainting>> =
        dao.getAll().mapNotNull { entity -> entity.toDomainOrNull()?.let { entity.localId to it } }

    /** Sync bookkeeping only — never touches the earned painting content (I12). */
    suspend fun markSynced(localId: Long, syncedAt: Long) = dao.markSynced(localId, syncedAt)

    private fun PaintingEntity.toDomainOrNull(): CompletedPainting? = runCatching {
        CompletedPainting(
            // A gradient painting is still re-derived from the seed (deterministic hash)
            // rather than trusted from a possibly-stale stored title column.
            //
            // A photo-backed painting cannot be: its picture and the name the user gave it
            // are not recoverable from a hash, so for those rows the stored columns ARE the
            // source of truth. The seed is still carried, because identity is (startedAt,
            // seed) regardless of what the painting looks like.
            painting = if (photoUri != null) {
                assignPainting(seed).copy(title = title, imageRef = photoUri)
            } else {
                assignPainting(seed)
            },
            totalMinutes = totalMinutes,
            sittingMinutes = sittingMinutes,
            pieces = pieces,
            startedAt = startedAt,
            completedAt = completedAt,
        )
    }.getOrNull()
}
