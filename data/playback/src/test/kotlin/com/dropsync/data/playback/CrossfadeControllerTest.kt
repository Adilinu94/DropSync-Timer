package com.dropsync.data.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Gapless-/CUE-Regeln des Crossfade (Plan Phase 4, ADR-0007). */
@RunWith(AndroidJUnit4::class)
class CrossfadeControllerTest {
    @Test
    fun `verschiedene alben werden uebergeblendet`() {
        val current = item(mediaId = "1", album = "Album A")
        val next = item(mediaId = "2", album = "Album B")
        assertTrue(CrossfadeController.shouldCrossfade(current, next))
    }

    @Test
    fun `gleiches album bleibt gapless`() {
        val current = item(mediaId = "1", album = "Live in Tokyo")
        val next = item(mediaId = "2", album = "Live in Tokyo")
        assertFalse(CrossfadeController.shouldCrossfade(current, next))
    }

    @Test
    fun `ohne albuminfo wird uebergeblendet`() {
        val current = item(mediaId = "1", album = null)
        val next = item(mediaId = "2", album = "Album B")
        assertTrue(CrossfadeController.shouldCrossfade(current, next))
    }

    @Test
    fun `cue tracks werden nie uebergeblendet`() {
        val cue = item(mediaId = "${MediaItemFactory.CUE_MEDIA_ID_PREFIX}7:2", album = "Album A")
        val song = item(mediaId = "9", album = "Album B")
        assertFalse(CrossfadeController.shouldCrossfade(cue, song))
        assertFalse(CrossfadeController.shouldCrossfade(song, cue))
    }

    private fun item(
        mediaId: String,
        album: String?,
    ): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setAlbumTitle(album)
                    .build(),
            ).build()
}
