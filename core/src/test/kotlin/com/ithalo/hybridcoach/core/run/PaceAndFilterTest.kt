package com.ithalo.hybridcoach.core.run

import com.ithalo.hybridcoach.core.plan.PaceRange
import com.ithalo.hybridcoach.core.plan.RunBlockDef
import com.ithalo.hybridcoach.core.plan.RunBlockKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaceAndFilterTest {
    @Test
    fun `distance between fixes is close to the real distance`() {
        assertEquals(100.0, GeoMath.distance(fixAt(0, 0.0), fixAt(1, 100.0)), 0.01)
    }

    @Test
    fun `filter drops inaccurate fixes and impossible jumps`() {
        val first = fixAt(0, 0.0)
        assertEquals(LocationFilter.Result.Reject, LocationFilter.check(null, fixAt(0, 0.0, accuracy = 25f)))
        assertEquals(LocationFilter.Result.Accept(0.0), LocationFilter.check(null, first))
        val ok = LocationFilter.check(first, fixAt(1000, 4.0)) as LocationFilter.Result.Accept
        assertEquals(4.0, ok.meters, 0.01)
        assertEquals(LocationFilter.Result.Reject, LocationFilter.check(first, fixAt(1000, 50.0)))
        assertEquals(LocationFilter.Result.Reject, LocationFilter.check(first, fixAt(0, 1.0)))
    }

    @Test
    fun `smoother averages over the window and needs half of it`() {
        val s = PaceSmoother(20)
        s.add(0, 0.0)
        s.add(5_000, 16.0)
        assertNull(s.pace())
        // 5:00/km is 3.333 m/s.
        for (t in 1..40) s.add(t * 1000L, t * 1000.0 / 300)
        assertEquals(300, s.pace())
        // A change of speed shows up only gradually, then fully after the window.
        for (t in 41..45) s.add(t * 1000L, 40 * 1000.0 / 300 + (t - 40) * 1000.0 / 240)
        val mid = s.pace()!!
        assertTrue(mid in 241..299, "pace $mid")
        for (t in 46..70) s.add(t * 1000L, 40 * 1000.0 / 300 + (t - 40) * 1000.0 / 240)
        assertEquals(240, s.pace())
    }

    @Test
    fun `standing still gives no pace`() {
        val s = PaceSmoother(10)
        for (t in 0..20) s.add(t * 1000L, 3.0 + t * 0.01)
        assertNull(s.pace())
    }

    @Test
    fun `interval and recovery with the same repeat are interleaved`() {
        val pace = PaceRange(300, 330)
        val steps = RunSteps.expand(
            listOf(
                RunBlockDef(RunBlockKind.WARMUP, 600, null, null, 1),
                RunBlockDef(RunBlockKind.INTERVAL, null, 400, pace, 3),
                RunBlockDef(RunBlockKind.RECOVERY, 90, null, null, 3),
                RunBlockDef(RunBlockKind.STEADY, 60, null, null, 2),
            ),
        )
        assertEquals(
            listOf("WARMUP", "INTERVAL", "RECOVERY", "INTERVAL", "RECOVERY", "INTERVAL", "RECOVERY", "STEADY", "STEADY"),
            steps.map { it.kind.name },
        )
        assertEquals(listOf(1, 1, 2, 2, 3, 3), steps.subList(1, 7).map { it.repetition })
    }

    @Test
    fun `run and walk as steady plus recovery with the same repeat are interleaved too`() {
        // How the prompt asks for beginner sessions: 5 min warm-up, then 6 x (8 min run + walk).
        val steps = RunSteps.expand(
            listOf(
                RunBlockDef(RunBlockKind.WARMUP, 300, null, null, 1),
                RunBlockDef(RunBlockKind.STEADY, 480, null, null, 6),
                RunBlockDef(RunBlockKind.RECOVERY, 120, null, null, 6),
                RunBlockDef(RunBlockKind.COOLDOWN, 300, null, null, 1),
            ),
        )
        assertEquals(listOf("WARMUP") + List(6) { listOf("STEADY", "RECOVERY") }.flatten() + "COOLDOWN", steps.map { it.kind.name })
        assertEquals((1..6).flatMap { listOf(it, it) }, steps.subList(1, 13).map { it.repetition })
        // A recovery with a different repeat is not paired: both repeat back to back.
        val unpaired = RunSteps.expand(listOf(RunBlockDef(RunBlockKind.STEADY, 480, null, null, 2), RunBlockDef(RunBlockKind.RECOVERY, 120, null, null, 1)))
        assertEquals(listOf("STEADY", "STEADY", "RECOVERY"), unpaired.map { it.kind.name })
    }
}

class PaceCoachTest {
    private val range = PaceRange(300, 330)
    private val coach = PaceCoach(RunSettings())

    /** Evaluates once per second from [from] for [seconds] at [pace]; returns (second, advice) pairs. */
    private fun feed(from: Int, seconds: Int, pace: Int?, r: PaceRange? = range) =
        (from until from + seconds).mapNotNull { s -> coach.evaluate(s * 1000L, pace, r)?.let { s to it } }

    @Test
    fun `alert only after ten seconds out of range plus tolerance`() {
        assertEquals(emptyList(), feed(0, 60, 334)) // inside the 5 s tolerance
        val alerts = feed(100, 12, 340)
        assertEquals(listOf(110 to PaceAdvice.SPEED_UP), alerts)
    }

    @Test
    fun `alerts are spaced by the minimum interval`() {
        val alerts = feed(0, 100, 280)
        assertEquals(listOf(10 to PaceAdvice.SLOW_DOWN, 40 to PaceAdvice.SLOW_DOWN, 70 to PaceAdvice.SLOW_DOWN), alerts)
    }

    @Test
    fun `on pace once when coming back and at most once per minute`() {
        feed(0, 11, 340)
        assertEquals(listOf(11 to PaceAdvice.ON_PACE), feed(11, 5, 315))
        // Out again and back within a minute: the alert repeats, the "on pace" does not.
        assertEquals(listOf(50 to PaceAdvice.SPEED_UP), feed(40, 11, 340))
        assertEquals(emptyList(), feed(51, 20, 315))
        // A minute after the previous "on pace" it may be said again.
        assertEquals(listOf(71 to PaceAdvice.ON_PACE), feed(71, 30, 315))
    }

    @Test
    fun `no target or no pace means no alerts`() {
        assertEquals(emptyList(), feed(0, 60, 500, r = null))
        assertEquals(emptyList(), feed(0, 60, null))
    }

    @Test
    fun `brief excursions do not alert`() {
        val alerts = (0 until 60).mapNotNull { s -> coach.evaluate(s * 1000L, if (s % 8 < 6) 340 else 315, range) }
        assertEquals(emptyList(), alerts)
    }
}
