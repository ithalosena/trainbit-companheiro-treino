package com.ithalo.hybridcoach.core.strength

import com.ithalo.hybridcoach.core.summary.BestSet
import com.ithalo.hybridcoach.core.summary.DoneStrength
import com.ithalo.hybridcoach.core.summary.PlannedStrength
import com.ithalo.hybridcoach.core.summary.StrengthSummary
import kotlin.math.roundToInt

/** One logged set joined with the plan it belonged to. [exerciseId] is the exercise actually performed. */
data class StrengthSetRecord(
    val exerciseId: String,
    val exerciseName: String,
    val sessionId: Long,
    val reps: Int,
    val loadKg: Double,
    val failed: Boolean,
    val rpe: Int?,
    val plannedSets: Int,
    val plannedReps: String,
    val plannedLoadKg: Double?,
)

object StrengthSummarizer {
    /** [records] must be in chronological order. */
    fun summarize(records: List<StrengthSetRecord>): List<StrengthSummary> =
        records.groupBy { it.exerciseId }.values.map { rows ->
            val first = rows.first()
            val ranking = compareBy<StrengthSetRecord>({ it.loadKg }, { it.reps })
            val best = rows.filter { !it.failed }.maxWithOrNull(ranking) ?: rows.maxWith(ranking)
            val rpes = rows.mapNotNull { it.rpe }
            val trend = rows.groupBy { it.sessionId }.values.map { session -> session.maxOf { it.loadKg } }
            StrengthSummary(
                exercise = first.exerciseName,
                planned = PlannedStrength(first.plannedSets, first.plannedReps, first.plannedLoadKg),
                done = DoneStrength(
                    bestSet = BestSet(best.reps, best.loadKg),
                    failedSets = rows.count { it.failed },
                    avgRpe = rpes.takeIf { it.isNotEmpty() }?.average()?.let { (it * 10).roundToInt() / 10.0 },
                    loadTrendKg = trend,
                ),
            )
        }
}
