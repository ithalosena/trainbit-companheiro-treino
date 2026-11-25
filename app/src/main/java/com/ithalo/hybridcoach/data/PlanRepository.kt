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
import com.ithalo.hybridcoach.core.block.BlockExercise
import com.ithalo.hybridcoach.core.block.BlockHistory
import com.ithalo.hybridcoach.core.block.BlockLifecycle
import com.ithalo.hybridcoach.core.block.BlockRun
import com.ithalo.hybridcoach.core.block.BlockSession
import com.ithalo.hybridcoach.core.block.BlockSet
import com.ithalo.hybridcoach.core.block.BlockSnapshot
import com.ithalo.hybridcoach.core.block.CloseAnswers
import com.ithalo.hybridcoach.core.block.NextBlockPrompt
import com.ithalo.hybridcoach.core.block.ProgressionRules
import com.ithalo.hybridcoach.core.block.SkeletonSession
import com.ithalo.hybridcoach.core.block.SnapshotBuilder
import com.ithalo.hybridcoach.core.plan.PlanValidator
import com.ithalo.hybridcoach.core.strength.SessionQueue
import com.ithalo.hybridcoach.core.strength.MuscleVolume
import com.ithalo.hybridcoach.core.strength.WorkClock
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.ithalo.hybridcoach.core.schedule.Modality
import com.ithalo.hybridcoach.core.schedule.ModalityQueue
import com.ithalo.hybridcoach.core.schedule.QueueSession
import com.ithalo.hybridcoach.core.schedule.Reentry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What to do with an exercise name the catalog did not recognise. */
sealed interface Resolution {
    data class MapTo(val exerciseId: String) : Resolution

    /** A new exercise; [muscles] are the groups the user picked (at least one). */
    data class CreateNew(val muscles: List<String>) : Resolution
}

/** Muscles an exercise works, as pt-BR labels. */
data class MuscleSet(val primary: List<String>, val secondary: List<String>) {
    val isEmpty: Boolean get() = primary.isEmpty() && secondary.isEmpty()
}

data class ExportedSummary(val json: String, val text: String)

data class GameSnapshot(val state: GameState, val avatar: AvatarInput)

const val VARIANT_GYM = "gym"
const val VARIANT_HOME = "home"

private val STEPS_JSON = Json { ignoreUnknownKeys = true }

/** A strength session with one of these as a primary muscle trains the legs (no hard run the day before). */
private val LEG_MUSCLES = setOf("quadríceps", "posterior de coxa", "glúteos")

/** Kept in `reentryApplied` when the user chose to follow the plan after a pause. */
const val REENTRY_DECLINED = "declined"

/** A run this long (minutes) is a long run, kept apart like an interval one. */
private const val LONG_RUN_MINUTES = 70

class PlanRepository(private val db: AppDatabase, private val context: Context) {
    private val dao = db.dao()

