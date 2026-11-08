package com.ithalo.hybridcoach.core.strength

import com.ithalo.hybridcoach.core.summary.SessionRecord
import com.ithalo.hybridcoach.core.summary.SessionStatus
import com.ithalo.hybridcoach.core.summary.SummaryBuilder
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StrengthSummarizerTest {
    private fun set(
        session: Long, reps: Int, load: Double, failed: Boolean = false, rpe: Int? = null,
        id: String = "supino", name: String = "Supino reto com barra",
    ) = StrengthSetRecord(id, name, session, reps, load, failed, rpe, plannedSets = 4, plannedReps = "6-8", plannedLoadKg = 40.0)

    private val records = listOf(
        set(1, 8, 40.0, rpe = 8), set(1, 7, 40.0, rpe = 9), set(1, 5, 42.5, failed = true, rpe = 10),
        set(2, 8, 42.5, rpe = 8), set(2, 8, 42.5, rpe = 9),
        set(3, 6, 45.0),
        set(3, 10, 20.0, id = "triceps", name = "Tríceps na polia"),
    )

    @Test
    fun `groups by exercise in order of first appearance`() {
        val summary = StrengthSummarizer.summarize(records)
        assertEquals(listOf("Supino reto com barra", "Tríceps na polia"), summary.map { it.exercise })
    }

    @Test
    fun `best set ignores failed sets and prefers load then reps`() {
        val bench = StrengthSummarizer.summarize(records).first().done
        assertEquals(45.0, bench.bestSet?.loadKg)
        assertEquals(6, bench.bestSet?.reps)
    }

    @Test
    fun `best set falls back to failed sets when every set failed`() {
        val only = listOf(set(1, 3, 50.0, failed = true), set(1, 2, 52.5, failed = true))
        assertEquals(52.5, StrengthSummarizer.summarize(only).single().done.bestSet?.loadKg)
    }

    @Test
    fun `counts failures and averages rpe to one decimal`() {
        val bench = StrengthSummarizer.summarize(records).first().done
        assertEquals(1, bench.failedSets)
        assertEquals(8.8, bench.avgRpe)
    }

    @Test
    fun `rpe is null when never recorded`() {
        assertEquals(null, StrengthSummarizer.summarize(records).last().done.avgRpe)
    }

    @Test
    fun `load trend keeps the top load of each session in order`() {
        assertEquals(listOf(42.5, 42.5, 45.0), StrengthSummarizer.summarize(records).first().done.loadTrendKg)
    }

    @Test
    fun `planned values come from the plan`() {
        val planned = StrengthSummarizer.summarize(records).first().planned
        assertEquals(4, planned.sets)
        assertEquals("6-8", planned.reps)
        assertEquals(40.0, planned.loadKg)
    }

    @Test
    fun `empty log gives an empty summary`() {
        assertEquals(emptyList(), StrengthSummarizer.summarize(emptyList()))
    }

    @Test
    fun `large blocks are trimmed under the size target`() {
        val many = (1..40).flatMap { e ->
            (1L..12L).flatMap { s -> (1..4).map { set(s, 8, 20.0 + s, id = "ex$e", name = "Exercício número $e com um nome bem comprido") } }
        }
        val strength = StrengthSummarizer.summarize(many)
        val full = SummaryBuilder.build("Bloco", LocalDate.of(2026, 11, 9), LocalDate.of(2027, 1, 31), emptyList(), strength)
        assertTrue(SummaryBuilder.toJson(full).length <= SummaryBuilder.MAX_JSON_CHARS)
        assertTrue(full.strength.isNotEmpty())
    }

    @Test
    fun `small blocks are left untouched`() {
        val strength = StrengthSummarizer.summarize(records)
        val s = SummaryBuilder.build(
            "Bloco", LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 6),
            listOf(SessionRecord(SessionStatus.DONE, null)), strength, listOf("joelho incomodou"),
        )
        assertEquals(strength, s.strength)
        assertEquals(listOf("joelho incomodou"), s.userNotes)
        assertNotNull(s.strength.first().done.bestSet)
    }
}
