package com.ithalo.hybridcoach.core.run

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** What the coach should say; the app turns each cue into pt-BR speech. */
sealed interface Cue {
    data class StepStart(val step: RunStep, val number: Int, val total: Int) : Cue
    data class StepEnd(val step: RunStep) : Cue
    data class StepHalf(val step: RunStep) : Cue
    /** The current block ends in about [seconds]; [next] is the block that follows (null on the last one). */
    data class StepEndingSoon(val seconds: Int, val next: RunStep?) : Cue
    data class Kilometer(val km: Int, val splitSeconds: Int, val avgPaceSecPerKm: Int) : Cue
    data class Pace(val advice: PaceAdvice, val paceSecPerKm: Int) : Cue
    data class Finished(val distanceM: Double, val activeSeconds: Long, val avgPaceSecPerKm: Int?) : Cue
    /** Every block is done; the run keeps recording (the way back) until it is finished by hand or by standing still. */
    data object BlocksDone : Cue
    /** Standing still mid-run: the run was paused on its own; ask if everything is fine. */
    data object AutoPaused : Cue
    /** Moving again after an automatic pause: the run goes on by itself. */
    data object AutoResumed : Cue
}

/** Distance and active time spent in one executed block. */
@Serializable
data class StepResult(val kind: String, val distanceM: Double, val seconds: Long, val skipped: Boolean = false)

/** Everything needed to resume a run after the process dies. Times are wall-clock ms. */
@Serializable
data class RunState(
    val startedAtMs: Long,
    val pausedAtMs: Long? = null,
    val pausedTotalMs: Long = 0,
    val distanceM: Double = 0.0,
    val stepIndex: Int = 0,
    val stepStartActiveMs: Long = 0,
    val stepStartDistanceM: Double = 0.0,
    val halfAnnounced: Boolean = false,
    val soonAnnounced: Boolean = false,
    /** Active ms at each completed km. */
    val kmMarksMs: List<Long> = emptyList(),
    val stepResults: List<StepResult> = emptyList(),
    val lastFix: Fix? = null,
    val finished: Boolean = false,
    /** All blocks are behind: no current step, but distance and time still count. */
    val blocksDone: Boolean = false,
    /** Active ms of the last counted movement (null before the first one); drives the stand-still checks. */
    val lastMoveActiveMs: Long? = null,
    /** The current pause was started by standing still (moving again resumes it). */
    val autoPaused: Boolean = false,
    /** Where the runner stood when the automatic pause started. */
    val pauseAnchor: Fix? = null,
    val endedAtMs: Long? = null,
    /** Treadmill mode: distance comes from this speed instead of GPS. Null means GPS. */
    val treadmillKmh: Double? = null,
    /** Active ms up to which treadmill distance has been added. */
    val treadmillUntilActiveMs: Long? = null,
) {
    val paused: Boolean get() = pausedAtMs != null

    fun activeMs(nowMs: Long): Long = ((pausedAtMs ?: nowMs) - startedAtMs - pausedTotalMs).coerceAtLeast(0)

    /** Seconds of each completed km. */
    val splitSeconds: List<Int>
        get() = kmMarksMs.mapIndexed { i, t -> ((t - (kmMarksMs.getOrNull(i - 1) ?: 0L)) / 1000.0).roundToInt() }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        fun fromJson(text: String): RunState = JSON.decodeFromString(serializer(), text)
    }
}

/**
 * Pure run logic: distance from filtered fixes, block progress, km splits, milestones and pace alerts.
 * Not thread safe; call it from one thread. Every method returns the cues to speak, in order.
 */
class RunEngine(val steps: List<RunStep>, private val settings: RunSettings, initial: RunState) {
    var state: RunState = initial
        private set

    private val smoother = PaceSmoother(settings.smoothingSeconds)
    private val coach = PaceCoach(settings)

    val currentStep: RunStep? get() = steps.getOrNull(state.stepIndex)

    /** Smoothed current pace, or null while there is not enough data. */
    fun currentPace(): Int? = smoother.pace()

    fun start(nowMs: Long): List<Cue> =
        steps.firstOrNull()?.let { listOf(Cue.StepStart(it, 1, steps.size)) } ?: finish(nowMs)

    /** Changes the treadmill speed (km/h); distance so far is kept at the old speed. */
    fun setTreadmillSpeed(kmh: Double, nowMs: Long) {
        if (state.finished || state.treadmillKmh == null) return
        integrateTreadmill(nowMs)
        state = state.copy(treadmillKmh = kmh.coerceIn(0.0, MAX_TREADMILL_KMH))
    }

    private fun integrateTreadmill(nowMs: Long) {
        val kmh = state.treadmillKmh ?: return
        val active = state.activeMs(nowMs)
        val from = state.treadmillUntilActiveMs ?: 0L
        val meters = kmh / 3.6 * (active - from).coerceAtLeast(0) / 1000.0
        state = state.copy(distanceM = state.distanceM + meters, treadmillUntilActiveMs = active)
        smoother.add(active, state.distanceM)
    }

