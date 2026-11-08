package com.ithalo.hybridcoach.core.run

import com.ithalo.hybridcoach.core.plan.PaceRange
import com.ithalo.hybridcoach.core.plan.RunBlockKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunEngineTest {
    private fun engine(vararg steps: RunStep) = RunEngine(steps.toList(), RunSettings(), RunState(startedAtMs = 0))

    private val steadyPace = PaceRange(300, 330)

    @Test
    fun `time block ends on time, the run keeps going and finishing it gives a summary`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, 60, null, null)))
        r.cues += r.engine.start(0)
        r.run(59, 300)
        assertEquals(emptyList(), r.of<Cue.StepEnd>())
        r.run(1, 300)
        assertEquals(1, r.of<Cue.StepEnd>().size)
        // The last block ending does not end the run: the runner may still be on the way back.
        assertEquals(1, r.of<Cue.BlocksDone>().size)
        assertTrue(!r.engine.state.finished && r.engine.state.blocksDone && r.engine.currentStep == null)
        r.cues += r.engine.finish(r.timeMs)
        val done = r.of<Cue.Finished>().single()
        assertEquals(60, done.activeSeconds)
        // The first fix only sets the starting point and distance is counted in segments of at least 8 m,
        // so up to one segment of the last 59 s of movement may still be pending.
        assertTrue(done.distanceM in (1000.0 / 300 * 59 - LocationFilter.MIN_STEP_M)..(1000.0 / 300 * 59), "distance ${done.distanceM}")
        assertTrue(done.avgPaceSecPerKm!! in 305..320, "pace ${done.avgPaceSecPerKm}")
        assertTrue(r.engine.state.finished)
        assertEquals(listOf(StepResult("steady", r.engine.state.distanceM, 60)), r.engine.state.stepResults)
    }

    @Test
    fun `distance block advances by distance and km splits are announced`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, null, 2000, null), RunStep(RunBlockKind.COOLDOWN, 60, null, null)))
        r.run(700, 300)
        val kms = r.of<Cue.Kilometer>()
        assertEquals(listOf(1, 2), kms.map { it.km })
        assertTrue(kms.all { it.splitSeconds in 299..305 && it.avgPaceSecPerKm in 299..305 }, kms.toString())
        assertEquals(RunBlockKind.COOLDOWN, r.of<Cue.StepStart>().single().step.kind)
        assertEquals(2, r.engine.state.splitSeconds.size)
    }

    @Test
    fun `half way only on long blocks and a warning 10 s before every block ends`() {
        val interval = RunStep(RunBlockKind.INTERVAL, 60, null, null)
        val recovery = RunStep(RunBlockKind.RECOVERY, 60, null, null)
        val r = Runner(engine(RunStep(RunBlockKind.WARMUP, 300, null, null), interval, recovery))
        r.tick(300)
        assertEquals(1, r.of<Cue.StepHalf>().size)
        assertEquals(listOf(Cue.StepEndingSoon(10, interval)), r.of<Cue.StepEndingSoon>())
        r.tick(120)
        assertEquals(1, r.of<Cue.StepHalf>().size)
        assertEquals(listOf(Cue.StepEndingSoon(10, interval), Cue.StepEndingSoon(10, recovery), Cue.StepEndingSoon(10, null)), r.of<Cue.StepEndingSoon>())
        assertTrue(r.engine.state.blocksDone && !r.engine.state.finished)
    }

    @Test
    fun `very short blocks get no ending warning`() {
        val r = Runner(engine(RunStep(RunBlockKind.INTERVAL, 20, null, null), RunStep(RunBlockKind.RECOVERY, 20, null, null)))
        r.tick(40)
        assertTrue(r.of<Cue.StepEndingSoon>().isEmpty())
    }

    @Test
    fun `pace alerts follow the target and wait for the smoothing window`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, 600, null, steadyPace)))
        r.run(25, 360) // too slow from the start, but the first window is not judged
        assertEquals(emptyList(), r.of<Cue.Pace>())
        r.run(20, 360)
        val first = r.of<Cue.Pace>().first()
        assertEquals(PaceAdvice.SPEED_UP, first.advice)
        assertTrue(first.paceSecPerKm in 350..370, "pace ${first.paceSecPerKm}")
        r.run(60, 315)
        assertEquals(PaceAdvice.ON_PACE, r.of<Cue.Pace>().last().advice)
        val count = r.of<Cue.Pace>().size
        r.run(200, 315)
        assertEquals(count, r.of<Cue.Pace>().size, "no repeated alerts while on pace")
    }

    @Test
    fun `pause stops time and distance and skip moves to the next block`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, 600, null, null), RunStep(RunBlockKind.COOLDOWN, 300, null, null)))
        r.run(60, 300)
        r.engine.pause(r.timeMs)
        val before = r.engine.state.distanceM
        r.run(120, 300)
        assertEquals(before, r.engine.state.distanceM)
        r.engine.resume(r.timeMs)
        assertEquals(60_000, r.engine.state.activeMs(r.timeMs))
        r.run(10, 300)
        assertEquals(before + 1000.0 / 300 * 9, r.engine.state.distanceM, 0.5)
        r.cues += r.engine.skipStep(r.timeMs)
        assertEquals(RunBlockKind.COOLDOWN, r.engine.currentStep?.kind)
        assertTrue(r.engine.state.stepResults.single().skipped)
    }

    @Test
    fun `state survives a json round trip`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, null, 3000, null)))
        r.run(400, 300)
        val restored = RunState.fromJson(r.engine.state.toJson())
        assertEquals(r.engine.state, restored)
        val resumed = Runner(RunEngine(r.engine.steps, RunSettings(), restored), r.timeMs, r.meters)
        resumed.run(250, 300)
        assertEquals(listOf(2), resumed.of<Cue.Kilometer>().map { it.km })
    }

    /** Fixes at the same spot for [seconds] (a runner standing still). */
    private fun Runner.stand(seconds: Int): Runner {
        repeat(seconds) {
            timeMs += 1000
            cues += engine.onFix(fixAt(timeMs, meters), timeMs)
            cues += engine.onTick(timeMs)
        }
        return this
    }

    @Test
    fun `standing still mid-run pauses it, asks, and moving again resumes it`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, 600, null, null)))
        r.run(120, 300)
        r.stand(RunEngine.AUTO_PAUSE_STILL_SECONDS - 5)
        assertTrue(r.of<Cue.AutoPaused>().isEmpty())
        r.stand(10)
        assertEquals(1, r.of<Cue.AutoPaused>().size)
        assertTrue(r.engine.state.paused && r.engine.state.autoPaused)
        // The still time is not counted: active time stopped at the last movement (~120 s).
        assertTrue(r.engine.state.activeMs(r.timeMs) in 115_000..121_000, "active ${r.engine.state.activeMs(r.timeMs)}")
        r.stand(120)
        assertEquals(1, r.of<Cue.AutoPaused>().size)
        assertTrue(r.engine.state.paused)
        // Walking a few meters around is GPS-noise level and keeps it paused; running off resumes it.
        r.run(10, 300)
        assertEquals(1, r.of<Cue.AutoResumed>().size)
        assertTrue(!r.engine.state.paused && !r.engine.state.autoPaused)
        val before = r.engine.state.distanceM
        r.run(60, 300)
        assertTrue(r.engine.state.distanceM - before in 180.0..205.0)
    }

    @Test
    fun `asking for a break keeps the pause even when moving`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, 600, null, null)))
        r.run(60, 300)
        r.stand(RunEngine.AUTO_PAUSE_STILL_SECONDS + 1)
        assertTrue(r.engine.state.autoPaused)
        r.engine.holdPause()
        r.run(30, 300)
        assertTrue(r.engine.state.paused && r.of<Cue.AutoResumed>().isEmpty())
        r.engine.resume(r.timeMs)
        assertTrue(!r.engine.state.paused)
    }

    @Test
    fun `after the last block it finishes by itself after standing still, counting the way back`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, 60, null, null)))
        r.run(60, 300)
        assertTrue(r.engine.state.blocksDone)
        r.run(120, 300) // the way back
        r.stand(RunEngine.AUTO_FINISH_STILL_SECONDS - 5)
        assertTrue(!r.engine.state.finished)
        r.stand(10)
        val done = r.of<Cue.Finished>().single()
        assertTrue(done.activeSeconds in 175..181, "active ${done.activeSeconds}") // the still minute is left out
        val results = r.engine.state.stepResults
        assertEquals(listOf("steady", RunEngine.EXTRA_KIND), results.map { it.kind })
        assertTrue(results.last().distanceM in 380.0..405.0, "extra ${results.last().distanceM}")
        assertTrue(r.of<Cue.AutoPaused>().isEmpty())
    }

    @Test
    fun `no automatic pause before moving, without gps fixes or on the treadmill`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, 600, null, null)))
        r.stand(120) // waiting at the start
        assertTrue(r.of<Cue.AutoPaused>().isEmpty())
        val t = Runner(RunEngine(listOf(RunStep(RunBlockKind.STEADY, 600, null, null)), RunSettings(), RunState(startedAtMs = 0, treadmillKmh = 0.0)))
        t.tick(300)
        assertTrue(t.of<Cue.AutoPaused>().isEmpty() && !t.engine.state.paused)
    }

    @Test
    fun `gps jumps are ignored`() {
        val r = Runner(engine(RunStep(RunBlockKind.STEADY, 600, null, null)))
        r.run(10, 300)
        r.timeMs += 1000
        r.cues += r.engine.onFix(fixAt(r.timeMs, r.meters + 500), r.timeMs)
        r.run(10, 300)
        assertEquals(20 * 1000.0 / 300, r.engine.state.distanceM, LocationFilter.MIN_STEP_M)
    }
}
