package com.ithalo.hybridcoach.core.strength

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionQueueTest {
    private val order = listOf(1L, 2L, 3L, 4L)

    @Test
    fun `postpone sends the exercise to the end and keeps the rest`() {
        assertEquals(listOf(1L, 3L, 4L, 2L), SessionQueue.postpone(order, 2))
        assertEquals(order, SessionQueue.postpone(order, 9))
        assertEquals(listOf(1L, 2L, 3L, 4L), SessionQueue.postpone(SessionQueue.postpone(order, 4), 4))
    }

    @Test
    fun `do now puts the exercise before the first open one`() {
        assertEquals(listOf(1L, 4L, 2L, 3L), SessionQueue.doNow(order, 4, complete = setOf(1L)))
        assertEquals(listOf(4L, 1L, 2L, 3L), SessionQueue.doNow(order, 4, complete = emptySet()))
        assertEquals(listOf(1L, 2L, 3L, 4L), SessionQueue.doNow(order, 4, complete = setOf(1L, 2L, 3L)))
    }

    @Test
    fun `drag moves between positions and ignores bad indices`() {
        assertEquals(listOf(2L, 3L, 1L, 4L), SessionQueue.move(order, 0, 2))
        assertEquals(listOf(4L, 1L, 2L, 3L), SessionQueue.move(order, 3, 0))
        assertEquals(order, SessionQueue.move(order, 0, 9))
    }

    @Test
    fun `only postponed left are offered again by the next exercise rule`() {
        // 2 was postponed to the end; 1, 3 and 4 are done: the next one is 2.
        val queue = SessionQueue.postpone(order, 2)
        val progress = queue.map { ExerciseProgress(false, 3, if (it == 2L) 1 else 3) }
        assertEquals(queue.indexOf(2L), SessionProgress.nextExercise(progress))
    }

    @Test
    fun `executed order follows the first set of each exercise`() {
        assertEquals(listOf(3L, 1L, 2L), SessionQueue.executedOrder(mapOf(1L to 200L, 2L to 300L, 3L to 100L)))
    }

    @Test
    fun `work clock keeps time across pauses and a killed process`() {
        var clock = WorkClock().start(now = 1_000)
        assertTrue(clock.running)
        assertEquals(5, clock.elapsedSeconds(now = 6_000))
        clock = clock.pause(now = 11_000)
        assertFalse(clock.running)
        assertEquals(10, clock.elapsedSeconds(now = 99_000))
        // Saved and read back (process death): the same two numbers give the same time.
        val restored = WorkClock(clock.accumulatedMs, clock.startedAt).start(now = 20_000)
        assertEquals(15, restored.elapsedSeconds(now = 25_000))
        assertEquals(restored, restored.start(now = 30_000))
    }

    @Test
    fun `work clock tells when the target passed while away`() {
        val clock = WorkClock(startedAt = 0)
        assertTrue(clock.reachedSince(seenSeconds = 20, targetSeconds = 30, now = 45_000))
        assertFalse(clock.reachedSince(seenSeconds = 31, targetSeconds = 30, now = 45_000))
        assertFalse(clock.reachedSince(seenSeconds = 20, targetSeconds = 60, now = 45_000))
        assertEquals(0, WorkClock(startedAt = 5_000).elapsedSeconds(now = 1_000))
    }

    @Test
    fun `effort bar maps 0 to no data and stays within 0 to 10`() {
        assertEquals(0, EffortScale.position(null))
        assertNull(EffortScale.stored(0))
        assertEquals(1, EffortScale.step(null, +1))
        assertNull(EffortScale.step(1, -1))
        assertNull(EffortScale.step(null, -1))
        assertEquals(10, EffortScale.step(10, +1))
        assertEquals(7, EffortScale.stored(7))
    }
}
