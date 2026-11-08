package com.ithalo.hybridcoach.core.strength

/** One logged set, as the summary needs it. [timed] sets store seconds in [reps] and are left out of volume and best set. */
data class SummarySet(
    val exercise: String,
    val reps: Int,
    val loadKg: Double,
    val failed: Boolean,
    val rpe: Int?,
    val timestamp: Long,
    val timed: Boolean,
)

/** One exercise of the session: planned sets (extra ones included) and what happened to it. */
data class SummaryExercise(val plannedSets: Int, val loggedSets: Int, val skipped: Boolean, val substituted: Boolean)

/** The "treino concluído" recap. [avgRpe] is the mean effort of the sets that have one; [records] are names of the records beaten. */
data class WorkoutSummary(
    val durationMinutes: Int,
    val setsDone: Int,
    val setsPlanned: Int,
    val volumeKg: Double,
    val exercisesDone: Int,
    val exercisesSkipped: Int,
    val exercisesSubstituted: Int,
    val bestSet: SummarySet?,
    val avgRpe: Double?,
    val records: List<String>,
    /** Volume of the last time this same workout was done, when there is one with volume. */
    val previousVolumeKg: Double? = null,
) {
    /** Change against [previousVolumeKg] in whole percent, or null with nothing to compare. */
    val volumeChangePercent: Int?
        get() = previousVolumeKg?.takeIf { it > 0 && volumeKg > 0 }?.let { Math.round((volumeKg - it) / it * 100).toInt() }
}

/** A logged set as the record check needs it; [timed] sets store seconds in [reps]. */
data class RecordSet(
    val id: Long,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val reps: Int,
    val loadKg: Double,
    val failed: Boolean,
    val timed: Boolean,
    val timestamp: Long,
)

object WorkoutSummaries {
    fun build(
        sets: List<SummarySet>,
        exercises: List<SummaryExercise>,
        records: List<String>,
        previousVolumeKg: Double? = null,
    ): WorkoutSummary {
        val ordered = sets.sortedBy { it.timestamp }
        val minutes = if (ordered.size < 2) 0 else ((ordered.last().timestamp - ordered.first().timestamp) / 60_000L).toInt()
        val weighted = weighted(sets)
        val rpes = sets.mapNotNull { it.rpe }
        return WorkoutSummary(
            durationMinutes = minutes,
            setsDone = sets.size,
            setsPlanned = exercises.filter { !it.skipped }.sumOf { it.plannedSets },
            volumeKg = volumeKg(sets),
            exercisesDone = exercises.count { !it.skipped && it.loggedSets > 0 },
            exercisesSkipped = exercises.count { it.skipped },
            exercisesSubstituted = exercises.count { it.substituted && !it.skipped },
            bestSet = weighted.maxWithOrNull(compareBy({ it.loadKg }, { it.reps })),
            avgRpe = rpes.takeIf { it.isNotEmpty() }?.average(),
            records = records.distinct(),
            previousVolumeKg = previousVolumeKg?.takeIf { it > 0 },
        )
    }

    private fun weighted(sets: List<SummarySet>) = sets.filter { !it.timed && !it.failed && it.loadKg > 0 && it.reps > 0 }

    /** Load × reps of the valid weighted sets (failed and timed sets are not volume). */
    fun volumeKg(sets: List<SummarySet>): Double = weighted(sets).sumOf { it.loadKg * it.reps }

    /**
     * Names of the exercises whose record was beaten by a set of [sessionId], in the order they were beaten.
     * Same rule as the player: per exercise, in time order, the first valid set is the baseline and a later
     * one is a record when it beats the best so far (more load, or same load and more reps; more seconds when timed).
     */
    fun recordsIn(sessionId: Long, history: List<RecordSet>): List<String> {
        val best = HashMap<String, RecordSet>()
        val beaten = LinkedHashSet<String>()
        history.sortedWith(compareBy({ it.timestamp }, { it.id })).forEach { s ->
            if (s.failed || s.reps < 1) return@forEach
            val key = s.exerciseId + if (s.timed) "#t" else ""
            val prev = best[key]
            val beats = prev != null && if (s.timed) s.reps > prev.reps else s.loadKg > prev.loadKg || (s.loadKg == prev.loadKg && s.reps > prev.reps)
            if (prev == null || beats) best[key] = s
            if (beats && s.sessionId == sessionId) beaten += s.exerciseName
        }
        return beaten.toList()
    }

    /** The effort grade 1..10 the finish screen starts from: the mean of the set efforts, rounded. */
    fun suggestedRpe(avgRpe: Double?): Int? = avgRpe?.let { Math.round(it).toInt().coerceIn(1, 10) }
}
