package com.artoffocus.app.domain.focus

/**
 * The curiosity hook: a clue about the painting waiting behind the tiles.
 *
 * The point is to make starting feel like opening something, not to describe the work. So a
 * hint says roughly *when* and *what kind*, and never the title — naming it would spoil the
 * reveal the whole app is built around, and the title is displayed in full the moment the
 * painting is uncovered.
 *
 * Pure, so the wording can be tested without a painting, a network, or a screen.
 */

/**
 * A hint for [painting], or null when there is nothing honest to say — a generated gradient
 * has no era and no artist, and inventing one would be a lie the reveal then contradicts.
 *
 * [credit] is the museum's own "Artist, Year" line; everything here is derived from it and
 * from the painting's source, never from the title.
 */
fun paintingHint(painting: Painting): String? {
    if (painting.source != ArtSource.COMMUNITY) return null
    val era = painting.credit?.let { eraPhrase(it) }
    return when {
        era != null -> "A painting from $era is waiting."
        else -> "A painting from the collection is waiting."
    }
}

/**
 * Turns "Gustave Caillebotte, 1877" into "the 1870s".
 *
 * Deliberately vague: the decade is enough to intrigue and not enough to identify. Returns
 * null when the credit carries no year we can read — many records give a range, a circa, or
 * nothing at all, and a wrong guess is worse than saying less.
 */
internal fun eraPhrase(credit: String): String? {
    val year = FOUR_DIGIT_YEAR.find(credit)?.value?.toIntOrNull() ?: return null
    if (year !in PLAUSIBLE_YEARS) return null
    val decade = (year / 10) * 10
    return when {
        year < 1500 -> "before 1500"
        else -> "the ${decade}s"
    }
}

private val FOUR_DIGIT_YEAR = Regex("\\b(1[0-9]{3}|20[0-2][0-9])\\b")

/** Guards against a stray number in a credit line being read as a date. */
private val PLAUSIBLE_YEARS = 1000..2029
