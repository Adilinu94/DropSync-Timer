# DropSync

Lokale Android-App fuer Musikwiedergabe, markerbasierte Timer (DropSync) und
ein Krafttrainingstagebuch — vollstaendig offline, ohne Konto, ohne Analytics.

Grundlage ist der verbindliche technische Bauplan
(`DropSync-Technischer-Bauplan.md`, Stand 27.07.2026). Abweichungen vom
Bauplan sind nur ueber ADRs in [`docs/adr/`](docs/adr/) erlaubt.

## Status der Umsetzungsschritte

| Schritt | Inhalt | Status |
|---|---|---|
| 1 | Projekt und Lieferkette | Abgeschlossen |
| 2 | Modulgrenzen, DI, Fehlervertrag | Abgeschlossen |
| 3 | Datenbank und Migrationen | Abgeschlossen |
| 4 | Lokale Medienbibliothek | Abgeschlossen (Datenschicht; UI folgt in Schritt 12) |
| 5 | Media3-Playback-Service | Abgeschlossen (Code; Geraeteverifikation in Schritt 13) |
| 6 | Songmarker-Import | Abgeschlossen (Import/Zuordnung; Settings-UI folgt in Schritt 12) |
| 7 | Timerkern | Abgeschlossen (Domainkern; Cue-Adapter folgen in Schritt 8) |
| 8 | Audio-Cues, Ducking, Haptik | Abgeschlossen (Adapter; TTS-/Haptik-Geraetetest in Schritt 13) |
| 9 | Uebungen, Routinen, Session-Log | Abgeschlossen (Datenschicht; UI folgt in Schritt 12) |
| 10 | Volumen, PRs, Verlauf | Abgeschlossen (Berechnung/Persistenz; Verlaufs-UI folgt in Schritt 12) |
| 11 | Musik-/Workout-Integration | Abgeschlossen (Domainregeln + Snapshots; UI-Verdrahtung in Schritt 12) |
| 12 | UI und Barrierefreiheit | Abgeschlossen (Shell, Navigation, Satz-Logging mit Undo, Resttimer, Markerimport mit Zuordnung, Drop-Rest; TalkBack-/200%-Schrift-Abnahme nur am Geraet, siehe Schritt 13) |
| 13 | Tests, Performance, Release-Gates | Offen |
| 14 | Datenschutz-, Lizenz-, Releasepruefung | Offen |

### Audio-Engine-Ausbau (Plan `AUDIO_ENGINE_AUSBAU_PLAN.md`, ADR-0005 bis ADR-0010)

