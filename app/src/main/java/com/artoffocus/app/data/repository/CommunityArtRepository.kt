package com.artoffocus.app.data.repository

import com.artoffocus.app.data.local.CachedArtwork
import com.artoffocus.app.data.local.CommunityArtCache
import com.artoffocus.app.data.remote.CommunityArtApi
import com.artoffocus.app.data.remote.RemoteArtwork
import com.artoffocus.app.domain.focus.ArtSource

/**
 * Community paintings, as the rest of the app sees them: "give me one" and "top up the
 * cache". Nothing above this layer knows which museum the art comes from, or that a network
 * is involved at all.
 *
 * The split matters for I7. [next] only reads the local cache and never blocks on anything —
 * it is safe on the path to starting a sitting. [topUp] is the only thing that touches the
 * network, and is called at launch and after a painting completes, never during a session.
 */
class CommunityArtRepository(
    private val api: CommunityArtApi,
    private val cache: CommunityArtCache,
) {

    /**
     * A cached painting the user has not seen, or null when there is none — offline with an
     * empty cache, or every cached work already used. Null is what sends the caller to the
     * gradient fallback.
     *
     * Peeking only. The painting is not spent until [commit], because the hint on the idle
     * screen needs to describe the painting before the user has agreed to start, and a user
     * who changes the tempo instead of beginning should not have burned a painting.
     */
    suspend fun peek(): CommunityChoice? = cache.pickUnused()?.let {
        CommunityChoice(remoteId = it.remoteId, artwork = it.toChosen())
    }

    /**
     * Spends a painting, so it is never offered twice. Called when a sitting actually starts.
     * Marking used does not delete anything — a completed painting still points at the image
     * and I12 forbids destroying it.
     */
    suspend fun commit(choice: CommunityChoice) = cache.markUsed(choice.remoteId)

    suspend fun cachedCount(): Int = cache.count()

    /**
     * Downloads paintings until the cache holds at least [TARGET_CACHE_SIZE], and returns how
     * many were added.
     *
     * Silent on failure by design: this is background work and there is nothing the user
     * could usefully do about a failed fetch. The gradient fallback covers the case where it
     * never succeeds, so a permanently offline device still gets a working app.
     */
    suspend fun topUp(): Int {
        cache.pruneOrphans()
        val existing = cache.count()
        if (existing >= TARGET_CACHE_SIZE) return 0

        // Shuffled so two devices (and two top-ups) do not converge on the same handful of
        // famous paintings.
        val ids = api.searchPaintingIds().shuffled()
        var added = 0
        var examined = 0
        for (id in ids) {
            if (existing + added >= TARGET_CACHE_SIZE) break
            // A cap on attempts, not just successes: without it a run of unusable records
            // could walk the entire result set on one launch.
            if (examined++ >= MAX_ATTEMPTS) break

            val candidate = api.artwork(id) ?: continue
            val bytes = api.downloadImage(candidate.imageUrl) ?: continue
            val stored = cache.add(
                remoteId = candidate.remoteId,
                title = candidate.title,
                credit = candidate.creditLine(),
                imageBytes = bytes,
                sourceUrl = candidate.imageUrl,
            )
            if (stored != null) added++
        }
        return added
    }

    private fun CachedArtwork.toChosen() = ChosenArtwork(
        imageRef = imagePath,
        source = ArtSource.COMMUNITY,
        title = title,
        credit = credit,
        remoteUrl = sourceUrl,
    )

    private fun RemoteArtwork.creditLine(): String? = listOfNotNull(artist, year)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

    /**
     * A painting chosen but not yet spent. Carries the cache id alongside the artwork so
     * [commit] can mark exactly this one used without the UI having to know cache ids exist.
     */
    data class CommunityChoice(val remoteId: String, val artwork: ChosenArtwork)

    private companion object {
        /** Enough that a user rarely runs dry, small enough to be a modest download. */
        const val TARGET_CACHE_SIZE = 12

        /** Bounds one top-up's work when records turn out to be unusable. */
        const val MAX_ATTEMPTS = 40
    }
}
