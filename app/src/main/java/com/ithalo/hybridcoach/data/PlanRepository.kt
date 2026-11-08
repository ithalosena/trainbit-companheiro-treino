package com.ithalo.hybridcoach.data

import android.content.Context
import androidx.room.withTransaction
import com.ithalo.hybridcoach.core.exercises.CatalogEntry
import com.ithalo.hybridcoach.core.exercises.ExerciseCatalog
import com.ithalo.hybridcoach.core.plan.ExerciseDef
import com.ithalo.hybridcoach.core.plan.PaceRange
import com.ithalo.hybridcoach.core.plan.RunBlockDef
import com.ithalo.hybridcoach.core.plan.RunBlockKind
import com.ithalo.hybridcoach.core.run.Fix
import com.ithalo.hybridcoach.core.run.RunEngine
import com.ithalo.hybridcoach.core.run.RunState
import com.ithalo.hybridcoach.core.run.RunStep
import com.ithalo.hybridcoach.core.run.RunSteps
import com.ithalo.hybridcoach.core.run.StepResult
import com.ithalo.hybridcoach.core.summary.ExecutedRunStep
import com.ithalo.hybridcoach.core.summary.PlannedRunBlock
import com.ithalo.hybridcoach.core.summary.RunRecord
import com.ithalo.hybridcoach.core.summary.RunSummarizer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.ithalo.hybridcoach.core.plan.PlanDocument
import com.ithalo.hybridcoach.core.plan.RunSessionDef
import com.ithalo.hybridcoach.core.plan.StrengthSessionDef
import com.ithalo.hybridcoach.core.questionnaire.QuestionnaireAnswers
import com.ithalo.hybridcoach.core.schedule.PlanScheduler
import com.ithalo.hybridcoach.core.strength.StrengthSetRecord
import com.ithalo.hybridcoach.core.strength.StrengthSummarizer
import com.ithalo.hybridcoach.core.strength.TimedTarget
import com.ithalo.hybridcoach.core.summary.BlockSummary
import com.ithalo.hybridcoach.core.summary.SessionRecord
import com.ithalo.hybridcoach.core.summary.SessionStatus
import com.ithalo.hybridcoach.core.summary.SummaryBuilder
import androidx.room.InvalidationTracker
import com.ithalo.hybridcoach.core.avatar.AvatarInput
import com.ithalo.hybridcoach.core.avatar.AvatarInputs
import com.ithalo.hybridcoach.core.game.GameEngine
import com.ithalo.hybridcoach.core.game.GameFacts
import com.ithalo.hybridcoach.core.game.GameState
import com.ithalo.hybridcoach.core.game.PlanFact
import com.ithalo.hybridcoach.core.game.RunFact
import com.ithalo.hybridcoach.core.game.SessionFact
import com.ithalo.hybridcoach.core.game.SessionKind
import com.ithalo.hybridcoach.core.game.SetFact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What to do with an exercise name the catalog did not recognise. */
sealed interface Resolution {
    data class MapTo(val exerciseId: String) : Resolution
    data object CreateNew : Resolution
}

data class ExportedSummary(val json: String, val text: String)

data class GameSnapshot(val state: GameState, val avatar: AvatarInput)

const val VARIANT_GYM = "gym"
const val VARIANT_HOME = "home"

private val STEPS_JSON = Json { ignoreUnknownKeys = true }

class PlanRepository(private val db: AppDatabase, private val context: Context) {
    private val dao = db.dao()

    fun asset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    // --- exercises ---

    /** Adds catalog exercises missing from the database (all on first run, new ones after an app update). */
    suspend fun seedExercises() = withContext(Dispatchers.IO) {
        val existing = dao.exercises().map { it.id }.toSet()
        val entries = ExerciseCatalog.parse(asset("exercises.json")).filter { it.id !in existing }
        if (entries.isEmpty()) return@withContext
        db.withTransaction {
            dao.insertExercises(entries.map { ExerciseEntity(it.id, it.canonicalName) })
            dao.insertAliases(
                entries.flatMap { e ->
                    (e.aliases + e.canonicalName).map { ExerciseAliasEntity(ExerciseCatalog.normalize(it), e.id) }
                },
            )
        }
    }

    suspend fun exercises(): List<ExerciseEntity> = dao.exercises()

