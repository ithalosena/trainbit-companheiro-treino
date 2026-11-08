package com.ithalo.hybridcoach.core.game

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Deterministic gamification: replays the history day by day (oldest first) and derives XP, level,
 * trophies, items, stats and personal records. Facts dated after [GameFacts.today] are ignored.
 *
 * XP rules (skipped and pending sessions give nothing; sets and runs only count inside done sessions):
 * - done session (strength or run): [XP_SESSION];
 * - each logged set: [XP_PER_SET];
 * - running: [XP_PER_200M] per full 200 m (5 XP/km);
 * - new personal record (beating an existing best of an exercise; the first log is just the baseline):
 *   [XP_PR] per exercise and session, and per running best beaten (distance, time, pace, fastest km);
 * - week streak: on the first done session of the N-th consecutive ISO week (N >= 2), 5 × min(N, 10);
 * - perfect week (an ISO week with 2+ sessions, all done): [XP_PERFECT_WEEK] on its last session date;
 * - block finished with at least half of its sessions done: [XP_BLOCK], plus [XP_BLOCK_PERFECT] at 100%.
 *   A block finishes on the date of its last done session when all are done, otherwise the day after
 *   its end date has started (end date < today);
 * - weekly missions ([WeeklyMissions]): each one on the day it is completed, plus a bonus for all three;
 * - questionnaire answered: [XP_QUESTIONNAIRE]; each summary exported: [XP_SUMMARY];
 * - each unlocked trophy adds its own [AchievementDef.xp].
 *
 * The questionnaire and exports have no date of their own: the questionnaire is credited on the
 * earliest known date (first plan start or session), and the i-th export on the i-th block finish
 * date (or that earliest date when no block has finished), so dates stay stable over time.
 */
object GameEngine {
    const val XP_SESSION = 20
    const val XP_PER_SET = 1
    const val XP_PER_200M = 1
    const val XP_PR = 5
    const val XP_PERFECT_WEEK = 30
    const val XP_BLOCK = 60
    const val XP_BLOCK_PERFECT = 90
    const val XP_QUESTIONNAIRE = 10
    const val XP_SUMMARY = 10
    const val XP_STREAK_PER_WEEK = 3
    const val STREAK_CAP_WEEKS = 10
    const val XP_LOG_SIZE = 50
    const val COMEBACK_DAYS = 28

    private val ptBr = Locale.forLanguageTag("pt-BR")

