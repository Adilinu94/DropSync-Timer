package com.dropsync.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migrationstest gemaess Schritt 3.5: jede exportierte Schemaversion muss
 * bis zur aktuellen Version migrierbar sein. Aktuell existiert Version 1;
 * fuer jede neue Version wird hier die Migrationskette ergaenzt.
 * Destruktive Migration ist in Release verboten.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DropSyncDatabase::class.java,
        )

    @Test
    fun `schema version 1 laesst sich anlegen und validieren`() {
        // Room 2.8 erwartet konsistente Pfade zwischen Anlegen und Validieren.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbPath = context.getDatabasePath(TEST_DB).absolutePath

        // Erzeugt die Datenbank exakt nach exportiertem Schema v1 und
        // validiert sie gegen die aktuelle Entity-Definition.
        helper.createDatabase(dbPath, 1).close()
        helper.runMigrationsAndValidate(dbPath, 1, true)
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
