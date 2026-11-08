package com.ithalo.hybridcoach.core.strength

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutSummaryTest {
    private fun set(ex: String, reps: Int, kg: Double, t: Long, rpe: Int? = null, failed: Boolean = false, timed: Boolean = false) =
        SummarySet(ex, reps, kg, failed, rpe, t * 60_000L, timed)

    @Test
    fun `adds up duration, sets, volume, best set and mean effort`() {
        val sets = listOf(
            set("Supino", 10, 40.0, 0, rpe = 6),
            set("Supino", 8, 50.0, 3, rpe = 8),
            set("Remada", 12, 30.0, 40, rpe = 7),
            set("Prancha", 60, 0.0, 41, timed = true),
        )
        val exercises = listOf(
            SummaryExercise(2, 2, skipped = false, substituted = false),
            SummaryExercise(1, 1, skipped = false, substituted = true),
            SummaryExercise(1, 1, skipped = false, substituted = false),
            SummaryExercise(3, 0, skipped = true, substituted = false),
        )
        val s = WorkoutSummaries.build(sets, exercises, listOf("Supino", "Supino"))
        assertEquals(41, s.durationMinutes)
        assertEquals(4, s.setsDone)
        assertEquals(4, s.setsPlanned) // the skipped exercise's sets do not count
        assertEquals(400.0 + 400.0 + 360.0, s.volumeKg)
        assertEquals(3, s.exercisesDone)
        assertEquals(1, s.exercisesSkipped)
        assertEquals(1, s.exercisesSubstituted)
        assertEquals("Supino", s.bestSet?.exercise)
        assertEquals(50.0, s.bestSet?.loadKg)
        assertEquals(7.0, s.avgRpe)
        assertEquals(listOf("Supino"), s.records)
    }

    @Test
    fun `failed and timed sets are not volume and a lone set has no duration`() {
        val s = WorkoutSummaries.build(
            listOf(set("A", 5, 100.0, 0, failed = true)),
            listOf(SummaryExercise(1, 1, skipped = false, substituted = false)),
            emptyList(),
        )
        assertEquals(0.0, s.volumeKg)
        assertNull(s.bestSet)
        assertNull(s.avgRpe)
        assertEquals(0, s.durationMinutes)
    }

    @Test
    fun `volume is compared with the last time in whole percent`() {
        val sets = listOf(set("Supino", 10, 50.0, 0), set("Supino", 10, 60.0, 2))
        val ex = listOf(SummaryExercise(2, 2, skipped = false, substituted = false))
        assertEquals(10, WorkoutSummaries.build(sets, ex, emptyList(), previousVolumeKg = 1000.0).volumeChangePercent)
        assertEquals(-12, WorkoutSummaries.build(sets, ex, emptyList(), previousVolumeKg = 1250.0).volumeChangePercent)
        assertNull(WorkoutSummaries.build(sets, ex, emptyList()).volumeChangePercent)
        assertNull(WorkoutSummaries.build(sets, ex, emptyList(), previousVolumeKg = 0.0).volumeChangePercent)
        assertEquals(1100.0, WorkoutSummaries.volumeKg(sets))
    }

    private fun rec(id: Long, session: Long, ex: String, reps: Int, kg: Double, failed: Boolean = false, timed: Boolean = false) =
        RecordSet(id, session, ex, ex.uppercase(), reps, kg, failed, timed, id * 1000)

    @Test
    fun `records of a session survive from the saved sets, with the player's rule`() {
        val history = listOf(
            rec(1, 1, "supino", 8, 40.0), // baseline
            rec(2, 1, "remada", 10, 30.0), // baseline
            rec(3, 2, "supino", 8, 40.0), // equal: no record
            rec(4, 2, "supino", 9, 40.0), // same load, more reps: record
            rec(5, 2, "remada", 12, 35.0, failed = true), // failed: ignored
            rec(6, 2, "agacha", 5, 80.0), // first time ever: baseline only
            rec(7, 2, "prancha", 60, 0.0, timed = true),
            rec(8, 3, "remada", 10, 32.0), // another session
        )
        assertEquals(listOf("SUPINO"), WorkoutSummaries.recordsIn(2, history))
        assertEquals(listOf("REMADA"), WorkoutSummaries.recordsIn(3, history))
        val beatInSameSession = history + rec(9, 2, "agacha", 5, 85.0) + rec(10, 2, "prancha", 70, 0.0, timed = true)
        assertEquals(listOf("SUPINO", "AGACHA", "PRANCHA"), WorkoutSummaries.recordsIn(2, beatInSameSession))
    }

    @Test
    fun `suggested effort is the rounded mean, clamped`() {
        assertEquals(8, WorkoutSummaries.suggestedRpe(7.5))
        assertEquals(7, WorkoutSummaries.suggestedRpe(7.4))
        assertEquals(10, WorkoutSummaries.suggestedRpe(11.0))
        assertNull(WorkoutSummaries.suggestedRpe(null))
    }
}