    fun evaluate(facts: GameFacts): GameState {
        val today = facts.today
        val sessions = facts.sessions.filter { !it.date.isAfter(today) }.sortedWith(compareBy({ it.date }, { it.sessionId }))
        val doneIds = sessions.filter { it.done && !it.skipped }.map { it.sessionId }.toSet()
        val setsBySession = facts.sets.filter { it.sessionId in doneIds }.groupBy { it.sessionId }
        val runsBySession = facts.runs.filter { it.sessionId in doneIds }.groupBy { it.sessionId }

        // Day-level events that are not sessions.
        val perfectWeekDates = facts.sessions.groupBy { monday(it.date) }.values
            .filter { week -> week.size >= 2 && week.all { it.done && !it.skipped } }
            .map { week -> week.maxOf { it.date } }
            .filter { !it.isAfter(today) }
        val blockFinishes = facts.plans.mapNotNull { plan -> blockFinish(plan, sessions, today)?.let { plan to it } }
        val anchor = (facts.plans.map { it.startDate } + sessions.map { it.date }).filter { !it.isAfter(today) }.minOrNull() ?: today
        val questionnaireDate = if (facts.questionnaireDone) anchor else null
        val finishDates = blockFinishes.map { it.second }.sorted()
        val summaryDates = (0 until facts.summariesExported).map { i -> finishDates.getOrNull(i) ?: finishDates.lastOrNull() ?: anchor }

        val days = (sessions.map { it.date } + perfectWeekDates + finishDates + summaryDates + listOfNotNull(questionnaireDate))
            .toSortedSet()

        val weekPlans = facts.sessions.groupBy { monday(it.date) }.mapValues { (_, list) ->
            WeekPlan(list.size, list.count { it.kind == SessionKind.RUN }, list.count { it.kind == SessionKind.STRENGTH })
        }
        val weeks = HashMap<LocalDate, WeekProgress>()
        val missionsDone = HashSet<String>()

        val p = Progress()
        val log = mutableListOf<XpEvent>()
        val unlockedOn = mutableMapOf<String, LocalDate>()
        val bests = mutableMapOf<String, PersonalRecord>()
        val runBests = mutableMapOf<RunRecordKind, RunRecord>()
        var lastWeek: LocalDate? = null
        var lastDone: LocalDate? = null
        val byDay = sessions.groupBy { it.date }

        fun xp(date: LocalDate, amount: Int, reason: String) {
            if (amount > 0) log += XpEvent(date, amount, reason)
        }

        for (day in days) {
            val kindsToday = mutableSetOf<SessionKind>()
            for (s in byDay[day].orEmpty()) {
                if (s.skipped) {
                    p.skips++
                    continue
                }
                if (!s.done) continue

                lastDone?.let { if (ChronoUnit.DAYS.between(it, day) >= COMEBACK_DAYS) p.comebacks++ }
                lastDone = day
                val week = monday(day)
                if (week != lastWeek) {
                    p.weekStreak = if (lastWeek == week.minusWeeks(1)) p.weekStreak + 1 else 1
                    p.bestWeekStreak = maxOf(p.bestWeekStreak, p.weekStreak)
                    lastWeek = week
                    if (p.weekStreak >= 2) xp(day, XP_STREAK_PER_WEEK * minOf(p.weekStreak, STREAK_CAP_WEEKS), "Sequência de ${p.weekStreak} semanas")
                }

                p.sessionsDone++
                val wp = weeks.getOrPut(week) { WeekProgress() }
                wp.sessions++
                if (day.dayOfWeek <= DayOfWeek.WEDNESDAY) wp.early = true
                kindsToday += s.kind
                when (s.kind) {
                    SessionKind.STRENGTH -> p.strengthDone++
                    SessionKind.RUN -> p.runsDone++
                }
                xp(day, XP_SESSION, if (s.kind == SessionKind.STRENGTH) "Sessão de força concluída" else "Corrida concluída")
                if (s.rpe == 10) p.rpe10++
                if (day.dayOfWeek == DayOfWeek.SUNDAY) p.sundays++
                if ((day.month == Month.DECEMBER && day.dayOfMonth == 25) || (day.month == Month.JANUARY && day.dayOfMonth == 1)) p.holidays++

                val sets = setsBySession[s.sessionId].orEmpty()
                if (sets.isNotEmpty()) {
                    p.totalSets += sets.size
                    wp.sets += sets.size
                    // Timed sets store seconds in reps: they are not weight lifted.
                    p.tonnageKg += sets.filter { !it.timed }.sumOf { it.reps * it.loadKg }
                    p.maxFailsInSession = maxOf(p.maxFailsInSession, sets.count { it.failed })
                    xp(day, sets.size * XP_PER_SET, "${sets.size} ${if (sets.size == 1) "série registrada" else "séries registradas"}")
                }
                val prNames = linkedSetOf<String>()
                for (set in sets) {
                    p.exercises += set.exerciseId
                    if (set.failed || set.reps < 1) continue
                    if (!set.timed) p.maxValidLoadKg = maxOf(p.maxValidLoadKg, set.loadKg)
                    val best = bests[set.exerciseId]
                    if (best == null || isBetter(set, best)) {
                        if (best != null) prNames += set.exerciseName
                        bests[set.exerciseId] = PersonalRecord(set.exerciseId, set.exerciseName, set.loadKg, set.reps, day, set.timed)
                    }
                }
                for (name in prNames) {
                    p.prs++
                    wp.prs++
                    xp(day, XP_PR, "Recorde pessoal: $name")
                }

                for (run in runsBySession[s.sessionId].orEmpty()) {
                    p.runMeters += run.distanceM
                    wp.runMeters += run.distanceM
                    p.runSeconds += run.durationSeconds
                    p.longestRunM = maxOf(p.longestRunM, run.distanceM)
                    val pace = RunBests.pace(run)
                    if (pace != null && run.distanceM >= 1000) p.bestPace = minOf(p.bestPace ?: pace, pace)
                    if (pace != null && run.distanceM >= 3000 && pace < 300) p.sub5Runs++
                    if (isNegativeSplit(run.splitSeconds)) p.negativeSplits++
                    // Running bests: the first value of a kind is the baseline, beating it later is a record (XP, like a lift).
                    for ((kind, value) in RunBests.values(run)) {
                        val old = runBests[kind]
                        if (old != null && !RunBests.better(kind, value, old.value)) continue
                        runBests[kind] = RunRecord(kind, value, day)
                        if (old != null) {
                            p.prs++
                            wp.prs++
                            xp(day, XP_PR, "Recorde de corrida: ${RunBests.label(kind)}")
                        }
                    }
                    xp(day, (run.distanceM / 200).toInt() * XP_PER_200M, "${km(run.distanceM)} km corridos")
                }
            }
            if (kindsToday.size == 2) p.hybridDays++

            if (kindsToday.isNotEmpty()) {
                val w = monday(day)
                val states = WeeklyMissions.states(weekPlans[w] ?: NO_PLAN, weeks[w.minusWeeks(1)], weeks[w])
                states.forEachIndexed { i, m ->
                    if (m.done && missionsDone.add("$w:$i")) xp(day, m.xp, "Missão: ${m.title}")
                }
                if (states.all { it.done } && missionsDone.add("$w:all")) {
                    xp(day, WeeklyMissions.XP_ALL_MISSIONS, "Missões da semana completas")
                }
            }

            if (day in perfectWeekDates) {
                p.perfectWeeks++
                xp(day, XP_PERFECT_WEEK, "Semana perfeita")
            }
            for ((plan, date) in blockFinishes) {
                if (date != day || plan.doneSessions * 2 < plan.totalSessions || plan.doneSessions == 0) continue
                p.blocksDone++
                xp(day, XP_BLOCK, "Bloco concluído")
                if (plan.doneSessions >= plan.totalSessions) {
                    p.blocksPerfect++
                    xp(day, XP_BLOCK_PERFECT, "Bloco 100% concluído")
                }
            }
            if (day == questionnaireDate) {
                p.questionnaire = 1
                xp(day, XP_QUESTIONNAIRE, "Questionário respondido")
            }
            repeat(summaryDates.count { it == day }) {
                p.summaries++
                xp(day, XP_SUMMARY, "Resumo exportado")
            }

            for (def in AchievementCatalog.all) {
                if (def.id == AchievementCatalog.PLATINUM_ID || def.id in unlockedOn) continue
                if (AchievementCatalog.progressOf(def, p) >= def.target) {
                    unlockedOn[def.id] = day
                    xp(day, def.xp, "Troféu: ${def.title}")
                }
            }
            val platinum = AchievementCatalog.all.last()
            if (platinum.id !in unlockedOn && platinumProgress(unlockedOn.keys) >= platinum.target) {
                unlockedOn[platinum.id] = day
                xp(day, platinum.xp, "Troféu: ${platinum.title}")
            }
        }

        val achievements = AchievementCatalog.all.map { def ->
            val progress = if (def.id == AchievementCatalog.PLATINUM_ID) platinumProgress(unlockedOn.keys)
            else AchievementCatalog.progressOf(def, p)
            AchievementState(def, progress.coerceAtMost(def.target), unlockedOn[def.id])
        }
        val total = log.sumOf { it.amount }
        val level = Leveling.levelFor(total)
        val step = Leveling.stepXp(level)
        val items = ItemCatalog.all.map { def ->
            val unlocked = when (val u = def.unlock) {
                is ItemUnlock.Level -> level >= u.level
                is ItemUnlock.Achievement -> u.id in unlockedOn
            }
            ItemState(def, unlocked)
        }
        val thisWeek = monday(today)
        val stats = GameStats(
            sessionsDone = p.sessionsDone,
            strengthSessions = p.strengthDone,
            runSessions = p.runsDone,
            totalSets = p.totalSets,
            tonnageKg = p.tonnageKg,
            totalKm = p.runMeters / 1000,
            currentWeekStreak = if (lastWeek == thisWeek || lastWeek == thisWeek.minusWeeks(1)) p.weekStreak else 0,
            bestWeekStreak = p.bestWeekStreak,
            longestRunKm = p.longestRunM / 1000,
            bestPaceSecPerKm = p.bestPace,
        )
        return GameState(
            xp = total,
            level = level,
            xpIntoLevel = (total - Leveling.xpForLevel(level)).coerceAtMost(step),
            xpForNextLevel = step,
            rankTitle = Leveling.rankTitle(level),
            achievements = achievements,
            items = items,
            stats = stats,
            records = bests.values.sortedWith(compareBy({ it.exerciseName }, { it.exerciseId })),
            xpLog = log.asReversed().take(XP_LOG_SIZE),
            missions = if (facts.sessions.isEmpty()) emptyList()
            else WeeklyMissions.states(weekPlans[thisWeek] ?: NO_PLAN, weeks[thisWeek.minusWeeks(1)], weeks[thisWeek]),
            runRecords = RunRecordKind.entries.mapNotNull { runBests[it] },
        )
    }

