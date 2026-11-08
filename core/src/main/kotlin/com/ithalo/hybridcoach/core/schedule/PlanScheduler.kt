package com.ithalo.hybridcoach.core.schedule

import com.ithalo.hybridcoach.core.plan.PlanDocument
import com.ithalo.hybridcoach.core.plan.SessionDef
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ScheduledSession(val weekNumber: Int, val date: LocalDate, val session: SessionDef)

/**
 * Date = week anchor + (week - 1) * 7 + weekday offset, where the anchor is the Monday of the
 * calendar week that contains `start_date` (the start date itself when it is a Monday).
 */
object PlanScheduler {
    fun weekAnchor(startDate: LocalDate): LocalDate =
        startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Suggested start date: [from] itself when it is a Monday, otherwise the next one. */
    fun nextMonday(from: LocalDate): LocalDate = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

    fun dateFor(startDate: LocalDate, week: Int, day: DayOfWeek): LocalDate =
        weekAnchor(startDate).plusDays((week - 1) * 7L + (day.value - DayOfWeek.MONDAY.value))

    /** Last day (Sunday) of the final week of the block. */
    fun endDate(startDate: LocalDate, durationWeeks: Int): LocalDate =
        weekAnchor(startDate).plusDays(durationWeeks * 7L - 1)

    /** Expands grouped weeks into one entry per week, sorted by date (same-day order follows the JSON). */
    fun schedule(doc: PlanDocument): List<ScheduledSession> =
        doc.weeks
            .flatMap { group ->
                group.weeks.flatMap { week ->
                    group.sessions.map { ScheduledSession(week, dateFor(doc.plan.startDate, week, it.dayOfWeek), it) }
                }
            }
            .sortedBy { it.date }
}