    suspend fun catalog(): ExerciseCatalog {
        seedExercises()
        val aliases = dao.aliases().groupBy({ it.exerciseId }, { it.alias })
        return ExerciseCatalog(dao.exercises().map { CatalogEntry(it.id, it.canonicalName, aliases[it.id].orEmpty()) })
    }

    // --- import ---

    suspend fun importPlan(
        doc: PlanDocument,
        rawJson: String,
        resolutions: Map<String, Resolution>,
    ): Long = withContext(Dispatchers.IO) {
        val catalog = catalog()
        db.withTransaction {
            val exerciseIds = HashMap<String, String>()
            val names = doc.weeks.flatMap { it.sessions }.filterIsInstance<StrengthSessionDef>().flatMap { it.exercises + it.homeExercises }.map { it.name }
            for (name in names.distinct()) {
                exerciseIds[name] = catalog.match(name)?.id ?: when (val r = resolutions[name] ?: Resolution.CreateNew) {
                    is Resolution.MapTo -> r.exerciseId.also { learnAlias(name, it) }
                    Resolution.CreateNew -> ExerciseCatalog.newId(name).also {
                        dao.insertExercises(listOf(ExerciseEntity(it, name.trim())))
                        learnAlias(name, it)
                    }
                }
            }

            dao.archiveActivePlans()
            val planId = dao.insertPlan(
                PlanEntity(
                    name = doc.plan.name,
                    startDate = doc.plan.startDate.toString(),
                    durationWeeks = doc.plan.durationWeeks,
                    goal = doc.plan.goal,
                    notes = doc.plan.notes,
                    rawJson = rawJson,
                    createdAt = System.currentTimeMillis(),
                    status = "active",
                ),
            )

            for (item in PlanScheduler.schedule(doc)) {
                val session = item.session
                val sessionId = dao.insertSession(
                    PlannedSessionEntity(
                        planId = planId,
                        weekNumber = item.weekNumber,
                        dayOfWeek = session.dayOfWeek.name.lowercase(),
                        date = item.date.toString(),
                        type = if (session is StrengthSessionDef) "strength" else "run",
                        title = session.title,
                        estimatedMinutes = session.estimatedMinutes,
                        optional = session.optional,
                    ),
                )
                when (session) {
                    is StrengthSessionDef -> {
                        fun rows(list: List<ExerciseDef>, variant: String) = list.mapIndexed { i, e ->
                            PlannedExerciseEntity(
                                sessionId = sessionId,
                                exerciseId = exerciseIds.getValue(e.name),
                                orderIndex = i,
                                sets = e.sets,
                                reps = e.reps,
                                loadKg = e.loadKg,
                                restSeconds = e.restSeconds,
                                rpeTarget = e.rpeTarget,
                                notes = e.notes,
                                variant = variant,
                            )
                        }
                        dao.insertPlannedExercises(rows(session.exercises, VARIANT_GYM) + rows(session.homeExercises, VARIANT_HOME))
                    }
                    is RunSessionDef -> dao.insertRunBlocks(
                        session.blocks.mapIndexed { i, b ->
                            RunBlockEntity(
                                sessionId = sessionId,
                                orderIndex = i,
                                kind = b.kind.name.lowercase(),
                                durationSeconds = b.durationSeconds,
                                distanceM = b.distanceM,
                                paceMin = b.targetPace?.min,
                                paceMax = b.targetPace?.max,
                                repeat = b.repeat,
                            )
                        },
                    )
                }
            }
            planId
        }
    }

    private suspend fun learnAlias(name: String, exerciseId: String) =
        dao.insertAliases(listOf(ExerciseAliasEntity(ExerciseCatalog.normalize(name), exerciseId)))

    // --- plan and sessions ---

    fun observeActivePlan() = dao.observeActivePlan()
    suspend fun activePlan() = dao.activePlan()
    fun observeSessions(planId: Long) = dao.observeSessions(planId)
    fun observeSession(id: Long) = dao.observeSession(id)
    fun observeSessionExercises(id: Long) = dao.observeSessionExercises(id)
    fun observeRunBlocks(id: Long) = dao.observeRunBlocks(id)

    fun observeHasHomeVersion(id: Long) = dao.observeHasHomeVersion(id)

    /** Switches a strength session between its gym and home versions ([VARIANT_GYM] / [VARIANT_HOME]). */
    suspend fun setVariant(sessionId: Long, variant: String) = dao.updateSessionVariant(sessionId, variant)