    private val NO_PLAN = WeekPlan(0, 0, 0)

    /**
     * What is new in [current] compared with what the user has already seen. Ordered: one level up (for the
     * current level, even if several were skipped), trophies by tier (bronze to platinum, then unlock date),
     * items in catalog order.
     */
    fun unlocksSince(seenLevel: Int, seenAchievementIds: Set<String>, seenItemIds: Set<String>, current: GameState): List<Unlock> {
        val out = mutableListOf<Unlock>()
        if (current.level > seenLevel) out += Unlock.LevelUp(current.level, current.rankTitle)
        current.achievements
            .filter { it.unlocked && it.def.id !in seenAchievementIds }
            .sortedWith(compareBy({ it.def.tier.ordinal }, { it.unlockedOn }))
            .forEach { out += Unlock.AchievementUnlocked(it) }
        current.items.filter { it.unlocked && it.def.id !in seenItemIds }.forEach { out += Unlock.ItemUnlocked(it) }
        return out
    }

    /** True when the second half of the full-km splits is faster than the first (odd middle split ignored). */
    fun isNegativeSplit(splitSeconds: List<Int>): Boolean {
        if (splitSeconds.size < 2) return false
        val half = splitSeconds.size / 2
        return splitSeconds.takeLast(half).sum() < splitSeconds.take(half).sum()
    }

    /** Heavier wins, reps break ties; timed exercises compare the time only. */
    private fun isBetter(set: SetFact, best: PersonalRecord) =
        if (set.timed) set.reps > best.reps
        else set.loadKg > best.loadKg || (set.loadKg == best.loadKg && set.reps > best.reps)

    private fun platinumProgress(unlocked: Set<String>) =
        AchievementCatalog.all.count { !it.hidden && it.tier != TrophyTier.PLATINUM && it.id in unlocked }

    private fun blockFinish(plan: PlanFact, sessions: List<SessionFact>, today: LocalDate): LocalDate? {
        if (plan.totalSessions > 0 && plan.doneSessions >= plan.totalSessions) {
            val last = sessions.filter { it.planId == plan.planId && it.done && !it.skipped }.maxOfOrNull { it.date }
            return minOf(last ?: plan.endDate, plan.endDate).takeIf { !it.isAfter(today) }
                ?: plan.endDate.takeIf { it.isBefore(today) }
        }
        return plan.endDate.takeIf { it.isBefore(today) }
    }

    private fun monday(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun km(meters: Double) = String.format(ptBr, "%.1f", meters / 1000)
}
