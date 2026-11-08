package com.ithalo.hybridcoach.core.run

import com.ithalo.hybridcoach.core.plan.PaceRange
import com.ithalo.hybridcoach.core.plan.RunBlockDef
import com.ithalo.hybridcoach.core.plan.RunBlockKind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Field-test stand-ins: a 30 min run with noisy GPS (jitter, weak fixes and jumps), and the treadmill mode.
 * Mirrors the phase 3 acceptance: alerts follow the rules, no repetitive alerts, splits per km.
 */
class RunSimulationTest {
    private val intervalSession = RunSteps.expand(
        listOf(
            RunBlockDef(RunBlockKind.WARMUP, 600, null, PaceRange(400, 440), 1),
            RunBlockDef(RunBlockKind.INTERVAL, null, 400, PaceRange(300, 330), 6),
            RunBlockDef(RunBlockKind.RECOVERY, 90, null, null, 6),
            RunBlockDef(RunBlockKind.COOLDOWN, 300, null, PaceRange(420, 480), 1),
        ),
    )

    /** Pace the simulated runner holds in each block (on target, except the 4th interval, run too slow). */
    private fun runnerPace(step: RunStep?): Int = when (step?.kind) {
        RunBlockKind.WARMUP -> 420
        RunBlockKind.INTERVAL -> if (step.repetition == 4) 360 else 315
        RunBlockKind.RECOVERY -> 540
        RunBlockKind.COOLDOWN -> 450
        else -> 450
    }

    @Test
    fun `thirty minutes with noisy gps gives sane alerts and splits`() {
        val random = Random(42)
        val engine = RunEngine(intervalSession, RunSettings(), RunState(startedAtMs = 0))
        val cues = mutableListOf<Pair<Long, Cue>>()
        var t = 0L
        var meters = 0.0
        cues += engine.start(0).map { 0L to it }
        while (!engine.state.blocksDone && t < 45 * 60_000L) {
            t += 1000
            meters += 1000.0 / runnerPace(engine.currentStep)
            val noise = random.nextDouble(-3.0, 3.0)
            val fix = when {
                random.nextInt(40) == 0 -> fixAt(t, meters + 300) // jump
                random.nextInt(15) == 0 -> fixAt(t, meters, accuracy = 35f) // weak
                else -> fixAt(t, meters + noise)
            }
            cues += engine.onFix(fix, t).map { t to it }
            cues += engine.onTick(t).map { t to it }
        }
        assertTrue(engine.state.blocksDone, "every block should be done")
        cues += engine.finish(t).map { t to it }
        assertTrue(engine.state.finished)
        val minutes = t / 60_000.0
        // 10 min warm-up + 6 × (400 m + 90 s) + 5 min cool-down ≈ 37 min.
        assertTrue(minutes in 34.0..40.0, "took $minutes min")

        val paces = cues.filter { it.second is Cue.Pace }
        val alerts = paces.filter { (it.second as Cue.Pace).advice != PaceAdvice.ON_PACE }
        // Alerts at least 30 s apart, never while the runner is on target, and present in the slow interval.
        alerts.zipWithNext().forEach { (a, b) -> assertTrue(b.first - a.first >= 30_000, "alerts too close: $paces") }
        assertTrue(paces.size <= 6, "too many alerts: $paces")
        assertTrue(alerts.isNotEmpty() && alerts.all { (it.second as Cue.Pace).advice == PaceAdvice.SPEED_UP }, "alerts: $paces")
        assertTrue(alerts.none { it.first <= 600_000 }, "false alerts in the warm-up: $paces")

        val kms = cues.map { it.second }.filterIsInstance<Cue.Kilometer>()
        assertEquals(kms.map { it.km }, (1..kms.size).toList())
        assertEquals(kms.size, engine.state.splitSeconds.size)
        assertTrue(kms.size >= 4, "kms ${kms.size}")
        // GPS noise must not inflate the distance by more than a few percent.
        assertTrue(engine.state.distanceM in meters * 0.97..meters * 1.05, "distance ${engine.state.distanceM} vs $meters")
        assertEquals(intervalSession.size, engine.state.stepResults.size)
    }

    @Test
    fun `treadmill distance comes from the speed and ignores gps`() {
        val engine = RunEngine(
            listOf(RunStep(RunBlockKind.STEADY, 600, null, PaceRange(330, 360))),
            RunSettings(),
            RunState(startedAtMs = 0, treadmillKmh = 10.0),
        )
        val cues = mutableListOf<Cue>()
        for (s in 1..120) {
            cues += engine.onFix(fixAt(s * 1000L, s * 50.0), s * 1000L)
            cues += engine.onTick(s * 1000L)
        }
        assertEquals(10.0 / 3.6 * 120, engine.state.distanceM, 0.5)
        // 10 km/h is a 6:00/km pace, inside the target: no alert.
        assertEquals(emptyList(), cues.filterIsInstance<Cue.Pace>())
        assertEquals(360, engine.currentPace())

        engine.setTreadmillSpeed(8.0, 120_000)
        engine.pause(150_000)
        for (s in 151..200) cues += engine.onTick(s * 1000L)
        engine.resume(200_000)
        for (s in 201..260) cues += engine.onTick(s * 1000L)
        // 120 s at 10 km/h + 30 s at 8 before the pause + 60 s at 8 after it.
        assertEquals(10.0 / 3.6 * 120 + 8.0 / 3.6 * 90, engine.state.distanceM, 0.5)
        assertEquals(PaceAdvice.SPEED_UP, cues.filterIsInstance<Cue.Pace>().first().advice)
        assertEquals(450, RunEngine.paceForKmh(8.0))
    }
}
