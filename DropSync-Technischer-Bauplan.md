# DropSync: Technischer Bauplan und Implementierungsleitfaden

Stand: 27. Juli 2026  
Zielplattform: Android, Kotlin, Jetpack Compose  
Dokumentstatus: verbindliche technische Spezifikation fuer Version 1

## 1. Zweck, Leseregeln und Erfolgskriterien

Dieses Dokument ersetzt die bisherige Projektdokumentation vollstaendig. Es ist absichtlich konkret formuliert: Eine implementierende KI darf keine Architektur-, Datenmodell- oder Ablaufentscheidung aus diesem Dokument durch eine eigene Variante ersetzen. Wenn eine Anforderung hier nicht definiert ist, darf sie nicht in Version 1 implementiert werden.

DropSync ist eine lokale Android-App mit drei gemeinsam genutzten Bereichen:

1. lokale Musikwiedergabe;
2. ein Timer, dessen Ende an einen analysierten Songmarker gebunden werden kann;
3. ein Krafttrainingstagebuch mit Satzprotokoll, Routinen und nachvollziehbarer Statistik.

Version 1 ist erfolgreich, wenn eine Person ohne Konto und ohne Internetzugang Musik aus ihrer lokalen Bibliothek abspielt, eine Trainingssession protokolliert, einen normalen oder DropSync-Resttimer verwendet, die Daten nach einem App-Neustart wiederfindet und die Kernablaeufe mit TalkBack bedienen kann.

### 1.1 Begriffsdefinitionen

| Begriff | Verbindliche Bedeutung |
|---|---|
| Song | Ein lokaler, ueber MediaStore gefundener Audioeintrag. Kein Dateipfad und keine Streaming-URL. |
| Songmarker | Ein manuell oder extern analysierter Zeitstempel innerhalb eines Songs, zum Beispiel ein Drop. Ein Song kann mehrere Marker haben. |
| DropSync | Ein Timer, dessen Ende auf der **Wiedergabe-Zeitlinie** eines bestimmten Songmarkers liegt. |
| Exakte Synchronisation | Die Zeitlinie des Players erreicht den Marker, waehrend der Timer `0` erreicht. Es ist keine Zusage fuer eine physikalisch identische Schallausgabezeit am Lautsprecher oder Bluetooth-Kopfhoerer. Deren Latenz ist geraete- und codecabhaengig. |
| Trainingssatz | Ein protokollierter Versuch mit Gewicht und Wiederholungen oder mit Zeit bzw. Distanz. |
| Arbeitsset | Ein Satz, der fuer Trainingsvolumen, PRs und Satzzaehlung qualifiziert ist. Warm-up-Saetze qualifizieren nie. |
| Satzcluster | Eine fachliche Einheit aus einem oder mehreren Satzsegmenten, etwa ein Drop Set. Ein Cluster kann mehrere Gewichts-/Wiederholungssegmente enthalten, zaehlt aber als genau ein Arbeitsset. |

### 1.2 Verbindliche Produktgrenzen fuer Version 1

Enthalten sind lokale Audiofiles, lokale Datenbank, DropSync, normaler Resttimer, Uebungsbibliothek, eigene Uebungen, Routinen, Supersets bis zu drei Uebungen, Satzprotokoll, PRs, Verlauf und Basisstatistiken.

Nicht enthalten sind Streaming, Konten, Cloud-Synchronisation, soziale Funktionen, Ernaehrung, On-Device-Audioanalyse, Health Connect, Wear OS, Android Auto-spezifische Oberflaechen, Tag-Editor, Equalizer, Crossfade, eine physiologische Recovery-Berechnung und eine iOS-Version. Diese Grenzen verhindern, dass Version 1 in mehrere eigenstaendige Produkte zerfaellt.

## 2. Kritische Analyse der bisherigen Dokumentation

### 2.1 Projektziel und Scope

**Aktueller Inhalt und Ziel:** Die bisherige Fassung verbindet Player, Timer und Tracker plausibel zu einem Produkt mit klarer Zielgruppe.

**Fehlende Festlegungen und Risiken:** Der Begriff "exakt auf den Drop" ist technisch zu stark. Die Dokumentation unterscheidet nicht zwischen Playerposition, Systemzeit und physischer Audioausgabe. Zudem ist unklar, wie eine frei waehlbare Timerdauer mit einem festen Songzeitstempel zusammenpassen soll. Ein 90-Sekunden-Timer kann nicht auf einen Drop bei Sekunde 45 enden, ohne die Wiedergabe vor dem Songanfang zu starten oder einen anderen Marker zu waehlen.

**Verbindliche Verbesserung:** DropSync startet den gewaehlten Song an der Position `markerZeit - timerDauer`. Die Timerdauer muss daher groesser als 0 und kleiner oder gleich der Markerzeit sein. Ist diese Bedingung nicht erfuellt, blockiert die UI den Start und fordert einen spaeteren Marker oder eine kuerzere Dauer. Es gibt keinen stillen Fallback. Ein normaler Timer bleibt ein eigener, klar sichtbarer Modus.

### 2.2 Quellen, Forks und Lizenzen

**Aktueller Inhalt und Ziel:** Symphony soll als Fork dienen; weitere Repositories dienen als Code- oder Ideenquelle.

