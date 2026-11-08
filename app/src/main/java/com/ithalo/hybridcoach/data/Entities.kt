package com.ithalo.hybridcoach.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Dates are stored as ISO `yyyy-MM-dd` text, which sorts chronologically. */
@Entity
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String,
    val durationWeeks: Int,
    val goal: String,
    val notes: String,
    val rawJson: String,
    val createdAt: Long,
    val status: String,
)

@Entity(
    foreignKeys = [ForeignKey(PlanEntity::class, ["id"], ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId"), Index("date")],
)
data class PlannedSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val weekNumber: Int,
    val dayOfWeek: String,
    val date: String,
    val type: String,
    val title: String,
    val estimatedMinutes: Int?,
    val status: String = "pending",
    val skipReason: String? = null,
    /** Version being trained: "gym" or "home" (only strength sessions imported with `home_exercises` have a home one). */
    val variant: String = "gym",
    /** Complementary session (`"optional": true` in the plan): not doing it is not a miss. */
    val optional: Boolean = false,
)

@Entity(
    foreignKeys = [ForeignKey(PlannedSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class PlannedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val orderIndex: Int,
    val sets: Int,
    val reps: String,
    val loadKg: Double?,
    val restSeconds: Int,
    val rpeTarget: Int?,
    val notes: String,
    /** "pending" or "skipped" (skipped during the workout). */
    val status: String = "pending",
    val skipReason: String? = null,
    /** Exercise actually performed when the user swapped the planned one. */
    val substituteExerciseId: String? = null,
    val substituteReason: String? = null,
    val extraSets: Int = 0,
    /** "gym" or "home": which version of the session this exercise belongs to. */
    val variant: String = "gym",
)

@Entity(
    foreignKeys = [
        ForeignKey(PlannedSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PlannedExerciseEntity::class, ["id"], ["plannedExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("plannedExerciseId"), Index("exerciseId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val plannedExerciseId: Long,
    /** Exercise performed (the substitute, when there is one), so history follows the real exercise. */
    val exerciseId: String,
    val setIndex: Int,
    val repsDone: Int,
    val loadKg: Double,
    val failed: Boolean,
    val rpe: Int?,
    val timestamp: Long,
)

@Entity(
    foreignKeys = [ForeignKey(PlannedSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
)
data class SessionFeedbackEntity(
    @PrimaryKey val sessionId: Long,
    val rpeOverall: Int,
    val note: String,
)

@Entity(
    foreignKeys = [ForeignKey(PlannedSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class RunBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val orderIndex: Int,
    val kind: String,
    val durationSeconds: Int?,
    val distanceM: Int?,
    val paceMin: Int?,
    val paceMax: Int?,
    val repeat: Int,
)

/** A finished run. [stepsJson] holds the executed blocks (core `StepResult` list) for the summary. */
@Entity(
    foreignKeys = [ForeignKey(PlannedSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class RunLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val distanceM: Double,
    val durationSeconds: Long,
    val avgPaceSecPerKm: Int?,
    val notes: String,
    val stepsJson: String,
    val batteryStart: Int?,
    val batteryEnd: Int?,
)

@Entity(
    foreignKeys = [ForeignKey(RunLogEntity::class, ["id"], ["runLogId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("runLogId")],
)
data class RunSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runLogId: Long,
    val kmIndex: Int,
    val seconds: Int,
)

/** The run in progress (at most one), saved every few seconds so it can be resumed after the process dies. */
@Entity(foreignKeys = [ForeignKey(PlannedSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class RunProgressEntity(
    @PrimaryKey val sessionId: Long,
    val stateJson: String,
    val batteryStart: Int?,
    val updatedAt: Long,
)

/** GPS track point, only stored when the user turns the option on in Settings. */
@Entity(
    foreignKeys = [ForeignKey(PlannedSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class RunTrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
)

/** A run log with its session data, for the exported summary. */
/** A finished run with its session title and planned date, for the Corrida tab. */
data class RunHistoryRow(
    @Embedded val log: RunLogEntity,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "date") val date: String,
)

data class RunLogRow(
    @Embedded val log: RunLogEntity,
    @ColumnInfo(name = "weekNumber") val weekNumber: Int,
    @ColumnInfo(name = "rpeOverall") val rpeOverall: Int?,
)

@Entity
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val canonicalName: String,
)

/** [alias] holds the normalized name, so it is also the lookup key. */
@Entity(
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId")],
)
data class ExerciseAliasEntity(
    @PrimaryKey val alias: String,
    val exerciseId: String,
)

@Entity
data class QuestionnaireEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
)

@Entity
data class ExportedSummaryEntity(
    @PrimaryKey val planId: Long,
    val json: String,
    val text: String,
    val createdAt: Long,
)

data class PlannedExerciseWithName(
    @Embedded val exercise: PlannedExerciseEntity,
    /** Name of the exercise being performed (the substitute, if any). */
    @ColumnInfo(name = "exerciseName") val exerciseName: String,
    @ColumnInfo(name = "originalName") val originalName: String,
    @ColumnInfo(name = "effectiveExerciseId") val effectiveExerciseId: String,
)

data class SetLogRow(
    @Embedded val log: SetLogEntity,
    @ColumnInfo(name = "exerciseName") val exerciseName: String,
    @ColumnInfo(name = "sessionDate") val sessionDate: String,
    /** Target of the planned exercise ("8", "8min"); tells whether repsDone holds seconds. */
    @ColumnInfo(name = "plannedReps") val plannedReps: String? = null,
)

/** A logged set joined with the plan it came from, for the exported summary. */
data class StrengthLogRow(
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val reps: Int,
    val loadKg: Double,
    val failed: Boolean,
    val rpe: Int?,
    val plannedSets: Int,
    val plannedReps: String,
    val plannedLoadKg: Double?,
)

data class FeedbackRow(
    val title: String,
    val rpeOverall: Int,
    val note: String,
)
