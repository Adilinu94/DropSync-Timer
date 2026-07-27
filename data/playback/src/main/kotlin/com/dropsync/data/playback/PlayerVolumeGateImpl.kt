package com.dropsync.data.playback

import com.dropsync.core.common.DispatcherProvider
import com.dropsync.domain.playback.PlayerVolumeGate
import kotlinx.coroutines.withContext

/**
 * Lautstaerkezugriff auf denselben MediaController (Bauplan 5.3):
 * betrifft ausschliesslich den App-Player, nie die Systemlautstaerke.
 */
class PlayerVolumeGateImpl(
    private val connection: PlayerConnection,
    private val dispatchers: DispatcherProvider,
) : PlayerVolumeGate {
    override suspend fun currentVolume(): Float = withContext(dispatchers.main) { connection.requirePlayer().volume }

    override suspend fun setVolume(volume: Float) {
        withContext(dispatchers.main) {
            connection.requirePlayer().volume = volume.coerceIn(0f, 1f)
        }
    }
}
