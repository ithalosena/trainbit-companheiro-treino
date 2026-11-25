package com.ithalo.hybridcoach.core.block

import com.ithalo.hybridcoach.core.plan.PlanValidator
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockTest {
    private val start = LocalDate.of(2026, 11, 9) // Monday
    private val rules = ProgressionRules()

    @Test
    fun `lifecycle goes planned, active, closing in the last week, closed`() {
        assertEquals(BlockState.PLANNED, BlockLifecycle.state(start, 4, start.minusDays(1), closed = false))
        assertEquals(BlockState.ACTIVE, BlockLifecycle.state(start, 4, start, closed = false))
        assertEquals(BlockState.ACTIVE, BlockLifecycle.state(start, 4, start.plusDays(20), closed = false))
        assertEquals(BlockState.CLOSING, BlockLifecycle.state(start, 4, start.plusDays(21), closed = false))
        assertEquals(BlockState.CLOSING, BlockLifecycle.state(start, 4, start.plusDays(40), closed = false))
        assertEquals(BlockState.CLOSED, BlockLifecycle.state(start, 4, start.plusDays(10), closed = true))
        assertEquals(LocalDate.of(2026, 12, 6), BlockLifecycle.end(start, 4))
        assertEquals(0, BlockLifecycle.daysLeft(start, 4, LocalDate.of(2026, 12, 6)))
        assertEquals(LocalDate.of(2026, 12, 7), BlockLifecycle.nextStart(start, 4))
    }

    private fun set(reps: Int, load: Double, failed: Boolean = false, rpe: Int? = null, session: Long = 1, day: Long = 0) =
        BlockSet("supino", "Supino", session, start.plusDays(day), reps, load, failed, rpe, "6-8")

    @Test
    fun `double progression goes up one step only with every set at the top and effort within the limit`() {
        val top = listOf(listOf(set(8, 40.0, rpe = 8), set(8, 40.0, rpe = 8), set(8, 40.0, rpe = 7)))
        assertEquals(42.5, Progression.decide(top, "6-8", rules, consolidation = false)!!.loadKg)
        // Effort above 8: keeps.
        val hard = listOf(listOf(set(8, 40.0, rpe = 9), set(8, 40.0, rpe = 9)))
        assertEquals(40.0, Progression.decide(hard, "6-8", rules, consolidation = false)!!.loadKg)
        // One set below the top: keeps.
        val short = listOf(listOf(set(8, 40.0), set(7, 40.0)))
        assertEquals(40.0, Progression.decide(short, "6-8", rules, consolidation = false)!!.loadKg)
        // Light loads (dumbbells) go up by the small step.
        val light = listOf(listOf(set(8, 12.0), set(8, 12.0)))
        assertEquals(13.0, Progression.decide(light, "6-8", rules, consolidation = false)!!.loadKg)
        // Consolidation: never up.
        assertEquals(40.0, Progression.decide(top, "6-8", rules, consolidation = true)!!.loadKg)
    }

    @Test
    fun `two failed sets in two sessions in a row bring the load down`() {
        val failing = listOf(
            listOf(set(5, 50.0, failed = true, session = 1), set(5, 50.0, failed = true, session = 1)),
            listOf(set(5, 50.0, failed = true, session = 2, day = 3), set(4, 50.0, failed = true, session = 2, day = 3)),
        )
        assertEquals(47.5, Progression.decide(failing, "6-8", rules, consolidation = false)!!.loadKg)
        // Only the last session failing: keeps.
        assertEquals(50.0, Progression.decide(failing.drop(1), "6-8", rules, consolidation = false)!!.loadKg)
        assertNull(Progression.decide(emptyList(), "6-8", rules, consolidation = false))
        assertEquals(8, Progression.topReps("6-8"))
        assertNull(Progression.topReps("30s"))
        assertEquals(53.3, (Progression.epley(40.0, 10) * 10).toInt() / 10.0)
    }

    private fun history(done: Int, total: Int, lastDoneDay: Long = 25) = BlockHistory(
        planId = 7, planName = "Bloco 1", number = 1, start = start, weeks = 4, phase = "progression",
        sessions = (0 until total).map { i ->
            BlockSession(week = 1 + (i * 4 / total), date = start.plusDays(i.toLong() * 27 / total), status = if (i < done) "done" else "skipped", optional = false)
        } + BlockSession(4, start.plusDays(lastDoneDay), "done", optional = true),
        exercises = listOf(BlockExercise("supino", "Supino", main = true, postponed = 1), BlockExercise("rosca", "Rosca", main = false)),
        sets = listOf(set(8, 40.0, rpe = 7, session = 1), set(8, 40.0, rpe = 8, session = 1), set(8, 42.5, rpe = 8, session = 2, day = 7), set(8, 42.5, rpe = 8, session = 2, day = 7)),
        runs = listOf(
            BlockRun(start.plusDays(1), 5000.0, 1800, 360, "steady", 350, 370),
            BlockRun(start.plusDays(8), 6000.0, 2100, 350, "steady", 340, 360),
            BlockRun(start.plusDays(15), 4000.0, 1200, 300, "interval", 290, 300),
        ),
        skeleton = listOf(SkeletonSession("mon", "strength", "A", listOf("Supino", "Rosca")), SkeletonSession("tue", "run", "Leve")),
        limitations = "joelho direito",
        targets = mapOf("supino" to "6-8", "rosca" to "10-12"),
        today = start.plusDays(27),
        deloadDone = true,
    )

    @Test
    fun `snapshot holds the athlete state and the suggested start`() {
        val s = SnapshotBuilder.build(history(done = 10, total = 12), CloseAnswers(fatigue = 4, motivation = 2), rules)
        assertFalse(s.suggestedStart.consolidation)
        val supino = s.exercises.first { it.id == "supino" }
        assertEquals(42.5, supino.lastLoadKg)
        assertEquals(1, supino.postponed)
        assertTrue(supino.main)
        assertEquals(6, supino.e1rmTrendPct) // 40 x 8 -> 42.5 x 8
        assertEquals(45.0, s.suggestedStart.loads.single { it.id == "supino" }.loadKg)
        assertEquals(83, s.general.adherencePct)
        val run = assertNotNull(s.run)
        assertEquals(listOf(5.0, 6.0, 4.0, 0.0), run.weeklyKm)
        assertEquals(6.0, run.longestKm)
        assertEquals(100, run.paceAdherencePct)
        assertEquals("progression", s.suggestedStart.phase)
        // Frozen as JSON and read back the same.
        assertEquals(s, BlockSnapshot.fromJson(s.toJson()))
        // Same history, same snapshot.
        assertEquals(s, SnapshotBuilder.build(history(done = 10, total = 12), CloseAnswers(fatigue = 4, motivation = 2), rules))
    }

    @Test
    fun `low adherence or a long pause makes a consolidation block`() {
        val low = SnapshotBuilder.build(history(done = 5, total = 12), CloseAnswers(), rules)
        assertTrue(low.suggestedStart.consolidation)
        assertEquals("base", low.suggestedStart.phase)
        assertEquals(42.5, low.suggestedStart.loads.single { it.id == "supino" }.loadKg)
        val paused = SnapshotBuilder.build(history(done = 12, total = 12, lastDoneDay = 2).copy(today = start.plusDays(40)), CloseAnswers(), rules)
        assertTrue(paused.suggestedStart.consolidation)
    }

    private val snapshot by lazy { SnapshotBuilder.build(history(done = 10, total = 12), CloseAnswers(change = "mais costas"), rules) }

    @Test
    fun `next block prompt carries the state, the suggested start and the rules`() {
        val text = NextBlockPrompt.section(snapshot, rules)
        assertContains(text, "Este é o bloco 2")
        assertContains(text, "Supino (principal): última carga 42.5 kg x 8")
        assertContains(text, "Supino: 45 kg em 6-8")
        assertContains(text, "no máximo 10% por semana")
        assertContains(text, "`justification`")
        assertContains(text, "`plan.phase`")
        assertContains(text, "mais costas")
        assertContains(text, "joelho direito")
        assertContains(text, "mon musculação: A (Supino, Rosca)")
    }

    private fun plan(load: Double, justification: String? = null, km: Int = 5000, rosca: Boolean = true) = PlanValidator.validateText(
        """
        {"schema_version": 1, "plan": {"name": "Bloco 2", "start_date": "2026-12-07", "duration_weeks": 1, "phase": "progression"},
         "weeks": [{"weeks": [1], "focus": "", "is_deload": false, "sessions": [
           {"day_of_week": "mon", "type": "strength", "title": "A", "estimated_minutes": 60, "exercises": [
             {"name": "Supino", "sets": 3, "reps": "6-8", "load_kg": $load, "rest_seconds": 90${justification?.let { ", \"justification\": \"$it\"" } ?: ""}}
             ${if (rosca) ", {\"name\": \"Rosca\", \"sets\": 3, \"reps\": \"10-12\", \"load_kg\": 12, \"rest_seconds\": 60}" else ""}]},
           {"day_of_week": "tue", "type": "run", "title": "Leve", "estimated_minutes": 40, "blocks": [
             {"kind": "steady", "distance_m": $km, "target_pace_sec_per_km": null, "repeat": 1}]}]}]}
        """.trimIndent(),
    ).let { assertNotNull(it.document, it.errors.toString()) }

    private val ids = mapOf("Supino" to "supino", "Rosca" to "rosca")

    @Test
    fun `a plan within the suggested start has no warnings`() {
        val r = PlanCheck.check(plan(45.0, km = 4200), snapshot, rules) { ids[it] }
        assertEquals(emptyList(), r.warnings)
        assertEquals(100, r.mainKeptPct)
        assertEquals(100, r.daysKeptPct)
        assertEquals("progression", plan(45.0).plan.phase)
    }

    @Test
    fun `load and volume jumps are warned and turned into a correction request`() {
        val r = PlanCheck.check(plan(54.0, km = 8000), snapshot, rules) { ids[it] }
        assertEquals(2, r.warnings.size, r.warnings.toString())
        assertContains(r.warnings[0].text, "Supino: +20% de carga (sugerido 45 kg, plano 54 kg)")
        assertContains(r.warnings[1].text, "Volume de corrida da semana 1: +100%")
        val request = PlanCheck.correctionRequest(r)
        assertContains(request, "Supino: +20% de carga")
        assertContains(request, "justification")
    }

    @Test
    fun `a justified jump is shown but is not a warning, and dropped exercises are flagged`() {
        val justified = PlanCheck.check(plan(54.0, justification = "testou 50 kg x 8 semana passada", km = 4200), snapshot, rules) { ids[it] }
        assertTrue(justified.warnings.isEmpty(), justified.warnings.toString())
        assertTrue(justified.issues.single().justified)
        val noAccessory = PlanCheck.check(plan(45.0, km = 4200, rosca = false), snapshot, rules) { ids[it] }
        assertContains(noAccessory.warnings.single().text, "100% dos acessórios trocados")
    }

    @Test
    fun `phase must be one of the four`() {
        val bad = PlanValidator.validateText(
            """{"schema_version": 1, "plan": {"name": "X", "start_date": "2026-12-07", "duration_weeks": 1, "phase": "bulk"},
               "weeks": [{"weeks": [1], "focus": "", "is_deload": false, "sessions": [{"day_of_week": "mon", "type": "run", "title": "L", "estimated_minutes": 30,
               "blocks": [{"kind": "steady", "duration_seconds": 1200, "target_pace_sec_per_km": null, "repeat": 1}]}]}]}""",
        )
        assertTrue(bad.errors.any { it.contains("plan.phase") }, bad.errors.toString())
    }

    @Test
    fun `rules round trip through their json file`() {
        val custom = ProgressionRules(incrementKg = 5.0, maxWeeklyRunIncreasePct = 15)
        assertEquals(custom, ProgressionRules.fromJson(custom.toJson()))
        assertEquals(ProgressionRules(), ProgressionRules.fromJson("{}"))
    }

    @Test
    fun `the shipped rules file has the documented defaults`() {
        val text = checkNotNull(javaClass.classLoader.getResource("progression_rules.json")).readText()
        assertEquals(ProgressionRules(), ProgressionRules.fromJson(text))
    }
}
