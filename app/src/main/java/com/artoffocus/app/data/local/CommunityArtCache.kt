package com.artoffocus.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Community paintings that are already on this device, image and all.
 *
 * I7 — the timer path never touches the network. Starting a sitting reads only from here, so
 * a sitting begins instantly whether or not the phone has signal, and a painting in progress
 * can never be interrupted by a failed request. Filling the cache is background work that
 * happens well away from a running session.
 *
 * The image is copied to app-private storage rather than referenced by URL for the same
 * reason photos are (see [PhotoStore]): I12 says a completed painting is never destroyed, and
 * a painting whose artwork is a remote URL is one outage away from an empty frame.
 *
 * The index is a single JSON file rather than a Room table. It is a disposable cache — losing
 * it costs a re-download and nothing else — so it deliberately does not sit in the database
 * that holds the Museum, where a migration would be required and I12 would apply.
 */
class CommunityArtCache(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val dir: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private val indexFile: File
        get() = File(dir, INDEX_NAME)

    /**
     * Every cached painting whose image file is still present and is really an image.
     *
     * The content check earns its keep: an earlier source answered image requests with an
     * HTML bot-challenge page, which was written to disk as a .jpg and would have rendered
     * as an empty frame. Entries like that are ignored here and cleaned up by [pruneOrphans],
     * so a cache poisoned by a bad source heals itself instead of showing blank paintings.
     */
    suspend fun all(): List<CachedArtwork> = withContext(Dispatchers.IO) {
        readIndex().filter { File(it.imagePath).isRealImage() }
    }

    /** Magic-number check — cheap, and independent of file name or declared content type. */
    private fun File.isRealImage(): Boolean = runCatching {
        if (!exists() || length() < MIN_IMAGE_BYTES) return false
        inputStream().use { stream ->
            val head = ByteArray(4)
            if (stream.read(head) != 4) return false
            val jpeg = head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte()
            val png = head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() &&
                head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte()
            jpeg || png
        }
    }.getOrDefault(false)

    suspend fun count(): Int = all().size

    /**
     * Adds a painting, skipping one already cached by [CachedArtwork.remoteId]. The image is
     * written before the index entry, so a crash between the two leaves an orphaned file
     * (harmless, cleaned by [pruneOrphans]) rather than an index pointing at nothing.
     */
    suspend fun add(
        remoteId: String,
        title: String,
        credit: String?,
        imageBytes: ByteArray,
        sourceUrl: String? = null,
    ): CachedArtwork? =
        withContext(Dispatchers.IO) {
            runCatching {
                val index = readIndex()
                index.firstOrNull { it.remoteId == remoteId }?.let { return@runCatching it }

                val file = File(dir, "$remoteId.jpg")
                file.outputStream().use { it.write(imageBytes) }
                // Last line of defence: never index something that is not an image, even if
                // a caller skipped its own check.
                if (!file.isRealImage()) {
                    file.delete()
                    return@runCatching null
                }

                val entry = CachedArtwork(remoteId, title, credit, file.absolutePath, sourceUrl = sourceUrl)
                writeIndex(index + entry)
                entry
            }.getOrNull()
        }

    /**
     * Marks a painting as used, so the next sitting gets something the user has not seen.
     *
     * Used entries stay cached rather than being deleted: a completed painting may still
     * point at that image, and I12 forbids destroying it. Only [pickUnused] cares about the
     * flag.
     */
    suspend fun markUsed(remoteId: String) = withContext(Dispatchers.IO) {
        writeIndex(readIndex().map { if (it.remoteId == remoteId) it.copy(used = true) else it })
    }

    /**
     * A painting the user has not had yet, at random, or null when the cache holds nothing
     * unused — which is the one case the gradient fallback exists for.
     */
    suspend fun pickUnused(): CachedArtwork? = all().filter { !it.used }.randomOrNull()

    /**
     * Cleans up two kinds of rubbish: images with no index entry (the crash window in [add]),
     * and entries whose file is missing or is not really an image (a bad source). Both are
     * safe to delete — an unused cache entry is not a painting, so I12 does not apply.
     */
    suspend fun pruneOrphans() = withContext(Dispatchers.IO) {
        runCatching {
            val index = readIndex()
            val (valid, invalid) = index.partition { File(it.imagePath).isRealImage() }
            if (invalid.isNotEmpty()) {
                invalid.forEach { runCatching { File(it.imagePath).delete() } }
                writeIndex(valid)
            }
            val known = valid.map { File(it.imagePath).name }.toSet()
            dir.listFiles()
                ?.filter { it.name != INDEX_NAME && it.name !in known }
                ?.forEach { it.delete() }
        }
        Unit
    }

    /** A malformed index reads as empty: the cache rebuilds itself, which costs only bandwidth. */
    private fun readIndex(): List<CachedArtwork> = runCatching {
        if (!indexFile.exists()) return emptyList()
        json.decodeFromString<List<CachedArtwork>>(indexFile.readText())
    }.getOrDefault(emptyList())

    private fun writeIndex(entries: List<CachedArtwork>) {
        runCatching { indexFile.writeText(json.encodeToString(entries)) }
    }

    private companion object {
        const val DIR_NAME = "community"
        const val INDEX_NAME = "index.json"

        /** Below this, a "painting" is an error page, not art. */
        const val MIN_IMAGE_BYTES = 1024
    }
}

@Serializable
data class CachedArtwork(
    val remoteId: String,
    val title: String,
    /** "Gustave Caillebotte, 1877" — null when the museum recorded neither. */
    val credit: String?,
    val imagePath: String,
    val used: Boolean = false,
    /**
     * The public URL the image came from, so a painting made of it can sync and render on
     * another device — [imagePath] is meaningless anywhere but this phone.
     *
     * Defaulted because this is a disposable cache: an entry written before this field existed
     * reads as null and simply syncs no image, which is lossless rather than wrong.
     */
    val sourceUrl: String? = null,
)
