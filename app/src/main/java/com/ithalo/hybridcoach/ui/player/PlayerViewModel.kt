package com.ithalo.hybridcoach.ui.player

import com.ithalo.hybridcoach.core.schedule.Reentry
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.core.strength.CoachLines
import com.ithalo.hybridcoach.core.strength.ExerciseProgress
import com.ithalo.hybridcoach.core.strength.RestCue
import com.ithalo.hybridcoach.core.summary.SessionStatus
import com.ithalo.hybridcoach.core.strength.RestTimer
import com.ithalo.hybridcoach.core.strength.SessionProgress
import com.ithalo.hybridcoach.core.strength.TimedTarget
import com.ithalo.hybridcoach.core.strength.SessionQueue
import com.ithalo.hybridcoach.core.strength.WorkClock
import com.ithalo.hybridcoach.core.strength.RecordSet
import com.ithalo.hybridcoach.core.strength.SummarySet
import com.ithalo.hybridcoach.core.strength.WorkoutSummaries
import com.ithalo.hybridcoach.data.ExerciseEntity
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.data.PlannedExerciseWithName
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.data.SetLogEntity
import com.ithalo.hybridcoach.data.SetLogRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class Inputs(val reps: String = "", val load: String = "", val failed: Boolean = false, val rpe: Int? = null) {
    /** Reps, or seconds for a timed exercise (up to almost 3 h). */
    val repsValue: Int? get() = reps.trim().toIntOrNull()?.takeIf { it in 0..9999 }
    val loadValue: Double? get() = load.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }
    val canLog: Boolean get() = repsValue != null && loadValue != null
}

data class RestUi(val remaining: Int, val total: Int)

/** Stopwatch of a timed exercise ("8min"): counts up past [target] until paused. */
data class WorkTimerUi(val elapsed: Int = 0, val target: Int, val running: Boolean = false) {
    val reached: Boolean get() = elapsed >= target
}

/** Best valid set ever logged for an exercise; [reps] holds seconds when [timed]. */
data class RecordUi(val loadKg: Double, val reps: Int, val timed: Boolean)

/** A record just beaten, shown in the "NOVO RECORDE!" window until dismissed. */
data class NewRecordUi(val exercise: String, val record: RecordUi, val serial: Long)

data class PlayerExercise(val item: PlannedExerciseWithName, val logs: List<SetLogEntity>) {
    val planned get() = item.exercise
    val totalSets: Int get() = planned.sets + planned.extraSets
    val skipped: Boolean get() = planned.status == "skipped"
    val progress: ExerciseProgress get() = ExerciseProgress(skipped, totalSets, logs.size)
    val substituted: Boolean get() = planned.substituteExerciseId != null
    /** The stopwatch of a timed exercise, as saved in the database. */
    val clock: WorkClock get() = WorkClock(planned.timerAccumulatedMs, planned.timerStartedAt)
}

/** An exercise just postponed, kept for "Desfazer" (the queue before and the postpone count before). */
data class PostponedUi(val exerciseName: String, val plannedExerciseId: Long, val previousQueue: List<Long>, val previousCount: Int, val previousReason: String?)

data class PlayerUi(
    val loaded: Boolean = false,
    val session: PlannedSessionEntity? = null,
    val exercises: List<PlayerExercise> = emptyList(),
    val currentIndex: Int? = null,
    val inputs: Inputs = Inputs(),
    val loadHint: Double? = null,
    val rest: RestUi? = null,
    val voiceOn: Boolean = true,
    val voiceAvailable: Boolean = true,
    val catalog: List<ExerciseEntity> = emptyList(),
    val workTimer: WorkTimerUi? = null,
    val record: RecordUi? = null,
    val newRecord: NewRecordUi? = null,
) {
    val current: PlayerExercise? get() = currentIndex?.let { exercises.getOrNull(it) }
}

/** What identifies "the set about to be logged"; the input fields are prefilled when it changes. */
private data class PositionKey(val plannedExerciseId: Long, val setsLogged: Int, val substituteId: String?, val extraSets: Int)

