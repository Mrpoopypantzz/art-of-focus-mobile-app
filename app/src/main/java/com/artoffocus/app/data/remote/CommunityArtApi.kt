package com.artoffocus.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Public-domain paintings from the Metropolitan Museum of Art.
 *
 * Chosen because it needs no API key or account, marks public-domain works explicitly
 * (`isPublicDomain`), and — unlike the Art Institute of Chicago, which was tried first — serves
 * its images without a bot challenge. The Art Institute's metadata API is fine, but its IIIF
 * image host sits behind Cloudflare and answers a plain image request with a "Just a moment..."
 * HTML page, so every download produced an HTML file with a .jpg name and a blank frame.
 *
 * Artist and year come back with each work and are credited in the UI, because crediting an
 * artist is right even when the licence does not demand it.
 *
 * This is the only file that knows this API exists. Everything above it speaks in terms of
 * [RemoteArtwork], so swapping or adding a museum later does not reach the rest of the app.
 */
class CommunityArtApi {

    private val client = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Candidate painting ids. The Met's search returns ids only; details come one at a time
     * from [artwork]. Metadata only — no images are downloaded here.
     */
    suspend fun searchPaintingIds(): List<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/search?q=painting&hasImages=true&medium=Paintings"
            json.decodeFromString<SearchResponse>(client.get(url).bodyAsText()).objectIDs.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** One work's details, or null if it is not usable (not public domain, no image, no title). */
    suspend fun artwork(objectId: Long): RemoteArtwork? = withContext(Dispatchers.IO) {
        runCatching {
            val body = client.get("$BASE/objects/$objectId").bodyAsText()
            json.decodeFromString<ObjectDto>(body).toRemoteArtwork()
        }.getOrNull()
    }

    /**
     * The image bytes, or null when the response is not actually an image.
     *
     * The content check is not paranoia: a CDN bot-challenge answers with HTTP 200 (or 403)
     * and an HTML body, which would otherwise be written to disk as a .jpg and render as an
     * empty frame. Checking the magic number catches that regardless of status code or
     * declared content type.
     */
    suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = client.get(url).readBytes()
            if (bytes.isJpeg() || bytes.isPng()) bytes else null
        }.getOrNull()
    }

    private fun ByteArray.isJpeg(): Boolean =
        size > 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()

    private fun ByteArray.isPng(): Boolean =
        size > 8 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte() &&
            this[2] == 'N'.code.toByte() && this[3] == 'G'.code.toByte()

    @Serializable
    private data class SearchResponse(val objectIDs: List<Long>? = null)

    @Serializable
    private data class ObjectDto(
        val objectID: Long = 0,
        val title: String? = null,
        val artistDisplayName: String? = null,
        val objectDate: String? = null,
        val isPublicDomain: Boolean = false,
        /** The web-sized image: a few hundred KB rather than many megabytes. */
        val primaryImageSmall: String? = null,
    ) {
        fun toRemoteArtwork(): RemoteArtwork? {
            // Only public-domain work. Anything else is not ours to show.
            if (!isPublicDomain) return null
            val image = primaryImageSmall?.takeIf { it.isNotBlank() } ?: return null
            val name = title?.takeIf { it.isNotBlank() } ?: return null
            return RemoteArtwork(
                remoteId = objectID.toString(),
                title = name,
                artist = artistDisplayName?.takeIf { it.isNotBlank() },
                year = objectDate?.takeIf { it.isNotBlank() },
                imageUrl = image,
            )
        }
    }

    private companion object {
        const val BASE = "https://collectionapi.metmuseum.org/public/collection/v1"
    }
}

/** One painting as the museum describes it, before the app has downloaded anything. */
data class RemoteArtwork(
    val remoteId: String,
    val title: String,
    val artist: String?,
    val year: String?,
    val imageUrl: String,
)
