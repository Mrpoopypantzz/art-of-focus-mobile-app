package com.artoffocus.app.data.repository

import com.artoffocus.app.data.local.PaintingDao
import com.artoffocus.app.data.local.PaintingEntity
import com.artoffocus.app.domain.focus.ArtSource
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
                // Null for a gradient painting; an image path for a photo or community one.
                photoUri = record.painting.imageRef,
                source = record.painting.source.name,
                credit = record.painting.credit,
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

    /**
     * Tolerant on purpose. An unknown value (a row from a future version, or a corrupted one)
     * reads as GENERATED, which always renders — I12 means a museum row must survive being
     * misunderstood, not be dropped for it.
     */
    private fun PaintingEntity.parseSource(raw: String): ArtSource {
        val parsed = runCatching { ArtSource.valueOf(raw) }.getOrDefault(ArtSource.GENERATED)
        // A source that promises an image without one would render an empty frame. The
        // gradient is the honest thing to show instead.
        return if (parsed != ArtSource.GENERATED && photoUri == null) ArtSource.GENERATED else parsed
    }

    private fun PaintingEntity.toDomainOrNull(): CompletedPainting? = runCatching {
        CompletedPainting(
            // A gradient painting is still re-derived from the seed (deterministic hash)
            // rather than trusted from a possibly-stale stored title column.
            //
            // A painting with an image cannot be: its picture, and a real artist's title,
            // are not recoverable from a hash, so for those rows the stored columns ARE the
            // source of truth. The seed is still carried, because identity is (startedAt,
            // seed) regardless of what the painting looks like.
            //
            // An unrecognised source falls back to GENERATED rather than throwing — a row
            // written by a future version must never take the whole museum down with it.
            painting = when (val artSource = parseSource(source)) {
                ArtSource.GENERATED -> assignPainting(seed)
                else -> assignPainting(seed).copy(
                    title = title,
                    imageRef = photoUri,
                    source = artSource,
                    credit = credit,
                )
            },
            totalMinutes = totalMinutes,
            sittingMinutes = sittingMinutes,
            pieces = pieces,
            startedAt = startedAt,
            completedAt = completedAt,
        )
    }.getOrNull()
}
