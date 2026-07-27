package com.dropsync.data.library

import com.dropsync.core.common.AppResult
import com.dropsync.core.database.entity.SongEntity
import com.dropsync.core.model.LinkMethod
import com.dropsync.core.testing.FakeClock
import com.dropsync.core.testing.TestDispatcherProvider
import com.dropsync.domain.library.ImportValidator
import com.dropsync.domain.library.ImportedMarker
import com.dropsync.domain.library.ImportedTrack
import com.dropsync.domain.library.MarkerMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerRepositoryImplTest {
    private val markerDao = FakeMarkerDao()
    private val songDao = FakeSongDao()

    private val repository =
        MarkerRepositoryImpl(
            markerDao = markerDao,
            songDao = songDao,
            transactionRunner = FakeTransactionRunner(),
            validator = ImportValidator(),
            matcher = MarkerMatcher(),
            clock = FakeClock(initialEpochMillis = 1_700_000_000_000),
            dispatchers = TestDispatcherProvider(),
        )

    private fun songEntity(
        id: Long,
        name: String = "track.mp3",
        size: Long = 1000,
        duration: Long = 200_000,
        hash: String? = null,
    ) = SongEntity(
        mediaStoreId = id,
        contentUri = "content://media/external/audio/media/$id",
        displayName = name,
        relativePath = "Music/Training",
        durationMs = duration,
        sizeBytes = size,
        dateModifiedSeconds = 0,
        title = null,
        artist = null,
        album = null,
        isAvailable = true,
        knownSha256 = hash,
    )

    private fun track(
        name: String = "track.mp3",
        size: Long = 1000,
        duration: Long = 200_000,
        hash: String? = null,
        markers: List<ImportedMarker> = listOf(ImportedMarker("Drop 1", 100_000)),
    ) = ImportedTrack(
        relativePath = "Music/Training",
        displayName = name,
        sizeBytes = size,
        durationMs = duration,
        sha256 = hash,
        markers = markers,
    )

    @Test
    fun `ungueltiges dokument veraendert keine tabelle`() =
        runTest {
            // Abnahmekriterium Schritt 6: Import mit Regelverstoss wird
            // abgelehnt und veraendert keine Tabelle.
            val bad = track(markers = listOf(ImportedMarker("", -1)))
            val result = repository.importDocument(1, listOf(bad))

            val report = (result as AppResult.Success).value
            assertTrue(report.wasRejected)
            assertTrue(markerDao.markers.isEmpty())
            assertTrue(markerDao.links.isEmpty())
        }

    @Test
    fun `import legt marker an und verlinkt per metadaten`() =
        runTest {
            songDao.rows[7] = songEntity(7)
            val result = repository.importDocument(1, listOf(track()))

            val report = (result as AppResult.Success).value
            assertEquals(1, report.added)
            assertEquals(0, report.unmatched)
            val link = markerDao.links.values.single()
            assertEquals(7, link.songId)
            assertEquals(LinkMethod.METADATA.name, link.linkMethod)
        }

    @Test
    fun `reimport aktualisiert statt zu duplizieren`() =
        runTest {
            // Abnahmekriterium Schritt 6: erneuter Import desselben
            // Dokuments erzeugt keine Duplikate.
            songDao.rows[7] = songEntity(7)
            repository.importDocument(1, listOf(track()))
            val second =
                repository.importDocument(
                    1,
                    listOf(track(markers = listOf(ImportedMarker("Drop 1 neu", 100_000)))),
                )

            val report = (second as AppResult.Success).value
            assertEquals(0, report.added)
            assertEquals(1, report.updated)
            assertEquals(1, markerDao.markers.size)
            assertEquals(1, markerDao.links.size)
            assertEquals("Drop 1 neu", markerDao.markers.getValue(1).label)
        }

    @Test
    fun `gespeicherter hash gewinnt und wird als HASH verlinkt`() =
        runTest {
            val hash = "b".repeat(64)
            songDao.rows[3] = songEntity(3, name = "umbenannt.mp3", size = 555, hash = hash)
            val result = repository.importDocument(1, listOf(track(hash = hash)))

            val report = (result as AppResult.Success).value
            assertEquals(0, report.unmatched)
            val link = markerDao.links.values.single()
            assertEquals(3, link.songId)
            assertEquals(LinkMethod.HASH.name, link.linkMethod)
        }

    @Test
    fun `externer hash wird bei zuordnung am song gespeichert`() =
        runTest {
            // SHA-256 kommt ausschliesslich aus dem Analyzer-Dokument
            // (Abschnitt 2) und macht spaetere Importe hash-faehig.
            val hash = "c".repeat(64)
            songDao.rows[7] = songEntity(7)
            repository.importDocument(1, listOf(track(hash = hash)))

            assertEquals(hash, songDao.rows.getValue(7).knownSha256)
        }

    @Test
    fun `ohne treffer bleibt der marker unzugeordnet erhalten`() =
        runTest {
            val result = repository.importDocument(1, listOf(track()))

            val report = (result as AppResult.Success).value
            assertEquals(1, report.added)
            assertEquals(1, report.unmatched)
            assertEquals(1, markerDao.markers.size)
            assertTrue(markerDao.links.isEmpty())
        }

    @Test
    fun `linkManually ersetzt eine bestehende zuordnung`() =
        runTest {
            songDao.rows[7] = songEntity(7)
            songDao.rows[8] = songEntity(8, name = "anders.mp3", size = 2000)
            repository.importDocument(1, listOf(track()))
            val markerId = markerDao.markers.keys.single()

            val result = repository.linkManually(markerId, 8)

            assertTrue(result is AppResult.Success)
            val link = markerDao.links.values.single()
            assertEquals(8, link.songId)
            assertEquals(LinkMethod.MANUAL.name, link.linkMethod)
        }
}