    suspend fun reschedule(sessionId: Long, date: LocalDate) = dao.updateSessionDate(sessionId, date.toString())

    suspend fun skip(sessionId: Long, reason: String) =
        dao.updateSessionStatus(sessionId, SessionStatus.SKIPPED.value, reason.trim())

    suspend fun markDone(sessionId: Long) = dao.updateSessionStatus(sessionId, SessionStatus.DONE.value, null)

    suspend fun markPending(sessionId: Long) = dao.updateSessionStatus(sessionId, SessionStatus.PENDING.value, null)

    // --- strength player ---

    fun observeSetLogs(sessionId: Long) = dao.observeSetLogs(sessionId)
    fun observeSetLogRows(sessionId: Long) = dao.observeSetLogRowsForSession(sessionId)
    fun observeRecordHistory(sessionId: Long) = dao.observeSetLogRowsSharingExercises(sessionId)

    /** The sets of the last time this same workout was finished, or null when it is the first time. */
    suspend fun previousSameSessionSets(sessionId: Long): List<SetLogRow>? =
        dao.previousSameSession(sessionId)?.let { dao.setLogRowsForSession(it.id) }
    fun observeFeedback(sessionId: Long) = dao.observeFeedback(sessionId)

    suspend fun logSet(
        exercise: PlannedExerciseWithName,
        setIndex: Int,
        reps: Int,
        loadKg: Double,
        failed: Boolean,
        rpe: Int?,
    ) {
        dao.insertSetLog(
            SetLogEntity(
                sessionId = exercise.exercise.sessionId,
                plannedExerciseId = exercise.exercise.id,
                exerciseId = exercise.effectiveExerciseId,
                setIndex = setIndex,
                repsDone = reps,
                loadKg = loadKg,
                failed = failed,
                rpe = rpe,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteSetLog(id: Long) = dao.deleteSetLog(id)
    suspend fun lastLoad(exerciseId: String): Double? = dao.lastLoad(exerciseId)
    suspend fun bestSet(exerciseId: String, timed: Boolean): SetLogEntity? =
        if (timed) dao.bestSetByTime(exerciseId) else dao.bestSetByLoad(exerciseId)
    suspend fun skipExercise(id: Long, reason: String) = dao.skipExercise(id, reason.trim())
    suspend fun substituteExercise(id: Long, exerciseId: String, reason: String) = dao.substituteExercise(id, exerciseId, reason.trim())
    suspend fun addExtraSet(id: Long) = dao.addExtraSet(id)

    /** Set by the app container: told after a session was finished (true for a run), to cheer the avatar on. */
    var onSessionFinished: ((isRun: Boolean) -> Unit)? = null

    suspend fun finishSession(sessionId: Long, rpeOverall: Int, note: String) {
        db.withTransaction {
            dao.saveFeedback(SessionFeedbackEntity(sessionId, rpeOverall, note.trim()))
            markDone(sessionId)
        }
        val isRun = dao.observeSession(sessionId).first()?.type == "run"
        onSessionFinished?.invoke(isRun)
    }

    // --- run player ---

    suspend fun runSteps(sessionId: Long): List<RunStep> = RunSteps.expand(
        dao.runBlocks(sessionId).map { b ->
            RunBlockDef(
                kind = RunBlockKind.entries.first { it.name.lowercase() == b.kind },
                durationSeconds = b.durationSeconds,
                distanceM = b.distanceM,
                targetPace = if (b.paceMin != null && b.paceMax != null) PaceRange(b.paceMin, b.paceMax) else null,
                repeat = b.repeat,
            )
        },
    )

    suspend fun runProgress() = dao.runProgress()
    fun observeRunProgress() = dao.observeRunProgress()

    suspend fun saveRunProgress(sessionId: Long, state: RunState, batteryStart: Int?) =
        dao.saveRunProgress(RunProgressEntity(sessionId, state.toJson(), batteryStart, System.currentTimeMillis()))

    suspend fun discardRunProgress() = dao.clearRunProgress()

    suspend fun saveTrackPoints(sessionId: Long, fixes: List<Fix>) {
        if (fixes.isNotEmpty()) dao.insertTrackPoints(fixes.map { RunTrackPointEntity(sessionId = sessionId, timeMs = it.timeMs, lat = it.lat, lon = it.lon, accuracyM = it.accuracyM) })
    }

    /** Stores the finished run with its km splits, clears the progress and marks the session done. */
    suspend fun saveRun(sessionId: Long, state: RunState, batteryStart: Int?, batteryEnd: Int?): Long = db.withTransaction {
        val ended = state.endedAtMs ?: System.currentTimeMillis()
        val active = state.activeMs(ended)
        val id = dao.insertRunLog(
            RunLogEntity(
                sessionId = sessionId,
                startedAt = state.startedAtMs,
                endedAt = ended,
                distanceM = state.distanceM,
                durationSeconds = active / 1000,
                avgPaceSecPerKm = RunEngine.averagePace(state.distanceM, active),
                notes = "",
                stepsJson = STEPS_JSON.encodeToString(ListSerializer(StepResult.serializer()), state.stepResults),
                batteryStart = batteryStart,
                batteryEnd = batteryEnd,
            ),
        )
        dao.insertRunSplits(state.splitSeconds.mapIndexed { i, s -> RunSplitEntity(runLogId = id, kmIndex = i + 1, seconds = s) })
        dao.clearRunProgress()
        markDone(sessionId)
        id
    }

    fun observeRunLog(sessionId: Long) = dao.observeRunLog(sessionId)
    fun observeRunSplits(runLogId: Long) = dao.observeRunSplits(runLogId)
    fun observeRunHistory() = dao.observeRunHistory()

    private suspend fun runSummaries(planId: Long) = dao.runLogsForPlan(planId).map { row ->
        val steps = runCatching { STEPS_JSON.decodeFromString(ListSerializer(StepResult.serializer()), row.log.stepsJson) }.getOrDefault(emptyList())
        RunSummarizer.summarize(
            RunRecord(
                week = row.weekNumber,
                plannedBlocks = dao.runBlocks(row.log.sessionId).map { PlannedRunBlock(it.kind, it.paceMin, it.paceMax) },
                distanceM = row.log.distanceM,
                durationSeconds = row.log.durationSeconds,
                steps = steps.filter { !it.skipped }.map { ExecutedRunStep(it.kind, it.distanceM, it.seconds) },
                rpe = row.rpeOverall,
            ),
        )
    }

    /** Deletes every plan, log and answer; the exercise catalog is seeded again. Settings are kept. */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        seedExercises()
    }

    // --- history ---

    fun observeExercisesWithLogs() = dao.observeExercisesWithLogs()
    fun observeDoneSessions() = dao.observeDoneSessions()
    fun observeExerciseHistory(exerciseId: String) = dao.observeSetLogRowsForExercise(exerciseId)

    // --- questionnaire ---

    suspend fun loadQuestionnaire(): QuestionnaireAnswers? =
        dao.questionnaire()?.let { runCatching { QuestionnaireAnswers.fromJson(it.json) }.getOrNull() }

    suspend fun saveQuestionnaire(answers: QuestionnaireAnswers) =
        dao.saveQuestionnaire(QuestionnaireEntity(json = answers.toJson()))

    // --- summary ---

    suspend fun latestSummaryText(): String? = dao.latestSummary()?.text

    suspend fun hasExportedSummary(): Boolean = dao.latestSummary() != null

    suspend fun buildSummary(plan: PlanEntity): BlockSummary {
        val start = LocalDate.parse(plan.startDate)
        val records = dao.sessions(plan.id).map { SessionRecord(SessionStatus.fromValue(it.status), it.skipReason) }
        val strength = StrengthSummarizer.summarize(
            dao.strengthLogsForPlan(plan.id).map {
                StrengthSetRecord(it.exerciseId, it.exerciseName, it.sessionId, it.reps, it.loadKg, it.failed, it.rpe, it.plannedSets, it.plannedReps, it.plannedLoadKg)
            },
        )
        return SummaryBuilder.build(
            plan.name, PlanScheduler.weekAnchor(start), PlanScheduler.endDate(start, plan.durationWeeks), records, strength, userNotes(plan.id),
            runSummaries(plan.id),
        )
    }

    /** Exercise changes and session notes the user made while training, as short plain sentences. */
    private suspend fun userNotes(planId: Long): List<String> {
        val notes = mutableListOf<String>()
        for (e in dao.exercisesForPlan(planId)) {
            val item = e.exercise
            if (item.status == "skipped") notes += "Pulou ${e.originalName}" + reasonSuffix(item.skipReason)
            if (item.substituteExerciseId != null) notes += "Substituiu ${e.originalName} por ${e.exerciseName}" + reasonSuffix(item.substituteReason)
        }
        for (f in dao.feedbackForPlan(planId)) {
            notes += "${f.title}: RPE geral ${f.rpeOverall}" + reasonSuffix(f.note)
        }
        return notes
    }

    private fun reasonSuffix(text: String?) = text?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()

    // --- gamification ---

    /** Room tables whose changes can change the game state. */
    private val gameTables = arrayOf(
        "PlannedSessionEntity", "SessionFeedbackEntity", "SetLogEntity", "RunLogEntity", "RunSplitEntity",
        "PlanEntity", "ExportedSummaryEntity", "QuestionnaireEntity",
    )

    private fun tablesChanged(): Flow<Unit> = callbackFlow {
        val observer = object : InvalidationTracker.Observer(gameTables) {
            override fun onInvalidated(tables: Set<String>) {
                trySend(Unit)
            }
        }
        db.invalidationTracker.addObserver(observer)
        trySend(Unit)
        awaitClose { db.invalidationTracker.removeObserver(observer) }
    }.flowOn(Dispatchers.IO)

    /** Reads the whole history as [GameFacts] for the core engine. */
    suspend fun gameFacts(today: LocalDate = LocalDate.now()): GameFacts = withContext(Dispatchers.IO) {
        // A complementary session only exists for the game once it is done: pending or skipped, it is not a miss
        // (nor part of the block's total).
        val sessions = dao.gameSessions().filter { !it.optional || it.status == SessionStatus.DONE.value }
        val splits = dao.allRunSplits().groupBy({ it.runLogId }, { it.seconds })
        val byPlan = sessions.groupBy { it.planId }
        GameFacts(
            sessions = sessions.mapNotNull { s ->
                val date = runCatching { LocalDate.parse(s.date) }.getOrNull() ?: return@mapNotNull null
                SessionFact(
                    sessionId = s.id,
                    planId = s.planId,
                    date = date,
                    kind = if (s.type == "run") SessionKind.RUN else SessionKind.STRENGTH,
                    done = s.status == SessionStatus.DONE.value,
                    skipped = s.status == SessionStatus.SKIPPED.value,
                    rpe = s.rpe,
                )
            },
            sets = dao.gameSets().mapNotNull { r ->
                val date = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@mapNotNull null
                SetFact(r.sessionId, r.exerciseId, r.exerciseName, r.reps, r.loadKg, r.failed, date, timed = r.plannedReps?.let { TimedTarget.isTimed(it) } == true)
            },
            runs = dao.gameRuns().mapNotNull { r ->
                val date = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@mapNotNull null
                RunFact(r.sessionId, date, r.distanceM, r.durationSeconds, r.avgPaceSecPerKm, splits[r.id].orEmpty())
            },
            plans = dao.allPlans().mapNotNull { p ->
                val start = runCatching { LocalDate.parse(p.startDate) }.getOrNull() ?: return@mapNotNull null
                val planSessions = byPlan[p.id].orEmpty()
                PlanFact(
                    planId = p.id,
                    startDate = start,
                    endDate = PlanScheduler.endDate(start, p.durationWeeks),
                    totalSessions = planSessions.size,
                    doneSessions = planSessions.count { it.status == SessionStatus.DONE.value },
                )
            },
            summariesExported = dao.summaryCount(),
            questionnaireDone = dao.hasQuestionnaire(),
            today = today,
        )
    }

    /** Game state, recomputed (lightly debounced, off the main thread) whenever the history changes. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    /** The game state and the pet avatar's inputs, both read from one pass over the history. */
    fun observeGameSnapshot(): Flow<GameSnapshot> = tablesChanged()
        .debounce(250)
        .mapLatest {
            val facts = gameFacts()
            withContext(Dispatchers.Default) {
                val state = GameEngine.evaluate(facts)
                GameSnapshot(state, AvatarInputs.from(facts, state.stats.currentWeekStreak))
            }
        }
        .flowOn(Dispatchers.Default)

    suspend fun exportSummary(plan: PlanEntity): ExportedSummary = withContext(Dispatchers.IO) {
        val summary = buildSummary(plan)
        val exported = ExportedSummary(SummaryBuilder.toJson(summary), SummaryBuilder.toText(summary))
        dao.saveSummary(ExportedSummaryEntity(plan.id, exported.json, exported.text, System.currentTimeMillis()))
        exported
    }
}
