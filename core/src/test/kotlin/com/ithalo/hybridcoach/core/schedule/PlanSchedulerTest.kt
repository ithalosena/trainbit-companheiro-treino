package com.ithalo.hybridcoach.core.schedule

import com.ithalo.hybridcoach.core.plan.PlanValidator
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlanSchedulerTest {
    private val monday = LocalDate.of(2026, 11, 9)

    private fun fullPlan() = assertNotNull(
        PlanValidator.validateText(checkNotNull(javaClass.classLoader.getResource("valid_full.json")).readText()).document,
    )

    @Test
    fun `monday start maps weekdays to offsets`() {
        assertEquals(LocalDate.of(2026, 11, 9), PlanScheduler.dateFor(monday, 1, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 10), PlanScheduler.dateFor(monday, 1, DayOfWeek.TUESDAY))
        assertEquals(LocalDate.of(2026, 11, 15), PlanScheduler.dateFor(monday, 1, DayOfWeek.SUNDAY))
    }

    @Test
    fun `later weeks add seven days each`() {
        assertEquals(LocalDate.of(2026, 11, 16), PlanScheduler.dateFor(monday, 2, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 12, 5), PlanScheduler.dateFor(monday, 4, DayOfWeek.SATURDAY))
    }

    @Test
    fun `non monday start anchors to the monday of that week`() {
        val wednesday = LocalDate.of(2026, 11, 11)
        assertEquals(monday, PlanScheduler.weekAnchor(wednesday))
        assertEquals(LocalDate.of(2026, 11, 16), PlanScheduler.dateFor(wednesday, 2, DayOfWeek.MONDAY))
        assertEquals(monday, PlanScheduler.weekAnchor(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun `next monday keeps a monday and advances other days`() {
        assertEquals(monday, PlanScheduler.nextMonday(monday))
        assertEquals(LocalDate.of(2026, 11, 16), PlanScheduler.nextMonday(LocalDate.of(2026, 11, 10)))
        assertEquals(LocalDate.of(2026, 11, 16), PlanScheduler.nextMonday(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun `end date is the sunday of the last week`() {
        assertEquals(LocalDate.of(2026, 12, 6), PlanScheduler.endDate(monday, 4))
        assertEquals(LocalDate.of(2026, 11, 15), PlanScheduler.endDate(monday, 1))
    }

    @Test
    fun `month and year boundaries`() {
        assertEquals(LocalDate.of(2027, 1, 4), PlanScheduler.dateFor(LocalDate.of(2026, 12, 28), 2, DayOfWeek.MONDAY))
    }

    @Test
    fun `valid_full expands grouped weeks into every session`() {
        val sessions = PlanScheduler.schedule(fullPlan())
        // weeks 1 and 2 share 4 sessions, week 3 has 4, week 4 has 3
        assertEquals(15, sessions.size)
        assertEquals(sessions.sortedBy { it.date }, sessions)
        assertEquals(LocalDate.of(2026, 11, 9), sessions.first().date)
        assertEquals(LocalDate.of(2026, 12, 5), sessions.last().date)
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4), sessions.map { it.weekNumber })
    }

    @Test
    fun `week two repeats week one on the following week`() {
        val sessions = PlanScheduler.schedule(fullPlan())
        val week1 = sessions.filter { it.weekNumber == 1 }.map { it.date.plusDays(7) }
        assertEquals(week1, sessions.filter { it.weekNumber == 2 }.map { it.date })
    }
}
