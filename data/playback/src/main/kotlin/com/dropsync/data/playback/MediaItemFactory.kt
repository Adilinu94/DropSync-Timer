package com.dropsync.data.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.dropsync.core.model.Song

/**
 * Abbildung Song -> MediaItem (Bauplan Schritt 5.4): mediaId ist die
 * MediaStore-ID, die contentUri kommt aus der Bibliothek, Metadaten sind
 * vollstaendig lokal fuer korrekte Systembenachrichtigungen.
 */
object MediaItemFactory {
    fun fromSong(song: Song): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(song.mediaStoreId.toString())
            .setUri(song.contentUri)
            .setRequestMetadata(
                // Controller strippen die localConfiguration; die URI wird
                // deshalb zusaetzlich in den RequestMetadata transportiert.
                MediaItem.RequestMetadata
                    .Builder()
                    .setMediaUri(Uri.parse(song.contentUri))
                    .build(),
            ).setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(song.title ?: song.displayName)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build(),
            ).build()

    /**
     * Stellt im Service die abspielbare URI wieder her, wenn ein Item vom
     * MediaController ohne localConfiguration ankommt (Media3-Muster fuer
     * onAddMediaItems).
     */
    fun resolveForPlayback(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri ?: return item
        return item.buildUpon().setUri(uri).build()
    }
}
