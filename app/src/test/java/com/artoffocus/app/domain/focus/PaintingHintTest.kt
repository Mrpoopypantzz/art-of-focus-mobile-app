package com.artoffocus.app.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hint's job is to intrigue without spoiling. These pin both halves of that: it must say
 * something, and it must never say the title.
 */
class PaintingHintTest {

    private fun community(title: String, credit: String?) = Painting(
        id = "p1",
        title = title,
        seed = 1L,
        imageRef = "/community/1.jpg",
        source = ArtSource.COMMUNITY,
        credit = credit,
    )

    @Test
    fun `a community painting with a year is hinted by its decade`() {
        val hint = paintingHint(community("Paris Street; Rainy Day", "Gustave Caillebotte, 1877"))
        assertEquals("A painting from the 1870s is waiting.", hint)
    }

    @Test
    fun `the hint never contains the painting's title`() {
        // The reveal is the entire point of the app; naming the work in advance destroys it.
        val title = "A Sunday on La Grande Jatte"
        val hint = paintingHint(community(title, "Georges Seurat, 1884"))
        assertNotNull(hint)
        // No distinctive word of the title may survive into the hint. ("A" and "on" are
        // ordinary English and would match anything.)
        val distinctive = title.split(" ").filter { it.length > 3 }
        assertTrue(distinctive.none { word -> hint!!.contains(word, ignoreCase = true) })
    }

    @Test
    fun `the hint never contains the artist's name`() {
        val hint = paintingHint(community("Self-Portrait", "Walter Shirlaw, 1878"))
        assertTrue(hint!!.contains("1870s"))
        assertTrue(!hint.contains("Shirlaw"))
    }

    @Test
    fun `a community painting with no readable year still gets a hint`() {
        val hint = paintingHint(community("Untitled", "Unknown artist"))
        assertEquals("A painting from the collection is waiting.", hint)
    }

    @Test
    fun `a community painting with no credit at all still gets a hint`() {
        assertEquals("A painting from the collection is waiting.", paintingHint(community("Untitled", null)))
    }

    @Test
    fun `a generated gradient gets no hint`() {
        // There is nothing truthful to say about a gradient's era or origin, and a hint the
        // reveal then contradicts is worse than none.
        val gradient = Painting(id = "p0", title = "Dusk Over the Harbour", seed = 1L)
        assertNull(paintingHint(gradient))
    }

    @Test
    fun `the user's own photo gets no hint`() {
        // They already know what their own picture is.
        val photo = Painting(
            id = "p0",
            title = "Study in Ochre",
            seed = 1L,
            imageRef = "/photos/a.jpg",
            source = ArtSource.OWN_PHOTO,
        )
        assertNull(paintingHint(photo))
    }

    // --- era parsing ---

    @Test
    fun `a date range takes the first year`() {
        assertEquals("the 1880s", eraPhrase("Georges Seurat, 1884-86, border added 1888-89"))
    }

    @Test
    fun `a very old work is described as before 1500 rather than by decade`() {
        assertEquals("before 1500", eraPhrase("Unknown, 1480"))
    }

    @Test
    fun `a credit with no year reads as no era`() {
        assertNull(eraPhrase("Gustave Caillebotte"))
    }

    @Test
    fun `a stray small number is not mistaken for a year`() {
        assertNull(eraPhrase("Plate 12"))
    }

    @Test
    fun `a turn-of-century year lands in the right decade`() {
        assertEquals("the 1900s", eraPhrase("Pablo Picasso, 1903"))
        assertEquals("the 2010s", eraPhrase("Anon, 2015"))
    }
}
