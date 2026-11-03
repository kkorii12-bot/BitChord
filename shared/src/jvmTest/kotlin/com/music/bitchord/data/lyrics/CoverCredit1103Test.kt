package com.music.bitchord.data.lyrics

import com.music.bitchord.data.YouTubeDisplayCleaner
import com.music.bitchord.data.cover.CoverTitles
import com.music.bitchord.data.model.Song
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Phone 11-03: the artist line of a cover read someone other than its singer. */
class CoverCredit1103Test {

    private val originalSingers = CoverTitles.knownSingers

    @AfterTest
    fun restore() {
        CoverTitles.knownSingers = originalSingers
    }

    private fun song(title: String, channel: String) = Song(
        videoId = "test",
        title = title,
        artist = channel,
        thumbnailUrl = null,
        isVideo = true,
    )

    @Test
    fun `an upload suffix never becomes part of the singer`() {
        listOf("가사포함", "가사", "lyrics", "MV", "풀버전").forEach { suffix ->
            val channel = "오화요 $suffix"
            val read = assertNotNull(CoverTitles.read(song("instagram (cover)", channel)), suffix)
            assertEquals("instagram", read.title, suffix)
            assertEquals("오화요", read.singer, suffix)

            val shown = YouTubeDisplayCleaner.clean(song("instagram (cover)", channel))
            assertEquals("instagram", shown.title, suffix)
            assertEquals("오화요 커버", shown.artist, suffix)
        }
    }

    @Test
    fun `a known singer upload names the other title name as the original`() {
        CoverTitles.knownSingers = { setOf("오화요", "콩밥특별시 경찰 야유회") }
        val read = assertNotNull(
            CoverTitles.read(song("콩밥특별시 경찰 야유회 - Memories(Smiling Test)", "오화요")),
        )
        assertEquals("Memories(Smiling Test)", read.title)
        assertEquals("오화요", read.singer)
        assertEquals("콩밥특별시 경찰 야유회", read.originalArtist)

        val shown = YouTubeDisplayCleaner.clean(song("콩밥특별시 경찰 야유회 - Memories(Smiling Test)", "오화요"))
        assertEquals("Memories(Smiling Test)", shown.title)
        assertEquals("콩밥특별시 경찰 야유회 · 오화요 커버", shown.artist)
        assertEquals("콩밥특별시 경찰 야유회", shown.coverOriginalArtist)
    }

    @Test
    fun `a name before Cover on the singers channel is the original, not the artist`() {
        CoverTitles.knownSingers = { setOf("오화요") }
        listOf(
            "Memories(Smiling Test) / 윤미래 cover",
            "Memories(Smiling Test) / 콩밥특별시 경찰 야유회 cover",
        ).forEach { title ->
            val read = assertNotNull(CoverTitles.read(song(title, "오화요")), title)
            assertEquals("Memories(Smiling Test)", read.title, title)
            assertEquals("오화요", read.singer, title)
            assertEquals(title.substringAfter(" / ").removeSuffix(" cover"), read.originalArtist, title)

            val shown = YouTubeDisplayCleaner.clean(song(title, "오화요"))
            assertEquals("Memories(Smiling Test)", shown.title, title)
            assertEquals(
                title.substringAfter(" / ").removeSuffix(" cover") + " · 오화요 커버",
                shown.artist,
                title,
            )
        }
    }

    @Test
    fun `the reported cover title shapes keep their singer`() {
        CoverTitles.knownSingers = { setOf("오화요") }

        val by = assertNotNull(CoverTitles.read(song("Song / cover by X", "오화요")))
        assertEquals("Song", by.title)
        assertEquals("X", by.singer)

        val bare = assertNotNull(CoverTitles.read(song("Song (cover)", "오화요")))
        assertEquals("Song", bare.title)
        assertEquals("오화요", bare.singer)

        val originalFirst = assertNotNull(CoverTitles.read(song("Original Artist - Song / cover", "오화요")))
        assertEquals("Song", originalFirst.title)
        assertEquals("Original Artist", originalFirst.originalArtist)
        assertEquals("오화요", originalFirst.singer)
    }

