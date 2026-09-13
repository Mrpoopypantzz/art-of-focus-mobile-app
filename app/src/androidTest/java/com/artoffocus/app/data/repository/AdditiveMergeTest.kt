package com.artoffocus.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artoffocus.app.data.local.AppDatabase
import com.artoffocus.app.domain.focus.ArtSource
import com.artoffocus.app.domain.focus.Painting
import com.artoffocus.app.domain.focus.assignPainting
import com.artoffocus.app.domain.museum.CompletedPainting
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The I12 conflict rule behind sync pull: **pulling only ever adds.**
 *
 * Pulling from the server was deliberately not built for a long time, because two devices with
 * divergent museums need a merge rule and guessing at one risks I12 ("a completed painting is
 * never destroyed"). These tests pin the rule that made it safe to build, at the layer the pull
 * actually goes through — [MuseumRepository.recordIfAbsent].
 *
 * In-memory Room, so nothing touches a real museum.
 */
@RunWith(AndroidJUnit4::class)
class AdditiveMergeTest {

    private lateinit var db: AppDatabase
    private lateinit var museum: MuseumRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        museum = MuseumRepository(db.paintingDao())
    }

    @After
    fun tearDown() = db.close()

    private fun record(
        seed: Long,
        startedAt: Long,
        title: String = "Test",
        source: ArtSource = ArtSource.GENERATED,
        imageRef: String? = null,
        credit: String? = null,
        remoteUrl: String? = null,
    ) = CompletedPainting(
        painting = assignPainting(seed).copy(
            title = title,
            imageRef = imageRef,
            source = source,
            credit = credit,
            remoteUrl = remoteUrl,
        ),
        totalMinutes = 120,
        sittingMinutes = 30,
        pieces = 4,
        startedAt = startedAt,
        completedAt = startedAt + 7_200_000L,
    )

    @Test
    fun a_painting_this_device_does_not_have_is_added() = runBlocking {
        assertTrue(museum.recordIfAbsent(record(seed = 1L, startedAt = 1000L)))
        assertEquals(1, museum.getAll().size)
    }

    @Test
    fun a_painting_already_held_is_not_added_twice() = runBlocking {
        museum.recordCompletion(record(seed = 1L, startedAt = 1000L))
        // Same identity (startedAt, seed) — a repeated pull must be a no-op, not a duplicate.
        assertFalse(museum.recordIfAbsent(record(seed = 1L, startedAt = 1000L)))
        assertEquals(1, museum.getAll().size)
    }

    @Test
    fun pulling_does_not_overwrite_a_painting_already_held() = runBlocking {
        // The dangerous case: the server's copy of the same painting differs. I12 says the local
        // one is not to be touched, and OnConflictStrategy.IGNORE means it cannot be.
        museum.recordCompletion(
            record(seed = 1L, startedAt = 1000L, title = "The Local Title", source = ArtSource.GENERATED),
        )
        museum.recordIfAbsent(
            record(
                seed = 1L,
                startedAt = 1000L,
                title = "A Different Remote Title",
                source = ArtSource.COMMUNITY,
                remoteUrl = "https://example.invalid/x.jpg",
            ),
        )

        val all = museum.getAll()
        assertEquals(1, all.size)
        // A gradient re-derives its title from the seed, so assert on what proves no overwrite:
        // the source and the absence of a remote url are still the local row's.
        assertEquals(ArtSource.GENERATED, all.first().painting.source)
        assertEquals(null, all.first().painting.remoteUrl)
    }

    @Test
    fun a_local_only_painting_survives_a_pull_that_does_not_mention_it() = runBlocking {
        museum.recordCompletion(record(seed = 1L, startedAt = 1000L, title = "Local Only"))
        // The server has an entirely different painting. Nothing about the pull may remove the
        // one this device holds alone — this is the case that justified deferring pull at all.
        museum.recordIfAbsent(record(seed = 2L, startedAt = 5000L, title = "From The Server"))

        val seeds = museum.getAll().map { it.painting.seed }.toSet()
        assertEquals(setOf(1L, 2L), seeds)
    }

    @Test
    fun a_pulled_community_painting_keeps_its_credit_and_renders_from_the_url() = runBlocking {
        // No local file exists for a painting that arrived by sync, so the url has to serve as
        // the image — otherwise the frame is blank and the artist uncredited.
        museum.recordIfAbsent(
            record(
                seed = 7L,
                startedAt = 2000L,
                title = "Wheat Field with Cypresses",
                source = ArtSource.COMMUNITY,
                imageRef = null,
                credit = "Vincent van Gogh, 1889",
                remoteUrl = "https://images.metmuseum.org/x.jpg",
            ),
        )

        val painting = museum.getAll().single().painting
        assertEquals(ArtSource.COMMUNITY, painting.source)
        assertEquals("Wheat Field with Cypresses", painting.title)
        assertEquals("Vincent van Gogh, 1889", painting.credit)
        assertEquals("https://images.metmuseum.org/x.jpg", painting.imageRef)
    }

    @Test
    fun a_pulled_photo_painting_keeps_its_identity_without_a_picture() = runBlocking {
        // Private photos are never uploaded, so on a second device this painting has no image.
        // It must still read as the user's own photo rather than silently becoming a gradient,
        // so the Museum can explain that the picture is on the other phone.
        museum.recordIfAbsent(
            record(seed = 9L, startedAt = 3000L, title = "My Own Picture", source = ArtSource.OWN_PHOTO),
        )

        val painting = museum.getAll().single().painting
        assertEquals(ArtSource.OWN_PHOTO, painting.source)
        assertEquals(null, painting.imageRef)
    }

    @Test
    fun a_remote_url_is_not_stored_as_if_it_were_a_local_file() = runBlocking {
        // photoUri is for files on this device. Writing a url into it would make the photo
        // library believe it owns a file it has never seen.
        museum.recordIfAbsent(
            record(
                seed = 11L,
                startedAt = 4000L,
                title = "Remote",
                source = ArtSource.COMMUNITY,
                imageRef = "https://example.invalid/y.jpg",
                remoteUrl = "https://example.invalid/y.jpg",
            ),
        )

        val row = db.paintingDao().getAll().single()
        assertEquals(null, row.photoUri)
        assertEquals("https://example.invalid/y.jpg", row.remoteUrl)
    }
}