| Phase | Inhalt | Status |
| ----- | ------ | ------ |
| 1 (Schritt 15) | Audio-Engine-Fundament: Float-Output, DSP-Kette (64-Bit-Double), Preamp+Limiter, Audioinformationen | Abgeschlossen (`:domain:audio`, `:data:audio`, Service-Wiring; Audioinformationen-Panel in `:feature:audio`; Cue-Ducking auf dem Preamp-Knoten der DSP-Kette statt Player-Lautstaerke, kollisionsfrei zu DVC) |
| 2 (Schritt 16) | EQ (32 Baender), Bass/Hoehen, Stereo Expansion, Reverb, Dither, Resampler, DVC, Presets | Abgeschlossen (`MasterDspProcessor`, EQ-Presets in Room + Seeder/CRUD; UI in `:feature:audio`: EQ grafisch/parametrisch mit Presets, Klangregler, Stereobreite, Reverb, Resampler, Dither, DVC, Crossfade) |
| 3 (Schritt 17) | FFmpeg-Formate (ALAC/AIFF/WMA/APE/TAK/TTA/DSD), CUE, M3U, SAF-Ordnerscan | Codeseitig abgeschlossen: Parser, Formatkatalog, SAF-Ordnerscan, `cue_tracks` + Clipping-MediaItems, Renderer-Extension-Mode (`DspRenderersFactory`, `EXTENSION_RENDERER_MODE_PREFER`), M3U/M3U8-Playlisten-Import. FFmpeg-Extension ist artifact-ready: Gradle-Flag `dropsync.enableFfmpeg` (Default aus), automatisches Wiring in `settings.gradle.kts` + `:data:audio`, Build-Skript `scripts/build-ffmpeg.sh` + Anleitung `docs/ffmpeg-build.md`. Code-seitig vollstaendig und verifiziert (Wiring, Lizenz, Renderer-Prioritaet); das native Artefakt erfordert einen Entwickler-Host mit NDK r27 — kein Projektrueckstand, sondern strukturelle Voraussetzung nativer Android-Builds |
| 4 (Schritt 18) | Gapless-Absicherung, Crossfade (Dual-Player), Auto-Resume, MusicFX | Abgeschlossen: `CrossfadeController` (Equal-Power, Gapless-/CUE-Ausschluss, Fallback harter Uebergang), `onPlaybackResumption` aus `PlayerStateStore`, Option "Bei BT-Verbindung automatisch fortsetzen", MusicFX-Session-Broadcasts + `useSystemEffects`-Bypass |
| 5 (Schritt 19) | Pro-Ausgang-Profile, Bit-Perfect (USB, Android 14+), BT-Anzeige | Abgeschlossen: Profile je Geraet (`DeviceProfileStore` + `OutputProfileController`, automatischer Wechsel + Save-Through), `BitPerfectGateway` (AudioMixerAttributes, API 34+), Bit-Perfect-Bypass (DSP aus, Float-Output aus, Crossfade aus), Vertrag `activeOutputProfileKey`/`bitPerfectSupport`; UI in `:feature:audio` (Bit-Perfect-Panel mit DAC-Faehigkeiten, aktives Ausgabeprofil, BT-Hinweis + Link zu den System-Toneinstellungen), erreichbar ueber Einstellungen -> Audio & DSP |
| 6 (Schritt 20) | Bibliothek: Kuenstler/Alben/Genres/Ordner, Statistiken, Favoriten, Suche, Queue | Datenschicht + UI-Ansichten abgeschlossen: Room (`play_stats`, `favorites`, `playlists`/`playlist_items`, FTS4 `song_fts`, `genre`-Spalte), `LibraryBrowseRepository` (Alben/Kuenstler/Genres/Ordner, zuletzt/meistgespielt, Favoriten, Volltextsuche, Playlisten-CRUD/Move, M3U-Import); `LibraryScreen`/`LibraryContent` mit Ansichts-Chips, Volltextsuche, Sortierung, Filtern nach Format/Dauer/Hi-Res, Favoriten-Toggle, Alphabet-Schnellscroller, horizontalem Swipen zwischen Ansichten (`HorizontalPager`) und Sammlungs-Drilldown; konfigurierbare Ansichten (ein-/ausblenden + Reihenfolge, persistiert via `LibraryViewPreferencesRepository`); Queue-Editor (verschieben/entfernen/als Naechstes/zur Queue) ueber `PlaybackRepository` als Bottom-Sheet am Mini-Player; `MediaLibraryService`-Browse-Baum (Root -> Titel/Alben/Interpreten/Ordner -> Songs) fuer Android Auto/BT |
| 7 (Schritt 21) | Feinschliff, Barrierefreiheit, Performance, Geraetetests | Codeseitig abgeschlossen: Barrierefreiheit (Slider mit `stateDescription`, 48-dp-Schaltflaechen, Schaltererklaerungen), DE/EN-Strings, Akku-Hinweis bei Hi-Res-Resampling, DSP-Durchsatz-/Stabilitaetswaechter (`DspPerformanceTest`: 32 Baender/48 kHz > 2x Echtzeit, keine NaN/Inf). Geraeteabhaengig offen (nicht automatisierbar): Baseline-Profile-Generierung (Macrobenchmark auf Geraet), reale CPU-Messung Mittelklasse, USB-DAC-Bit-Perfect, BT-Codec-Verhalten, MusicFX mit/ohne Systemequalizer |

### Workout-Funktionen-Ausbau (Plan `WORKOUT_FUNKTIONEN_AUSBAU_PLAN.md`)

| Phase | Inhalt | Status |
| ----- | ------ | ------ |
| A | Datenschicht: `exercise_rest_prefs` (Rest-Timer pro Uebung), DB v2 + `MIGRATION_1_2`, DAO-Erweiterungen (Bibliothek, Tausch, Wiederholen, Session-Tracks) | Abgeschlossen |
| B | Domain: eigene Uebungen mit Muskel-Mapping (`Slugs`), Uebungstausch KEEP/MOVE/DISCARD mit PR-Neuberechnung, `repeatLastSession`, Routinen aus Session, `ProgressSeriesBuilder` + `ProgressionClassifier` (Plateau-Alarm + Vorschlag) | Abgeschlossen |
| C | Musik-Kopplung: `DropRestRequestBus` (Workout fordert DropSync-Rest an), Playback-Snapshot beim Satzabschluss (best-effort, 11.1) | Abgeschlossen |
| D | Charts: animierte `LineChart`/`BarChart` in `:core:designsystem` (nur Compose-Canvas) | Abgeschlossen |
| E | Feature-UI: `WorkoutFeature`-NavHost (Session, Bibliothek, Uebungsdetail, Routinen, Fortschritt), Prefill, Rest Normal/DropSync, Swap-Dialog, Routinen-Aktionen, Track-Chip, Strings DE/EN | Abgeschlossen (Geraete-Abnahme in Schritt 13) |
| F | Tests: Domain (Slugs, Progressanalyse), Repository (18 Tests inkl. Swap/Wiederholen/RestPref/Snapshot), Migration 1 -> 2 gegen `2.json` | Abgeschlossen |