**Risiken:** Ein Fork mit anschliessender Audio-Engine-Migration bindet die App an eine fremde Architektur, schafft hohe Migrationskosten und uebernimmt AGPL-Pflichten. Das direkte Zusammenfuehren von GPLv3- und AGPLv3-Code ist grundsaetzlich moeglich, aber die Distribution muss die strengeren Bedingungen einhalten. Die AGPL erweitert bei netzwerkinteraktiver Bereitstellung die Quellcodepflicht; GPL und AGPL erlauben die Kombination entsprechender Module. Dies ist keine Rechtsberatung. Quellen: [GNU zur AGPL](https://www.gnu.org/licenses/why-affero-gpl.html.en), [GNU-Lizenzuebersicht](https://www.gnu.org/licenses/license-list.en.html).

**Verbindliche Verbesserung:** Version 1 wird als neues Android-Projekt aufgebaut. Es wird **kein Quellcode** aus Symphony, Booming Music, Tracker, liftapp oder anderen Referenzprojekten kopiert. Referenzen duerfen nur als Produkt- und UX-Inspiration dienen. Dadurch entfallen die AGPL-/GPL-Vererbungsrisiken der Anwendungslogik. Vor jeder neuen Fremd-Bibliothek ist ihre Lizenz in `THIRD_PARTY_NOTICES.md` zu erfassen. Die finale Lizenz der eigenen App wird erst vor der ersten Veroeffentlichung festgelegt; bis dahin wird keine Lizenzbehauptung im Code gemacht.

Die behauptete FFmpeg-Verbesserung wird ebenfalls korrigiert: Media3 nutzt standardmaessig Platform-Decoder. Software-Decoder wie FFmpeg sind gesonderte, manuell einzubindende Erweiterungen und duerfen nicht als automatische Codec-Abdeckung vorausgesetzt werden. Quelle: [Media3: unterstuetzte Formate](https://developer.android.com/media/media3/exoplayer/supported-formats). FFmpeg ist nicht Teil von Version 1.

### 2.3 Nicht-Ziele und Datenschutz

**Aktueller Inhalt und Ziel:** Offline-first und keine Cloud sind sinnvoll und reduzieren Datenschutz- sowie Ausfallrisiken.

**Fehlende Punkte:** Ohne Festlegung von Berechtigungen, Sicherung und Importgrenzen ist "lokal" nicht ausreichend. Android 13+ verlangt fuer fremde Audiodateien `READ_MEDIA_AUDIO`; Benachrichtigungen und Hintergrundtimer brauchen eigene Zustands- und Berechtigungsablaeufe. Quelle: [Android 13 Medienberechtigungen](https://developer.android.com/about/versions/13/behavior-changes-13).

**Verbindliche Verbesserung:** Die App fordert Berechtigungen erst im passenden Kontext an und nie beim ersten Start gesammelt. Sie sammelt keine Analytics, keine Werbe-ID und keine Telemetrie. Auto Backup wird in Version 1 deaktiviert, damit Trainings- und Bibliotheksmetadaten nicht unbemerkt in einen Cloud-Backupdienst gelangen; ein bewusstes Exportformat kommt erst spaeter. Android weist darauf hin, dass Auto Backup standardmaessig aktiv sein kann. Quelle: [Backup-Sicherheit](https://developer.android.com/privacy-and-security/risks/backup-best-practices).

### 2.4 Funktionale Anforderungen

**Aktueller Inhalt und Ziel:** Die Player- und Tracker-Funktionsliste ist umfangreich und die Wiederverwendung des Timerkerns ist ein guter Ansatz.

**Risiken und Korrekturen:**

- `filePath` als primaerer Schluessel ist unter Scoped Storage instabil und nicht fuer eine robuste Zuordnung geeignet. MediaStore arbeitet mit Content-URIs und IDs; Dateinamen und Speicherorte koennen sich aendern. Quelle: [Android: Shared Media](https://developer.android.com/training/data-storage/shared/media).
- Ein einzelner `dropTimeMs` pro Song reicht nicht fuer Songs mit mehreren geeigneten Drops und verhindert eine nachvollziehbare Auswahl.
- `duckingMode: Int` ist nicht selbsterklaerend, nicht validiert und speichert keinen Basislautstaerkezustand.
- `SetEntity` vermischt Satzsegment, fachlichen Satz und Gruppenbeziehung. Damit sind Drop-Sets, Supersets, Satzreihenfolge und ehrliche Volumenberechnung nicht deterministisch moeglich.
- "Maximale Wiederholungen" ist ohne Gewicht, Satztyp und Uebungsart keine sinnvolle PR-Definition.

Die Ersatzdatenmodelle in Abschnitt 6 beheben diese Punkte.

### 2.5 Nicht-funktionale Anforderungen und Architektur

**Aktueller Inhalt und Ziel:** Room, Compose, Media3 und Offline-first sind passende Grundlagen. Die bisherige Paketstruktur mischt aber technische Services, Domainedaten und UI in einem Baum. Dadurch waeren Abhaengigkeiten und Tests nach kurzer Zeit schwer kontrollierbar.

**Verbindliche Verbesserung:** Die App wird feature- und schichtorientiert modularisiert. Die UI kann weder Room noch ExoPlayer direkt verwenden. Komplexe, mehrfach verwendete Fachlogik wird als kleine Use-Case-Klasse im Domain-Modul implementiert. Das entspricht den Android-Empfehlungen: Datenzugriff liegt in der Data Layer; eine Domain Layer ist bei komplexer, wiederverwendeter Fachlogik sinnvoll. Quellen: [Android-Architekturempfehlungen](https://developer.android.com/topic/architecture/recommendations), [Domain Layer](https://developer.android.com/topic/architecture/domain-layer), [Modularisierungsmuster](https://developer.android.com/topic/modularization/patterns).

### 2.6 UI, Annahmen und Build-Reihenfolge

**Aktueller Inhalt und Ziel:** Die Bildschirmuebersicht ist brauchbar, aber die bisherige "Dark-only"-Annahme steht im Konflikt mit Material You und Barrierefreiheit. Die alte Build-Reihenfolge validiert zuerst einen Timer auf einer bald zu ersetzenden Wiedergabe-Engine; das erzeugt doppelte Arbeit.

**Verbindliche Verbesserung:** Material 3 mit System-Dark-/Light-Mode ist von Beginn an verpflichtend. Eigene Markenfarben duerfen Dynamic Color ergaenzen, aber Kontraste nicht reduzieren. Die UI muss Fensterbreiten adaptiv behandeln; Android empfiehlt Window Size Classes statt Geraetetyp-Abfragen. Quelle: [Window Size Classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes).

Die Reihenfolge wird umgestellt: zuerst das neue Grundgeruest, dann Datenmodell und Player mit Media3, danach der pure Timerkern, anschliessend Tracker und erst zuletzt die Kopplung. Es gibt keine MediaPlayer-Zwischenloesung.

## 3. Verbindliche Architekturentscheidungen

### 3.1 Technologieentscheidungen

| Bereich | Entscheidung | Begruendung |
|---|---|---|
| Sprache | Kotlin | Einheitliche, typsichere Android-Entwicklung und Coroutines/Flow. |
| UI | Jetpack Compose mit Material 3 | Deklarative UI, gute Test- und Accessibility-Unterstuetzung. |
| Wiedergabe | AndroidX Media3, `ExoPlayer` in `MediaLibraryService` | Offizieller aktueller Android-Mediastack; der Service ist fuer Hintergrundwiedergabe und eine durchsuchbare Medienbibliothek vorgesehen. Quellen: [Media3](https://developer.android.com/media/media3), [Background Playback](https://developer.android.com/media/media3/session/background-playback). |
| Persistenz | Room auf SQLite | Typsichere lokale Datenbank, Flow-Unterstuetzung und testbare Migrationen. Quelle: [Room-Migrationen](https://developer.android.com/training/data-storage/room/migrating-db-versions). |
| Nebenlaeufigkeit | Kotlin Coroutines und Flow | Einheitliches Modell fuer DB, Playerstatus und UI. |
| Navigation | Navigation Compose | Ein Stack pro App, typisierte Routenobjekte; keine Navigation in Repositories oder Use Cases. |
| Abhaengigkeitsverwaltung | Gradle Version Catalog `gradle/libs.versions.toml` | Jede Version ist zentral sichtbar und reproduzierbar. |
| DI | Hilt | Eine standardisierte, testbare Objektbereitstellung fuer Service, Datenbank, Repositories und ViewModels. Die App startet neu; die Bindung an ein unbekanntes Fork-DI-Muster hat keinen Nutzen. |
| Hintergrundarbeit | WorkManager nur fuer aufschiebbare, persistente Aufgaben wie Bibliotheks-Nachscan | WorkManager garantiert keinen exakten Zeitpunkt und ist daher nie der Timer. Quelle: [Task Scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent). |

`minSdk = 26` bleibt verbindlich. `compileSdk` und `targetSdk` muessen bei jedem Release der aktuell stabilen Android-SDK-Version entsprechen. Exakte Versionsnummern werden nicht in diesen Bauplan geschrieben, sondern vor Projektanlage in `libs.versions.toml` als stabile Versionen dokumentiert. Dadurch ist die Spezifikation nicht schon beim ersten Build veraltet.

### 3.2 Modulbaum und erlaubte Abhaengigkeiten

```text
:app
:core:common        (Result, Clock, Dispatcher, Fehlerklassen)
:core:model         (reine Domain-Modelle und Value Objects)
:core:database      (Room, DAO, Migrationen; keine UI)
:core:designsystem  (Theme, wiederverwendbare Compose-Komponenten)
:core:testing       (Fakes, Testdaten, Regeln)
:data:library       (MediaStore, Song- und Marker-Repository)
:data:playback      (Media3-Service, Controller, PlaybackRepository)
:data:timer         (Timer-Speicherung, Alarm-Adapter)
:data:workout       (Room-DAOs und Workout-Repository)
:domain:timer       (Timerzustandsmaschine, Triggerplanung)
:domain:workout     (Volumen, PRs, Routinen, Sessionlogik)
:feature:library
:feature:player
:feature:timer
:feature:workout
:feature:settings
:baselineprofile
```

Abhaengigkeitsregeln:

1. `:core:model` haengt von keinem anderen App-Modul ab.
2. `:domain:*` darf nur `:core:common` und `:core:model` sowie Repository-Interfaces kennen. Es darf kein Android-UI-, Room- oder ExoPlayer-Objekt importieren.
3. `:data:*` implementiert Repository-Interfaces aus dem jeweiligen Domain-Modul und darf `:core:database` verwenden.
4. `:feature:*` verwendet nur Domain-Use-Cases, UI-State und `:core:designsystem`; kein Feature importiert ein anderes Feature.
5. `:app` verdrahtet Navigation und Hilt. Es enthaelt keine Fachlogik.

Diese Regeln verhindern Kreisabhaengigkeiten. Ein Build muss fehlschlagen, wenn ein Feature direkt auf Room oder ExoPlayer zugreift.

### 3.3 Laufzeitkomponenten

```text
Compose Screen -> ViewModel -> Use Case -> Repository Interface
                                           |
                                           +-> Room / MediaStore / Media3 Adapter

MediaLibraryService <-> ExoPlayer <-> MediaSession
         ^
         +-- PlaybackRepository (einziger App-Zugang zur Wiedergabe)

TimerCoordinator -> PlaybackPositionProvider oder MonotonicClock
                 -> CueOutput (TTS, Haptik, Ton)
```

Es existiert genau eine `MediaLibraryService`-Instanz und genau ein `ExoPlayer`. Eine Activity, ein ViewModel oder ein Composable darf keinen eigenen Player erzeugen. Android empfiehlt fuer Hintergrundwiedergabe, Player und Media Session im zugehoerigen Service zu halten; mit `MediaLibraryService` kann spaeter eine browsebare lokale Bibliothek angeboten werden. Quelle: [Media3 Background Playback](https://developer.android.com/media/media3/session/background-playback).

## 4. Berechtigungen, Hintergrundverhalten und Datenschutz

| Berechtigung / API | Wann anfragen | Pflichtverhalten bei Ablehnung |
|---|---|---|
| `READ_MEDIA_AUDIO` ab API 33, auf aelteren APIs passende Lese-Berechtigung | Erst wenn die Person die Bibliothek oeffnet oder scannt | Bibliotheksansicht zeigt eine Erklaerung und einen Button zu den App-Einstellungen. Kein stiller leerer Screen. |
| `POST_NOTIFICATIONS` ab API 33 | Erst wenn Hintergrundabschluss oder Sessionbenachrichtigungen aktiviert werden | Wiedergabe funktioniert weiter; normaler Timer zeigt Abschluss nur sichtbar in der App. |
| `FOREGROUND_SERVICE` und `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Manifest, keine Runtime-Abfrage | Nur waehrend tatsaechlicher Musikwiedergabe verwenden. |

Die Media-Playback-Foreground-Service-Deklaration muss `foregroundServiceType="mediaPlayback"` tragen. Android 14+ verlangt passende FGS-Typen und Berechtigungen. Quelle: [FGS-Typen](https://developer.android.com/about/versions/14/changes/fgs-types-required). Der Service darf nicht als allgemeiner Workout- oder Timerdienst missbraucht werden.

Version 1 deklariert weder `USE_EXACT_ALARM` noch `SCHEDULE_EXACT_ALARM`. Ein Resttimer ohne laufende Musik ist in Version 1 nur bei sichtbarer App garantiert. Wird die App ohne aktive Medienwiedergabe in den Hintergrund gelegt, zeigt sie sofort den Status "Im Hintergrund nicht exakt"; sie darf keinen exakten Abschluss vortaeuschen. Ein spaeteres Feature fuer exakte Hintergrundtimer ist eine separate Produkt- und Play-Policy-Entscheidung. Die beiden exakten Alarmberechtigungen haben Plattform- und Play-Store-Einschraenkungen. Quellen: [Android Alarmplanung](https://developer.android.com/develop/background-work/services/alarms), [Play-Richtlinie](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en). WorkManager ist fuer diesen Zweck verboten, weil es bewusst nur irgendwann nach erfuellter Bedingung ausfuehrt.

## 5. Fachliche Regeln

### 5.1 Bibliothek und Songidentitaet

Ein Song wird im laufenden Betrieb ueber `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` gelesen. Der technische primaere Schluessel der lokalen Bibliothek ist `mediaStoreId: Long`; die Wiedergabe verwendet die daraus gebildete `contentUri`. Zusaetzlich werden `displayName`, `relativePath`, `durationMs`, `sizeBytes`, `dateModifiedSeconds`, Album, Artist und Titel gespeichert. Ein erneuter Scan aktualisiert vorhandene Zeilen ueber `mediaStoreId`.

Ein importierter Marker wird nie nur ueber einen absoluten Dateipfad zugeordnet. Das Importformat enthaelt Versionsnummer, relative Quelle, Dateiname, Groesse, Dauer und optional einen SHA-256-Hash, den ausschliesslich das externe Desktop-Analysewerkzeug erzeugt. Die Android-App berechnet in Version 1 keinen SHA-256-Hash und liest dafuer keine Audiodatei vollstaendig ein. Ein bereits gespeicherter Hash stammt daher immer aus einem frueheren Import oder aus einer vom Nutzer uebernommenen externen Zuordnung. Die Zuordnung erfolgt streng in dieser Reihenfolge:

1. Der im Import enthaltene SHA-256 stimmt mit einem bereits gespeicherten externen Hash ueberein. Gibt es noch keinen gespeicherten Hash, wird diese Stufe uebersprungen;
2. `relativePath + displayName + sizeBytes + durationMs` stimmen exakt;
3. `displayName + sizeBytes + durationMs` liefern genau einen Treffer;
4. sonst wird der Marker als **nicht zugeordnet** gespeichert und in Settings zur manuellen Zuordnung angezeigt. Bei manueller Zuordnung wird der Import-Hash lediglich gespeichert; die App berechnet keinen neuen Hash.

Bei mehreren Treffern darf die App nicht raten. Die Person waehlt den Song einmal; diese Zuordnung wird in `MarkerSongLink` gespeichert.

### 5.2 DropSync-Algorithmus

Vorbedingungen fuer den Start:

1. Der Song ist lokal lesbar.
2. Ein aktivierter Marker ist vorhanden.
3. `5_000 <= requestedDurationMs <= marker.positionMs`.
4. Keine andere Timerinstanz hat den Status `RUNNING`, `PREPARING` oder `PAUSED`.
5. Die Wiedergabequeue wird fuer die Dauer des Timers nicht durch Auto-Advance veraendert.

Ablauf:

1. Der Use Case berechnet `startPositionMs = marker.positionMs - requestedDurationMs`.
2. Der Playback-Service setzt ausschliesslich diesen Song als Queue, bereitet ihn vor, sucht auf `startPositionMs` und wartet auf den Status `READY`.
3. Erst wenn `player.isPlaying == true` ist, wechselt der Timer in `RUNNING`.
4. Fuer den Songmarker und jede vorbereitende Triggerposition plant der Service ein `PlayerMessage` auf dem passenden `MediaItem` und der passenden Medienposition. Die Ausloesung stammt damit aus der Media3-Wiedergabezeitlinie und nicht aus einem UI-Tick. Quelle: [Media3 Player-Ereignisse](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).
5. Der Timer berechnet fuer die Anzeige `remainingMs = max(0, marker.positionMs - player.currentPosition)`. Ein Updateintervall von 100 ms ist nur fuer die UI; `delay()` ist nie die Quelle eines Abschlussereignisses.
6. Die an der Position `marker.positionMs` ausgeloeste `PlayerMessage` schliesst den Timer genau einmal ab. Jeder Trigger besitzt eine Timer-Sitzungs-ID, damit veraltete Callbacks nach Abbruch oder Neustart keine Ausgabe oder Lautstaerkeaenderung mehr verursachen.

Die App zeigt im UI den Hinweis: "Synchron mit der Player-Zeitlinie; Bluetooth- und Lautsprecherlatenz koennen die hoerbare Ausgabe verschieben." Sie darf nie eine garantierte Millisekundengenauigkeit der Luftschallausgabe behaupten.

Bei Nutzer-Pause oder Audio-Focus-Verlust, der die Wiedergabe pausiert, pausiert der DropSync-Timer. Bei Nutzer-Seek, Queue-Wechsel, Songende vor Marker, Player-Fehler oder Service-Neustart wird der Timer abgebrochen und eine erklaerte Fehlermeldung angezeigt. Ein Resume startet nicht automatisch; die Person startet einen neuen, konsistenten Timer.

### 5.3 Normaler und Resttimer

Der normale Timer besitzt dieselbe Zustandsmaschine, verwendet aber `SystemClock.elapsedRealtime()` als monotone Zeitquelle. Sein Ende ist `startedElapsedRealtimeMs + durationMs`. Systemzeit-Aenderungen duerfen ihn nicht beeinflussen.

Die Triggerliste ist fix: 180, 120, 60, 30, 10 bis 1 und 0 Sekunden. Nur Grenzwerte kleiner als die Startdauer werden geplant. Jede Ausgabe besitzt eine stabile Kennung `timerId:thresholdMs`; sie wird in `deliveredCues` gehalten, damit Pause, Recomposition oder Service-Neustart nicht doppelt ansagen. Fuer den normalen Timer und den Resttimer werden alle relevanten Grenzwerte gesprochen. Fuer DropSync gelten dieselben Zeitgrenzen, aber ein anderes Ausgabeprofil: 180/120/60/30 Sekunden duerfen gesprochen werden; 10 bis 1 Sekunden sind standardmaessig nur Haptik und visuelle Anzeige; bei 0 Sekunden werden Haptik und Abschluss-Signalton ausgeloest. Dadurch wird die Musik nicht zehnmal hintereinander fuer Sprache geduckt.

TTS verwendet `AudioAttributes` mit `USAGE_ASSISTANCE_SONIFICATION` und `CONTENT_TYPE_SPEECH`; `VOICE_COMMUNICATION` wird nicht verwendet. `USAGE_ASSISTANCE_SONIFICATION` ist fuer UI-Sounds und akustische Hilfen definiert. Quelle: [AudioAttributes](https://developer.android.com/reference/android/media/AudioAttributes.html). Ist TTS nicht initialisiert, nicht verfuegbar oder die Sprache nicht installiert, funktioniert der Timer mit Haptik und optionalem kurzen Signalton weiter; die App zeigt einmalig eine nicht blockierende Meldung.

Beim Ducking veraendert DropSync nur den eigenen Player. Es speichert unmittelbar vor der Ausgabe `baseVolume`, setzt `effectiveVolume = baseVolume * duckingFactor` und stellt nach der Ausgabe genau den zuletzt bekannten `baseVolume` wieder her. Veraendert die Person waehrend einer Ansage die Lautstaerke, wird der neue Wert als `baseVolume` uebernommen. Fuer andere Audio-Apps wird trotzdem reguler Audio Focus beantragt; Android beschreibt Audio Focus als Mechanismus gegen parallele laute Wiedergabe. Quelle: [Audio Focus](https://developer.android.com/media/optimize/audio-focus).

### 5.4 Trainingsmathematik und PRs

Ein Arbeitsset qualifiziert fuer Volumen und PRs, wenn `setRole` gleich `WORKING` oder `FAILURE` ist, `isCompleted = true`, `exercise.kind = STRENGTH` und mindestens ein Segment `reps > 0` sowie `externalLoadMilliKg >= 0` hat. WARMUP qualifiziert nie. Zeit- und Distanzsaetze werden separat gespeichert und erhalten in Version 1 keine Gewichts-PRs.

Volumen eines Segments lautet:

```text
volumeMilliKg = externalLoadMilliKgPerImplement * loadMultiplier * reps
```

`loadMultiplier` ist kein berechnetes UI-Hilfsfeld, sondern ein gespeicherter, expliziter Wert. Beispiele: Langhantel = 1, zwei gleich schwere Kurzhanteln = 2, einarmige Uebung mit 20 kg = 1. Ein Satzcluster summiert die Segmentvolumina, zaehlt fuer Satzanzahl aber genau einmal. Koerpergewicht und zusaetzliche Belastung werden in Version 1 nicht kombiniert; bei Bodyweight-Uebungen wird nur `externalLoadMilliKgPerImplement = 0` gespeichert und kein fiktives Koerpergewichtsvolumen erzeugt. Alle Gewichte werden als ganze Millikilogramm gespeichert und erst fuer die Anzeige durch 1.000 geteilt; damit sind Vergleiche und Volumenrechnungen frei von Gleitkommafehlern.

PR-Regeln sind exakt:

| PR | Vergleichsmenge | Gewinnbedingung |
|---|---|---|
| Hoechste Last | abgeschlossene, qualifizierte Einzel- oder Clustersegmente derselben Uebung | `externalLoadMilliKgPerImplement * loadMultiplier` ist strikt groesser als bisheriger Maximalwert. Bei Gleichstand keine neue PR. |
| Hoechstes Session-Volumen | Summe aller qualifizierten Segmentvolumina derselben Uebung innerhalb einer Session | aktuelle Summe ist strikt groesser als jede abgeschlossene fruehere Session. |
| Meiste Wiederholungen bei gleicher Last | qualifizierte Segmente derselben Uebung mit identischer effektiver Last | Wiederholungen sind strikt groesser als bisher bei dieser Last. |

Geschaetztes 1RM ist nur ein Trendwert. Er wird fuer Satzsegmente mit 1 bis 10 Wiederholungen und positiver Last als `loadKg * (1 + reps / 30)` berechnet, mit Formelversion gespeichert und nie mit dem Label "PR" angezeigt.

## 6. Datenmodell

Alle Zeitpunkte werden als UTC-Epoch-Millis gespeichert. Jede Trainingssession speichert zusaetzlich `zoneIdAtStart`, damit Kalenderansichten auch nach einer Zeitzonenaenderung nachvollziehbar bleiben. Alle Gewichte werden als ganze Millikilogramm gespeichert; Umrechnung aus lb ist ein UI-Eingabeformat und wird vor Speicherung kaufmaennisch auf Millikilogramm gerundet.

```text
Song
  mediaStoreId PK, contentUri, displayName, relativePath, durationMs,
  sizeBytes, dateModifiedSeconds, title, artist, album, isAvailable

SongMarker
  id PK, sourceFingerprint, label, positionMs,
  source {IMPORT, MANUAL}, isEnabled, createdAtEpochMs

MarkerSongLink
  id PK, markerId FK unique, songId FK, linkMethod {HASH, METADATA, MANUAL},
  linkedAtEpochMs

TimerPreset
  id PK, name unique, durationMs, duckingPercent {0,50,100},
  ttsEnabled, hapticsEnabled, completionToneEnabled

WorkoutSession
  id PK, startedAtEpochMs, endedAtEpochMs nullable, zoneIdAtStart,
  status {ACTIVE, COMPLETED, DISCARDED}, title nullable, notes nullable

Exercise
  id PK, canonicalName unique, kind {STRENGTH,TIME,DISTANCE},
  equipment, isCustom, isArchived

ExerciseMuscle
  exerciseId FK, muscleGroupId FK, contributionPercent 1..100

Routine
  id PK, name unique, isArchived, createdAtEpochMs, updatedAtEpochMs

RoutineExercise
  id PK, routineId FK, exerciseId FK, orderIndex, supersetGroupId nullable,
  targetSets nullable, targetRepsMin nullable, targetRepsMax nullable, restSeconds nullable

SessionExercise
  id PK, sessionId FK, exerciseId FK, orderIndex, supersetGroupId nullable

SetCluster
  id PK, sessionExerciseId FK, orderIndex, setRole {WARMUP,WORKING,FAILURE,TIME,DISTANCE},
  isCompleted, note nullable, completedAtEpochMs nullable

SetSegment
  id PK, clusterId FK, segmentIndex, externalLoadMilliKgPerImplement nullable,
  loadMultiplier {1,2}, reps nullable, durationMs nullable, distanceM nullable

PersonalRecord
  id PK, exerciseId FK, type, achievedSessionId FK, achievedClusterId nullable,
  valueLong, valueUnit {MILLI_KG, REPS}, comparableLoadMilliKg nullable, achievedAtEpochMs

PlaybackSnapshot
  id PK, sessionId FK, songId FK, markerId FK nullable, positionMs,
  capturedAtEpochMs
```

Room nutzt Foreign Keys mit passenden Indizes. `MarkerSongLink` ist die einzige Quelle fuer die Zuordnung eines Markers zu einem Song; bei einem nicht zugeordneten Marker existiert einfach noch keine Linkzeile. Dadurch bleiben Importmetadaten und eine spaetere manuelle Zuordnung getrennt. `PlaybackSnapshot` ist eine eigene, append-only Historientabelle fuer die optionale Musikverknuepfung einer Trainingssession. Es gibt keine JSON-Liste in Room-Entities und keine Enum-Ordinals in der Datenbank; Enums werden als stabile Strings gespeichert. Jede Datenbankschemaversion wird exportiert und mit Room-Testmigrationen geprueft. Quelle: [Room-Migrationen testen](https://developer.android.com/training/data-storage/room/migrating-db-versions).

### 6.1 Importformat fuer Songmarker

```json
{
  "schemaVersion": 1,
  "generatedBy": "dropsync-drop-analyzer",
  "tracks": [
    {
      "relativePath": "Music/Training",
      "displayName": "example.mp3",
      "sizeBytes": 8421137,
      "durationMs": 215000,
      "sha256": "optional-lowercase-hex-string",
      "markers": [
        { "label": "Drop 1", "positionMs": 134500 }
      ]
    }
  ]
}
```

Der Import erfolgt ueber den Android Storage Access Framework Dateiwaehler. Die Datei ist auf 5 MB begrenzt. Der Parser lehnt unbekannte `schemaVersion`, negative Zeiten, Marker ausserhalb der Songdauer, leere Namen, doppelte Markerpositionen pro Track und ungueltige Hashes ab. Ein Import ist transaktional: Bei einem ungueltigen Dokument wird keine teilweise Datenmenge gespeichert. Der Abschlussbericht nennt hinzugefuegte, aktualisierte, nicht zugeordnete und abgelehnte Eintraege.

## 7. Verbindliche Umsetzungsschritte

Die Schritte sind in der angegebenen Reihenfolge auszufuehren. Ein Schritt beginnt erst, wenn seine Abnahmekriterien erfuellt sind. "Fast fertig" oder manuell nur einmal getestet gilt nicht als abgeschlossen.

### Schritt 1: Neues Projekt und Lieferkette anlegen

**Ziel**

Ein leeres, reproduzierbar bauendes Android-Projekt erstellen, das keinerlei Quellcode aus den genannten Referenz-Repositories enthaelt.

**Hintergrund**

Die bisherige Fork-Strategie koppelt das Vorhaben an fremde Architektur und Copyleft. Da kein produktiver DropSync-Code existiert, ist ein neues Projekt schneller kontrollierbar und rechtlich einfacher. Dieser Schritt ist Voraussetzung fuer alle folgenden Schritte.

**Technische Anforderungen**

1. Erstelle ein Gradle-Kotlin-DSL-Projekt mit Paketnamen `com.dropsync.app`.
2. Setze `minSdk = 26`; `compileSdk` und `targetSdk` muessen der aktuell stabilen SDK-Version entsprechen.
3. Lege `gradle/libs.versions.toml` an. Jede Plugin- und Bibliotheksversion steht ausschliesslich dort; Moduldateien referenzieren nur Aliase.
4. Aktiviere Kotlin-Formatierung und statische Analyse in CI. Der konkrete Linter darf erst nach dokumentierter Lizenzpruefung aufgenommen werden.
5. Lege `THIRD_PARTY_NOTICES.md` mit den Spalten Name, Version, Lizenz, Zweck, Quelle und Freigabestatus an.
6. Lege `docs/adr/` an. Jede Abweichung von diesem Bauplan erfordert eine ADR mit Problem, Optionen, Entscheidung, Folgen und Datum.
7. Erstelle eine Debug- und eine Release-Variante. Release verwendet R8-Minifizierung; geheime Werte oder API-Schluessel existieren nicht, weil Version 1 keine externen Dienste hat.

**Abnahmekriterien**

- Ein frischer Checkout baut Debug und Release ohne manuelle Dateiuebertragung.
- `THIRD_PARTY_NOTICES.md` enthaelt mindestens die anfangs verwendeten AndroidX-Bibliotheken.
- Eine Suche nach Referenzprojekt-Paketnamen oder kopierten Headern liefert keinen Produktcode.

### Schritt 2: Modulgrenzen, DI und Fehlervertrag etablieren

**Ziel**

Den Modulbaum aus Abschnitt 3 implementieren, bevor Features entstehen.

**Hintergrund**

Ein Player, Timer und Tracker teilen nur wenige Modelle, haben aber unterschiedliche Lebenszyklen. Fruehe Modulgrenzen verhindern, dass UI und Infrastruktur untrennbar werden. Hilt erzeugt die wenigen langlebigen Objekte einheitlich und testbar.

**Technische Anforderungen**

1. Erstelle alle in Abschnitt 3.2 genannten Module, zunaechst mit minimalen Build-Dateien und ohne Platzhalter-Fachcode.
2. Definiere in `:core:common` einen geschlossenen Fehlervertrag: `AppError.PermissionDenied`, `MediaUnavailable`, `MarkerUnmatched`, `TimerConflict`, `TtsUnavailable`, `DatabaseFailure` und `Unknown`.
3. Repository-Methoden geben entweder einen typisierten Erfolg oder einen `AppError` zurueck; sie werfen keine Infrastrukturfehler bis in ein Composable durch.
4. Hilt-Singletons sind nur Datenbank, MediaStore-Client, Playback-Controller-Connector, `Clock` und `CoroutineDispatcher`-Provider. ViewModels werden nicht als Singleton gebunden.
5. `Clock` ist eine Interface-Abstraktion mit `elapsedRealtimeMs()` und `epochMillis()`. Tests nutzen eine kontrollierbare Fake-Clock.
6. Richte einen Modulabhaengigkeitstest ein, der die Regeln aus Abschnitt 3.2 prueft.

**Abnahmekriterien**

- Kein `feature:*` importiert Room-, Media3- oder Android-DAO-Typen.
- Alle Module bauen einzeln mit ihren erlaubten Abhaengigkeiten.
- Ein Test beweist, dass eine Fake-Clock den Ablauf eines Use Cases ohne Echtzeit steuert.

### Schritt 3: Datenbank und Migrationstrategie implementieren

**Ziel**

Die Datenbasis aus Abschnitt 6 als Room-Schema mit stabilen Beziehungen, Indizes und getesteten Migrationen bereitstellen.

**Hintergrund**

Trainingsdaten sind die wichtigsten Nutzerdaten. Ein nachtraeglich unscharfes Datenmodell erzeugt Datenverluste, unmoegliche Statistiken und teure Migrationen. Room verlangt bei Schemaaenderungen explizite Migrationen, wenn Daten erhalten bleiben sollen.

**Technische Anforderungen**

1. Implementiere die Tabellen exakt aus Abschnitt 6 sowie notwendige Lookup-Tabellen fuer `MuscleGroup` und `SetRole` als stabile Stringwerte.
2. Verwende `Long` fuer IDs, Zeitwerte, Gewichte in Millikilogramm und Volumen in Millikilogramm. `Double` und `BigDecimal` sind in Entities, PR-Vergleichen und SQL-Abfragen verboten.
3. Fuege Indizes auf allen Foreign Keys, `WorkoutSession.startedAtEpochMs`, `SetCluster.sessionExerciseId + orderIndex`, `SetSegment.clusterId + segmentIndex` und `SongMarker.songId` hinzu.
4. Jede Schreiboperation zum Abschluss eines Satzes laeuft in einer DB-Transaktion: Segmente, Clusterstatus, Sessionstatistik und neue PRs werden zusammen gespeichert oder gar nicht.
5. Exportiere Room-Schemas in das Repository. Jede neue Version erhaelt eine Migration; destruktive Migration ist in Release verboten.
6. Seed-Daten fuer die Standarduebungsbibliothek werden versionskontrolliert als JSON geliefert und idempotent eingespielt. Benutzerdaten werden nie beim Seed ueberschrieben.

**Abnahmekriterien**

- Migrationstest von jeder vorhandenen Schema-Version auf die aktuelle Version besteht.
- Ein abgebrochener Schreibvorgang hinterlaesst weder halbe Segmente noch eine halbe PR.
- Ein Drop-Set mit drei Segmenten hat einen Cluster und drei Segmente, nicht drei Arbeitssets.

### Schritt 4: Lokale Medienbibliothek implementieren

**Ziel**

Eine schnelle, berechtigungsbewusste, lokale Songbibliothek aus MediaStore bereitstellen.

**Hintergrund**

Dateipfade sind unter Scoped Storage kein verlaesslicher Appvertrag. MediaStore bietet dafuer den Android-Medienindex. Ein Vollscan bei jedem Start verschwendet Akku und verzoegert die UI.

**Technische Anforderungen**

1. Frage `READ_MEDIA_AUDIO` erst beim ersten Bibliothekszugriff an. Bei API 26 bis 32 verwende die passende Legacy-Berechtigung mit begrenzter SDK-Reichweite.
2. Query nur Audiodateien mit positiver Dauer und lesbarer Content-URI. Die App darf weder `MANAGE_EXTERNAL_STORAGE` noch breiten Dateisystemzugriff anfordern.
3. Persistiere den letzten bekannten MediaStore-Aenderungsstand. Ist er unveraendert, wird kein Vollscan gestartet; bei Aenderung wird nur dann ein erneuter Abgleich durchgefuehrt. Android beschreibt diesen Versionsabgleich fuer Medienkollektionen. Quelle: [Shared Media](https://developer.android.com/training/data-storage/shared/media).
4. Ein Song mit nicht mehr oeffenbarer URI bleibt als `isAvailable = false` erhalten, damit verknuepfte Historie und Marker nicht verloren gehen. Die Wiedergabe dieses Songs ist deaktiviert.
5. Suche, Alben, Artists und Ordner basieren auf Datenbankprojektionen des Scans. Die erste Version implementiert keine Tagbearbeitung.
6. Der Library-Scan ist abbrechbar. UI zeigt Ladefortschritt, Teilfehler und einen Retry an; sie blockiert nie den Hauptthread.

**Abnahmekriterien**

- Berechtigungsablehnung fuehrt zu einer erlaeuterten, bedienbaren Fehleransicht.
- Ein wiederholter Start ohne Medienaenderung erzeugt keinen Vollscan.
- Nach Loeschen einer Datei bleiben Workout- und Markerhistorie konsistent; der Song ist nur nicht abspielbar.

### Schritt 5: Media3-Playback-Service implementieren

**Ziel**

Lokale Wiedergabe mit Queue, Shuffle, Repeat, Sperrbildschirm und Bluetooth-Steuerung ueber genau eine Media3-Instanz umsetzen.

**Hintergrund**

Player und `MediaSession` muessen fuer echte Hintergrundwiedergabe im selben `MediaSessionService` oder `MediaLibraryService` leben. Die offizielle Media3-Dokumentation zeigt diesen Lebenszyklus und die erforderlichen Manifestangaben. Quelle: [Background Playback](https://developer.android.com/media/media3/session/background-playback).

**Technische Anforderungen**

1. Implementiere `PlaybackService` als `MediaLibraryService`; er erzeugt in `onCreate` einen `ExoPlayer` und eine `MediaSession`, und gibt beides in `onDestroy` frei.
2. Das Manifest deklariert nur die benoetigten Media-Playback-Foreground-Service-Berechtigungen und `foregroundServiceType="mediaPlayback"`.
3. Alle Screens verbinden sich ueber einen `MediaController` beziehungsweise `PlaybackRepository`. Keines der Features darf `ExoPlayer.Builder` aufrufen.
4. Verwende `MediaItem` mit `contentUri`, `mediaStoreId` als `mediaId` und vollstaendigen lokalen Metadaten fuer korrekte Systembenachrichtigungen.
5. Queue, Shuffle, Repeat, zuletzt gespielter Song und Position werden nach jeder relevanten Aenderung lokal gespeichert. Wiedergabe-Resume ist erst nach separater Implementierung und Test des Media3-Callbacks aktiv.
6. Media3 verwaltet fuer Musik Audio Focus. Eigene doppelte Fokusverwaltung ist verboten. Bei dauerhaftem Fokusverlust pausiert die Wiedergabe und der DropSync-Zustand reagiert gemaess Abschnitt 5.2.
7. Akzeptiere nur vertrauenswuerdige externe Controller, soweit dies die Systembenachrichtigung nicht beeintraechtigt. Die UI darf keine gefaehrlichen Custom Commands aus externen Apps annehmen.

**Abnahmekriterien**

- App im Hintergrund, Sperrbildschirm und Bluetooth-Pause steuern dieselbe Wiedergabeinstanz.
- Beim Beenden des Services werden Player und Session genau einmal freigegeben.
- Eine Android-Medienbenachrichtigung zeigt lokal gespeicherten Titel und Artist.

### Schritt 6: Songmarker-Import und Zuordnung implementieren

**Ziel**

Extern analysierte Marker sicher importieren und eindeutig mit lokalen Songs verbinden.

**Hintergrund**

Das alte `drops.json` auf Basis absoluter Pfade scheitert bei Geraetewechsel, Verschieben und Scoped Storage. Ein nicht erkannter Song darf nicht zufaellig einem gleichnamigen Titel zugeordnet werden.

**Technische Anforderungen**

1. Implementiere das JSON-Schema aus Abschnitt 6.1 exakt. Die Versionspruefung erfolgt vor jeder Fachpruefung.
2. Parse ueber einen Streaming-Parser, nicht durch vollstaendiges Laden eines beliebig grossen Dokuments.
3. Fuehre die vierstufige Zuordnung aus Abschnitt 5.1 aus. Automatische Zuordnung ist nur bei eindeutigem Ergebnis erlaubt.
4. Jede Importzeile wird mit Quelle, Importzeit, Zuordnungsstatus und Fehlermeldung protokolliert.
5. Ein erneuter Import mit derselben fachlichen Track-/Markeridentitaet aktualisiert den vorhandenen Marker statt Duplikate anzulegen.
6. Eine manuelle Zuordnung zeigt Dateiname, Dauer, Groesse und Markerposition. Die Auswahl muss bestaetigt werden.

**Abnahmekriterien**

- Ein ungueltiges Dokument veraendert keine Tabelle.
- Zwei gleichnamige Songs mit unterschiedlicher Groesse werden nicht falsch zugeordnet.
- Ein Song kann mindestens zwei aktive Marker mit unterscheidbaren Labels enthalten.

### Schritt 7: Timerkern als zustandsbasierte Domainlogik implementieren

**Ziel**

Eine testbare gemeinsame Timerzustandsmaschine fuer normalen Timer, Resttimer und DropSync bereitstellen.

**Hintergrund**

Timerlogik in Composables oder Services ist nicht deterministisch testbar und fuehrt bei Rotation, Recomposition oder Prozesswechseln leicht zu doppelten Ausgaben. Ein gemeinsamer Kern verhindert drei leicht unterschiedliche Timer.

**Technische Anforderungen**

1. Definiere die einzigen erlaubten Zustaende: `IDLE`, `PREPARING`, `RUNNING`, `PAUSED`, `COMPLETED`, `CANCELLED`, `FAILED`.
2. Definiere erlaubte Uebergaenge: `IDLE -> PREPARING`, `PREPARING -> RUNNING|FAILED|CANCELLED`, `RUNNING -> PAUSED|COMPLETED|CANCELLED|FAILED`, `PAUSED -> RUNNING|CANCELLED`, Endzustaende nur nach `reset` zu `IDLE`.
3. `TimerSession` besitzt eine zufaellige UUID, Modus, Startdauer, Startzeit beziehungsweise Markerbezug, Triggerkonfiguration und `deliveredCueIds`.
4. Normaler Timer verwendet `elapsedRealtime`, niemals `currentTimeMillis`. DropSync erhaelt seine Ereignisse ausschliesslich aus `PlayerMessage`; der sichtbare Countdown ist eine Projektion der Playerposition.
5. Jede `PlayerMessage` prueft vor Ausgabe die aktuelle Timer-Sitzungs-ID und den Zustand `RUNNING`.
6. `CueOutput` ist eine Schnittstelle mit getrennten Implementierungen fuer TTS, Haptik, Signalton und No-Op-Testfake. Es darf keine TTS- oder Vibrator-API im Use Case geben.
7. Bei Abbruch werden alle noch geplanten `PlayerMessage`-Objekte verworfen oder entwertet, TTS wird gestoppt und ein aktives Ducking wird sofort rueckgaengig gemacht.

**Abnahmekriterien**

- Zustandsuebergangstests decken jeden erlaubten und verbotenen Uebergang ab.
- Ein Test mit verzogener UI-Zeit zeigt, dass der normale Timer korrekt bleibt.
- Nach `cancel` kann kein alter Callback eine Haptik, Ansage oder Lautstaerkeaenderung ausloesen.

### Schritt 8: Audio-Cues, Ducking und Haptik implementieren

**Ziel**

Timerhinweise reproduzierbar, fehlertolerant und ohne Beschaedigung der Musiklautstaerke ausgeben.

**Hintergrund**

TTS ist asynchron und von installierten Stimmen abhaengig. Haptik ist geraeteabhaengig. Ohne Sitzungsbindung kann ein spaeter Callback eine neue Wiedergabe falsch entducken.

**Technische Anforderungen**

1. Initialisiere TTS vor einer Timer-Session und pruefe Sprache sowie Daten mit den vorgesehenen TTS-APIs. Fehlschlag setzt `ttsAvailable = false`, nicht den Timer auf `FAILED`.
2. Setze TTS-Attribute auf `CONTENT_TYPE_SPEECH` und `USAGE_ASSISTANCE_SONIFICATION`. Verwalte Antworten ueber `UtteranceProgressListener`.
3. Fuer jede Ansage wird `cueSessionId` mitgegeben. Ein Callback darf nur handeln, wenn sie der aktiven Timer-Sitzungs-ID entspricht.
4. Duckingwerte sind nur 0, 50 oder 100 Prozent. 100 Prozent bedeutet Stille der App-Musik waehrend der Cue-Ausgabe. Unbekannte Werte werden beim Speichern abgelehnt.
5. Der Haptikadapter prueft vor Ausgabe die Faehigkeit des Geraets und nutzt eine kurze, vordefinierte Haptik; bei fehlender Faehigkeit ist er ein No-Op ohne Fehlermeldungsflut. Quelle: [Haptic Feedback](https://developer.android.com/develop/ui/views/haptics/haptic-feedback).
6. Der Drop selbst erhaelt primaer visuellen Status und Haptik. TTS wird nur fuer Vorwarnungen genutzt, da Sprachsynthese keine harte Audiolatenzgarantie geben kann.

**Abnahmekriterien**

- Fehlende TTS-Stimme erlaubt eine vollstaendige Session mit Haptik und UI-Countdown.
- Eine alte TTS-Antwort veraendert die Lautstaerke einer neuen Session nicht.
- Ducking wird nach Cancel, Fehler und Completion auf die vorherige Basislautstaerke zurueckgesetzt.

### Schritt 9: Uebungsbibliothek, Routinen und Session-Log implementieren

**Ziel**

Eine schnelle Trainingsdokumentation schaffen, die echte Gym-Ablaufe einschliesslich Drop-Sets und Supersets abbildet.

**Hintergrund**

Der Moment zwischen Saetzen darf nicht durch komplexe Formulare unterbrochen werden. Gleichzeitig muss das Datenmodell die fachlichen Unterschiede zwischen Satzsegment, Arbeitsset und Superset bewahren.

**Technische Anforderungen**

1. Liefere eine kuratierte, versionierte Standarduebungsbibliothek; alle Standarduebungen sind unveraenderbar, aber archiviert darstellbar. Eigene Uebungen sind editierbar.
2. Eine Uebung hat einen Typ `STRENGTH`, `TIME` oder `DISTANCE`. Ein Screen darf nur zum Typ passende Felder anbieten.
3. Beim Anlegen eines neuen Clusters werden die Werte des letzten abgeschlossenen Clusters derselben Uebung aus der letzten Session vorausgefuellt. Die Person kann jeden Wert vor Abschluss aendern.
4. Ein Drop-Set erzeugt einen Cluster mit mehreren Segmenten; ein normaler Satz erzeugt einen Cluster mit genau einem Segment.
5. Superset-/Triset-Zugehoerigkeit steht an `SessionExercise` und `RoutineExercise`, nie an einzelnen Saetzen. Eine Gruppe hat exakt zwei oder drei unterschiedliche Sessionuebungen.
6. Routinen enthalten nur Reihenfolge, Zielwerte und optionale Pausenempfehlung. Sie enthalten keine historischen Gewichte oder echten Session-IDs.
7. Eine aktive Session kann nach App-Neustart fortgesetzt, abgeschlossen oder verworfen werden. Verwerfen setzt `status = DISCARDED`; es loescht keine Daten ohne ausdruecklichen, separat bestaetigten Userbefehl.

**Abnahmekriterien**

- Ein Triset mit jeweils zwei Saetzen wird in einer reproduzierbaren Reihenfolge angezeigt und gespeichert.
- Ein Drop-Set wird als ein Arbeitsset, aber mit allen Segmenten sichtbar dargestellt.
- Eine Session ueberlebt Prozessende und zeigt die zuletzt unvollstaendige Eingabe klar als offen an.

### Schritt 10: Volumen, PRs und Verlauf implementieren

**Ziel**

Statistiken erzeugen, die mathematisch nachpruefbar und in ihrer Aussage nicht uebertrieben sind.

**Hintergrund**

Unklare PRs und implizite Volumenformeln fuehren zu falschen Fortschrittsanzeigen. Besonders bei Kurzhanteln und Drop-Sets muss die Formel als Datenregel und nicht als nachtraegliche UI-Schaetzung existieren.

**Technische Anforderungen**

1. Implementiere die Formeln und PR-Regeln aus Abschnitt 5.4 wortgleich in `:domain:workout`.
2. Werte werden mit einer expliziten Vergleichspraezision gespeichert. Gewichte und Volumen liegen bereits als ganze Millikilogramm vor; die UI-Eingabe rundet vor Speicherung mit `HALF_UP` auf diesen Wert.
3. Eine PR wird innerhalb derselben Transaktion wie der abgeschlossene Satzcluster bestimmt und gespeichert.
4. Bei Korrektur oder Loeschen eines abgeschlossenen Clusters wird die betroffene Uebung vollstaendig neu berechnet. Eine inkrementelle Ruecknahme darf nicht angenommen werden, solange sie nicht per Test bewiesen ist.
5. Verlaufscharts zeigen pro Uebung getrennt: effektive Last, Sessionvolumen und 1RM-Trend. 1RM ist mit einem Infohinweis als Schaetzung markiert.
6. Kalender/Heatmap basiert auf abgeschlossenen Sessions und deren `zoneIdAtStart`; `DISCARDED` wird nie gezaehlt.

**Abnahmekriterien**

- 30 kg pro Hand, 10 Wiederholungen, `loadMultiplier=2` ergeben 600 kg Segmentvolumen.
- Ein dreiteiliges Drop-Set zaehlt als ein Arbeitsset, sein Segmentvolumen wird aber komplett summiert.
- Gleichstand erzeugt keine neue PR; nach Datenkorrektur werden falsche alte PRs entfernt.

### Schritt 11: Musik- und Workout-Integration implementieren

**Ziel**

Training und Musik verbinden, ohne dass die eine Domane die Daten oder Lebenszyklen der anderen beschaedigt.

**Hintergrund**

Die Integration ist der Produktunterschied, aber auch der Bereich mit den meisten Nebenwirkungen. Sie beginnt erst nach isoliert getesteten Player-, Timer- und Workoutmodulen.

**Technische Anforderungen**

1. Die Trainingssession speichert in Version 1 nur optionale `PlaybackSnapshot`-Ereignisse: Song-ID, Marker-ID nullable, Playerposition, Zeitstempel. Sie speichert keine dauerhafte Queuekopie.
2. "Rest bis zum naechsten Drop" ist nur aktivierbar, wenn genau ein aktueller Song, mindestens ein zukuenftiger aktiver Marker und eine laufende Playback-Session vorliegen.
3. Die UI zeigt vor Start die effektive Dauer `markerPosition - currentPlayerPosition` und nicht einen frei editierbaren Wert. Der User bestaetigt diese Dauer.
4. Ein Songwechsel, Seek, Pause oder Playerfehler beendet diesen Modus mit einem klaren Ergebnis; der Trainingssatz selbst bleibt unveraendert.
5. Es gibt keine automatische Satzbestaetigung beim Drop. Der Drop ist nur ein Cue, weil ein Satz tatsaechlich vom Nutzer beendet werden muss.

**Abnahmekriterien**

- Ein Songwechsel waehrend Drop-Rest abbricht nur den Timer, nicht die Workout-Session.
- Ein abgeschlossener Satz kann optional den damals laufenden Song referenzieren.
- Ohne zukuenftigen Marker ist der Drop-Rest-Button deaktiviert und erklaert warum.

### Schritt 12: Bedienoberflaeche und Barrierefreiheit implementieren

**Ziel**

Eine konzentrierte, auf kleinen und grossen Android-Fenstern bedienbare UI schaffen.

**Hintergrund**

Die App wird im Training unter Zeitdruck genutzt. Informationen, die fuer den aktuellen Satz wichtig sind, muessen ohne visuelle Ueberladung erreichbar sein. Compose stellt Semantik bereit, aber Custom Controls brauchen absichtliche Beschreibungen und Tests. Quellen: [Compose Accessibility](https://developer.android.com/develop/ui/compose/accessibility), [Compose Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

**Technische Anforderungen**

1. Verwende Material 3 mit System-Light- und System-Dark-Theme. Jede Farbe erhaelt getestete Kontrastvarianten; keine Funktion wird nur durch Farbe kommuniziert.
2. Die Hauptnavigation hat drei Ziele: Musik, Training, Einstellungen. Der aktive Mini-Player bleibt als klar beschriebene, bedienbare Komponente sichtbar.
3. Kernflows sind verbindlich: Berechtigung -> Bibliothek -> Play; Training -> Routine oder freie Session -> Satz abschliessen; Timer -> Modus -> Start -> Pause/Abbruch; Settings -> Markerimport -> Ergebnis.
4. Jeder Icon-Button hat eine lokalisierte Inhaltsbeschreibung. Timerstatus verwendet `stateDescription`; der Countdown liefert bei sekundenweisen UI-Aenderungen keine ununterbrochenen TalkBack-Ansagen.
5. Verwende grosse Touch-Ziele und keine zeitkritischen Doppeltipp-Gesten. Satzabschluss hat immer eine klar sichtbare Rueckgaengig-Aktion fuer die letzten 10 Sekunden.
6. Passe die Shell anhand von Window Size Classes an: kompakt mit Bottom Navigation, ab Medium mit Navigation Rail beziehungsweise Two-Pane-Listen/Details. Keine Geraetemodellabfragen. Quelle: [Window Size Classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes).
7. Alle Texte werden ueber String-Ressourcen lokalisiert; Version 1 liefert Deutsch und Englisch. Zahlen, Gewicht und Datum folgen Locale-Formaten, waehrend Daten intern normiert bleiben.

**Abnahmekriterien**

- Die vier Kernflows sind komplett mit TalkBack bedienbar.
- Bei 200 Prozent Schriftgroesse bleiben Satzfelder und Timeraktionen erreichbar.
- Kompakt-, Medium- und Expanded-Layout werden in UI-Tests und manuell geprueft.

### Schritt 13: Teststrategie, Performance und Release-Gates etablieren

**Ziel**

Vor einer Veroeffentlichung die wichtigsten Fehlerklassen automatisiert und auf realen Geraeten nachweisen.

**Hintergrund**

Timer, Datenverlust und Hintergrundaudio fallen oft erst ausserhalb eines normalen Emulatorlaufs auf. UI-Tests, Datenbanktests und Geraetetests haben verschiedene Aufgaben. Compose stellt Semantik- und Test-APIs bereit. Quelle: [Compose-Tests](https://developer.android.com/develop/ui/compose/testing).

**Technische Anforderungen**

1. Unit-Tests: Timerzustandsmaschine, Triggerplanung, Importvalidierung, Zuordnung, Volumen, PRs, Rundung, Routineexpansion und Fehlerabbildung.
2. Datenbanktests: jede Migration, Foreign-Key-Verhalten, Transaktionsrollback und Neuberechnung nach Korrektur.
3. Instrumentierte Tests: Berechtigungsablaeufe, MediaStore-Adapter mit Testprovider soweit moeglich, Servicelebenszyklus, Notification und Compose-Kernflows.
4. Manuelle reale Geraetetestmatrix: mindestens API 26, API 33 und aktuellste API; je ein Geraet mit Bluetooth, ohne TTS-Stimme und mit verweigerter Benachrichtigung. Dokumentiere Ergebnisse in `docs/test-matrix.md`.
5. DropSync-Geraetetest: Marker in einer bekannten lokalen Audiodatei, Lautsprecher und Bluetooth getrennt pruefen. Das Ergebnis protokolliert die wahrgenommene Latenz nur als Beobachtung, nicht als harte Produktzusage.
6. Performancebudget: Bibliotheksansicht laedt aus Room, nicht direkt aus MediaStore pro Recomposition; UI-Listen sind lazy; keine Audioanalyse auf dem Geraet. Nach funktionaler Stabilitaet wird ein Baseline Profile fuer Appstart, Bibliothek, Timerstart und Satzabschluss erzeugt. Baseline Profiles verbessern Start und Interaktionen ueber vorab kompilierte Nutzungspfade. Quelle: [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview).
7. CI blockiert bei fehlgeschlagenem Build, Formatcheck, Unit-Test, Instrumented-Test, Datenbankmigrationstest oder Lizenzinventar-Aenderung ohne Review.

**Abnahmekriterien**

- Alle automatischen Tests laufen fuer jeden Merge.
- Die reale Testmatrix ist fuer den Releasekandidaten vollstaendig und ohne kritischen offenen Fehler.
- Ein frischer Release-Build installiert, erhaelt Berechtigung, spielt Medien ab, speichert eine Session und migriert eine aeltere Testdatenbank.

### Schritt 14: Datenschutz-, Lizenz- und Releasepruefung abschliessen

**Ziel**

Einen veroeffentlichungsfaehigen Build ohne unerklaerte Berechtigungen, Lizenzen oder Datenabfluesse bereitstellen.

**Hintergrund**

Die technische Funktion allein reicht fuer einen Store-Release nicht aus. Berechtigungen und Foreground-Service-Typen werden bei der Veroeffentlichung geprueft; jede neue Abhaengigkeit kann Pflichten erzeugen.

**Technische Anforderungen**

1. Vergleiche das finale Manifest mit der Berechtigungstabelle in Abschnitt 4. Entferne jede nicht begruendete Berechtigung.
2. Pruefe den Release-Build auf Netzwerkendpunkte. Version 1 darf ausser den Android-Systemdiensten keine Netzwerkverbindung benoetigen oder aufbauen.
3. Pruefe jede Abhaengigkeit und transitive Lizenz; aktualisiere `THIRD_PARTY_NOTICES.md` und die im Release sichtbare Notice.
4. Erstelle eine Datenschutzerklaerung, die eindeutig festhaelt: keine Konten, keine Cloud-Synchronisation, keine Analytics, lokale Trainings- und Musikmetadaten, optionale Systemberechtigungen.
5. Pruefe Play-Console-Angaben fuer `mediaPlayback`-Foreground-Service und falls spaeter doch Alarmberechtigungen geplant werden, erneut deren Richtlinien. Die Berechtigungsdeklaration ist Teil des Releaseprozesses. Quelle: [Play Permission Declarations](https://support.google.com/googleplay/android-developer/answer/9214102?hl=en-EN).
6. Liefere AAB, Release Notes, bekannte Einschraenkungen und den Link auf den vollstaendigen Quellcode erst dann aus, wenn die gewaehlte Lizenz dies verlangt oder der Projektinhaber es festlegt.

**Abnahmekriterien**

- Das Release-Manifest entspricht der dokumentierten Berechtigungstabelle.
- Kein GPL-/AGPL-Code aus Referenzprojekten ist enthalten, sofern keine explizite spaetere Lizenzentscheidung dokumentiert ist.
- Alle Release-Gates aus Schritt 13 sind gruen.

## 8. Verbindlicher Bildschirmvertrag

Dieser Abschnitt legt fest, was jeder sichtbare Screen mindestens leisten muss. Zusaetzliche Screens oder Schalter sind in Version 1 verboten, sofern sie nicht ueber eine ADR freigegeben werden.

| Screen | Muss anzeigen | Primaere Aktionen | Pflichtfehler / Sonderfall |
|---|---|---|---|
| Musik: Bibliothek | Suchfeld, Tabs Songs/Alben/Artists/Ordner, Lade- oder Leerzustand, Mini-Player bei aktiver Queue | Song abspielen, suchen, sortieren | Keine Medienberechtigung: Erklaerung + Einstellungen-Button; leerer Index: Neu scannen. |
| Musik: Player | Cover falls lokal vorhanden, Titel, Artist, Fortschritt, Queuezugang, Play/Pause, Vor/Zurueck, Shuffle, Repeat | Playback steuern | Nicht verfuegbare URI: Player pausiert und erklaert den fehlenden Song. |
| Timer: Setup | Modus, Dauer oder naechster Marker, Cue-Optionen, Ducking, Validierung | Preset waehlen, Timer starten | DropSync ohne zugeordneten Marker, unpassende Dauer oder Playerfehler: Start bleibt deaktiviert und nennt die konkrete Ursache. |
| Timer: Laufend | grosse Restzeit, Modus, Song/Marker falls relevant, Cue-Status, Pause und Abbruch | Pause, Abbruch | Bei DropSync-Seek/Pause/Songwechsel: Abschlussdialog mit "Neuen Timer einrichten". |
| Training: Dashboard | aktive Session falls vorhanden, Start freie Session, Routinen, zuletzt verwendete Uebungen, Kalenderzusammenfassung | Session fortsetzen/starten, Routine starten | Mehr als eine aktive Session ist technisch unmoeglich; falls Altdaten dies enthalten, oeffnet ein Reparaturdialog. |
| Training: Session | Uebungsreihenfolge, Supersetkennzeichnung, Satzcluster mit Segmenten, Resttimer, Sessionabschluss | Satz hinzufuegen, Satz abschliessen, Drop-Set erweitern, Uebung hinzufuegen, Session abschliessen | Unvollstaendiges Eingabefeld blockiert nur dessen Abschluss und benennt das fehlende Feld. |
| Training: Uebungsdetail | Verlauf der effektiven Last, Sessionvolumen, 1RM-Schaetzung, PR-Historie | Zeitraum aendern, Archivstatus aendern | Keine Daten: Erklaerung statt leerem Chart. |
| Training: Routineeditor | Name, geordnete Uebungen, optionale Ziele, Supersetgruppen | Hinzufuegen, verschieben, speichern, archivieren | Doppelte Namen oder ungueltige Supersetgruppe werden vor Speichern gezeigt. |
| Einstellungen | Berechtigungsstatus, Markerimport, Timerdefaults, Theme, Datenschutz, Drittanbieterhinweise | Berechtigung erneut anfragen, Datei importieren | Importbericht ist dauerhaft einsehbar und nicht nur ein Toast. |

Navigation ist zustandsbewusst: Beim Verlassen einer aktiven Session oder laufenden Timeransicht erscheint keine generische Warnung, weil die Session persistiert. Beim konkreten Abbruch eines Timers ist dagegen immer eine explizite Bestaetigung erforderlich. Ein Back-Press darf nie einen Satz oder eine Session loeschen.

## 9. Schnittstellen- und Zustandsvertrag

Die folgenden Abstraktionen sind die Mindestgrenzen zwischen Features. Methodennamen duerfen angepasst werden, Semantik und Eigentuemlichkeiten nicht.

```kotlin
interface PlaybackRepository {
    val state: StateFlow<PlaybackState>
    suspend fun play(songId: Long, startPositionMs: Long): AppResult<Unit>
    suspend fun replaceQueue(items: List<QueueItem>, startIndex: Int): AppResult<Unit>
    suspend fun pause(): AppResult<Unit>
    suspend fun seekTo(positionMs: Long): AppResult<Unit>
    suspend fun scheduleAtPosition(request: PositionedCue): AppResult<CueHandle>
    suspend fun cancel(handle: CueHandle)
    suspend fun setAppVolume(volume: Float): AppResult<Unit>
}

interface TimerCoordinator {
    val state: StateFlow<TimerState>
    suspend fun startNormal(config: NormalTimerConfig): AppResult<TimerSessionId>
    suspend fun startDropSync(config: DropSyncConfig): AppResult<TimerSessionId>
    suspend fun pause(id: TimerSessionId): AppResult<Unit>
    suspend fun resume(id: TimerSessionId): AppResult<Unit>
    suspend fun cancel(id: TimerSessionId): AppResult<Unit>
}

interface WorkoutRepository {
    val activeSession: Flow<WorkoutSessionSummary?>
    suspend fun completeCluster(input: CompleteClusterInput): AppResult<ClusterCompletion>
    suspend fun recalculateExercise(exerciseId: Long): AppResult<Unit>
    suspend fun completeSession(sessionId: Long): AppResult<Unit>
}
```

`PositionedCue` enthaelt immer `timerSessionId`, `mediaItemId`, `positionMs` und `cueId`. Der Playback-Adapter setzt dies auf eine Media3-`PlayerMessage`. Ein UI-ViewModel kennt weder `PlayerMessage` noch `ExoPlayer`.

`PlaybackState` ist ein unveraenderlicher Snapshot mit Queue, aktuellem Song, `isPlaying`, `positionMs`, `durationMs`, Wiederholungsmodus, Shuffle und `PlaybackProblem?`. Ein ViewModel darf daraus nur UI-State ableiten; es darf keine eigene Wahrheit fuer Play/Pause oder Position halten.

## 10. Fehler- und Degradationsmatrix

| Ereignis | Erkennung | Verbindliche Reaktion | Datenfolge |
|---|---|---|---|
| Medienberechtigung verweigert | Permission-Callback | Bibliotheksfunktion erklaert blockiert; Training bleibt voll nutzbar | Keine Daten loeschen. |
| Songdatei verschoben/geloescht | URI beim Oeffnen nicht lesbar | Wiedergabe stoppen, Song als nicht verfuegbar markieren, Marker behalten | Historie bleibt unveraendert. |
| TTS nicht bereit | Initialisierung oder Sprache fehlgeschlagen | Haptik/Visual weiter; einmalige Info | Timer laeuft weiter. |
| Keine Haptikfaehigkeit | Vibratorcheck | Keine Haptik, kein Fehlerdialog | Timer laeuft weiter. |
| Audio-Focus dauerhaft verloren | Playerstatus | Musik und DropSync pausieren; sichtbarer Grund | Kein Satz wird abgeschlossen. |
| Player-Seek/Songwechsel im DropSync | Playerlistener | DropSync abbrechen; normalen Timer nicht automatisch starten | Workout-Session bleibt aktiv. |
| DB-Schreibfehler beim Satzabschluss | Transaktion fehlschlaegt | Eingabe bleibt offen; Retry moeglich; keine Erfolgsmeldung | Keine Teilzeilen. |
| Ungueltiger Markerimport | Parser/Validierung | Ganze Datei ablehnen, Bericht anzeigen | Keine Teilimporte. |
| Prozessende waehrend aktiver Session | Neustart und DB-Status | Session als aktiv wiederherstellen; Timer nur wiederherstellen, wenn seine Zeitquelle noch eindeutig ist | Satzdaten bleiben erhalten. |
| Prozessende waehrend DropSync | Service-Neustart / fehlende PlayerMessage | Timer als abgebrochen markieren | Keine falsche Completion-Cue. |

Ein Fehler wird fuer Nutzerinnen und Nutzer als konkrete Handlung formuliert, zum Beispiel "Die Musikdatei wurde nicht gefunden. Bibliothek neu scannen." Entwicklerdetails wie Stacktraces erscheinen nur in Debug-Logs und niemals im Release-UI.

## 11. Messbare Qualitaetsziele

| Bereich | Ziel | Messmethode |
|---|---|---|
| Datenintegritaet | Kein teilweiser Satzabschluss | Room-Transaktionstest und Fehler-Injection. |
| Timer | Kein doppelter Cue je `timerId + cueId` | Unit-Test mit wiederholten Events und Cancel/Race-Szenarien. |
| DropSync | Abschlussereignis wird durch `PlayerMessage` fuer den korrekten Songmarker ausgeloest | Instrumentierter Test mit Fake-/Testplayer; reale Geraetebeobachtung als Zusatz. |
| Bibliothek | Kein Vollscan ohne MediaStore-Aenderung | Repositorytest mit unveraenderter Versionskennung. |
| UI | Kernflows mit Semantik ansprechbar | Compose-UI-Tests. |
| Accessibility | Keine kritischen automatischen Accessibility-Verstoesse; TalkBack-Manualtest | Compose Accessibility Checks plus reale Kontrolle. Android dokumentiert beides als Ergaenzung. Quelle: [Accessibility Tests](https://developer.android.com/develop/ui/compose/accessibility/testing). |
| Performance | Kein Dateiscanning, keine JSON-Analyse und keine DB-Query im Composable | Code Review, StrictMode in Debug und Macrobenchmark nach Stabilisierung. |

## 12. Ausdruecklich vertagte Entscheidungen

Diese Punkte sind **nicht** unklar; sie sind bewusst ausserhalb von Version 1 und duerfen nicht vorgezogen werden:

1. FFmpeg-Erweiterung: erst nach einer dokumentierten Codec-Problemstatistik, separater Build-Groessenmessung und Lizenzpruefung.
2. Cloud Sync: erst mit Konfliktmodell, Verschluesselung, Datenexport und Datenschutzkonzept. Room bleibt dann weiterhin lokale Quelle der Wahrheit.
3. Health Connect: erst nach eigener Berechtigungs- und Produktentscheidung; Version 1 fragt keine Gesundheitsdaten ab.
4. Recovery Score: erst nach fachlich validiertem Trainingsmodell; kein rein marketinggetriebener Prozentwert.
5. Hintergrund-Resttimer ohne Musik: erst nach eigener Play-Policy- und Berechtigungsentscheidung. Version 1 verspricht ihn nicht.
6. Android Auto: Media Session erlaubt spaetere Integration, aber Version 1 implementiert keine Auto-spezifische Browse- oder Custom-Command-Logik.

## 13. Quellenbasis der Recherche

Die technischen Entscheidungen dieses Bauplans beruhen vorrangig auf folgenden Primaerquellen:

- [AndroidX Media3 Uebersicht](https://developer.android.com/media/media3)
- [Media3 Hintergrundwiedergabe und MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
- [Media3 Ereignisse und PlayerMessage](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)
- [Media3 unterstuetzte Formate und Decodererweiterungen](https://developer.android.com/media/media3/exoplayer/supported-formats)
- [Android MediaStore und Shared Media](https://developer.android.com/training/data-storage/shared/media)
- [Android Medienberechtigungen ab Android 13](https://developer.android.com/about/versions/13/behavior-changes-13)
- [Audio Focus](https://developer.android.com/media/optimize/audio-focus)
- [Android Alarmplanung](https://developer.android.com/develop/background-work/services/alarms)
- [Foreground-Service-Typen](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Room-Datenbankmigrationen](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Android-Architekturempfehlungen](https://developer.android.com/topic/architecture/recommendations)
- [Jetpack Compose Accessibility](https://developer.android.com/develop/ui/compose/accessibility)
- [Jetpack Compose Tests](https://developer.android.com/develop/ui/compose/testing)
- [Android Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [Google-Play-Richtlinie zu exakten Alarmen](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)
- [GNU: AGPL und GPLv3-Kompatibilitaet](https://www.gnu.org/licenses/why-affero-gpl.html.en)

Die Fassung ist absichtlich ohne fremde Repository-Codeuebernahmen konzipiert. Vor einer Implementierung muss nur noch geprueft werden, dass die tatsaechlich gewaehlten Bibliotheksversionen stabil sind und in `THIRD_PARTY_NOTICES.md` dokumentiert wurden; diese Routinepruefung ist Teil von Schritt 1 und keine offene Architekturentscheidung.
