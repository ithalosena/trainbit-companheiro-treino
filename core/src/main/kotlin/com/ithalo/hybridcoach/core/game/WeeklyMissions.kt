package com.ithalo.hybridcoach.core.game

import kotlin.math.roundToInt

/** What happened in one ISO week (done sessions only), accumulated while the history is replayed. */
internal class WeekProgress {
    var sessions = 0
    var runMeters = 0.0
    var sets = 0
    var prs = 0
    var early = false
}

/** What the plan schedules in one ISO week (any status). */
internal data class WeekPlan(val sessions: Int, val runs: Int, val strength: Int)

/**
 * Three missions per ISO week, derived only from the plan and the previous week, so replaying the
 * history always gives the same targets:
 * 1. SESSIONS: finish every session planned this week (3 when nothing is planned);
 * 2. volume: RUN_KM when runs are planned (10% over last week's km, or 3 km per planned run), else SETS
 *    (10% over last week's sets, or 30);
 * 3. RECORD (beat one personal record) when strength is planned, else EARLY (train by Wednesday).
 * Each mission gives [XP_MISSION]; completing all three adds [XP_ALL_MISSIONS].
 */
object WeeklyMissions {
    const val XP_MISSION = 15
    const val XP_ALL_MISSIONS = 20

    internal fun targets(plan: WeekPlan, previous: WeekProgress?): List<Pair<MissionKind, Int>> {
        val sessions = if (plan.sessions > 0) plan.sessions else 3
        val volume = if (plan.runs > 0) {
            val lastKm = (previous?.runMeters ?: 0.0) / 1000
            MissionKind.RUN_KM to if (lastKm >= 1) (lastKm * 1.1).roundToInt().coerceAtLeast(2) else 3 * plan.runs
        } else {
            val lastSets = previous?.sets ?: 0
            MissionKind.SETS to if (lastSets >= 10) (lastSets * 1.1).roundToInt() else 30
        }
        val third = if (plan.strength > 0) MissionKind.RECORD to 1 else MissionKind.EARLY to 1
        return listOf(MissionKind.SESSIONS to sessions, volume, third)
    }

    internal fun progress(kind: MissionKind, week: WeekProgress?): Int = when (kind) {
        MissionKind.SESSIONS -> week?.sessions ?: 0
        MissionKind.RUN_KM -> ((week?.runMeters ?: 0.0) / 1000).toInt()
        MissionKind.SETS -> week?.sets ?: 0
        MissionKind.RECORD -> week?.prs ?: 0
        MissionKind.EARLY -> if (week?.early == true) 1 else 0
    }

    fun title(kind: MissionKind, target: Int): String = when (kind) {
        MissionKind.SESSIONS -> if (target == 1) "Conclua 1 sessão" else "Conclua $target sessões"
        MissionKind.RUN_KM -> "Corra $target km"
        MissionKind.SETS -> "Registre $target séries"
        MissionKind.RECORD -> "Bata 1 recorde"
        MissionKind.EARLY -> "Treine até quarta"
    }

    internal fun states(plan: WeekPlan, previous: WeekProgress?, week: WeekProgress?): List<MissionState> =
        targets(plan, previous).map { (kind, target) ->
            MissionState(kind, title(kind, target), target, progress(kind, week).coerceAtMost(target), XP_MISSION)
        }
}
