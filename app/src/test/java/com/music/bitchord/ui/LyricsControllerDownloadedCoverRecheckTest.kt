package com.music.bitchord.ui

import android.app.Application
import android.content.Context
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.lyrics.Deepgram
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsTimeMap
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PowerState
import com.music.bitchord.playback.smart.LyricsTiming
import com.music.bitchord.testutil.FakeContext
import com.music.bitchord.testutil.FakePreferences
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Phone 11-03: a downloaded cover video with `file:<provider>@unknown` lyrics
 * was never re-checked, because the row's video flag sent it down the video
 * path and the unknown provider never named a recording. The one song check
 * has to run for it too, and its answer has to be stored under the video key
 * the lyrics screen reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsControllerDownloadedCoverRecheckTest {

    private val videoId = "vRjfYdwM5L4"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private lateinit var controller: LyricsController
    private lateinit var directory: File
    private lateinit var context: Context

    private val storeField = LyricsTiming::class.java.getDeclaredField("store").apply {
        isAccessible = true
    }

    private val savedSources = AppSettings.lyricsSources.value
    private val savedOrder = AppSettings.lyricsSourceOrder.value
    private val savedSynced = AppSettings.syncedLyrics.value
    private val savedRecheck = AppSettings.syncSafetyDownloadRecheck.value

    private val lines = listOf(
        LyricLine(1_000L, "one"),
        LyricLine(2_000L, "two"),
        LyricLine(3_000L, "three"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        AppSettings.syncedLyrics.value = true
        // A hidden source keeps the fresh-sheet lookup skipped: the embedded
        // sheet is all this test needs, and no provider is contacted.
        AppSettings.lyricsSources.value = setOf(LyricsSource.SIMP_MUSIC)
        AppSettings.lyricsSourceOrder.value = listOf(LyricsSource.SIMP_MUSIC)
        AppSettings.syncSafetyDownloadRecheck.value = true
        Deepgram.apiKey = "test-key"
        // The unit-test android.jar cannot parse a Uri, so the audio-readiness
        // probe must take its screen-off path instead of reading the cache.
        PowerState.update(false)
        directory = Files.createTempDirectory("bitchord-downloaded-cover-recheck").toFile()
        context = FakeContext.create(FakePreferences(), directory)
        storeField.set(LyricsTiming, null)
        controller = LyricsController(DownloadedCoverApplication(context), scope)
    }

    @After
    fun tearDown() {
        runBlocking { job.cancelAndJoin() }
        Dispatchers.resetMain()
        AppSettings.lyricsSources.value = savedSources
        AppSettings.lyricsSourceOrder.value = savedOrder
        AppSettings.syncedLyrics.value = savedSynced
        AppSettings.syncSafetyDownloadRecheck.value = savedRecheck
        Deepgram.apiKey = ""
        PowerState.update(true)
        LyricsTiming.downloadRecheckAnswerForTest = null
        storeField.set(LyricsTiming, null)
        directory.deleteRecursively()
    }

    private fun writeUnknownProviderFile(): String {
        // An offline package's sidecar; the header names no recording id, so
        // this is the `file:<provider>@unknown` row from the phone log.
        val playlist = File(directory, "cover.m3u8").apply { writeText("") }
        File(directory, "lyrics.lrc").writeText(
            buildString {
                append("[re:BitChord LRCLIB]\n")
                lines.forEach { line ->
                    val seconds = line.timeMs / 1000
                    append("[00:0$seconds.00]${line.text}\n")
                }
            },
        )
        return playlist.toURI().toString()
    }

    @Test
    fun `an unknown-provider downloaded cover is checked once and stored under the video key`() = runBlocking {
        val calls = AtomicInteger(0)
        LyricsTiming.downloadRecheckAnswerForTest = { _, _, _ ->
            calls.incrementAndGet()
            LyricsTiming.SongCheck(
                result = "applied",
                fitted = LyricsTiming.Adjustment.Mapped(
                    map = LyricsTimeMap.shift(0L),
                    byAi = true,
                    rate = 1.0,
                    matchPercent = 100,
                ),
                heard = emptyList(),
                match = 100,
                shift = 0L,
                seconds = 5,
                http = 200,
                words = 3,
            )
        }

        val localUri = writeUnknownProviderFile()
        // The unit-test android.jar's Uri.parse returns null, so the file read
        // is replaced with the same embedded answer the sidecar would give.
        controller.embeddedLyrics = { _, _ ->
            com.music.bitchord.data.lyrics.EmbeddedLyrics.Embedded(
                lines = lines,
                source = LyricsSource.LRCLIB,
                videoId = null,
            )
        }

        controller.loadLyricsFor(
            Song(
                videoId = videoId,
                title = "Fansa (cover)",
                artist = "오화요",
                thumbnailUrl = null,
                isVideo = true,
                localUri = localUri,
            ),
            durationMs = 0L,
        )

        val videoKey = lines.joinToString("\n") { it.text }.hashCode()
        withTimeout(5_000L) {
            while (!File(directory, "lyrics_timing_v1.json").let {
                    it.isFile && it.readText().contains("\"aiTextKey\":$videoKey")
                }
            ) {
                delay(10)
            }
        }

        val stored = File(directory, "lyrics_timing_v1.json").readText()
        assertEquals("the song check must run once per play", 1, calls.get())
        assertEquals(
            "the answer has to be keyed to the video, not the file",
            true,
            stored.contains("\"aiTextKey\":$videoKey"),
        )
        assertEquals(true, stored.contains("\"syncCheckVersion\":1"))
        val syncLines = TrackLog.exportText().lines()
            .filter { it.contains("lyrics-sync: $videoId") }
        assertNotNull("a sync line for the downloaded cover must be recorded", syncLines.lastOrNull())
        assertEquals(true, syncLines.any { it.contains("video=y") })
        // The phone log's evidence: an unknown provider must say why the check
        // runs, not report `none` because the row is a video.
        assertEquals(true, syncLines.any { it.contains("recheck=unknown-provider") })

        assertNotNull(
            LyricsTiming.timing(
                context = context,
                videoId = videoId,
                lines = lines,
                baseAllowed = true,
                isVideo = true,
                audioSource = null,
            )?.base,
        )
    }
}

private class DownloadedCoverApplication(private val base: Context) : Application() {
    override fun getApplicationContext(): Context = base
    override fun getFilesDir(): File = base.filesDir
}
