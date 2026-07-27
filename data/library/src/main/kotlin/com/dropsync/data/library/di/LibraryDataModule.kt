package com.dropsync.data.library.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.dropsync.core.common.Clock
import com.dropsync.core.common.DispatcherProvider
import com.dropsync.core.database.TransactionRunner
import com.dropsync.core.database.dao.MarkerDao
import com.dropsync.core.database.dao.SongDao
import com.dropsync.data.library.DataStoreScanStateStore
import com.dropsync.data.library.LibraryRepositoryImpl
import com.dropsync.data.library.MarkerRepositoryImpl
import com.dropsync.data.library.MediaStoreGateway
import com.dropsync.data.library.MediaStoreGatewayImpl
import com.dropsync.data.library.ScanStateStore
import com.dropsync.domain.library.ImportValidator
import com.dropsync.domain.library.LibraryRepository
import com.dropsync.domain.library.MarkerMatcher
import com.dropsync.domain.library.MarkerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Verdrahtet die Bibliotheks-Datenschicht (Bauplan Schritt 4/6):
 * Features sehen nur die Domain-Interfaces aus :domain:library.
 */
@Module
@InstallIn(SingletonComponent::class)
object LibraryDataModule {
    @Provides
    @Singleton
    fun provideMediaStoreGateway(
        @ApplicationContext context: Context,
    ): MediaStoreGateway = MediaStoreGatewayImpl(context)

    @Provides
    @Singleton
    fun provideScanStateStore(
        @ApplicationContext context: Context,
    ): ScanStateStore =
        DataStoreScanStateStore(
            PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile(DataStoreScanStateStore.DATA_STORE_NAME)
            },
        )

    @Provides
    @Singleton
    fun provideLibraryRepository(
        gateway: MediaStoreGateway,
        songDao: SongDao,
        scanStateStore: ScanStateStore,
        transactionRunner: TransactionRunner,
        dispatchers: DispatcherProvider,
    ): LibraryRepository = LibraryRepositoryImpl(gateway, songDao, scanStateStore, transactionRunner, dispatchers)

    @Provides
    @Singleton
    fun provideMarkerRepository(
        markerDao: MarkerDao,
        songDao: SongDao,
        transactionRunner: TransactionRunner,
        clock: Clock,
        dispatchers: DispatcherProvider,
    ): MarkerRepository =
        MarkerRepositoryImpl(
            markerDao = markerDao,
            songDao = songDao,
            transactionRunner = transactionRunner,
            validator = ImportValidator(),
            matcher = MarkerMatcher(),
            clock = clock,
            dispatchers = dispatchers,
        )
}
