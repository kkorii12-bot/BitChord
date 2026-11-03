package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phone grid asks only for a tile that has no cover yet, and one settled
 * cover must land on that exact tile rather than on every same-id row.
 */
class ExploreArtworkTest {

    private fun tile(
        title: String,
        browseId: String,
        params: String? = null,
        thumbnailUrl: String? = null,
    ) = MoodGenre(title, browseId, params, thumbnailUrl)

    @Test
    fun `a tile without a cover asks for one`() {
        assertTrue(tile("수면", "ytq:sleep").needsExploreArtwork())
    }

    @Test
    fun `a tile that already has a cover asks for nothing`() {
        assertFalse(
            tile("수면", "ytq:sleep", thumbnailUrl = "https://i.ytimg.com/vi/a/hq720.jpg")
                .needsExploreArtwork(),
        )
    }

    @Test
    fun `a blank cover still asks for one`() {
        assertTrue(tile("수면", "ytq:sleep", thumbnailUrl = "").needsExploreArtwork())
    }

    @Test
    fun `charts keep the colour tile instead of spending a search`() {
        assertFalse(tile("차트", Innertube.CHARTS_BROWSE_ID).needsExploreArtwork())
    }

    @Test
    fun `a settled cover lands on the exact tile only`() {
        val target = tile("수면", "ytq:sleep", params = "p1")
        val sameBrowseOtherParams = tile("수면", "ytq:sleep", params = "p2")
        val other = tile("운동", "ytq:workout")
        val sections = listOf(
            MoodGenreSection("분위기", listOf(target, sameBrowseOtherParams)),
            MoodGenreSection("장르", listOf(other)),
        )
        val url = "https://i.ytimg.com/vi/a/hq720.jpg"

        val updated = sections.withExploreArtwork("ytq:sleep", "p1", url)

        assertEquals(url, updated[0].items[0].thumbnailUrl)
        assertNull(updated[0].items[1].thumbnailUrl)
        assertNull(updated[1].items[0].thumbnailUrl)
        assertEquals(listOf("분위기", "장르"), updated.map { it.title })
        assertNull(sections[0].items[0].thumbnailUrl)
    }

    @Test
    fun `an unknown tile leaves every section unchanged`() {
        val sections = listOf(MoodGenreSection("분위기", listOf(tile("수면", "ytq:sleep"))))
        assertEquals(
            sections,
            sections.withExploreArtwork("ytq:missing", null, "https://i.ytimg.com/vi/a/hq720.jpg"),
        )
    }
}
