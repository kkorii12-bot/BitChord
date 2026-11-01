package com.music.bitchord.playback

import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.Song
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A party shares which track is playing, but the upload *form* is part of that
 * identity: a music video must stay a music video on every receiving device,
 * and a video-radio row must not be swapped to a catalogue song.
 */
class PartyVideoFormTest {

    private fun roundTrip(song: Song): Song {
        val encoded = Json.encodeToString(PartyTrack.serializer(), song.toPartyTrack(180_000L))
        return Json.decodeFromString(PartyTrack.serializer(), encoded).toSong()
    }

    @Test
    fun `a video track stays a video on the receiving device`() {
        val received = roundTrip(
            Song(
                videoId = "mv-id",
                title = "Music video",
                artist = "Artist",
                thumbnailUrl = null,
                isVideo = true,
                videoRadio = true,
            ),
        )

        assertTrue(received.isVideo)
        assertTrue(received.isVideoOrigin)
        assertTrue(received.videoRadio)
    }

    @Test
    fun `an audio track does not become a video`() {
        val received = roundTrip(
            Song(
                videoId = "audio-id",
                title = "Song",
                artist = "Artist",
                thumbnailUrl = null,
            ),
        )

        assertFalse(received.isVideo)
        assertFalse(received.isVideoOrigin)
        assertFalse(received.videoRadio)
    }
}
