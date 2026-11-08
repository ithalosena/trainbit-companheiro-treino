package com.ithalo.hybridcoach.core.strength

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestTimerTest {
    @Test
    fun `remaining rounds up and stops at zero`() {
        val t = RestTimer(startMs = 1_000, totalSeconds = 90)
        assertEquals(90, t.remainingSeconds(1_000))
        assertEquals(90, t.remainingSeconds(1_400))
        assertEquals(90, t.remainingSeconds(1_999))
        assertEquals(89, t.remainingSeconds(2_000))
        assertEquals(1, t.remainingSeconds(90_100))
        assertEquals(0, t.remainingSeconds(91_000))
        assertEquals(0, t.remainingSeconds(200_000))
    }

    @Test
    fun `adding and removing fifteen seconds moves the end`() {
        val t = RestTimer(0, 60)
        t.adjust(+15, nowMs = 10_000)
        assertEquals(65, t.remainingSeconds(10_000))
        t.adjust(-15, nowMs = 10_000)
        t.adjust(-15, nowMs = 10_000)
        assertEquals(35, t.remainingSeconds(10_000))
    }

    @Test
    fun `removing more time than left ends the rest instead of going negative`() {
        val t = RestTimer(0, 60)
        t.adjust(-15, nowMs = 50_000)
        assertEquals(0, t.remainingSeconds(50_000))
        assertEquals(50_000, t.endAtMs)
    }

    @Test
    fun `countdown cues fire once per second at 3 2 1 and done`() {
        assertEquals(RestCue.THREE, RestTimer.cue(4, 3))
        assertEquals(RestCue.TWO, RestTimer.cue(3, 2))
        assertEquals(RestCue.ONE, RestTimer.cue(2, 1))
        assertEquals(RestCue.DONE, RestTimer.cue(1, 0))
        assertNull(RestTimer.cue(3, 3))
        assertNull(RestTimer.cue(30, 29))
        assertNull(RestTimer.cue(0, 0))
    }

    @Test
    fun `adding time after the countdown started repeats the cues on the way down`() {
        assertNull(RestTimer.cue(2, 17))
        assertEquals(RestCue.THREE, RestTimer.cue(4, 3))
    }

    @Test
    fun `spoken texts are portuguese`() {
        assertEquals("Descanso terminado", RestCue.DONE.spoken)
        assertEquals("três", RestCue.THREE.spoken)
    }
}
