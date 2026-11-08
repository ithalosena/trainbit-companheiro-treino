package com.ithalo.hybridcoach.core.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A planned session as the reminders see it; only [pending] ones get a reminder. */
data class ReminderSession(val sessionId: Long, val date: LocalDate, val isRun: Boolean, val title: String, val pending: Boolean)

/**
 * The player's training times: when the gym session and the run usually start ([strengthTime], [runTime]) and
 * how many minutes before to be reminded ([leadMinutes], 0 = at the time).
 */
data class ReminderSettings(val enabled: Boolean, val strengthTime: LocalTime, val runTime: LocalTime, val leadMinutes: Int)

/** Notify at [at] about [session], which starts at [startsAt]. */
data class Reminder(val session: ReminderSession, val startsAt: LocalDateTime, val at: LocalDateTime)

object Reminders {
    val LEAD_CHOICES = listOf(0, 15, 30, 60)

    /**
     * The next reminder after [now]: the pending session (today or later) whose reminder time comes first.
     * A session whose reminder time already passed is skipped, even if it has not started yet. Null when
     * reminders are off or nothing is left.
     */
    fun next(now: LocalDateTime, sessions: List<ReminderSession>, settings: ReminderSettings): Reminder? {
        if (!settings.enabled) return null
        return sessions.asSequence()
            .filter { it.pending && !it.date.isBefore(now.toLocalDate()) }
            .map { s ->
                val start = s.date.atTime(if (s.isRun) settings.runTime else settings.strengthTime)
                Reminder(s, start, start.minusMinutes(settings.leadMinutes.coerceAtLeast(0).toLong()))
            }
            .filter { it.at.isAfter(now) }
            .minWithOrNull(compareBy<Reminder>({ it.at }, { it.session.sessionId }))
    }
}
