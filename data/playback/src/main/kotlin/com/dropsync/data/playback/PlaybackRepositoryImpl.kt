package com.dropsync.data.playback

import androidx.media3.common.Player
import com.dropsync.core.common.AppError
import com.dropsync.core.common.AppResult
import com.dropsync.core.common.DispatcherProvider
import com.dropsync.core.model.Song
import com.dropsync.domain.playback.PersistedPlayerState
import com.dropsync.domain.playback.PlaybackRepository
import com.dropsync.domain.playback.PlaybackState
import com.dropsync.domain.playback.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Einziger App-Zugang zur Wiedergabe (Bauplan 3.3, Schritt 5).
 *
 * Alle Kommandos laufen auf dem Main-Dispatcher gegen denselben
 * MediaController; der Zustand wird ueber einen Player.Listener
 * beobachtet, damit auch Sperrbildschirm- und Bluetooth-Steuerung
 * (Schritt 5, Abnahme 1) im App-Zustand ankommen.
 */
class PlaybackRepositoryImpl(
    private val connection: PlayerConnection,
    private val stateStore: PlayerStateStore,
    private val dispatchers: DispatcherProvider,
) : PlaybackRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val mutableState = MutableStateFlow(PlaybackState())
    private var listenerAttached = false

    override val state: Flow<PlaybackState> = mutableState.asStateFlow()

    override suspend fun setQueue(
        songs: List<Song>,
        startIndex: Int,
        playWhenReady: Boolean,
    ): AppResult<Unit> {
        if (songs.isEmpty() || startIndex !in songs.indices) {
            return AppResult.failure(AppError.MediaUnavailable(null))
        }
        return command { player ->
            player.setMediaItems(songs.map(MediaItemFactory::fromSong), startIndex, 0)
            player.prepare()
            player.playWhenReady = playWhenReady
        }
    }

    override suspend fun play(): AppResult<Unit> = command { it.play() }

    override suspend fun pause(): AppResult<Unit> = command { it.pause() }

    override suspend fun seekTo(positionMs: Long): AppResult<Unit> = command { it.seekTo(positionMs) }

    override suspend fun skipToNext(): AppResult<Unit> = command { it.seekToNextMediaItem() }

    override suspend fun skipToPrevious(): AppResult<Unit> = command { it.seekToPreviousMediaItem() }

    override suspend fun setShuffle(enabled: Boolean): AppResult<Unit> = command { it.shuffleModeEnabled = enabled }

    override suspend fun setRepeatMode(mode: RepeatMode): AppResult<Unit> =
        command {
            it.repeatMode =
                when (mode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                }
        }

    override suspend fun lastPersistedState(): PersistedPlayerState? = stateStore.read()

    private suspend fun command(block: (Player) -> Unit): AppResult<Unit> =
        try {
            withContext(dispatchers.main) {
                val player = connection.requirePlayer()
                attachListener(player)
                block(player)
                publishAndPersist(player)
            }
            AppResult.success(Unit)
        } catch (e: Exception) {
            AppResult.failure(AppError.Unknown(e.message))
        }

    /** Muss auf dem Main-Dispatcher laufen (MediaController-Vertrag). */
    private fun attachListener(player: Player) {
        if (listenerAttached) return
        listenerAttached = true
        player.addListener(
            object : Player.Listener {
                override fun onEvents(
                    eventsPlayer: Player,
                    events: Player.Events,
                ) {
                    if (events.containsAny(
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_TIMELINE_CHANGED,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_POSITION_DISCONTINUITY,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                        )
                    ) {
                        // Auch externe Steuerung (Notification, Bluetooth)
                        // landet so im Zustand und im Restore-Speicher (5.5).
                        publishAndPersist(eventsPlayer)
                    }
                }
            },
        )
    }

    private fun publishAndPersist(player: Player) {
        val snapshot = player.toPlaybackState()
        mutableState.value = snapshot
        scope.launch {
            stateStore.write(
                PersistedPlayerState(
                    queueSongIds = snapshot.queueSongIds,
                    currentSongId = snapshot.currentSongId,
                    positionMs = snapshot.positionMs,
                    shuffleEnabled = snapshot.shuffleEnabled,
                    repeatMode = snapshot.repeatMode,
                ),
            )
        }
    }

    companion object {
        /** Reine Abbildung Player -> Domainzustand; ohne Seiteneffekte. */
        fun Player.toPlaybackState(): PlaybackState =
            PlaybackState(
                isPlaying = isPlaying,
                currentSongId = currentMediaItem?.mediaId?.toLongOrNull(),
                positionMs = currentPosition.coerceAtLeast(0),
                durationMs = duration.coerceAtLeast(0),
                shuffleEnabled = shuffleModeEnabled,
                repeatMode =
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.OFF
                    },
                queueSongIds =
                    (0 until mediaItemCount).mapNotNull {
                        getMediaItemAt(it).mediaId.toLongOrNull()
                    },
            )
    }
}
