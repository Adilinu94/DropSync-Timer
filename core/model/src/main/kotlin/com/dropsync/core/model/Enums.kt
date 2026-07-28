package com.dropsync.core.model

// Stabile Domaenen-Aufzaehlungen (Bauplan Abschnitt 6).
//
// In der Datenbank werden ausschliesslich die Namen als Strings gespeichert;
// Enum-Ordinals sind verboten. Werte duerfen nie umbenannt werden, ohne eine
// Migration bereitzustellen.

/** Status einer Trainingssession. */
enum class SessionStatus { ACTIVE, COMPLETED, DISCARDED }

/** Art einer Uebung; bestimmt die erlaubten Eingabefelder (Schritt 9.3). */
enum class ExerciseKind { STRENGTH, TIME, DISTANCE }

/**
 * Rest-Modus einer Uebung (Abschnitt 8): normaler Resttimer mit fester
 * Dauer oder DropSync (naechster Satz startet auf dem naechsten Song-Drop).
 * In der Datenbank als stabiler String gespeichert.
 */
enum class RestMode { NORMAL, DROPSYNC }

/** Rolle eines Satzclusters. WARMUP qualifiziert nie fuer Volumen/PRs (5.4). */
enum class SetRole { WARMUP, WORKING, FAILURE, TIME, DISTANCE }

/** Herkunft eines Songmarkers. */
enum class MarkerSource { IMPORT, MANUAL }

/** Zuordnungsmethode eines Markers zu einem Song (5.1). */
enum class LinkMethod { HASH, METADATA, MANUAL }

/** PR-Typen gemaess der exakten Regeln in Abschnitt 5.4. */
enum class PrType { HIGHEST_LOAD, HIGHEST_SESSION_VOLUME, MOST_REPS_AT_LOAD }

/** Einheit des PR-Werts. */
enum class PrValueUnit { MILLI_KG, REPS }

/**
 * Feste Muskelgruppen der Standardbibliothek (Schritt 9.2).
 * Anzeigenamen werden lokalisiert; diese Schluessel sind stabil.
 */
enum class MuscleGroup {
    CHEST,
    BACK,
    SHOULDERS,
    BICEPS,
    TRICEPS,
    FOREARMS,
    QUADRICEPS,
    HAMSTRINGS,
    GLUTES,
    CALVES,
    ABDOMINALS,
    LOWER_BACK,
    FULL_BODY,
    OTHER,
}

/**
 * Feste Equipment-Schluessel der Standardbibliothek (Schritt 9.2).
 * Anzeigenamen werden lokalisiert; diese Schluessel sind stabil.
 */
enum class Equipment {
    BARBELL,
    DUMBBELL,
    CABLE,
    MACHINE,
    BODYWEIGHT,
    KETTLEBELL,
    BAND,
    SMITH_MACHINE,
    OTHER,
}

/**
 * Erlaubte Ducking-Stufen in Prozent (Schritt 8.4).
 * Unbekannte Werte werden beim Speichern abgelehnt.
 */
object DuckingPercent {
    const val NONE = 0
    const val HALF = 50
    const val FULL = 100

    val allowed: Set<Int> = setOf(NONE, HALF, FULL)

    fun isValid(value: Int): Boolean = value in allowed
}
