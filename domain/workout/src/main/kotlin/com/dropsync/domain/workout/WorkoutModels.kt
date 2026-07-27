package com.dropsync.domain.workout

import com.dropsync.core.model.ExerciseKind
import com.dropsync.core.model.PrType
import com.dropsync.core.model.PrValueUnit
import com.dropsync.core.model.SetRole

// Domainmodelle des Trainingslogs (Bauplan 5.4, Abschnitt 6).
// Gewichte sind IMMER ganze Millikilogramm (Long); Double ist verboten.

/** Eingabe eines Satzsegments vor dem Speichern. */
data class SegmentInput(
    val externalLoadMilliKgPerImplement: Long?,
    /** Nur 1 oder 2 (5.4); 2 nur bei zwei gleich schweren Implementseiten. */
    val loadMultiplier: Int,
    val reps: Int?,
    val durationMs: Long? = null,
    val distanceM: Long? = null,
)

/**
 * Ein qualifiziertes, abgeschlossenes Segment aus der Historie einer
 * Uebung; Grundlage der vollstaendigen PR-Neuberechnung (Schritt 10.4).
 */
data class QualifiedSegment(
    val sessionId: Long,
    val sessionStartedAtEpochMs: Long,
    val clusterId: Long,
    val completedAtEpochMs: Long,
    val loadMilliKg: Long,
    val loadMultiplier: Int,
    val reps: Int,
)

/** Ergebnis der PR-Berechnung; Persistenz uebernimmt :data:workout. */
data class PrRecord(
    val type: PrType,
    val achievedSessionId: Long,
    val achievedClusterId: Long?,
    val valueLong: Long,
    val valueUnit: PrValueUnit,
    val comparableLoadMilliKg: Long?,
    val achievedAtEpochMs: Long,
)

/**
 * Qualifikation fuer Volumen und PRs (Bauplan 5.4/1): setRole WORKING
 * oder FAILURE, Cluster abgeschlossen, Uebung STRENGTH, mindestens ein
 * Segment mit reps > 0 und externalLoadMilliKg >= 0. WARMUP nie.
 */
object Qualification {
    fun clusterQualifies(
        kind: ExerciseKind,
        setRole: SetRole,
        isCompleted: Boolean,
    ): Boolean =
        kind == ExerciseKind.STRENGTH &&
            isCompleted &&
            (setRole == SetRole.WORKING || setRole == SetRole.FAILURE)

    fun segmentQualifies(segment: SegmentInput): Boolean {
        val reps = segment.reps ?: return false
        val load = segment.externalLoadMilliKgPerImplement ?: return false
        return reps > 0 && load >= 0
    }
}
