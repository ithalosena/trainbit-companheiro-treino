package com.ithalo.hybridcoach.core.game

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {
    // 2026-11-02 is a Monday.
    private val mon = LocalDate.of(2026, 11, 2)
    private var nextId = 1L

    private fun session(date: LocalDate, kind: SessionKind = SessionKind.STRENGTH, done: Boolean = true, skipped: Boolean = false, rpe: Int? = null, planId: Long = 1) =
        SessionFact(nextId++, planId, date, kind, done, skipped, rpe)

    private fun set(s: SessionFact, reps: Int = 5, load: Double = 50.0, failed: Boolean = false, ex: String = "squat", name: String = "Agachamento") =
        SetFact(s.sessionId, ex, name, reps, load, failed, s.date)

    private fun run(s: SessionFact, meters: Double, seconds: Long = (meters * 0.36).toLong(), pace: Int? = null, splits: List<Int> = emptyList()) =
        RunFact(s.sessionId, s.date, meters, seconds, pace, splits)

    private fun facts(
        sessions: List<SessionFact> = emptyList(), sets: List<SetFact> = emptyList(), runs: List<RunFact> = emptyList(),
        plans: List<PlanFact> = emptyList(), summaries: Int = 0, questionnaire: Boolean = false, today: LocalDate = mon.plusDays(60),
    ) = GameFacts(sessions, sets, runs, plans, summaries, questionnaire, today)

    private fun GameState.trophy(id: String) = achievements.single { it.def.id == id }

    @Test
    fun `empty facts start at level 1 with 0 xp`() {
        val s = GameEngine.evaluate(facts())
        assertEquals(0, s.xp)
        assertEquals(1, s.level)
        assertEquals(0, s.xpIntoLevel)
        assertEquals(Leveling.stepXp(1), s.xpForNextLevel)
        assertEquals("Novato 8-bit", s.rankTitle)
        assertTrue(s.achievements.none { it.unlocked })
        assertTrue(s.achievements.all { it.progress == 0 })
        assertEquals(listOf("frame_classic"), s.items.filter { it.unlocked }.map { it.def.id })
        assertTrue(s.records.isEmpty())
        assertTrue(s.xpLog.isEmpty())
        assertEquals(0, s.stats.currentWeekStreak)
        assertNull(s.stats.bestPaceSecPerKm)
    }

    @Test
    fun `strength session xp adds base, sets and first trophies`() {
        val s1 = session(mon)
        val state = GameEngine.evaluate(facts(listOf(s1), sets = List(3) { set(s1) }))
        // session + 3 sets + trophies first_session and first_set (bronze each) + mission "1 sessão".
        assertEquals(GameEngine.XP_SESSION + 3 * GameEngine.XP_PER_SET + 2 * 10 + WeeklyMissions.XP_MISSION, state.xp)
        assertEquals(mon, state.trophy("first_session").unlockedOn)
        assertEquals(mon, state.trophy("first_set").unlockedOn)
        assertEquals(3, state.stats.totalSets)
        assertEquals(750.0, state.stats.tonnageKg)
        assertEquals("Troféu: Ferro Frio", state.xpLog.first().reason)
    }

    @Test
    fun `skipped and pending sessions give nothing but skip unlocks the hidden trophy`() {
        val skipped = session(mon, done = false, skipped = true)
        val pending = session(mon.plusDays(1), done = false)
        val state = GameEngine.evaluate(facts(listOf(skipped, pending), sets = listOf(set(pending)), runs = listOf(run(pending, 5000.0))))
        assertEquals(0, state.stats.sessionsDone)
        assertEquals(0, state.stats.totalSets)
        assertEquals(0.0, state.stats.totalKm)
        assertEquals(mon, state.trophy("first_skip").unlockedOn)
        assertEquals(10, state.xp)
    }

    @Test
    fun `run xp counts per 200 m and tracks distance stats`() {
        val s1 = session(mon, SessionKind.RUN)
        val state = GameEngine.evaluate(facts(listOf(s1), runs = listOf(run(s1, 5250.0, 1575))))
        val runXp = state.xpLog.filter { !it.reason.startsWith("Troféu") && !it.reason.startsWith("Miss") }.sumOf { it.amount }
        assertEquals(GameEngine.XP_SESSION + 26, runXp)
        assertTrue(state.xpLog.any { it.reason == "5,3 km corridos" })
        assertEquals(5.25, state.stats.totalKm)
        assertEquals(5.25, state.stats.longestRunKm)
        assertEquals(300, state.stats.bestPaceSecPerKm)
        assertTrue(state.trophy("run_5k").unlocked)
        assertTrue(state.trophy("km_5").unlocked)
        assertFalse(state.trophy("sub5_pace").unlocked) // exactly 5:00 is not below
    }

    @Test
    fun `weekly missions follow the plan and pay once each plus a bonus`() {
        val run = session(mon, SessionKind.RUN)
        val lift = session(mon.plusDays(2))
        val pending = session(mon.plusDays(4), done = false)
        val today = mon.plusDays(3)
        val state = GameEngine.evaluate(
            facts(
                listOf(run, lift, pending),
                sets = listOf(set(lift, load = 50.0), set(lift, load = 55.0)),
                runs = listOf(run(run, 4200.0)),
                today = today,
            ),
        )
        val m = state.missions
        assertEquals(listOf(MissionKind.SESSIONS, MissionKind.RUN_KM, MissionKind.RECORD), m.map { it.kind })
        assertEquals(3, m[0].target) // three planned this week, one still pending
        assertEquals(2, m[0].progress)
        assertEquals(3, m[1].target) // no run last week: 3 km per planned run
        assertTrue(m[1].done)
        assertTrue(m[2].done) // 55 kg beat the 50 kg set of the same session's baseline
        assertEquals(2, state.xpLog.count { it.reason.startsWith("Missão") })
        assertFalse(state.xpLog.any { it.reason == "Missões da semana completas" })
    }

    @Test
    fun `run target grows ten percent over last week`() {
        val last = session(mon.minusWeeks(1), SessionKind.RUN)
        val now = session(mon, SessionKind.RUN, done = false)
        val state = GameEngine.evaluate(facts(listOf(last, now), runs = listOf(run(last, 10000.0)), today = mon))
        assertEquals(11, state.missions[1].target)
        assertEquals(MissionKind.EARLY, state.missions[2].kind)
    }

    @Test
    fun `timed sets compare time only and stay out of tonnage`() {
        val s1 = session(mon)
        val s2 = session(mon.plusDays(2))
        val plank = { s: SessionFact, secs: Int -> SetFact(s.sessionId, "plank", "Prancha", secs, 0.0, false, s.date, timed = true) }
        val state = GameEngine.evaluate(facts(listOf(s1, s2), sets = listOf(plank(s1, 60), plank(s2, 90))))
        val rec = state.records.single()
        assertTrue(rec.timed)
        assertEquals(90, rec.reps)
        assertEquals(0.0, state.stats.tonnageKg)
        assertTrue(state.xpLog.any { it.reason == "Recorde pessoal: Prancha" })
    }

    @Test
    fun `facts after today are ignored`() {
        val future = session(mon.plusDays(10))
        val state = GameEngine.evaluate(facts(listOf(future), sets = listOf(set(future)), today = mon))
        assertEquals(0, state.xp)
    }

    @Test
    fun `unlock dates follow the replay order`() {
        val sessions = (0 until 12).map { session(mon.plusDays(it.toLong())) }
        val state = GameEngine.evaluate(facts(sessions.shuffled(java.util.Random(7))))
        assertEquals(mon, state.trophy("first_session").unlockedOn)
        assertEquals(mon.plusDays(9), state.trophy("sessions_10").unlockedOn)
        assertEquals(10, state.trophy("sessions_10").progress)
        assertEquals(12, state.trophy("sessions_50").progress)
        assertNull(state.trophy("sessions_50").unlockedOn)
        val trophyEvent = state.xpLog.single { it.reason == "Troféu: Jogador Assíduo" }
        assertEquals(mon.plusDays(9), trophyEvent.date)
    }

    @Test
    fun `xp log is most recent first and capped`() {
        val sessions = (0 until 40).map { session(mon.plusDays(it.toLong())) }
        val state = GameEngine.evaluate(facts(sessions, sets = sessions.map { set(it) }, today = mon.plusDays(100)))
        assertEquals(GameEngine.XP_LOG_SIZE, state.xpLog.size)
        assertEquals(state.xpLog.sortedByDescending { it.date }.map { it.date }, state.xpLog.map { it.date })
        assertEquals(mon.plusDays(39), state.xpLog.first().date)
    }

    @Test
    fun `personal records keep the best load with reps as tie breaker and ignore failed sets`() {
        val a = session(mon)
        val b = session(mon.plusDays(2))
        val c = session(mon.plusDays(4))
        val sets = listOf(
            set(a, reps = 5, load = 80.0),
            set(a, reps = 8, load = 60.0, ex = "bench", name = "Supino"),
            set(b, reps = 6, load = 80.0),
            set(b, reps = 0, load = 200.0),
            set(c, reps = 3, load = 120.0, failed = true),
            set(c, reps = 4, load = 80.0),
        )
        val state = GameEngine.evaluate(facts(listOf(a, b, c), sets))
        assertEquals(
            listOf(
                PersonalRecord("squat", "Agachamento", 80.0, 6, b.date),
                PersonalRecord("bench", "Supino", 60.0, 8, a.date),
            ),
            state.records,
        )
        // Only session b beats an existing best.
        assertEquals(listOf(b.date), state.xpLog.filter { it.reason.startsWith("Recorde") }.map { it.date })
        assertEquals(b.date, state.trophy("first_pr").unlockedOn)
        assertEquals(80, state.trophy("heavy_100").progress)
    }

    @Test
    fun `one pr bonus per exercise and session`() {
        val a = session(mon)
        val b = session(mon.plusDays(1))
        val sets = listOf(set(a, load = 50.0), set(b, load = 55.0), set(b, load = 60.0))
        val state = GameEngine.evaluate(facts(listOf(a, b), sets))
        assertEquals(1, state.xpLog.count { it.reason == "Recorde pessoal: Agachamento" })
        assertEquals(60.0, state.records.single().loadKg)
    }

    @Test
    fun `week streak counts consecutive iso weeks and gives a bonus`() {
        val sessions = listOf(mon, mon.plusDays(6), mon.plusDays(7), mon.plusDays(15), mon.plusDays(28)).map { session(it) }
        val state = GameEngine.evaluate(facts(sessions, today = mon.plusDays(29)))
        assertEquals(3, state.stats.bestWeekStreak)
        assertEquals(1, state.stats.currentWeekStreak)
        assertEquals(listOf(2 * GameEngine.XP_STREAK_PER_WEEK, 3 * GameEngine.XP_STREAK_PER_WEEK), state.xpLog.filter { it.reason.startsWith("Sequência") }.map { it.amount }.reversed())
    }

    @Test
    fun `current week without sessions does not break the streak until it ends`() {
        val sessions = (0 until 4).map { session(mon.plusWeeks(it.toLong())) }
        val inWeek4 = facts(sessions, today = mon.plusWeeks(4).plusDays(6))
        assertEquals(4, GameEngine.evaluate(inWeek4).stats.currentWeekStreak)
        val inWeek5 = facts(sessions, today = mon.plusWeeks(5))
        assertEquals(0, GameEngine.evaluate(inWeek5).stats.currentWeekStreak)
        assertEquals(4, GameEngine.evaluate(inWeek5).stats.bestWeekStreak)
        assertEquals(mon.plusWeeks(3), GameEngine.evaluate(inWeek5).trophy("streak_4").unlockedOn)
        val sameWeek = facts(sessions, today = mon.plusWeeks(3).plusDays(3))
        assertEquals(4, GameEngine.evaluate(sameWeek).stats.currentWeekStreak)
    }

    @Test
    fun `perfect week needs two or more sessions all done`() {
        val week1 = listOf(session(mon), session(mon.plusDays(2)), session(mon.plusDays(4)))
        val week2 = listOf(session(mon.plusDays(7)), session(mon.plusDays(9), done = false, skipped = true))
        val week3 = listOf(session(mon.plusDays(14)), session(mon.plusDays(16), done = false))
        val state = GameEngine.evaluate(facts(week1 + week2 + week3, today = mon.plusDays(20)))
        assertEquals(mon.plusDays(4), state.trophy("perfect_week").unlockedOn)
        assertEquals(1, state.trophy("perfect_weeks_4").progress)
        assertEquals(1, state.xpLog.count { it.reason == "Semana perfeita" })
    }

    @Test
    fun `negative split compares halves and ignores the odd middle split`() {
        assertTrue(GameEngine.isNegativeSplit(listOf(310, 305, 300, 295)))
        assertFalse(GameEngine.isNegativeSplit(listOf(300, 300, 300, 300)))
        assertTrue(GameEngine.isNegativeSplit(listOf(300, 999, 290)))
        assertFalse(GameEngine.isNegativeSplit(listOf(300)))
        assertFalse(GameEngine.isNegativeSplit(emptyList()))
        val s1 = session(mon, SessionKind.RUN)
        val state = GameEngine.evaluate(facts(listOf(s1), runs = listOf(run(s1, 4000.0, 1200, splits = listOf(310, 305, 295, 290)))))
        assertEquals(mon, state.trophy("negative_split").unlockedOn)
    }

    @Test
    fun `exact thresholds keep progress below target until reached`() {
        val s1 = session(mon, SessionKind.RUN)
        val s2 = session(mon.plusDays(1), SessionKind.RUN)
        val state = GameEngine.evaluate(facts(listOf(s1), runs = listOf(run(s1, 21050.0))))
        assertEquals(20, state.trophy("run_21k").progress)
        assertFalse(state.trophy("run_21k").unlocked)
        assertTrue(state.trophy("run_10k").unlocked)
        val state2 = GameEngine.evaluate(facts(listOf(s1, s2), runs = listOf(run(s1, 21050.0), run(s2, 21200.0))))
        assertEquals(mon.plusDays(1), state2.trophy("run_21k").unlockedOn)
        assertEquals(42, state2.trophy("km_42").progress)
        assertEquals(mon.plusDays(1), state2.trophy("km_42").unlockedOn)
    }

    @Test
    fun `hidden trophies from rpe, failures, sunday, holidays and comeback`() {
        val sunday = LocalDate.of(2026, 11, 8)
        val a = session(sunday, rpe = 10)
        val b = session(LocalDate.of(2026, 12, 25))
        val c = session(LocalDate.of(2027, 2, 1))
        val sets = listOf(set(b, failed = true), set(b, failed = true), set(b, failed = true))
        val state = GameEngine.evaluate(facts(listOf(a, b, c), sets, today = LocalDate.of(2027, 3, 1)))
        assertEquals(sunday, state.trophy("sunday").unlockedOn)
        assertEquals(sunday, state.trophy("rpe10").unlockedOn)
        assertEquals(b.date, state.trophy("three_fails").unlockedOn)
        assertEquals(b.date, state.trophy("holiday").unlockedOn)
        assertEquals(b.date, state.trophy("comeback").unlockedOn)
        assertTrue(state.items.single { it.def.id == "sticker_skull" }.unlocked)
    }

    @Test
    fun `hybrid day needs both kinds done the same day`() {
        val state = GameEngine.evaluate(facts(listOf(session(mon), session(mon, SessionKind.RUN), session(mon.plusDays(1)))))
        assertEquals(mon, state.trophy("hybrid_day").unlockedOn)
    }

    @Test
    fun `block completion uses the last done session or the end date`() {
        val perfectPlan = PlanFact(1, mon, mon.plusDays(27), totalSessions = 2, doneSessions = 2)
        val halfPlan = PlanFact(2, mon.plusDays(28), mon.plusDays(55), totalSessions = 4, doneSessions = 2)
        val sessions = listOf(
            session(mon.plusDays(1), planId = 1), session(mon.plusDays(10), planId = 1),
            session(mon.plusDays(30), planId = 2), session(mon.plusDays(31), planId = 2),
        )
        val state = GameEngine.evaluate(facts(sessions, plans = listOf(perfectPlan, halfPlan), summaries = 2, questionnaire = true, today = mon.plusDays(60)))
        assertEquals(mon.plusDays(10), state.trophy("block_perfect").unlockedOn)
        assertEquals(mon.plusDays(10), state.trophy("block_done").unlockedOn)
        assertEquals(2, state.xpLog.count { it.reason == "Bloco concluído" })
        assertEquals(1, state.xpLog.count { it.reason == "Bloco 100% concluído" })
        assertEquals(mon, state.trophy("questionnaire").unlockedOn)
        assertEquals(mon.plusDays(10), state.trophy("export_summary").unlockedOn)
        assertEquals(listOf(mon.plusDays(55), mon.plusDays(10)), state.xpLog.filter { it.reason == "Resumo exportado" }.map { it.date })

        val ongoing = GameEngine.evaluate(facts(sessions.take(1), plans = listOf(halfPlan.copy(doneSessions = 1)), today = mon.plusDays(40)))
        assertFalse(ongoing.trophy("block_done").unlocked)
    }

    @Test
    fun `block below half is not completed`() {
        val plan = PlanFact(1, mon, mon.plusDays(27), totalSessions = 10, doneSessions = 4)
        val state = GameEngine.evaluate(facts(listOf(session(mon)), plans = listOf(plan)))
        assertFalse(state.trophy("block_done").unlocked)
    }

    @Test
    fun `undated exports and questionnaire fall back to today without history`() {
        val state = GameEngine.evaluate(facts(summaries = 1, questionnaire = true, today = mon))
        assertEquals(mon, state.trophy("questionnaire").unlockedOn)
        assertEquals(mon, state.trophy("export_summary").unlockedOn)
        assertEquals(GameEngine.XP_QUESTIONNAIRE + GameEngine.XP_SUMMARY + 2 * 10, state.xp)
    }

    @Test
    fun `level matches total xp and rank`() {
        val sessions = (0 until 30).map { session(mon.plusDays(it.toLong())) }
        val state = GameEngine.evaluate(facts(sessions, sets = sessions.flatMap { s -> List(5) { set(s) } }))
        assertEquals(Leveling.levelFor(state.xp), state.level)
        assertEquals(state.xp - Leveling.xpForLevel(state.level), state.xpIntoLevel)
        assertTrue(state.xpIntoLevel in 0 until state.xpForNextLevel)
        assertEquals(Leveling.rankTitle(state.level), state.rankTitle)
    }

    @Test
    fun `running bests keep the best value and the day it was set`() {
        val a = session(mon, SessionKind.RUN)
        val b = session(mon.plusDays(2), SessionKind.RUN)
        val c = session(mon.plusDays(4), SessionKind.RUN)
        val runs = listOf(
            run(a, 5000.0, seconds = 1800, pace = 360, splits = listOf(370, 365, 360, 355, 350)),
            run(b, 3000.0, seconds = 960, pace = 320, splits = listOf(330, 320, 310)),
            run(c, 800.0, seconds = 200, pace = 250), // under 1 km: no pace record
        )
        val records = GameEngine.evaluate(facts(listOf(a, b, c), runs = runs)).runRecords
        assertEquals(RunRecordKind.entries.toList(), records.map { it.kind })
        val byKind = records.associateBy { it.kind }
        assertEquals(RunRecord(RunRecordKind.LONGEST_DISTANCE, 5000.0, a.date), byKind[RunRecordKind.LONGEST_DISTANCE])
        assertEquals(RunRecord(RunRecordKind.LONGEST_TIME, 1800.0, a.date), byKind[RunRecordKind.LONGEST_TIME])
        assertEquals(RunRecord(RunRecordKind.BEST_PACE, 320.0, b.date), byKind[RunRecordKind.BEST_PACE])
        assertEquals(RunRecord(RunRecordKind.FASTEST_KM, 310.0, b.date), byKind[RunRecordKind.FASTEST_KM])
        assertTrue(GameEngine.evaluate(facts()).runRecords.isEmpty())
    }

    @Test
    fun `beating a running best gives record xp, the first run is only the baseline`() {
        val a = session(mon, SessionKind.RUN)
        val b = session(mon.plusDays(2), SessionKind.RUN)
        val first = GameEngine.evaluate(facts(listOf(a), runs = listOf(run(a, 3000.0, seconds = 1080, pace = 360, splits = listOf(370, 360, 350)))))
        assertTrue(first.xpLog.none { it.reason.startsWith("Recorde de corrida") })
        // Longer, longer in time, faster on average, but no faster km: three records.
        val runs = listOf(
            run(a, 3000.0, seconds = 1080, pace = 360, splits = listOf(370, 360, 350)),
            run(b, 4000.0, seconds = 1400, pace = 350, splits = listOf(355, 352, 350, 351)),
        )
        val second = GameEngine.evaluate(facts(listOf(a, b), runs = runs))
        val records = second.xpLog.filter { it.reason.startsWith("Recorde de corrida") }
        assertEquals(setOf("maior distância", "maior tempo", "melhor ritmo"), records.map { it.reason.substringAfter(": ") }.toSet())
        assertTrue(records.all { it.amount == GameEngine.XP_PR && it.date == b.date })
        assertEquals(records.size, RunBests.beaten(runs[1], listOf(runs[0])).size)
        assertTrue(RunBests.beaten(runs[0], emptyList()).isEmpty())
    }
}
