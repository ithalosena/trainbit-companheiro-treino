package com.ithalo.hybridcoach.core.block

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Everything about a block the snapshot is built from, read from the app's history. */
data class BlockHistory(
    val planId: Long,
    val planName: String,
    val number: Int,
    val start: LocalDate,
    val weeks: Int,
    val phase: String?,
    val sessions: List<BlockSession>,
    val exercises: List<BlockExercise>,
    val sets: List<BlockSet>,
    val runs: List<BlockRun>,
    val skeleton: List<SkeletonSession>,
    val limitations: String,
    /** Planned rep target of each exercise id ("6-8"). */
    val targets: Map<String, String>,
    val today: LocalDate,
    val deloadDone: Boolean,
)

/**
 * Freezes a block when it is closed: the athlete's state (per exercise, running, general) and the suggested start of
 * the next block, by the [ProgressionRules]. Deterministic: the same history gives the same snapshot.
 */
object SnapshotBuilder {
    fun build(h: BlockHistory, answers: CloseAnswers, rules: ProgressionRules): BlockSnapshot {
        val general = general(h)
        val consolidation = general.adherencePct < rules.minAdherencePct ||
            (general.daysSinceLastWorkout ?: 0) >= rules.longPauseDays
        val exercises = exercises(h)
        val run = run(h)
        val loads = h.exercises.distinctBy { it.exerciseId }.mapNotNull { e ->
            val sessions = h.sets.filter { it.exerciseId == e.exerciseId }.groupBy { it.sessionId }.values.sortedBy { it.first().date }
            val target = h.targets[e.exerciseId] ?: return@mapNotNull null
            Progression.decide(sessions, target, rules, consolidation)?.let { SuggestedLoad(e.exerciseId, e.exerciseName, it.loadKg, target, it.rule) }
        }
        val suggestedRun = run?.let { r ->
            val lastWeeks = r.weeklyKm.filter { it > 0 }.takeLast(2)
            val base = if (lastWeeks.isEmpty()) 0.0 else if (consolidation) lastWeeks.average() else lastWeeks.last()
            val easy = r.avgPaceByType["steady"]
            SuggestedRun(
                week1Km = round1(base),
                longRunKm = round1(r.longestKm),
                easyPaceMin = easy?.let { it - 15 },
                easyPaceMax = easy?.let { it + 15 },
                intervalPace = r.avgPaceByType["interval"],
                maxWeeklyIncreasePct = rules.maxWeeklyRunIncreasePct,
            )
        }
        val phase = when {
            consolidation -> BlockPhase.BASE
            BlockPhase.of(h.phase) == BlockPhase.PEAK -> BlockPhase.DELOAD
            else -> BlockPhase.PROGRESSION
        }
        return BlockSnapshot(
            planId = h.planId,
            planName = h.planName,
            number = h.number,
            start = h.start.toString(),
            end = BlockLifecycle.end(h.start, h.weeks).toString(),
            closedAt = h.today.toString(),
            phase = h.phase,
            exercises = exercises,
            run = run,
            general = general,
            suggestedStart = SuggestedStart(loads, suggestedRun, consolidation, phase.value),
            answers = answers,
            skeleton = h.skeleton,
        )
    }

    private fun exercises(h: BlockHistory): List<ExerciseState> = h.exercises.groupBy { it.exerciseId }.map { (id, list) ->
        val e = list.first()
        val sets = h.sets.filter { it.exerciseId == id && !it.timed }
        val bySession = sets.groupBy { it.sessionId }.values.sortedBy { it.first().date }
        val last = bySession.lastOrNull()
        val best = sets.filter { !it.failed }.maxWithOrNull(compareBy({ Progression.epley(it.loadKg, it.reps) }, { it.reps }))
        fun e1rm(s: List<BlockSet>) = s.filter { !it.failed }.maxOfOrNull { Progression.epley(it.loadKg, it.reps) }
        val first = bySession.firstOrNull()?.let(::e1rm)
        val lastE1rm = last?.let(::e1rm)
        val rpes = sets.mapNotNull { it.rpe }
        ExerciseState(
            id = id,
            name = e.exerciseName,
            targetReps = h.targets[id].orEmpty(),
            lastLoadKg = last?.maxOf { it.loadKg },
            lastReps = last?.maxOf { it.reps },
            bestLoadKg = best?.loadKg,
            bestReps = best?.reps,
            e1rmKg = best?.let { round1(Progression.epley(it.loadKg, it.reps)) },
            e1rmTrendPct = if (first != null && lastE1rm != null && first > 0 && bySession.size >= 2) ((lastE1rm / first - 1) * 100).roundToInt() else null,
            failedSets = sets.count { it.failed },
            avgRpe = rpes.takeIf { it.isNotEmpty() }?.average()?.let(::round1),
            postponed = list.sumOf { it.postponed },
            pain = list.any { it.pain },
            main = list.any { it.main },
        )
    }

    private fun run(h: BlockHistory): RunState? {
        if (h.runs.isEmpty()) return null
        val byType = h.runs.groupBy { it.type }.mapValues { (_, rs) ->
            val km = rs.sumOf { it.distanceM } / 1000
            if (km <= 0) 0 else (rs.sumOf { it.durationSeconds } / km).roundToInt()
        }.filterValues { it > 0 }
        val weekly = (1..h.weeks).map { w ->
            val from = h.start.plusWeeks((w - 1).toLong())
            round1(h.runs.filter { !it.date.isBefore(from) && it.date.isBefore(from.plusWeeks(1)) }.sumOf { it.distanceM } / 1000)
        }
        val withTarget = h.runs.filter { it.targetMin != null && it.targetMax != null && it.avgPaceSecPerKm != null }
        val inTarget = withTarget.count { it.avgPaceSecPerKm!! in it.targetMin!!..it.targetMax!! }
        return RunState(
            avgPaceByType = byType,
            longestKm = round1(h.runs.maxOf { it.distanceM } / 1000),
            weeklyKm = weekly,
            paceAdherencePct = if (withTarget.isEmpty()) null else inTarget * 100 / withTarget.size,
        )
    }

    private fun general(h: BlockHistory): GeneralState {
        val main = h.sessions.filter { !it.optional && !it.date.isAfter(h.today) }
        val byWeek = (1..h.weeks).map { w ->
            val week = main.filter { it.week == w }
            if (week.isEmpty()) 100 else week.count { it.status == "done" } * 100 / week.size
        }
        val pastWeeks = (1..h.weeks).filter { w -> main.any { it.week == w } }
        val lastDone = h.sessions.filter { it.status == "done" }.maxOfOrNull { it.date }
        return GeneralState(
            adherenceByWeek = byWeek,
            adherencePct = if (main.isEmpty()) 100 else main.count { it.status == "done" } * 100 / main.size,
            skippedWeeks = pastWeeks.count { w -> main.filter { it.week == w }.none { it.status == "done" } },
            deloadDone = h.deloadDone,
            limitations = h.limitations,
            daysSinceLastWorkout = lastDone?.let { ChronoUnit.DAYS.between(it, h.today).toInt() },
        )
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
}