    fun asset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    /** Writes the write-ahead log into the main database file, so copying that one file copies everything. */
    suspend fun checkpoint() = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
    }

    /** Closes the database (a restore replaces its file; the app restarts right after). */
    fun closeDatabase() = db.close()

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

    /** The full catalog with muscles (assets/exercises_catalog.json, tools/exercises/build_catalog.py), read once. */
    private val library: List<CatalogEntry> by lazy { ExerciseCatalog.parseCatalog(asset("exercises_catalog.json")) }
    private val libraryById: Map<String, CatalogEntry> by lazy { library.associateBy { it.id } }

    /**
     * Everything the app can match a name against: the exercises in the database first (the app's own, the ones the
     * user created and the aliases learned at import), then the rest of the catalog.
     */
    suspend fun catalog(): ExerciseCatalog = withContext(Dispatchers.IO) {
        seedExercises()
        val aliases = dao.aliases().groupBy({ it.exerciseId }, { it.alias })
        val own = dao.exercises()
        val ids = own.map { it.id }.toSet()
        ExerciseCatalog(own.map { it.toEntry(aliases[it.id].orEmpty()) } + library.filter { it.id !in ids })
    }

    private fun ExerciseEntity.toEntry(aliases: List<String>): CatalogEntry {
        val lib = libraryById[id]
        val mine = muscles?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return (lib ?: CatalogEntry(id, canonicalName, emptyList(), primary = mine))
            .copy(canonicalName = canonicalName, aliases = aliases + lib?.aliases.orEmpty())
    }

    /** The app's own exercises (reviewed Portuguese names), for the list in the prompt. */
    suspend fun promptExercises(): List<CatalogEntry> =
        catalog().entries.filter { !it.autoTranslated && !it.id.startsWith("custom-") && !it.id.startsWith("fedb-") }

    /** Muscles of every exercise in the database, kept up to date (exercise id -> muscles). */
    fun observeMuscles(): Flow<Map<String, MuscleSet>> = dao.observeExercises().map { list ->
        list.associate { e -> e.id to e.toEntry(emptyList()).let { MuscleSet(it.primary, it.secondary) } }
    }.flowOn(Dispatchers.IO)

    /** Sets per muscle group logged since [since] (epoch ms), most worked first. */
    fun observeMuscleVolume(since: Long): Flow<List<Pair<String, Double>>> =
        combine(dao.observeSetExercisesSince(since), observeMuscles()) { ids, muscles ->
            MuscleVolume.sets(ids) { id -> muscles[id]?.let { it.primary to it.secondary } }
        }.flowOn(Dispatchers.IO)

    /** The pt-BR muscle groups offered when creating an exercise. */
    val muscleGroups: List<String> = listOf(
        "peitoral", "dorsal", "meio das costas", "lombar", "trapézio", "deltoides", "bíceps", "tríceps", "antebraço",
        "abdômen", "glúteos", "quadríceps", "posterior de coxa", "panturrilha", "adutores", "abdutores", "pescoço",
    )

    /** A catalog exercise chosen at import goes into the database (so plans and history can refer to it). */
    private suspend fun ensureExercise(id: String) {
        if (dao.exercises().none { it.id == id }) libraryById[id]?.let { dao.insertExercises(listOf(ExerciseEntity(it.id, it.canonicalName))) }
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
            val defs = doc.weeks.flatMap { it.sessions }.filterIsInstance<StrengthSessionDef>().flatMap { it.exercises + it.homeExercises }
            for ((name, sameName) in defs.groupBy { it.name }) {
                // The user's choice in the preview, then the id from the prompt's list, then the name.
                val known = sameName.firstNotNullOfOrNull { d -> d.exerciseId?.let { catalog.byId(it) } } ?: catalog.resolve(name)
                exerciseIds[name] = when (val r = resolutions[name]) {
                    is Resolution.MapTo -> r.exerciseId.also { ensureExercise(it); learnAlias(name, it) }
                    is Resolution.CreateNew -> ExerciseCatalog.newId(name).also {
                        dao.insertExercises(listOf(ExerciseEntity(it, name.trim(), r.muscles.joinToString(","))))
                        learnAlias(name, it)
                    }
                    null -> known?.id?.also { ensureExercise(it) } ?: ExerciseCatalog.newId(name).also {
                        dao.insertExercises(listOf(ExerciseEntity(it, name.trim())))
                        learnAlias(name, it)
                    }
                }
            }

            val previous = dao.activePlan() ?: dao.latestPlan()
            val number = (dao.maxPlanNumber() ?: 0) + 1
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
                    number = number,
                    previousPlanId = previous?.id,
                    phase = doc.plan.phase,
                ),
            )

            val sequences = HashMap<Modality, Int>()
            for (item in PlanScheduler.schedule(doc)) {
                val session = item.session
                val type = if (session is StrengthSessionDef) "strength" else "run"
                val sequence = sequences.merge(Modality.of(type), 1, Int::plus)!!
                val sessionId = dao.insertSession(
                    PlannedSessionEntity(
                        planId = planId,
                        weekNumber = item.weekNumber,
                        dayOfWeek = session.dayOfWeek.name.lowercase(),
                        date = item.date.toString(),
                        type = type,
                        title = session.title,
                        estimatedMinutes = session.estimatedMinutes,
                        optional = session.optional,
                        sequence = sequence,
                    ),
                )
                when (session) {
                    is StrengthSessionDef -> {
                        fun rows(list: List<ExerciseDef>, variant: String) = list.mapIndexed { i, e ->
                            PlannedExerciseEntity(
                                sessionId = sessionId,
                                exerciseId = exerciseIds.getValue(e.name),
                                orderIndex = i,
                                queueOrder = i,
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

    suspend fun reschedule(sessionId: Long, date: LocalDate) {
        dao.updateSessionDate(sessionId, date.toString())
        refreshQueue()
    }

    /** "Pular de vez": the session leaves its queue for good, with the reason (never the default path). */
    suspend fun skip(sessionId: Long, reason: String) {
        dao.updateSessionStatus(sessionId, SessionStatus.SKIPPED.value, reason.trim())
        refreshQueue()
    }

    /** Done today: the session moves to today and keeps how late it was against the planned date. */
    suspend fun markDone(sessionId: Long) {
        complete(sessionId)
        refreshQueue()
    }

    private suspend fun complete(sessionId: Long) {
        val s = dao.session(sessionId) ?: return
        val today = LocalDate.now()
        val planned = s.plannedDate.ifBlank { s.date }
        // Marking an old session done from the history keeps its day; only the queue's sessions move to today.
        val doneOn = if (s.status == SessionStatus.PENDING.value && LocalDate.parse(s.date) >= today.minusDays(1)) today else LocalDate.parse(s.date).coerceAtMost(today)
        dao.markCompleted(sessionId, doneOn.toString(), ModalityQueue.daysLate(LocalDate.parse(planned), doneOn))
    }

    suspend fun markPending(sessionId: Long) {
        dao.markSessionPending(sessionId)
        refreshQueue()
    }

    // --- queues per modality (core ModalityQueue) ---

    private val queueLock = Mutex()

    private fun PlannedSessionEntity.queue(hardRun: Boolean = false, legs: Boolean = false) = QueueSession(
        id, Modality.of(type), sequence, LocalDate.parse(date), LocalDate.parse(plannedDate.ifBlank { date }), status, optional, hardRun, legs,
    )

    /** The queue view of [sessions] (no hard-run or leg flags: enough for progress, next and closing). */
    fun queueOf(sessions: List<PlannedSessionEntity>): List<QueueSession> = sessions.map { it.queue() }

    /**
     * Slides the dates of the active block's pending sessions so that a missed one is the next of its modality
     * (see ModalityQueue.reschedule). Safe to call often: once the dates are right it changes nothing.
     */
    suspend fun refreshQueue(today: LocalDate = LocalDate.now()) = withContext(Dispatchers.IO) {
        queueLock.withLock {
            val plan = dao.activePlan() ?: return@withLock
            if (plan.closedAt != null) return@withLock
            val sessions = dao.sessions(plan.id)
            if (sessions.none { it.status == SessionStatus.PENDING.value }) return@withLock
            val muscles = catalog()
            val legSessions = dao.exercisesForPlan(plan.id).filter { e ->
                muscles.byId(e.effectiveExerciseId)?.primary.orEmpty().any { it in LEG_MUSCLES }
            }.map { it.exercise.sessionId }.toSet()
            val blocks = dao.runBlocksForPlan(plan.id).groupBy { it.sessionId }
            val list = sessions.map { s ->
                val hard = s.type == "run" && (blocks[s.id].orEmpty().any { it.kind == "interval" } || (s.estimatedMinutes ?: 0) >= LONG_RUN_MINUTES)
                s.queue(hardRun = hard, legs = s.id in legSessions)
            }
            val race = loadQuestionnaire()?.raceDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val moved = ModalityQueue.reschedule(list, today, race)
            if (moved.isNotEmpty()) db.withTransaction { moved.forEach { (id, date) -> dao.updateSessionDate(id, date.toString()) } }
        }
    }

    /** Pause before [session] in its modality, and the comeback it calls for (none when trained in the last week). */
    suspend fun reentryFor(session: PlannedSessionEntity, today: LocalDate = LocalDate.now()): Pair<Int, Reentry>? = withContext(Dispatchers.IO) {
        val sessions = dao.sessions(session.planId)
        val elsewhere = dao.lastDoneElsewhere(session.planId, session.type == "run")?.let { LocalDate.parse(it) }
        val plan = dao.activePlan()?.takeIf { it.id == session.planId }
        // A block with nothing done yet counts its pause from the last block, or from its own start.
        val since = elsewhere ?: plan?.let { LocalDate.parse(it.startDate).minusDays(1) }
        val gap = Reentry.gapDays(queueOf(sessions), Modality.of(session.type), today, since) ?: return@withContext null
        gap to Reentry.of(gap)
    }

    suspend fun setReentry(sessionId: Long, code: String?) = dao.setReentry(sessionId, code)

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
    ) = db.withTransaction {
        dao.setWorkClock(exercise.exercise.id, null, 0)
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

    /** The workout was opened: from now on it is "started" (Continuar) until finished. */
    suspend fun markStarted(sessionId: Long) {
        dao.markSessionStarted(sessionId, System.currentTimeMillis())
        dao.session(sessionId)?.let { s -> reentryFor(s)?.let { (gap, _) -> dao.setGapAtStart(sessionId, gap) } }
    }

    /** Saves the workout queue: [ids] are the planned exercises in the new order. */
    suspend fun saveQueue(ids: List<Long>) = db.withTransaction { ids.forEachIndexed { i, id -> dao.setQueueOrder(id, i) } }

    suspend fun setPostponed(id: Long, count: Int, reason: String?) = dao.setPostponed(id, count, reason?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun saveWorkClock(id: Long, clock: WorkClock) = dao.setWorkClock(id, clock.startedAt, clock.accumulatedMs)
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
            complete(sessionId)
        }
        refreshQueue()
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
        complete(sessionId)
        id
    }.also { refreshQueue() }

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

    // --- block lifecycle (close the block, continue in the next one) ---

    fun observeLatestPlan() = dao.observeLatestPlan()

    /** Default progression rules (assets/progression_rules.json) with the user's changes from Ajustes on top. */
    fun progressionRules(overridesJson: String?): ProgressionRules =
        overridesJson?.let { runCatching { ProgressionRules.fromJson(it) }.getOrNull() }
            ?: runCatching { ProgressionRules.fromJson(asset("progression_rules.json")) }.getOrDefault(ProgressionRules())

    suspend fun snapshot(planId: Long): BlockSnapshot? = dao.snapshot(planId)?.let { runCatching { BlockSnapshot.fromJson(it.json) }.getOrNull() }

    /** Everything about [plan] the snapshot is built from. */
    private suspend fun blockHistory(plan: PlanEntity, today: LocalDate): BlockHistory {
        val start = LocalDate.parse(plan.startDate)
        val sessions = dao.sessions(plan.id)
        val exercises = dao.exercisesForPlan(plan.id)
        val deloadWeeks = runCatching { PlanValidator.validateText(plan.rawJson).document }.getOrNull()
            ?.weeks?.filter { it.isDeload }?.flatMap { it.weeks }?.toSet().orEmpty()
        val namesBySession = exercises.groupBy { it.exercise.sessionId }
        val week1 = sessions.filter { it.weekNumber == 1 && !it.optional }
        val runs = dao.runLogsForPlan(plan.id).map { row ->
            val blocks = dao.runBlocks(row.log.sessionId)
            val interval = blocks.any { it.kind == "interval" }
            val main = blocks.filter { it.kind == (if (interval) "interval" else "steady") && it.paceMin != null && it.paceMax != null }
            BlockRun(
                date = java.time.Instant.ofEpochMilli(row.log.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                distanceM = row.log.distanceM,
                durationSeconds = row.log.durationSeconds,
                avgPaceSecPerKm = row.log.avgPaceSecPerKm,
                type = if (interval) "interval" else "steady",
                targetMin = main.minOfOrNull { it.paceMin!! },
                targetMax = main.maxOfOrNull { it.paceMax!! },
            )
        }
        return BlockHistory(
            planId = plan.id,
            planName = plan.name,
            number = plan.number,
            start = start,
            weeks = plan.durationWeeks,
            phase = plan.phase,
            sessions = sessions.map { BlockSession(it.weekNumber, LocalDate.parse(it.date), it.status, it.optional, it.skipReason) },
            exercises = exercises.map {
                // The first two exercises of a workout are its main ones (the ones the next block keeps).
                BlockExercise(it.effectiveExerciseId, it.exerciseName, it.exercise.orderIndex < 2, it.exercise.postponeCount, it.exercise.pain, it.exercise.painNote)
            },
            sets = dao.strengthLogsForPlan(plan.id).map {
                BlockSet(it.exerciseId, it.exerciseName, it.sessionId, LocalDate.parse(it.date), it.reps, it.loadKg, it.failed, it.rpe, it.plannedReps, TimedTarget.isTimed(it.plannedReps))
            },
            runs = runs,
            skeleton = week1.map { s ->
                SkeletonSession(s.dayOfWeek, s.type, s.title, namesBySession[s.id].orEmpty().sortedBy { it.exercise.orderIndex }.map { it.exerciseName })
            },
            limitations = loadQuestionnaire()?.let { q -> listOf(q.limitations, q.avoidExercises).filter { it.isNotBlank() }.joinToString("; ") }.orEmpty(),
            targets = exercises.groupBy { it.effectiveExerciseId }.mapValues { (_, l) -> l.first().exercise.reps },
            today = today,
            deloadDone = sessions.any { it.weekNumber in deloadWeeks && it.status == SessionStatus.DONE.value },
        )
    }

    /**
     * Closes [plan]: freezes its snapshot (once: closing again returns the first one), marks it closed, exports the
     * block summary and carries the close answers into the questionnaire (new limitations, start on the next Monday).
     */
    suspend fun closeBlock(plan: PlanEntity, answers: CloseAnswers, rules: ProgressionRules): BlockSnapshot = withContext(Dispatchers.IO) {
        snapshot(plan.id)?.let { return@withContext it }
        val snap = SnapshotBuilder.build(blockHistory(plan, LocalDate.now()), answers, rules)
        db.withTransaction {
            dao.insertSnapshot(BlockSnapshotEntity(plan.id, snap.toJson(), System.currentTimeMillis()))
            dao.closePlan(plan.id, System.currentTimeMillis())
        }
        exportSummary(plan)
        loadQuestionnaire()?.let { q ->
            val limits = listOf(q.limitations, answers.newLimitations).filter { it.isNotBlank() }.joinToString("; ")
            saveQuestionnaire(
                q.copy(
                    limitations = limits,
                    startDate = BlockLifecycle.nextStart(LocalDate.parse(plan.startDate), plan.durationWeeks).toString(),
                    includePreviousSummary = true,
                ),
            )
        }
        snapshot(plan.id) ?: snap
    }

    /** Pending sessions left in [plan] (to carry or drop when closing). */
    suspend fun incompleteSessions(plan: PlanEntity): Int = dao.sessions(plan.id).count { it.status == SessionStatus.PENDING.value && !it.optional }

    /** The continuity section of the next prompt: only after the latest block was closed. */
    suspend fun nextBlockSection(rules: ProgressionRules): String? {
        val latest = dao.latestPlan() ?: return null
        return snapshot(latest.id)?.let { NextBlockPrompt.section(it, rules) }
    }

    /** The snapshot a newly imported plan is checked against: the latest block, when closed. */
    suspend fun continuityBase(): BlockSnapshot? = dao.latestPlan()?.let { snapshot(it.id) }

    suspend fun setPain(plannedExerciseId: Long, pain: Boolean, note: String?) = dao.setPain(plannedExerciseId, pain, note?.trim()?.takeIf { it.isNotEmpty() })

    // --- summary ---

    suspend fun latestSummaryText(): String? = dao.latestSummary()?.text

    suspend fun hasExportedSummary(): Boolean = dao.latestSummary() != null

    suspend fun buildSummary(plan: PlanEntity): BlockSummary {
        val start = LocalDate.parse(plan.startDate)
        val records = dao.sessions(plan.id).map { SessionRecord(SessionStatus.fromValue(it.status), it.skipReason, it.optional, it.daysLate, it.reentryApplied) }
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
        val exercises = dao.exercisesForPlan(planId)
        for (e in exercises) {
            val item = e.exercise
            if (item.status == "skipped") notes += "Pulou ${e.originalName}" + reasonSuffix(item.skipReason)
            if (item.substituteExerciseId != null) notes += "Substituiu ${e.originalName} por ${e.exerciseName}" + reasonSuffix(item.substituteReason)
            if (item.postponeCount > 0) notes += "Adiou ${e.exerciseName} no treino" + reasonSuffix(item.postponeReason)
            if (item.pain) notes += "Sentiu dor em ${e.exerciseName}" + reasonSuffix(item.painNote)
        }
        // Workouts done in another order than planned (equipment taken, postponed): the order really done.
        val firstSet = dao.firstSetTimes(planId).associate { it.id to it.firstAt }
        val sessions = dao.sessions(planId).associateBy { it.id }
        for ((sessionId, list) in exercises.groupBy { it.exercise.sessionId }) {
            val done = list.filter { it.exercise.id in firstSet }
            val executed = SessionQueue.executedOrder(done.associate { it.exercise.id to firstSet.getValue(it.exercise.id) })
            val planned = done.sortedBy { it.exercise.orderIndex }.map { it.exercise.id }
            if (executed.size >= 2 && executed != planned) {
                val names = done.associate { it.exercise.id to it.exerciseName }
                val s = sessions[sessionId]
                notes += "Ordem feita em ${s?.title ?: "treino"} (${s?.date.orEmpty()}): ${executed.joinToString(", ") { names.getValue(it) }}"
            }
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
