package com.artoffocus.app.domain.focus

import com.artoffocus.app.domain.museum.CompletedPainting

/**
 * Which pictures a painting depends on, and therefore may not be deleted.
 *
 * I12 — a completed painting is never destroyed. When the art is the user's own photo rather
 * than a gradient derivable from a seed, deleting the file guts the painting: the Museum row
 * survives but renders nothing. So a picture referenced by a finished painting is locked for
 * as long as that painting exists.
 *
 * I7 — no progress is ever lost. The painting currently in progress is covered too: losing
 * its picture mid-session would destroy work the user is partway through.
 *
 * Pure so the rule can be tested directly, and so the policy lives here rather than in a
 * screen. Locking concerns deletion only — a locked picture stays selectable, because a
 * picture is never consumed by being used.
 *
 * Only the user's OWN_PHOTO paintings count. Community art also carries an image, but it is
 * not in the user's library and must never appear there as something "in your Museum" —
 * that is the whole reason a painting records its source rather than inferring it from
 * whether an image is present.
 */
fun picturesInUse(
    completed: List<CompletedPainting>,
    active: Painting?,
): Set<String> = buildSet {
    (completed.map { it.painting } + listOfNotNull(active))
        .filter { it.source == ArtSource.OWN_PHOTO }
        .forEach { painting -> painting.imageRef?.let { add(pictureKey(it)) } }
}

/** True when the picture may be deleted — that is, when no painting depends on it. */
fun canDeletePicture(ref: String, inUse: Set<String>): Boolean = pictureKey(ref) !in inUse

/**
 * Compares pictures by file name rather than full path.
 *
 * The same private file is reachable by more than one absolute path — `/data/data/<pkg>/…`
 * and `/data/user/0/<pkg>/…` are the same bytes — so comparing whole paths can miss a match
 * and silently unlock a picture a painting depends on. Names are generated unique within the
 * one directory these files live in, so the name is the honest identity here.
 */
private fun pictureKey(ref: String): String = ref.substringAfterLast('/')