    fun onFix(fix: Fix, nowMs: Long): List<Cue> {
        if (state.finished || state.treadmillKmh != null) return emptyList()
        if (state.paused) return if (state.autoPaused) autoResume(fix, nowMs) else emptyList()
        when (val r = LocationFilter.check(state.lastFix, fix)) {
            LocationFilter.Result.Reject -> return evaluate(nowMs)
            is LocationFilter.Result.Accept -> {
                // lastFix is the anchor of the next segment: it only moves once the runner is clearly away from it,
                // so GPS jitter while moving slowly or standing is not added up as distance.
                if (state.lastFix == null || r.meters >= LocationFilter.MIN_STEP_M) {
                    val moved = state.lastFix != null
                    state = state.copy(distanceM = state.distanceM + r.meters, lastFix = fix)
                    if (moved) state = state.copy(lastMoveActiveMs = state.activeMs(nowMs))
                }
                smoother.add(state.activeMs(nowMs), state.distanceM)
            }
        }
        return evaluate(nowMs)
    }

    fun onTick(nowMs: Long): List<Cue> {
        if (state.finished || state.paused) return emptyList()
        integrateTreadmill(nowMs)
        return evaluate(nowMs)
    }

    fun pause(nowMs: Long) {
        if (state.finished || state.paused) return
        integrateTreadmill(nowMs)
        state = state.copy(pausedAtMs = nowMs)
    }

    fun resume(nowMs: Long) {
        val pausedAt = state.pausedAtMs ?: return
        // Movement during the pause is not counted: the next fix starts a new segment.
        state = state.copy(
            pausedAtMs = null, pausedTotalMs = state.pausedTotalMs + (nowMs - pausedAt), lastFix = null,
            autoPaused = false, pauseAnchor = null,
        )
        // Standing still is measured again from now on.
        if (state.lastMoveActiveMs != null) state = state.copy(lastMoveActiveMs = state.activeMs(nowMs))
        smoother.clear()
        coach.reset()
    }

    /** "I need a break": the automatic pause becomes a normal one, so walking around does not resume the run. */
    fun holdPause() {
        if (state.paused) state = state.copy(autoPaused = false, pauseAnchor = null)
    }

    /** While auto-paused, moving [AUTO_RESUME_M] away from where the runner stopped resumes the run. */
    private fun autoResume(fix: Fix, nowMs: Long): List<Cue> {
        if (fix.accuracyM > LocationFilter.MAX_ACCURACY_M) return emptyList()
        val anchor = state.pauseAnchor ?: run {
            state = state.copy(pauseAnchor = fix)
            return emptyList()
        }
        if (GeoMath.distance(anchor, fix) < AUTO_RESUME_M) return emptyList()
        resume(nowMs)
        return listOf(Cue.AutoResumed)
    }

    /**
     * Pauses (or, with every block done, finishes) after standing still: the still time is taken out, as if
     * the pause had started at the last movement. GPS runs only; the treadmill has no position to watch.
     */
    private fun stillCheck(nowMs: Long, active: Long): List<Cue>? {
        val moved = state.lastMoveActiveMs ?: return null
        if (state.treadmillKmh != null || state.paused) return null
        val stillMs = active - moved
        val limit = if (state.blocksDone) AUTO_FINISH_STILL_SECONDS else AUTO_PAUSE_STILL_SECONDS
        if (stillMs < limit * 1000L) return null
        state = state.copy(pausedAtMs = nowMs - stillMs)
        if (state.blocksDone) return finish(nowMs)
        state = state.copy(autoPaused = true, pauseAnchor = state.lastFix)
        return listOf(Cue.AutoPaused)
    }

    fun skipStep(nowMs: Long): List<Cue> {
        if (state.finished) return emptyList()
        return advance(nowMs, skipped = true)
    }

    fun finish(nowMs: Long): List<Cue> {
        if (state.finished) return emptyList()
        if (!state.paused) integrateTreadmill(nowMs)
        val active = state.activeMs(nowMs)
        val current = currentStep
        val results = when {
            current != null -> state.stepResults + result(current, active, skipped = false)
            // The stretch after the last block (the way back), when there was one.
            state.blocksDone && active > state.stepStartActiveMs -> state.stepResults + result(null, active, skipped = false)
            else -> state.stepResults
        }
        state = state.copy(finished = true, endedAtMs = nowMs, pausedAtMs = null, pausedTotalMs = state.pausedTotalMs + (state.pausedAtMs?.let { nowMs - it } ?: 0), stepResults = results)
        return listOf(Cue.Finished(state.distanceM, active / 1000, averagePace(state.distanceM, active)))
    }

