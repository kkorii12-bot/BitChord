package com.music.bitchord.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyPlayback
import com.music.bitchord.data.listentogether.PartyQueue
import com.music.bitchord.data.listentogether.PartyTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A party track whose media items could not be built must not stay marked as
 * loading. Left set, every later reconcile skipped that same id and the song
 * on screen never made a sound until another track was selected.
 */
class PartyTrackLoadRetryTest {

    private class FakePlayer(private val failOnSet: Boolean) {
        val items = mutableListOf<MediaItem>()
        val setAttempted = CountDownLatch(1)
        val prepared = CountDownLatch(1)

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, args ->
            when (method.name) {
                "setMediaItems" -> {
                    if (failOnSet) {
                        setAttempted.countDown()
                        throw IllegalStateException("metadata unavailable")
                    }
                    @Suppress("UNCHECKED_CAST")
                    val newItems = args[0] as List<MediaItem>
                    items.clear()
                    items.addAll(newItems)
                    setAttempted.countDown()
                    null
                }
                "getMediaItemCount" -> items.size
                "getCurrentMediaItemIndex" -> 0
                "getCurrentMediaItem" -> items.firstOrNull()
                "getCurrentPosition" -> 0L
                "getDuration" -> 0L
                "isPlaying" -> false
                "getPlayWhenReady" -> false
                "getPlaybackState" -> Player.STATE_IDLE
                "prepare" -> {
                    prepared.countDown()
                    null
                }
                else -> null
            }
        } as Player
    }

    @Test
    fun `a failed party track load is retried on the next reconcile`() {
        val failing = FakePlayer(failOnSet = true)
        val working = FakePlayer(failOnSet = false)
        var current: Player = failing.player
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val sync = PartySync(scope) { current }
            val track = PartyTrack(videoId = "v1", title = "One", artist = "Artist", durationMs = 180_000L)
            val state = ListenTogether.State(
                code = "ABC123",
                playback = PartyPlayback(track = track),
                queue = PartyQueue(seq = 1, index = 0, items = listOf(track)),
            )

            sync.load(state)
            assertTrue("the first load was attempted", failing.setAttempted.await(5, TimeUnit.SECONDS))
            Thread.sleep(250)

            current = working.player
            sync.load(state)
            assertTrue(
                "the failed track was not retried",
                working.prepared.await(5, TimeUnit.SECONDS),
            )
        } finally {
            scope.cancel()
        }
    }
}
