package com.music.bitchord.data.sources

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The audio switch has to recognise the official song when the catalogue
 * spells the act without its leading "The", or credits the release to the
 * performer named in the video title's featuring clause. A cover, a karaoke
 * take or a genuinely different artist stays refused.
 */
class AudioSwitchArtistCreditTest {

    private fun song(
        id: String,
        title: String,
        artist: String,
        duration: String? = null,
        isVideo: Boolean = false,
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        durationText = duration,
        isVideo = isVideo,
    )

    @Test
    fun `The 1975 and 1975 are the same act`() {
        val row = song("mv1975", "Chocolate", "The 1975", "3:30", isVideo = true)
        val target = TrackMatcher.audioSwitchTargetOf(row)
        val official = song("official", "Chocolate", "1975", "3:30")
        assertEquals(official, TrackMatcher.bestSameArtist(listOf(official), target))
        assertNull(TrackMatcher.songVersionRejectReason(official, target))
    }

    @Test
    fun `a different artist covering or re-recording Chocolate stays refused`() {
        val row = song("mv1975", "Chocolate", "The 1975", "3:30", isVideo = true)
        val target = TrackMatcher.audioSwitchTargetOf(row)
        assertNull(
            TrackMatcher.bestSameArtist(
                listOf(song("cover", "Chocolate (Cover)", "Some Band", "3:30")),
                target,
            ),
        )
        assertNull(
            TrackMatcher.bestSameArtist(
                listOf(song("karaoke", "Chocolate (Karaoke Version)", "The 1975", "3:30")),
                target,
            ),
        )
        assertNull(
            TrackMatcher.bestSameArtist(
                listOf(song("remix", "Chocolate (Remix)", "The 1975", "3:30")),
                target,
            ),
        )
        assertNull(
            TrackMatcher.bestSameArtist(
                listOf(song("other", "Chocolate", "Another Artist", "3:30")),
                target,
            ),
        )
    }

    @Test
    fun `Get Lucky credited to its featured performer is the same song`() {
        val row = song(
            "mvgetlucky",
            "Get Lucky (feat. Pharrell Williams, Nile Rodgers)",
            "Daft Punk",
            "4:08",
            isVideo = true,
        )
        val target = TrackMatcher.audioSwitchTargetOf(row)
        assertTrue(target.artist.lowercase().contains("pharrell williams"))
        val creditedToFeatured = song(
            "official",
            "Get Lucky (feat. Pharrell Williams, Nile Rodgers)",
            "Pharrell Williams",
            "4:08",
        )
        assertEquals(
            creditedToFeatured,
            TrackMatcher.bestSameArtist(listOf(creditedToFeatured), target),
        )
        assertNull(TrackMatcher.songVersionRejectReason(creditedToFeatured, target))
        val creditedToDaftPunk = song("daft", "Get Lucky", "Daft Punk", "4:08")
        assertEquals(creditedToDaftPunk, TrackMatcher.bestSameArtist(listOf(creditedToDaftPunk), target))
    }

    @Test
    fun `Get Lucky by another act or as karaoke stays refused`() {
        val row = song(
            "mvgetlucky",
            "Get Lucky (feat. Pharrell Williams, Nile Rodgers)",
            "Daft Punk",
            "4:08",
            isVideo = true,
        )
        val target = TrackMatcher.audioSwitchTargetOf(row)
        assertNull(
            TrackMatcher.bestSameArtist(
                listOf(song("cover", "Get Lucky", "The Cover Crew", "4:08")),
                target,
            ),
        )
        assertNull(
            TrackMatcher.bestSameArtist(
                listOf(song("karaoke", "Get Lucky (Karaoke Version)", "Daft Punk", "4:08")),
                target,
            ),
        )
        assertNull(
            TrackMatcher.bestSameArtist(
                listOf(song("other", "Get Lucky", "Another Artist", "4:08")),
                target,
            ),
        )
    }
}