class PlayerViewModel(
    private val repository: PlanRepository,
    private val speaker: Speaker,
    private val sessionId: Long,
    private val notifier: RestNotifier,
) : ViewModel() {
    private val manualIndex = MutableStateFlow<Int?>(null)
    private val inputs = MutableStateFlow(Inputs())
    private val loadHint = MutableStateFlow<Double?>(null)
    private val rest = MutableStateFlow<RestUi?>(null)
    private val voiceOn = MutableStateFlow(true)
    private val catalog = MutableStateFlow<List<ExerciseEntity>>(emptyList())

    /** Wall clock for the stopwatch on screen; the stopwatch itself lives in the database. */
    private val clockNow = MutableStateFlow(System.currentTimeMillis())
    private val lastPostponed = MutableStateFlow<PostponedUi?>(null)
    val postponed: StateFlow<PostponedUi?> = lastPostponed
    private val record = MutableStateFlow<RecordUi?>(null)
    private val newRecord = MutableStateFlow<NewRecordUi?>(null)
    private val records = combine(record, newRecord) { r, n -> r to n }

    /** Exercises whose record was beaten in this session, read from the saved sets (so it survives the app being killed). */
    val sessionRecords: StateFlow<List<String>> = repository.observeRecordHistory(sessionId)
        .map { rows -> WorkoutSummaries.recordsIn(sessionId, rows.map { it.toRecordSet() }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Volume of the last time this same workout was finished (null the first time). */
    val previousVolumeKg = MutableStateFlow<Double?>(null)

    private var restJob: Job? = null
    private var restTimer: RestTimer? = null
    /** Last stopwatch second seen per exercise, for the spoken cues. */
    private val seenElapsed = HashMap<Long, Int>()

    private val exercises = combine(
        repository.observeSessionExercises(sessionId),
        repository.observeSetLogs(sessionId),
    ) { items, logs -> items.map { item -> PlayerExercise(item, logs.filter { it.plannedExerciseId == item.exercise.id }) } }

    private val base = combine(repository.observeSession(sessionId), exercises, manualIndex) { session, list, manual ->
        Triple(session, list, currentIndex(list, manual))
    }

    private val extras = combine(inputs, loadHint, rest, voiceOn, catalog) { i, hint, r, voice, cat ->
        Extras(i, hint, r, voice, cat)
    }

    private data class Extras(val inputs: Inputs, val hint: Double?, val rest: RestUi?, val voiceOn: Boolean, val catalog: List<ExerciseEntity>)

    private val workTimer = combine(base, clockNow) { (_, list, index), now ->
        val ex = index?.let { list.getOrNull(it) }
        val target = ex?.let { TimedTarget.seconds(it.planned.reps) }
        if (ex == null || target == null) null else WorkTimerUi(ex.clock.elapsedSeconds(now), target, ex.clock.running)
    }

    val ui: StateFlow<PlayerUi> = combine(base, extras, speaker.available, workTimer, records) { (session, list, index), e, available, work, recs ->
        PlayerUi(
            loaded = session != null,
            session = session,
            exercises = list,
            currentIndex = index,
            inputs = e.inputs,
            loadHint = e.hint,
            rest = e.rest,
            voiceOn = e.voiceOn,
            voiceAvailable = available,
            catalog = e.catalog,
            workTimer = work,
            record = recs.first,
            newRecord = recs.second,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUi())

    init {
        viewModelScope.launch { catalog.value = repository.exercises() }
        viewModelScope.launch {
            previousVolumeKg.value = repository.previousSameSessionSets(sessionId)?.let { rows ->
                WorkoutSummaries.volumeKg(
                    rows.map { SummarySet(it.exerciseName, it.log.repsDone, it.log.loadKg, it.log.failed, it.log.rpe, it.log.timestamp, TimedTarget.isTimed(it.plannedReps.orEmpty())) },
                )
            }
        }
        viewModelScope.launch {
            // Prefill the fields whenever the set to log changes (next set, other exercise, swap, extra set).
            ui.map { it.current }
                .distinctUntilChanged { a, b -> a?.positionKey() == b?.positionKey() }
                .collect { ex -> if (ex != null) prefill(ex) }
        }
        viewModelScope.launch { resumeRest() }
        viewModelScope.launch {
            // Opening the workout starts it: the session and Today show "Continuar" from now on.
            val session = ui.first { it.loaded }.session
            if (session != null && session.status == SessionStatus.PENDING.value) repository.markStarted(sessionId)
        }
        viewModelScope.launch { tickWork() }
        viewModelScope.launch {
            val state = ui.first { it.loaded }
            val session = state.session ?: return@launch
            if (session.status == SessionStatus.DONE.value || session.status == SessionStatus.SKIPPED.value) return@launch
            val firstSet = state.exercises.flatMap { it.logs }.minOfOrNull { it.timestamp }
            notifier.workoutStarted(session.title, firstSet ?: System.currentTimeMillis())
        }
    }

    /**
     * After the process died during a rest, picks the countdown up again from the last logged set
     * (manual ±15 s adjustments are lost).
     */
    private suspend fun resumeRest() {
        val state = ui.first { it.loaded }
        if (state.currentIndex == null || restJob != null) return
        val (ex, log) = state.exercises.flatMap { e -> e.logs.map { e to it } }.maxByOrNull { it.second.timestamp } ?: return
        val rest = ex.planned.restSeconds
        val since = System.currentTimeMillis() - log.timestamp
        if (rest > 0 && since in 0 until rest * 1000L) startRest(rest, announcement = null, startedAgoMs = since)
    }

    private fun PlayerExercise.positionKey() = PositionKey(planned.id, logs.size, planned.substituteExerciseId, planned.extraSets)

    /**
     * Drives the stopwatch on screen from the saved one: the "time done" field, the spoken cues and, when the
     * target was reached while the screen was away, one spoken notice on return.
     */
    private suspend fun tickWork() {
        val first = ui.first { it.loaded }
        first.current?.let { ex ->
            val target = TimedTarget.seconds(ex.planned.reps)
            if (target != null && ex.clock.running && ex.clock.elapsedSeconds(System.currentTimeMillis()) >= target) {
                speaker.speak(CoachLines.WORK_DONE_AWAY)
                seenElapsed[ex.planned.id] = target
            }
        }
        while (true) {
            val now = System.currentTimeMillis()
            clockNow.value = now
            val ex = ui.value.current
            val target = ex?.let { TimedTarget.seconds(it.planned.reps) }
            if (ex != null && target != null && ex.clock.running) {
                val elapsed = ex.clock.elapsedSeconds(now)
                val previous = seenElapsed[ex.planned.id] ?: elapsed
                if (elapsed != previous) {
                    inputs.update { it.copy(reps = elapsed.toString()) }
                    workCue(previous, elapsed, target)?.let { speaker.speak(it) }
                }
                seenElapsed[ex.planned.id] = elapsed
            }
            delay(200)
        }
    }

    /** [load] reduced by [cut] (0.15 = 15% less), rounded to 0.5 kg. */
    private fun lighter(load: Double, cut: Double): Double = if (cut <= 0.0) load else Math.round(load * (1 - cut) * 2) / 2.0

    private fun currentIndex(list: List<PlayerExercise>, manual: Int?): Int? {
        if (manual != null && list.getOrNull(manual)?.progress?.isComplete == false) return manual
        return SessionProgress.nextExercise(list.map { it.progress })
    }

    private suspend fun prefill(ex: PlayerExercise) {
        val p = ex.planned
        // A comeback after a pause the user accepted (core Reentry) starts the planned loads lighter.
        val cut = Reentry.fromCode(repository.observeSession(sessionId).first()?.reentryApplied)?.strengthLoadCut() ?: 0.0
        val plannedLoad = if (p.substituteExerciseId == null) p.loadKg?.let { lighter(it, cut) } else null
        val history = if (plannedLoad == null) repository.lastLoad(ex.item.effectiveExerciseId)?.let { lighter(it, cut) } else null
        val timedTarget = TimedTarget.seconds(p.reps)
        // Timed work (bike, plank) usually has no load: start at 0 kg instead of demanding one.
        val load = ex.logs.lastOrNull()?.loadKg ?: plannedLoad ?: history ?: if (timedTarget != null) 0.0 else null
        loadHint.value = history
        record.value = repository.bestSet(ex.item.effectiveExerciseId, timed = timedTarget != null)
            ?.let { RecordUi(it.loadKg, it.repsDone, timedTarget != null) }
        // A stopwatch already under way (saved before leaving the screen) shows its time, not the default.
        val clockSeconds = ex.clock.elapsedSeconds(System.currentTimeMillis()).takeIf { timedTarget != null && it > 0 }
        val reps = clockSeconds ?: SessionProgress.defaultReps(p.reps)
        inputs.value = Inputs(reps = reps?.toString().orEmpty(), load = load?.let(::formatLoad).orEmpty())
    }

    // --- work stopwatch (timed exercises) ---

    /** Starts or resumes the stopwatch of the current timed exercise; saved at once, so it survives leaving. */
    fun startWork() {
        val ex = ui.value.current ?: return
        val target = TimedTarget.seconds(ex.planned.reps) ?: return
        if (ex.clock.running) return
        cancelRest()
        val now = System.currentTimeMillis()
        val clock = ex.clock.start(now)
        seenElapsed[ex.planned.id] = clock.elapsedSeconds(now)
        viewModelScope.launch { repository.saveWorkClock(ex.planned.id, clock) }
        notifier.scheduleWorkTimer(target * 1000L - clock.elapsedMs(now))
    }

    fun pauseWork() {
        val ex = ui.value.current ?: return
        if (!ex.clock.running) return
        val clock = ex.clock.pause(System.currentTimeMillis())
        viewModelScope.launch { repository.saveWorkClock(ex.planned.id, clock) }
        notifier.cancelWorkTimer()
        inputs.update { it.copy(reps = clock.elapsedSeconds(0).toString()) }
    }

    fun resetWork() {
        val ex = ui.value.current ?: return
        seenElapsed.remove(ex.planned.id)
        viewModelScope.launch { repository.saveWorkClock(ex.planned.id, WorkClock()) }
        notifier.cancelWorkTimer()
        inputs.update { it.copy(reps = "") }
    }

    private fun workCue(previous: Int, elapsed: Int, target: Int): String? = when {
        target > 20 && previous < target - 10 && elapsed >= target - 10 -> "Faltam 10 segundos"
        previous < target && elapsed >= target -> "Tempo! Pode registrar a série"
        else -> null
    }

    // --- input fields ---

    fun onReps(v: String) = inputs.update { it.copy(reps = v.filter(Char::isDigit).take(4)) }
    fun onLoad(v: String) = inputs.update { it.copy(load = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)) }
    fun onFailed(v: Boolean) = inputs.update { it.copy(failed = v) }
    fun onRpe(v: Int?) = inputs.update { it.copy(rpe = v) }

    private fun MutableStateFlow<Inputs>.update(transform: (Inputs) -> Inputs) {
        value = transform(value)
    }

    // --- sets ---

    fun logSet() {
        val state = ui.value
        val index = state.currentIndex ?: return
        val ex = state.exercises[index]
        // A running stopwatch is stopped by logging; its exact count wins over the (lagging) field.
        val timedTarget = TimedTarget.seconds(ex.planned.reps)
        val stopwatch = ex.clock.takeIf { timedTarget != null && it.running }?.elapsedSeconds(System.currentTimeMillis())
        if (timedTarget != null) notifier.cancelWorkTimer()
        val reps = stopwatch ?: state.inputs.repsValue ?: return
        val load = state.inputs.loadValue ?: return
        // Same rule as the game engine: the first log is the baseline, later ones must beat it.
        val timed = timedTarget != null
        val best = state.record
        val beaten = !state.inputs.failed && reps >= 1 && best != null &&
            (if (timed) reps > best.reps else load > best.loadKg || (load == best.loadKg && reps > best.reps))
        viewModelScope.launch {
            // An exercise reached by the arrows is being done now: the queue follows, so leaving and coming back resumes it.
            val queue = state.exercises.map { it.planned.id }
            if (SessionProgress.nextExercise(state.exercises.map { it.progress }) != index) {
                repository.saveQueue(SessionQueue.doNow(queue, ex.planned.id, completeIds(state)))
            }
            repository.logSet(ex.item, ex.logs.size, reps, load, state.inputs.failed, state.inputs.rpe)
            val recordLine = if (beaten) {
                val now = RecordUi(load, reps, timed)
                record.value = now
                newRecord.value = NewRecordUi(ex.item.exerciseName, now, System.currentTimeMillis())
                CoachLines.NEW_RECORD + " "
            } else ""

            val progress = state.exercises.mapIndexed { i, e -> if (i == index) e.progress.copy(loggedSets = e.progress.loggedSets + 1) else e.progress }
            val keepManual = manualIndex.value?.takeIf { progress.getOrNull(it)?.isComplete == false }
            manualIndex.value = keepManual
            val next = keepManual ?: SessionProgress.nextExercise(progress)
            if (next == null) {
                cancelRest()
                speaker.speak(recordLine + CoachLines.SESSION_DONE)
                return@launch
            }
            val target = state.exercises[next]
            val same = next == index
            val nextSet = CoachLines.NextSet(
                exercise = target.item.exerciseName,
                setNumber = if (same) ex.logs.size + 2 else target.logs.size + 1,
                totalSets = target.totalSets,
                target = target.planned.reps,
                loadKg = if (same) load else target.planned.loadKg.takeIf { target.planned.substituteExerciseId == null },
            )
            val rest = ex.planned.restSeconds
            val announcement = recordLine + CoachLines.afterSet(rest, nextSet, same)
            if (rest > 0) startRest(rest, announcement, goLine = CoachLines.restOver(nextSet, same)) else speaker.speak(announcement)
        }
    }

    fun dismissRecord() {
        newRecord.value = null
    }

    fun undoLast() {
        val last = ui.value.exercises.flatMap { it.logs }.maxWithOrNull(compareBy({ it.timestamp }, { it.id })) ?: return
        cancelRest()
        manualIndex.value = null
        viewModelScope.launch { repository.deleteSetLog(last.id) }
    }

    // --- rest timer ---

    /**
     * [announcement] is said at the start ("Descanse 1 minuto e 30. Depois, ..."), "Faltam 10 segundos"
     * on long rests, the 3-2-1 countdown, then [goLine] ("Vai! Série 2 de 4.") instead of a generic end.
     */
    private fun startRest(seconds: Int, announcement: String?, startedAgoMs: Long = 0, goLine: String? = null) {
        restJob?.cancel()
        val timer = RestTimer(SystemClock.elapsedRealtime() - startedAgoMs, seconds)
        restTimer = timer
        notifier.show(timer.remainingSeconds(SystemClock.elapsedRealtime()) * 1000L)
        restJob = viewModelScope.launch {
            announcement?.let { speaker.speak(it) }
            var previous = Int.MAX_VALUE
            while (true) {
                val remaining = timer.remainingSeconds(SystemClock.elapsedRealtime())
                rest.value = RestUi(remaining, maxOf(timer.totalSeconds, remaining))
                if (timer.totalSeconds >= 30 && previous in 11..timer.totalSeconds && remaining <= 10 && remaining > 3) {
                    speaker.speak(CoachLines.TEN_SECONDS_LEFT)
                }
                RestTimer.cue(previous, remaining)?.let { cue ->
                    speaker.speak(if (cue == RestCue.DONE && goLine != null) goLine else cue.spoken)
                }
                previous = remaining
                if (remaining == 0) break
                delay(200)
            }
            notifier.done()
            delay(1_200)
            rest.value = null
        }
    }

    fun adjustRest(deltaSeconds: Int) {
        val timer = restTimer ?: return
        timer.adjust(deltaSeconds, SystemClock.elapsedRealtime())
        notifier.show(timer.remainingSeconds(SystemClock.elapsedRealtime()) * 1000L)
    }

    fun skipRest() = cancelRest()

    private fun cancelRest() {
        notifier.cancel()
        restJob?.cancel()
        restJob = null
        restTimer = null
        rest.value = null
    }

    // --- exercise actions ---

    private fun completeIds(state: PlayerUi) = state.exercises.filter { it.progress.isComplete }.map { it.planned.id }.toSet()

    /** Equipment taken: the exercise goes to the end of the queue, keeping its sets and stopwatch. */
    fun postpone(index: Int, reason: String?) {
        val state = ui.value
        val ex = state.exercises.getOrNull(index) ?: return
        val queue = state.exercises.map { it.planned.id }
        lastPostponed.value = PostponedUi(ex.item.exerciseName, ex.planned.id, queue, ex.planned.postponeCount, ex.planned.postponeReason)
        manualIndex.value = null
        cancelRest()
        viewModelScope.launch {
            repository.saveQueue(SessionQueue.postpone(queue, ex.planned.id))
            repository.setPostponed(ex.planned.id, ex.planned.postponeCount + 1, reason ?: ex.planned.postponeReason)
        }
    }

    fun undoPostpone() {
        val p = lastPostponed.value ?: return
        lastPostponed.value = null
        viewModelScope.launch {
            repository.saveQueue(p.previousQueue)
            repository.setPostponed(p.plannedExerciseId, p.previousCount, p.previousReason)
        }
    }

    fun dismissPostponed() {
        lastPostponed.value = null
    }

    /** "Fazer agora": the exercise becomes the next one in the queue. */
    fun doNow(index: Int) {
        val state = ui.value
        val ex = state.exercises.getOrNull(index) ?: return
        if (ex.progress.isComplete) return
        manualIndex.value = null
        viewModelScope.launch { repository.saveQueue(SessionQueue.doNow(state.exercises.map { it.planned.id }, ex.planned.id, completeIds(state))) }
    }

    /** Pain in the current exercise (yes/no and a note), for the block summary and the next block. */
    fun setPain(pain: Boolean, note: String) {
        val ex = ui.value.current ?: return
        viewModelScope.launch { repository.setPain(ex.planned.id, pain, if (pain) note else null) }
    }

    /** Drag in the queue list. */
    fun move(from: Int, to: Int) {
        val queue = ui.value.exercises.map { it.planned.id }
        val moved = SessionQueue.move(queue, from, to)
        if (moved == queue) return
        manualIndex.value = null
        viewModelScope.launch { repository.saveQueue(moved) }
    }

    fun goTo(index: Int) {
        if (ui.value.exercises.getOrNull(index)?.progress?.isComplete == false) manualIndex.value = index
    }

    fun skipExercise(reason: String) {
        val ex = ui.value.current ?: return
        manualIndex.value = null
        viewModelScope.launch { repository.skipExercise(ex.planned.id, reason) }
    }

    fun substitute(exerciseId: String, reason: String) {
        val ex = ui.value.current ?: return
        viewModelScope.launch { repository.substituteExercise(ex.planned.id, exerciseId, reason) }
    }

    fun addExtraSet() {
        val state = ui.value
        val index = state.exercises.indexOf(state.current ?: state.exercises.lastOrNull { !it.skipped } ?: return)
        viewModelScope.launch {
            repository.addExtraSet(state.exercises[index].planned.id)
            manualIndex.value = index
        }
    }

    fun toggleVoice(on: Boolean) {
        voiceOn.value = on
        speaker.enabled = on
    }

    fun finish(rpe: Int, note: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.finishSession(sessionId, rpe, note)
            notifier.cancel()
            notifier.workoutEnded()
            onDone()
        }
    }

    override fun onCleared() {
        // The rest notification and the workout service outlive the screen on purpose.
        restJob?.cancel()
        speaker.shutdown()
    }

    private fun SetLogRow.toRecordSet() = RecordSet(
        log.id, log.sessionId, log.exerciseId, exerciseName, log.repsDone, log.loadKg, log.failed, TimedTarget.isTimed(plannedReps.orEmpty()), log.timestamp,
    )

    private fun formatLoad(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v)
}
