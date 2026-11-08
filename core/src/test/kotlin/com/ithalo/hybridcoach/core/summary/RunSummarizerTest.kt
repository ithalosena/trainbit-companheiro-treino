package com.ithalo.hybridcoach.core.summary

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunSummarizerTest {
    private val intervalPlan = listOf(
        PlannedRunBlock("warmup", 400, 440),
        PlannedRunBlock("interval", 300, 330),
        PlannedRunBlock("recovery", null, null),
    )

    @Test
    fun `interval run reports the pace of the interval blocks`() {
        val r = RunSummarizer.summarize(
            RunRecord(
                week = 2,
                plannedBlocks = intervalPlan,
                distanceM = 5230.0,
                durationSeconds = 1900,
                steps = listOf(
                    ExecutedRunStep("warmup", 1400.0, 600),
                    ExecutedRunStep("interval", 400.0, 128),
                    ExecutedRunStep("recovery", 150.0, 90),
                    ExecutedRunStep("interval", 400.0, 132),
                ),
                rpe = 8,
            ),
        )
        assertEquals(RunSummary(2, "interval", PaceSummary(300, 330), 325, 5.2, 8.0), r)
    }

    @Test
    fun `steady run without block data falls back to the whole run`() {
        val r = RunSummarizer.summarize(RunRecord(1, listOf(PlannedRunBlock("steady", null, null)), 3000.0, 1200, emptyList(), null))
        assertEquals("steady", r.type)
        assertNull(r.plannedPace)
        assertEquals(400, r.actualAvgPace)
        assertNull(r.avgRpe)
    }

    @Test
    fun `runs are merged by week when the summary is too long`() {
        val runs = (1..4).flatMap { w -> (1..40).map { RunSummary(w, "steady", PaceSummary(390, 430), 400 + it % 3, 5.0, 6.0) } }
        val s = SummaryBuilder.build("B", LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 6), emptyList(), runs = runs)
        assertEquals(4, s.runs.size)
        assertTrue(s.runs.all { it.distanceKm == 200.0 })
        assertTrue(SummaryBuilder.toJson(s).length <= SummaryBuilder.MAX_JSON_CHARS)
        assertTrue("Semana 1, steady: 200 km" in SummaryBuilder.toText(s))
    }
}
