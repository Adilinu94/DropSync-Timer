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
| 12 | UI und Barrierefreiheit | In Arbeit (Shell, Navigation, Kernscreens fertig; Satz-Logging-, Timer- und Import-UI folgen) |
| 13 | Tests, Performance, Release-Gates | Offen |
| 14 | Datenschutz-, Lizenz-, Releasepruefung | Offen |

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