    @Test
    fun `a stored upload suffix is dropped from the learned singer set`() {
        CoverTitles.knownSingers = { setOf("오화요 가사포함") }
        val read = assertNotNull(CoverTitles.read(song("Memories(Smiling Test) / 윤미래 cover", "오화요 가사포함")))
        assertEquals("Memories(Smiling Test)", read.title)
        assertEquals("오화요", read.singer)
        assertEquals("윤미래", read.originalArtist)
    }

    @Test
    fun `a version word on the song side is not the original artist`() {
        CoverTitles.knownSingers = { setOf("오화요") }
        val read = assertNotNull(CoverTitles.read(song("Song - Live / 윤미래 cover", "오화요")))
        assertEquals("Song", read.title)
        assertEquals("오화요", read.singer)
        assertEquals("윤미래", read.originalArtist)
        assertEquals(true, read.orderUnsure)

        val shown = YouTubeDisplayCleaner.clean(song("Song - Live / 윤미래 cover", "오화요"))
        assertEquals("Song", shown.title)
        assertEquals("윤미래 · 오화요 커버", shown.artist)
    }

    @Test
    fun `the uploader is the artist when the title names the same person twice`() {
        CoverTitles.knownSingers = { emptySet() }
        val shown = YouTubeDisplayCleaner.clean(song("Song - 오화요 (오화요 Cover)", "업로더"))
        assertEquals("Song", shown.title)
        assertEquals("오화요 · 업로더 커버", shown.artist)
        assertEquals("오화요", shown.coverOriginalArtist)
    }

    @Test
    fun `a bracketed original artist becomes the byline of the original title`() {
        CoverTitles.knownSingers = { setOf("오화요") }
        val read = assertNotNull(CoverTitles.read(song("Tomboy((G)I-DLE)", "오화요")))
        assertEquals("Tomboy", read.title)
        assertEquals("(G)I-DLE", read.originalArtist)
        assertEquals("오화요", read.singer)

        val shown = YouTubeDisplayCleaner.clean(song("Tomboy((G)I-DLE)", "오화요"))
        assertEquals("Tomboy", shown.title)
        assertEquals("(G)I-DLE · 오화요 커버", shown.artist)
        assertEquals("오화요", shown.coverSinger)
        assertEquals("(G)I-DLE", shown.coverOriginalArtist)

        val query = CoverTitles.lyricsQuery(song("Tomboy((G)I-DLE)", "오화요"))
        assertEquals("Tomboy", query.title)
        assertEquals("(G)I-DLE", query.artist)
    }

    @Test
    fun `a saved bracket cover with the old suffix still reads its original`() {
        CoverTitles.knownSingers = { setOf("오화요") }
        val shown = YouTubeDisplayCleaner.clean(song("Tomboy((G)I-DLE) (cover)", "오화요"))
        assertEquals("Tomboy", shown.title)
        assertEquals("(G)I-DLE · 오화요 커버", shown.artist)
    }
}

/** A cover that was matched to the original's catalogue release still shows its singer. */
class CatalogueMatchedCoverCreditTest {

    private val originalSingers = CoverTitles.knownSingers

    @AfterTest
    fun restore() {
        CoverTitles.knownSingers = originalSingers
    }

    private fun song(title: String, artist: String, albumId: String?) = Song(
        videoId = "test",
        title = title,
        artist = artist,
        thumbnailUrl = null,
        albumId = albumId,
        isVideo = true,
    )

    @Test
    fun `a stored catalogue-matched cover keeps the uploader as the artist`() {
        CoverTitles.knownSingers = { setOf("오화요") }
        val stored = song("Memories(Smiling Test) / cover by 오화요", "윤미래", "MPREb_original")

        val shown = com.music.bitchord.data.StoredSongDisplay.clean(stored)

        assertEquals("Memories(Smiling Test)", shown.title)
        assertEquals("오화요 커버", shown.artist)
    }

    @Test
    fun `a fresh catalogue-matched cover drops the upload suffix`() {
        CoverTitles.knownSingers = { setOf("오화요") }
        val upload = song("instagram (cover)", "오화요 가사포함", "MPREb_original")

        val shown = YouTubeDisplayCleaner.cleanUpload(upload)

        assertEquals("instagram", shown.title)
        assertEquals("오화요 커버", shown.artist)
    }
}
