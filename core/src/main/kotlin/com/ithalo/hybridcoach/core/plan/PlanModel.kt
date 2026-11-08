package com.ithalo.hybridcoach.core.plan

import java.time.DayOfWeek
import java.time.LocalDate

const val SUPPORTED_SCHEMA_VERSION = 1

data class PlanDocument(
    val schemaVersion: Int,
    val plan: PlanInfo,
    val weeks: List<WeekGroup>,
)

data class PlanInfo(
    val name: String,
    val startDate: LocalDate,
    val durationWeeks: Int,
    val goal: String,
    val notes: String,
)

/** A group of weeks that share the same sessions (`weeks: [1, 2]`). */
data class WeekGroup(
    val weeks: List<Int>,
    val focus: String,
    val isDeload: Boolean,
    val sessions: List<SessionDef>,
)

sealed interface SessionDef {
    val dayOfWeek: DayOfWeek
    val title: String
    val estimatedMinutes: Int?

    /**
     * A complementary session (`"optional": true`): bike at home, an extra easy run, a second workout of the day.
     * Not doing it is not a miss; doing it counts like any session.
     */
    val optional: Boolean
}

data class StrengthSessionDef(
    override val dayOfWeek: DayOfWeek,
    override val title: String,
    override val estimatedMinutes: Int?,
    val exercises: List<ExerciseDef>,
    /** Optional at-home version of the same workout (`home_exercises`); empty when absent. */
    val homeExercises: List<ExerciseDef> = emptyList(),
    override val optional: Boolean = false,
) : SessionDef

data class RunSessionDef(
    override val dayOfWeek: DayOfWeek,
    override val title: String,
    override val estimatedMinutes: Int?,
    val blocks: List<RunBlockDef>,
    override val optional: Boolean = false,
) : SessionDef

data class ExerciseDef(
    val name: String,
    val sets: Int,
    val reps: String,
    val loadKg: Double?,
    val restSeconds: Int,
    val rpeTarget: Int?,
    val notes: String,
)

enum class RunBlockKind { WARMUP, STEADY, INTERVAL, RECOVERY, COOLDOWN }

data class PaceRange(val min: Int, val max: Int)

data class RunBlockDef(
    val kind: RunBlockKind,
    val durationSeconds: Int?,
    val distanceM: Int?,
    /** Seconds per km; `min` is the fastest pace. Null means no pace target. */
    val targetPace: PaceRange?,
    val repeat: Int,
)

data class ValidationResult(
    val document: PlanDocument?,
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty() && document != null
}
