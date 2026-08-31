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
            ),
        )
    }

    fun observeAll(): Flow<List<CompletedPainting>> = dao.observeAll().map { entities ->
        entities.mapNotNull { it.toDomainOrNull() }
    }

    suspend fun getAll(): List<CompletedPainting> = dao.getAll().mapNotNull { it.toDomainOrNull() }

    private fun PaintingEntity.toDomainOrNull(): CompletedPainting? = runCatching {
        CompletedPainting(
            // Painting identity is always re-derived from the seed (deterministic hash),
            // never trusted from a possibly-stale stored title column.
            painting = assignPainting(seed),
            totalMinutes = totalMinutes,
            sittingMinutes = sittingMinutes,
            pieces = pieces,
            startedAt = startedAt,
            completedAt = completedAt,
        )
    }.getOrNull()
}
