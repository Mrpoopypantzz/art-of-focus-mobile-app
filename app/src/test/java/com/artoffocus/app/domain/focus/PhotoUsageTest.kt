package com.artoffocus.app.domain.focus

import com.artoffocus.app.domain.museum.CompletedPainting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoUsageTest {

    private fun painting(imageRef: String?, seed: Long = 1L) = CompletedPainting(
        painting = Painting(id = "p0", title = "Test", seed = seed, imageRef = imageRef),
        totalMinutes = 120,
        sittingMinutes = 30,
        pieces = 4,
        startedAt = seed,
        completedAt = seed + 1,
    )

    @Test
    fun `a picture used by a completed painting is in use`() {
        val inUse = picturesInUse(listOf(painting("/photos/a.jpg")), activeImageRef = null)
        assertFalse(canDeletePicture("/photos/a.jpg", inUse))
    }

    @Test
    fun `a picture used by the active painting is in use`() {
        val inUse = picturesInUse(completed = emptyList(), activeImageRef = "/photos/live.jpg")
        assertFalse(canDeletePicture("/photos/live.jpg", inUse))
    }

    @Test
    fun `an unused picture is not in use and can be deleted`() {
        val inUse = picturesInUse(listOf(painting("/photos/a.jpg")), activeImageRef = "/photos/live.jpg")
        assertTrue(canDeletePicture("/photos/spare.jpg", inUse))
    }

    @Test
    fun `locking one picture leaves the others deletable`() {
        val inUse = picturesInUse(
            completed = listOf(painting("/photos/a.jpg", seed = 1L), painting("/photos/b.jpg", seed = 2L)),
            activeImageRef = null,
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
            completed = listOf(painting(null, seed = 1L), painting(null, seed = 2L)),
            activeImageRef = null,
        )
        assertTrue(inUse.isEmpty())
        assertTrue(canDeletePicture("/photos/anything.jpg", inUse))
    }

    @Test
    fun `the same file under different absolute paths still counts as in use`() {
        // /data/data/<pkg>/... and /data/user/0/<pkg>/... address the same private file.
        val inUse = picturesInUse(
            completed = listOf(painting("/data/data/com.artoffocus.app/files/paintings/a.jpg")),
            activeImageRef = null,
        )
        assertFalse(canDeletePicture("/data/user/0/com.artoffocus.app/files/paintings/a.jpg", inUse))
    }

    @Test
    fun `the same picture used twice locks once`() {
        val inUse = picturesInUse(
            completed = listOf(painting("/photos/a.jpg", seed = 1L), painting("/photos/a.jpg", seed = 2L)),
            activeImageRef = "/photos/a.jpg",
        )
        assertTrue(inUse.size == 1)
        assertFalse(canDeletePicture("/photos/a.jpg", inUse))
    }
}