### Marker- und Waveform-Ausbau (Plan `MARKER_UND_WAVEFORM_AUSBAU_PLAN.md`)

| Phase | Inhalt | Status |
| ----- | ------ | ------ |
| 1 | Now-Playing-Screen-Fundament: `NowPlayingScreen` (Cover via `MediaMetadataRetriever.embeddedPicture`, da minSdk 26 < API 29 fuer `loadThumbnail()`), `NowPlayingUiState` + Positions-Ticker (200 ms `snapshotNow()`, nur bei sichtbarem Screen), `skipToPrevious`/`seekTo`, Route `now_playing` per Tap auf den Mini-Player | Abgeschlossen |
| 2 | Geteilte Analyse-Grundlage: `TrackAnalyzer`/`TrackAnalysisRepository` (`:domain:audio`), Streaming-Akkumulatoren (Min/Max-Buckets + RMS-Energie in einem Durchgang), `track_analysis`-Cache (DB v3, `MIGRATION_2_3`, `analyzer_version` zur Invalidierung), `TrackAnalyzerImpl` per MediaExtractor/MediaCodec (ADR-0011), aufschiebbarer `OneTimeWorkRequest` dedupliziert ueber `track_analysis_<songId>` | Abgeschlossen |
| 3 | Waveform-Anzeige mit Scrubbing: `Waveform`/`WaveformPlaceholder` (`:core:designsystem`, Compose-Canvas, Lime fuer gespielten Anteil), Tap = Sprung, Drag = Live-Vorschau mit Sprung beim Loslassen; Ladezustand pulsiert, Fehlerfall (persistiert als `bucket_count = 0`) faellt dauerhaft auf die Zeitleiste zurueck | Abgeschlossen |
| 4 | A1 - Manuelles Marker-Setzen (`createManualMarker`/`deleteMarker`, Bestaetigungs-Sheet, Marker-Ticks) | Offen |
| 5 | A2 - On-Device-Drop-/Onset-Erkennung (Novelty + Peak-Picking, Kandidaten `AUTO_DETECTED`/`isEnabled=false`, Review-Liste) | Offen |

## Build

Voraussetzungen:

- JDK 17 oder neuer (empfohlen: das mit Android Studio ausgelieferte JBR)
- Android SDK mit Platform `android-37` (Pfad in `local.properties` als
  `sdk.dir`, wird nicht eingecheckt)

```
./gradlew assembleDebug      # Debug-Build
./gradlew assembleRelease    # Release-Build (R8, unsigniert)
./gradlew test               # Unit-Tests aller Module
./gradlew spotlessCheck      # Formatierung und Lint
```

## Architektur

Feature- und schichtorientierte Module (Bauplan Abschnitt 3.2):

```
:app                 Navigation + Hilt-Verdrahtung, keine Fachlogik
:core:common         AppResult, AppError, Clock, DispatcherProvider
:core:model          reine Domain-Modelle
:core:database       Room, DAOs, Migrationen
:core:designsystem   Material-3-Theme, wiederverwendbare Komponenten
:core:testing        Fakes (FakeClock u. a.), Testregeln
:data:*              Repository-Implementierungen (MediaStore, Media3, Room)
:domain:*            Use Cases, Timerzustandsmaschine, Trainingsmathematik
:feature:*           Compose-Screens; kein Feature importiert ein anderes
```

Verbindliche Abhaengigkeitsregeln: Features kennen weder Room noch ExoPlayer;
Domain-Module importieren kein Android-UI-, Room- oder Player-API. Ein
Architekturtest (`:core:testing`) prueft diese Regeln bei jedem Testlauf.

## Richtlinien

- Versionen ausschliesslich in [`gradle/libs.versions.toml`](gradle/libs.versions.toml)
- Lizenzinventar in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- Zeitpunkte als UTC-Epoch-Millis, Gewichte als ganze Millikilogramm (`Long`)
- Keine Analytics, keine Telemetrie, kein Netzwerkzugriff; Auto Backup ist deaktiviert
