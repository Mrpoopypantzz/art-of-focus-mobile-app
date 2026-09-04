package com.artoffocus.app.domain.focus

/**
 * F8.2 — placeholder art: deterministic seeded gradients. Swapping in real art only touches
 * [PALETTES].
 *
 * THIS TABLE IS CROSS-PLATFORM DATA, NOT A LOCAL STYLE CHOICE. `paintings.title` is a stored
 * column and `seed` is part of a painting's identity key, so the web app and this one must
 * index the same table or the same painting renders as two different pictures. These entries
 * are the web app's `PALETTES` (src/modules/focus/domain/artwork.ts) verbatim — change them
 * in both places or not at all.
 */

data class Palette(val title: String, val stops: List<String>)

val PALETTES: List<Palette> = listOf(
    Palette("Dusk Over the Harbour", listOf("#c9a05a", "#8a5a2f", "#4a1f1a")),
    Palette("The Orchard Wall", listOf("#a8b47a", "#6f7f4a", "#39432a")),
    Palette("Study in Ochre", listOf("#e0b878", "#b8813f", "#6b4520")),
    Palette("Cathedral Light", listOf("#e6d3a3", "#c2a06a", "#7d5c33")),
    Palette("The Slate Coast", listOf("#9fb0b8", "#5f7481", "#33454f")),
    Palette("Vermilion Interior", listOf("#d98a6a", "#a4503a", "#5c2320")),
    Palette("Field at Nightfall", listOf("#8fa3b8", "#4f6377", "#2a3543")),
    Palette("The Gilded Room", listOf("#f0d9a8", "#cba36b", "#8a6a35")),
)

const val PAINTING_COUNT: Int = 8

data class Painting(val id: String, val title: String, val seed: Long)

/** Deterministic 32-bit hash (two-round Murmur-like finalizer) — same seed always yields the same painting. */
fun hash(seed: Long): Long {
    var h = seed.toInt()
    h = (h xor (h ushr 16)) * 0x45d9f3b.toInt()
    h = (h xor (h ushr 16)) * 0x45d9f3b.toInt()
    h = h xor (h ushr 16)
    return h.toLong() and 0xFFFFFFFFL
}

/** I6's one-active-painting enforcement lives in storage, not here — this just deterministically assigns. */
fun assignPainting(seed: Long): Painting {
    val index = (hash(seed) % PAINTING_COUNT).toInt()
    val palette = PALETTES[index]
    return Painting(id = "p$index", title = palette.title, seed = seed)
}

fun paintingGradient(painting: Painting): String {
    val palette = PALETTES[(hash(painting.seed) % PAINTING_COUNT).toInt()]
    val angle = hash(painting.seed) % 360
    return "linear-gradient(${angle}deg, ${palette.stops.joinToString(", ")})"
}

fun paintingOverlay(painting: Painting): Long = hash(painting.seed + 1)
