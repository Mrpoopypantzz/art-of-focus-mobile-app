package com.artoffocus.app.domain.focus

/** F8.2 — placeholder art: deterministic seeded gradients. Swapping in real art only touches [PALETTES]. */

data class Palette(val title: String, val stops: List<String>)

val PALETTES: List<Palette> = listOf(
    Palette("Dusk Over the Harbour", listOf("#c9a05a", "#8a5a2f", "#4a1f1a")),
    Palette("Morning Fog", listOf("#a9b8bd", "#6f7f86", "#333e42")),
    Palette("The Orchard in Autumn", listOf("#d98e4a", "#a3502a", "#5c2a17")),
    Palette("Quiet Water", listOf("#5f8fa3", "#2f5b6e", "#152a33")),
    Palette("Wheatfield at Noon", listOf("#e3c46a", "#b98a2f", "#6b4a17")),
    Palette("Night over the City", listOf("#3a3f6e", "#20233f", "#0d0e1c")),
    Palette("The Rose Garden", listOf("#d97a8c", "#a34a5c", "#5c2530")),
    Palette("Mountain Pass", listOf("#8a9a8f", "#4f6157", "#242e29")),
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
