package com.ithalo.hybridcoach.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HybridDao {
    // Plans
    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Query("UPDATE PlanEntity SET status = 'archived' WHERE status = 'active'")
    suspend fun archiveActivePlans()

    @Query("SELECT * FROM PlanEntity WHERE status = 'active' ORDER BY id DESC LIMIT 1")
    fun observeActivePlan(): Flow<PlanEntity?>

    @Query("SELECT * FROM PlanEntity WHERE status = 'active' ORDER BY id DESC LIMIT 1")
    suspend fun activePlan(): PlanEntity?

    // Sessions
    @Insert
    suspend fun insertSession(session: PlannedSessionEntity): Long

    @Insert
    suspend fun insertPlannedExercises(items: List<PlannedExerciseEntity>)

    @Insert
    suspend fun insertRunBlocks(items: List<RunBlockEntity>)

    @Query("SELECT * FROM PlannedSessionEntity WHERE planId = :planId ORDER BY date, id")
    fun observeSessions(planId: Long): Flow<List<PlannedSessionEntity>>

    @Query("SELECT * FROM PlannedSessionEntity WHERE planId = :planId ORDER BY date, id")
    suspend fun sessions(planId: Long): List<PlannedSessionEntity>

    @Query("SELECT * FROM PlannedSessionEntity WHERE id = :id")
    fun observeSession(id: Long): Flow<PlannedSessionEntity?>

    @Query("SELECT * FROM PlannedSessionEntity WHERE status = 'done' ORDER BY date DESC, id DESC")
    fun observeDoneSessions(): Flow<List<PlannedSessionEntity>>

    /** Only the exercises of the version (gym or home) the session is set to. */
    @Query(EXERCISE_SELECT + "JOIN PlannedSessionEntity s0 ON s0.id = pe.sessionId WHERE pe.sessionId = :sessionId AND pe.variant = s0.variant ORDER BY pe.orderIndex")
    fun observeSessionExercises(sessionId: Long): Flow<List<PlannedExerciseWithName>>

    @Query(EXERCISE_SELECT + "JOIN PlannedSessionEntity s0 ON s0.id = pe.sessionId WHERE s0.planId = :planId AND pe.variant = s0.variant ORDER BY s0.date, pe.orderIndex")
    suspend fun exercisesForPlan(planId: Long): List<PlannedExerciseWithName>

    @Query("SELECT EXISTS(SELECT 1 FROM PlannedExerciseEntity WHERE sessionId = :sessionId AND variant = 'home')")
    fun observeHasHomeVersion(sessionId: Long): Flow<Boolean>

    @Query("UPDATE PlannedSessionEntity SET variant = :variant WHERE id = :id")
    suspend fun updateSessionVariant(id: Long, variant: String)

    @Query("UPDATE PlannedExerciseEntity SET status = 'skipped', skipReason = :reason WHERE id = :id")
    suspend fun skipExercise(id: Long, reason: String)

    @Query("UPDATE PlannedExerciseEntity SET substituteExerciseId = :exerciseId, substituteReason = :reason WHERE id = :id")
    suspend fun substituteExercise(id: Long, exerciseId: String, reason: String)

    @Query("UPDATE PlannedExerciseEntity SET extraSets = extraSets + 1 WHERE id = :id")
    suspend fun addExtraSet(id: Long)

    // Set logs
    @Insert
    suspend fun insertSetLog(log: SetLogEntity): Long

    @Query("DELETE FROM SetLogEntity WHERE id = :id")
    suspend fun deleteSetLog(id: Long)

    @Query("SELECT * FROM SetLogEntity WHERE sessionId = :sessionId ORDER BY timestamp, id")
    fun observeSetLogs(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT loadKg FROM SetLogEntity WHERE exerciseId = :exerciseId ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastLoad(exerciseId: String): Double?

    /** Best valid set by load, reps breaking ties (the game engine's record rule). */
    @Query("SELECT * FROM SetLogEntity WHERE exerciseId = :exerciseId AND failed = 0 AND repsDone >= 1 ORDER BY loadKg DESC, repsDone DESC LIMIT 1")
    suspend fun bestSetByLoad(exerciseId: String): SetLogEntity?

    /** Longest valid timed set (seconds are stored in repsDone). */
    @Query("SELECT * FROM SetLogEntity WHERE exerciseId = :exerciseId AND failed = 0 AND repsDone >= 1 ORDER BY repsDone DESC LIMIT 1")
    suspend fun bestSetByTime(exerciseId: String): SetLogEntity?

    @Query(
        SET_LOG_ROW_SELECT + "WHERE l.sessionId = :sessionId ORDER BY l.timestamp, l.id",
    )
    fun observeSetLogRowsForSession(sessionId: Long): Flow<List<SetLogRow>>

    @Query(
        SET_LOG_ROW_SELECT + "WHERE l.exerciseId = :exerciseId ORDER BY s.date, l.timestamp, l.id",
    )
    fun observeSetLogRowsForExercise(exerciseId: String): Flow<List<SetLogRow>>

    /** Every set, from any session, of the exercises trained in [sessionId]: enough to tell the records it beat. */
    @Query(
        SET_LOG_ROW_SELECT + "WHERE l.exerciseId IN (SELECT exerciseId FROM SetLogEntity WHERE sessionId = :sessionId) ORDER BY l.timestamp, l.id",
    )
    fun observeSetLogRowsSharingExercises(sessionId: Long): Flow<List<SetLogRow>>

    @Query(SET_LOG_ROW_SELECT + "WHERE l.sessionId = :sessionId ORDER BY l.timestamp, l.id")
    suspend fun setLogRowsForSession(sessionId: Long): List<SetLogRow>

    /** The last finished session with the same title as [sessionId] on or before its date: the same workout done before. */
    @Query(
        "SELECT p.* FROM PlannedSessionEntity p JOIN PlannedSessionEntity s ON s.id = :sessionId " +
            "WHERE p.title = s.title AND p.id != s.id AND p.status = 'done' AND p.date <= s.date ORDER BY p.date DESC, p.id DESC LIMIT 1",
    )
    suspend fun previousSameSession(sessionId: Long): PlannedSessionEntity?

    @Query("SELECT * FROM ExerciseEntity WHERE id IN (SELECT DISTINCT exerciseId FROM SetLogEntity) ORDER BY canonicalName")
    fun observeExercisesWithLogs(): Flow<List<ExerciseEntity>>

    @Query(
        "SELECT l.sessionId AS sessionId, l.exerciseId AS exerciseId, e.canonicalName AS exerciseName, " +
            "l.repsDone AS reps, l.loadKg AS loadKg, l.failed AS failed, l.rpe AS rpe, " +
            "pe.sets AS plannedSets, pe.reps AS plannedReps, pe.loadKg AS plannedLoadKg " +
            "FROM SetLogEntity l " +
            "JOIN PlannedSessionEntity s ON s.id = l.sessionId " +
            "JOIN PlannedExerciseEntity pe ON pe.id = l.plannedExerciseId " +
            "JOIN ExerciseEntity e ON e.id = l.exerciseId " +
            "WHERE s.planId = :planId ORDER BY s.date, l.timestamp, l.id",
    )
    suspend fun strengthLogsForPlan(planId: Long): List<StrengthLogRow>

    // Session feedback
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFeedback(feedback: SessionFeedbackEntity)

    @Query("SELECT * FROM SessionFeedbackEntity WHERE sessionId = :sessionId")
    fun observeFeedback(sessionId: Long): Flow<SessionFeedbackEntity?>

    @Query(
        "SELECT s.title AS title, f.rpeOverall AS rpeOverall, f.note AS note FROM SessionFeedbackEntity f " +
            "JOIN PlannedSessionEntity s ON s.id = f.sessionId WHERE s.planId = :planId ORDER BY s.date",
    )
    suspend fun feedbackForPlan(planId: Long): List<FeedbackRow>

    @Query("SELECT * FROM RunBlockEntity WHERE sessionId = :sessionId ORDER BY orderIndex")
    fun observeRunBlocks(sessionId: Long): Flow<List<RunBlockEntity>>

    @Query("UPDATE PlannedSessionEntity SET date = :date WHERE id = :id")
    suspend fun updateSessionDate(id: Long, date: String)

    @Query("UPDATE PlannedSessionEntity SET status = :status, skipReason = :reason WHERE id = :id")
    suspend fun updateSessionStatus(id: Long, status: String, reason: String?)

    // Runs
    @Insert
    suspend fun insertRunLog(log: RunLogEntity): Long

    @Insert
    suspend fun insertRunSplits(items: List<RunSplitEntity>)

    @Query("SELECT * FROM RunLogEntity WHERE sessionId = :sessionId ORDER BY endedAt DESC LIMIT 1")
    fun observeRunLog(sessionId: Long): Flow<RunLogEntity?>

    @Query(
        "SELECT l.*, s.title AS title, s.date AS date FROM RunLogEntity l " +
            "JOIN PlannedSessionEntity s ON s.id = l.sessionId ORDER BY l.endedAt DESC",
    )
    fun observeRunHistory(): Flow<List<RunHistoryRow>>

    @Query("SELECT * FROM RunSplitEntity WHERE runLogId = :runLogId ORDER BY kmIndex")
    fun observeRunSplits(runLogId: Long): Flow<List<RunSplitEntity>>

    @Query(
        "SELECT l.*, s.weekNumber AS weekNumber, f.rpeOverall AS rpeOverall FROM RunLogEntity l " +
            "JOIN PlannedSessionEntity s ON s.id = l.sessionId " +
            "LEFT JOIN SessionFeedbackEntity f ON f.sessionId = l.sessionId " +
            "WHERE s.planId = :planId ORDER BY s.date, l.endedAt",
    )
    suspend fun runLogsForPlan(planId: Long): List<RunLogRow>

    @Query("SELECT * FROM RunBlockEntity WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun runBlocks(sessionId: Long): List<RunBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRunProgress(progress: RunProgressEntity)

    @Query("SELECT * FROM RunProgressEntity ORDER BY updatedAt DESC LIMIT 1")
    suspend fun runProgress(): RunProgressEntity?

    @Query("SELECT * FROM RunProgressEntity ORDER BY updatedAt DESC LIMIT 1")
    fun observeRunProgress(): Flow<RunProgressEntity?>

    @Query("DELETE FROM RunProgressEntity")
    suspend fun clearRunProgress()

    @Insert
    suspend fun insertTrackPoints(items: List<RunTrackPointEntity>)

    // Exercises

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(items: List<ExerciseAliasEntity>)

    @Query("SELECT * FROM ExerciseEntity ORDER BY canonicalName")
    suspend fun exercises(): List<ExerciseEntity>

    @Query("SELECT * FROM ExerciseAliasEntity")
    suspend fun aliases(): List<ExerciseAliasEntity>

    // Questionnaire and exported summary
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuestionnaire(entity: QuestionnaireEntity)

    @Query("SELECT * FROM QuestionnaireEntity WHERE id = 1")
    suspend fun questionnaire(): QuestionnaireEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSummary(entity: ExportedSummaryEntity)

    @Query("SELECT * FROM ExportedSummaryEntity ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestSummary(): ExportedSummaryEntity?

    // Gamification: the whole history, read as plain rows for the core GameEngine.
    @Query(
        "SELECT s.id AS id, s.planId AS planId, s.date AS date, s.type AS type, s.status AS status, f.rpeOverall AS rpe, s.optional AS optional " +
            "FROM PlannedSessionEntity s LEFT JOIN SessionFeedbackEntity f ON f.sessionId = s.id",
    )
    suspend fun gameSessions(): List<GameSessionRow>

    @Query(
        "SELECT l.sessionId AS sessionId, l.exerciseId AS exerciseId, e.canonicalName AS exerciseName, l.repsDone AS reps, " +
            "l.loadKg AS loadKg, l.failed AS failed, s.date AS date, pe.reps AS plannedReps FROM SetLogEntity l " +
            "JOIN ExerciseEntity e ON e.id = l.exerciseId JOIN PlannedSessionEntity s ON s.id = l.sessionId " +
            "LEFT JOIN PlannedExerciseEntity pe ON pe.id = l.plannedExerciseId",
    )
    suspend fun gameSets(): List<GameSetRow>

    @Query(
        "SELECT l.id AS id, l.sessionId AS sessionId, s.date AS date, l.distanceM AS distanceM, " +
            "l.durationSeconds AS durationSeconds, l.avgPaceSecPerKm AS avgPaceSecPerKm FROM RunLogEntity l " +
            "JOIN PlannedSessionEntity s ON s.id = l.sessionId",
    )
    suspend fun gameRuns(): List<GameRunRow>

    @Query("SELECT * FROM RunSplitEntity ORDER BY runLogId, kmIndex")
    suspend fun allRunSplits(): List<RunSplitEntity>

    @Query("SELECT * FROM PlanEntity")
    suspend fun allPlans(): List<PlanEntity>

    @Query("SELECT COUNT(*) FROM ExportedSummaryEntity")
    suspend fun summaryCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM QuestionnaireEntity)")
    suspend fun hasQuestionnaire(): Boolean
}

data class GameSessionRow(val id: Long, val planId: Long, val date: String, val type: String, val status: String, val rpe: Int?, val optional: Boolean = false)

data class GameSetRow(
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val reps: Int,
    val loadKg: Double,
    val failed: Boolean,
    val date: String,
    /** Planned reps text ("8-10", "30s"); tells timed sets apart. */
    val plannedReps: String? = null,
)

data class GameRunRow(
    val id: Long,
    val sessionId: Long,
    val date: String,
    val distanceM: Double,
    val durationSeconds: Long,
    val avgPaceSecPerKm: Int?,
)

private const val EXERCISE_SELECT =
    "SELECT pe.*, COALESCE(sub.canonicalName, e.canonicalName) AS exerciseName, e.canonicalName AS originalName, " +
        "COALESCE(pe.substituteExerciseId, pe.exerciseId) AS effectiveExerciseId " +
        "FROM PlannedExerciseEntity pe " +
        "JOIN ExerciseEntity e ON e.id = pe.exerciseId " +
        "LEFT JOIN ExerciseEntity sub ON sub.id = pe.substituteExerciseId "

private const val SET_LOG_ROW_SELECT =
    "SELECT l.*, e.canonicalName AS exerciseName, s.date AS sessionDate, pe.reps AS plannedReps FROM SetLogEntity l " +
        "JOIN ExerciseEntity e ON e.id = l.exerciseId " +
        "LEFT JOIN PlannedExerciseEntity pe ON pe.id = l.plannedExerciseId " +
        "JOIN PlannedSessionEntity s ON s.id = l.sessionId "
