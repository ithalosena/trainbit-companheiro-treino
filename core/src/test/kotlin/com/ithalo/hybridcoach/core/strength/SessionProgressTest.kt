package com.ithalo.hybridcoach.core.strength

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionProgressTest {
    private fun ex(total: Int, logged: Int, skipped: Boolean = false) = ExerciseProgress(skipped, total, logged)

    @Test
    fun `first unfinished exercise is next`() {
        assertEquals(1, SessionProgress.nextExercise(listOf(ex(3, 3), ex(3, 1), ex(3, 0))))
    }

    @Test
    fun `skipped exercises are passed over`() {
        assertEquals(2, SessionProgress.nextExercise(listOf(ex(3, 3), ex(3, 0, skipped = true), ex(3, 0))))
    }

    @Test
    fun `extra sets reopen a finished exercise`() {
        assertEquals(0, SessionProgress.nextExercise(listOf(ex(4, 3), ex(3, 3))))
    }

    @Test
    fun `complete or empty session has no next exercise`() {
        assertNull(SessionProgress.nextExercise(listOf(ex(3, 3), ex(2, 0, skipped = true))))
        assertNull(SessionProgress.nextExercise(emptyList()))
    }

    @Test
    fun `next after current wraps around`() {
        val list = listOf(ex(3, 0), ex(3, 3), ex(3, 3))
        assertEquals(0, SessionProgress.nextIncompleteAfter(list, 2))
        assertEquals(0, SessionProgress.nextIncompleteAfter(list, 1))
        assertEquals(0, SessionProgress.nextIncompleteAfter(list, 0))
        assertNull(SessionProgress.nextIncompleteAfter(listOf(ex(1, 1)), 0))
    }

    @Test
    fun `default reps uses the upper bound of a range`() {
        assertEquals(8, SessionProgress.defaultReps("6-8"))
        assertEquals(8, SessionProgress.defaultReps("8"))
        assertEquals(12, SessionProgress.defaultReps(" 10 – 12 "))
        assertNull(SessionProgress.defaultReps("AMRAP"))
        assertNull(SessionProgress.defaultReps("30s"))
    }
}
