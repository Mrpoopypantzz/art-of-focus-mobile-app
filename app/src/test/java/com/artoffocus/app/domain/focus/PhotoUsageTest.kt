package com.artoffocus.app.domain.focus

import com.artoffocus.app.domain.museum.CompletedPainting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoUsageTest {

    private fun photo(imageRef: String?, seed: Long = 1L) =
        painting(imageRef, ArtSource.OWN_PHOTO, seed)

    private fun painting(
        imageRef: String?,
        source: ArtSource = ArtSource.OWN_PHOTO,
        seed: Long = 1L,
    ) = CompletedPainting(
        painting = Painting(id = "p0", title = "Test", seed = seed, imageRef = imageRef, source = source),
        totalMinutes = 120,
        sittingMinutes = 30,
        pieces = 4,
        startedAt = seed,
        completedAt = seed + 1,
    )

    private fun activePhoto(ref: String) =
        Painting(id = "p0", title = "Active", seed = 9L, imageRef = ref, source = ArtSource.OWN_PHOTO)

    @Test
    fun `a picture used by a completed painting is in use`() {
        val inUse = picturesInUse(listOf(photo("/photos/a.jpg")), active = null)
        assertFalse(canDeletePicture("/photos/a.jpg", inUse))
    }

    @Test
    fun `a picture used by the active painting is in use`() {
        val inUse = picturesInUse(completed = emptyList(), active = activePhoto("/photos/live.jpg"))
        assertFalse(canDeletePicture("/photos/live.jpg", inUse))
    }

    @Test
    fun `an unused picture is not in use and can be deleted`() {
        val inUse = picturesInUse(listOf(photo("/photos/a.jpg")), active = activePhoto("/photos/live.jpg"))
        assertTrue(canDeletePicture("/photos/spare.jpg", inUse))
    }

    @Test
    fun `locking one picture leaves the others deletable`() {
        val inUse = picturesInUse(
            completed = listOf(photo("/photos/a.jpg", seed = 1L), photo("/photos/b.jpg", seed = 2L)),
            active = null,
        )
        assertFalse(canDeletePicture("/photos/a.jpg", inUse))
        assertFalse(canDeletePicture("/photos/b.jpg", inUse))
        assertTrue(canDeletePicture("/photos/c.jpg", inUse))
    }

    @Test
    fun `gradient paintings contribute no locked pictures`() {
        // Every painting made before this feature has a null imageRef; none of them should
        // lock anything, or the library would freeze for no reason.
        val inUse = picturesInUse(
            completed = listOf(
                painting(null, ArtSource.GENERATED, seed = 1L),
                painting(null, ArtSource.GENERATED, seed = 2L),
            ),
            active = null,
        )
        assertTrue(inUse.isEmpty())
        assertTrue(canDeletePicture("/photos/anything.jpg", inUse))
    }

    @Test
    fun `the same file under different absolute paths still counts as in use`() {
        // /data/data/<pkg>/... and /data/user/0/<pkg>/... address the same private file.
        val inUse = picturesInUse(
            completed = listOf(photo("/data/data/com.artoffocus.app/files/paintings/a.jpg")),
            active = null,
        )
        assertFalse(canDeletePicture("/data/user/0/com.artoffocus.app/files/paintings/a.jpg", inUse))
    }

    @Test
    fun `the same picture used twice locks once`() {
        val inUse = picturesInUse(
            completed = listOf(photo("/photos/a.jpg", seed = 1L), photo("/photos/a.jpg", seed = 2L)),
            active = activePhoto("/photos/a.jpg"),
        )
        assertTrue(inUse.size == 1)
        assertFalse(canDeletePicture("/photos/a.jpg", inUse))
    }

    // --- Community art is not the user's library ---

    @Test
    fun `a completed community painting locks nothing in the picture library`() {
        // Community art also carries an imageRef, but it lives in the art cache, not the
        // user's pictures. Locking on it would make the library claim to hold files it does
        // not, and show "In your Museum" on pictures that have nothing to do with it.
        val inUse = picturesInUse(
            completed = listOf(painting("/community/1234.jpg", ArtSource.COMMUNITY)),
            active = null,
        )
        assertTrue(inUse.isEmpty())
    }

    @Test
    fun `an active community painting locks nothing either`() {
        val active = Painting(
            id = "p1",
            title = "Paris Street; Rainy Day",
            seed = 5L,
            imageRef = "/community/20684.jpg",
            source = ArtSource.COMMUNITY,
        )
        assertTrue(picturesInUse(completed = emptyList(), active = active).isEmpty())
    }

    @Test
    fun `a community painting does not unlock a photo that shares its file name`() {
        // Keys are compared by file name, so a community file coincidentally named like a
        // photo must not confuse the two. The photo stays locked; the community art adds
        // nothing.
        val inUse = picturesInUse(
            completed = listOf(
                photo("/photos/a.jpg", seed = 1L),
                painting("/community/a.jpg", ArtSource.COMMUNITY, seed = 2L),
            ),
            active = null,
        )
        assertTrue(inUse.size == 1)
        assertFalse(canDeletePicture("/photos/a.jpg", inUse))
    }

    @Test
    fun `a mixed museum locks only the user's own pictures`() {
        val inUse = picturesInUse(
            completed = listOf(
                photo("/photos/mine.jpg", seed = 1L),
                painting("/community/famous.jpg", ArtSource.COMMUNITY, seed = 2L),
                painting(null, ArtSource.GENERATED, seed = 3L),
            ),
            active = null,
        )
        assertFalse(canDeletePicture("/photos/mine.jpg", inUse))
        assertTrue(canDeletePicture("/community/famous.jpg", inUse))
        assertTrue(inUse.size == 1)
    }
}
