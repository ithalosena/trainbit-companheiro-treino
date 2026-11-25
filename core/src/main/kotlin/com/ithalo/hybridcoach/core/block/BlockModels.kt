package com.ithalo.hybridcoach.core.block

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** PLANNED before the start, ACTIVE, CLOSING in its last week, CLOSED once the user closed it. */
enum class BlockState { PLANNED, ACTIVE, CLOSING, CLOSED }

/** Phase of a block, as the AI is asked to state it (`phase` in the plan). */
enum class BlockPhase(val value: String, val label: String) {
    BASE("base", "base"),
    PROGRESSION("progression", "progressão"),
    PEAK("peak", "pico"),
    DELOAD("deload", "deload");

    companion object {
        fun of(value: String?): BlockPhase? = entries.firstOrNull { it.value == value }
    }
}

object BlockLifecycle {
    /** Last day of a block that starts on [start] and lasts [weeks] weeks. */
    fun end(start: LocalDate, weeks: Int): LocalDate = start.plusWeeks(weeks.toLong()).minusDays(1)

    fun state(start: LocalDate, weeks: Int, today: LocalDate, closed: Boolean): BlockState = when {
        closed -> BlockState.CLOSED
        today.isBefore(start) -> BlockState.PLANNED
        !today.isBefore(end(start, weeks).minusDays(6)) -> BlockState.CLOSING
        else -> BlockState.ACTIVE
    }

    /** Days until the last day (0 on the last day; negative once it is over). */
    fun daysLeft(start: LocalDate, weeks: Int, today: LocalDate): Long = ChronoUnit.DAYS.between(today, end(start, weeks))

    /** The next block starts on the Monday after the end of this one. */
    fun nextStart(start: LocalDate, weeks: Int): LocalDate = end(start, weeks).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
}

// ------------------------------------------------------------------ inputs (read from the app's history)

/** A logged set of a block. [plannedReps] is the target ("6-8", "30s"); [timed] for work in seconds. */
data class BlockSet(
    val exerciseId: String,
    val exerciseName: String,
    val sessionId: Long,
    val date: LocalDate,
    val reps: Int,
    val loadKg: Double,
    val failed: Boolean,
    val rpe: Int?,
    val plannedReps: String,
    val timed: Boolean = false,
)

/** A planned exercise of the block: [main] for the first two of a session (the ones to keep), plus what happened. */
data class BlockExercise(
    val exerciseId: String,
    val exerciseName: String,
    val main: Boolean,
    val postponed: Int = 0,
    val pain: Boolean = false,
    val painNote: String? = null,
)

/** A run of the block; [type] is "steady" or "interval"; the target is the planned pace range (sec/km). */
data class BlockRun(
    val date: LocalDate,
    val distanceM: Double,
    val durationSeconds: Long,
    val avgPaceSecPerKm: Int?,
    val type: String,
    val targetMin: Int? = null,
    val targetMax: Int? = null,
)

data class BlockSession(val week: Int, val date: LocalDate, val status: String, val optional: Boolean, val skipReason: String? = null)

/** One session of the previous block's week, for the "skeleton" given to the AI. */
@Serializable
data class SkeletonSession(val day: String, val type: String, val title: String, val exercises: List<String> = emptyList())

// ------------------------------------------------------------------ outputs (frozen in the snapshot)

@Serializable
data class ExerciseState(
    val id: String,
    val name: String,
    @SerialName("target_reps") val targetReps: String,
    @SerialName("last_load_kg") val lastLoadKg: Double?,
    @SerialName("last_reps") val lastReps: Int?,
    @SerialName("best_load_kg") val bestLoadKg: Double?,
    @SerialName("best_reps") val bestReps: Int?,
    @SerialName("e1rm_kg") val e1rmKg: Double?,
    @SerialName("e1rm_trend_pct") val e1rmTrendPct: Int?,
    @SerialName("failed_sets") val failedSets: Int,
    @SerialName("avg_rpe") val avgRpe: Double?,
    val postponed: Int,
    val pain: Boolean,
    val main: Boolean,
)

@Serializable
data class RunState(
    @SerialName("avg_pace_by_type") val avgPaceByType: Map<String, Int>,
    @SerialName("longest_km") val longestKm: Double,
    @SerialName("weekly_km") val weeklyKm: List<Double>,
    @SerialName("pace_adherence_pct") val paceAdherencePct: Int?,
)

@Serializable
data class GeneralState(
    @SerialName("adherence_by_week") val adherenceByWeek: List<Int>,
    @SerialName("adherence_pct") val adherencePct: Int,
    @SerialName("skipped_weeks") val skippedWeeks: Int,
    @SerialName("deload_done") val deloadDone: Boolean,
    val limitations: String,
    @SerialName("days_since_last_workout") val daysSinceLastWorkout: Int?,
)

@Serializable
data class SuggestedLoad(val id: String, val name: String, @SerialName("load_kg") val loadKg: Double, val reps: String, val rule: String)

@Serializable
data class SuggestedRun(
    @SerialName("week1_km") val week1Km: Double,
    @SerialName("long_run_km") val longRunKm: Double,
    @SerialName("easy_pace_min") val easyPaceMin: Int?,
    @SerialName("easy_pace_max") val easyPaceMax: Int?,
    @SerialName("interval_pace") val intervalPace: Int?,
    @SerialName("max_weekly_increase_pct") val maxWeeklyIncreasePct: Int,
)

@Serializable
data class SuggestedStart(
    val loads: List<SuggestedLoad>,
    val run: SuggestedRun?,
    /** Low adherence or a long pause: a block to consolidate, not to progress. */
    val consolidation: Boolean,
    val phase: String,
)

/** Answers of the close-block mini questionnaire. */
@Serializable
data class CloseAnswers(
    val fatigue: Int = 3,
    val motivation: Int = 3,
    @SerialName("new_limitations") val newLimitations: String = "",
    val change: String = "",
    val availability: String = "",
    @SerialName("carry_incomplete") val carryIncomplete: Boolean = false,
    @SerialName("incomplete_sessions") val incompleteSessions: Int = 0,
)

/** The block frozen when it was closed: the athlete's state, the suggested start and the close answers. Never edited. */
@Serializable
data class BlockSnapshot(
    val version: Int = 1,
    @SerialName("plan_id") val planId: Long,
    @SerialName("plan_name") val planName: String,
    val number: Int,
    val start: String,
    val end: String,
    @SerialName("closed_at") val closedAt: String,
    val phase: String?,
    val exercises: List<ExerciseState>,
    val run: RunState?,
    val general: GeneralState,
    @SerialName("suggested_start") val suggestedStart: SuggestedStart,
    val answers: CloseAnswers,
    val skeleton: List<SkeletonSession>,
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        fun fromJson(text: String): BlockSnapshot = JSON.decodeFromString(serializer(), text)
    }
}