    private fun evaluate(nowMs: Long): List<Cue> {
        val cues = mutableListOf<Cue>()
        val active = state.activeMs(nowMs)

        // Kilometer marks.
        while (state.distanceM >= (state.kmMarksMs.size + 1) * 1000.0) {
            val marks = state.kmMarksMs + active
            state = state.copy(kmMarksMs = marks)
            val km = marks.size
            cues += Cue.Kilometer(km, state.splitSeconds.last(), (active / 1000.0 / km).roundToInt())
        }

        stillCheck(nowMs, active)?.let { return cues + it }

        val step = currentStep ?: return cues
        val stepSeconds = (active - state.stepStartActiveMs) / 1000.0
        val stepMeters = state.distanceM - state.stepStartDistanceM
        val fraction = when {
            step.durationSeconds != null -> stepSeconds / step.durationSeconds
            step.distanceM != null -> stepMeters / step.distanceM
            else -> 1.0
        }
        if (fraction >= 1.0) return cues + advance(nowMs, skipped = false)

        if (!state.halfAnnounced && fraction >= 0.5 && isLong(step)) {
            state = state.copy(halfAnnounced = true)
            cues += Cue.StepHalf(step)
        }

        // Every block long enough for it warns ~10 s before it ends, saying what comes next.
        if (!state.soonAnnounced && (step.durationSeconds ?: Int.MAX_VALUE) >= MIN_SOON_BLOCK_SECONDS) {
            val remaining = when {
                step.durationSeconds != null -> step.durationSeconds - stepSeconds
                step.distanceM != null -> smoother.pace()?.let { (step.distanceM - stepMeters) / 1000.0 * it }
                else -> null
            }
            if (remaining != null && remaining <= SOON_SECONDS) {
                state = state.copy(soonAnnounced = true)
                cues += Cue.StepEndingSoon(remaining.roundToInt().coerceAtLeast(1), steps.getOrNull(state.stepIndex + 1))
            }
        }

        // The smoothed pace still reflects the previous block for one window, so wait before judging it.
        if (stepSeconds >= settings.smoothingSeconds) {
            val pace = smoother.pace()
            coach.evaluate(active, pace, step.pace)?.let { advice -> if (pace != null) cues += Cue.Pace(advice, pace) }
        }
        return cues
    }

    private fun advance(nowMs: Long, skipped: Boolean): List<Cue> {
        val step = currentStep ?: return emptyList()
        val active = state.activeMs(nowMs)
        val cues = mutableListOf<Cue>(Cue.StepEnd(step))
        state = state.copy(
            stepIndex = state.stepIndex + 1,
            stepStartActiveMs = active,
            stepStartDistanceM = state.distanceM,
            halfAnnounced = false,
            soonAnnounced = false,
            stepResults = state.stepResults + result(step, active, skipped),
        )
        coach.reset()
        val next = currentStep
        if (next != null) {
            cues += Cue.StepStart(next, state.stepIndex + 1, steps.size)
        } else {
            state = state.copy(blocksDone = true)
            cues += Cue.BlocksDone
        }
        return cues
    }

    private fun result(step: RunStep?, activeMs: Long, skipped: Boolean) = StepResult(
        kind = step?.kind?.name?.lowercase() ?: EXTRA_KIND,
        distanceM = state.distanceM - state.stepStartDistanceM,
        seconds = (activeMs - state.stepStartActiveMs) / 1000,
        skipped = skipped,
    )

    /** Half-way is only announced on blocks long enough for it to help (2 min or 1 km). */
    private fun isLong(step: RunStep) = (step.durationSeconds ?: 0) >= 120 || (step.distanceM ?: 0) >= 1000

    companion object {
        const val SOON_SECONDS = 10

        /** Standing still this long mid-run pauses it; after the last block, it finishes it. */
        const val AUTO_PAUSE_STILL_SECONDS = 45
        const val AUTO_FINISH_STILL_SECONDS = 60

        /** Meters away from where the runner stopped that count as moving again (well beyond GPS jitter). */
        const val AUTO_RESUME_M = 25.0

        /** Step result kind of the stretch run after the last block. */
        const val EXTRA_KIND = "extra"

        /** Shorter timed blocks get no "ending soon" warning: it would talk over the block's own start. */
        const val MIN_SOON_BLOCK_SECONDS = 25
        const val MAX_TREADMILL_KMH = 30.0

        /** Pace (sec/km) for a treadmill speed, and back. */
        fun paceForKmh(kmh: Double): Int? = if (kmh <= 0) null else (3600 / kmh).roundToInt()
        fun kmhForPace(secPerKm: Int): Double = 3600.0 / secPerKm

        fun averagePace(distanceM: Double, activeMs: Long): Int? =
            if (distanceM < 50) null else (activeMs / 1000.0 / (distanceM / 1000.0)).roundToInt()
    }
}
