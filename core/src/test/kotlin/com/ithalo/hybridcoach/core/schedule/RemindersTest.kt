package com.ithalo.hybridcoach.core.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemindersTest {
    private val mon = LocalDate.of(2026, 11, 9)
    private val settings = ReminderSettings(enabled = true, strengthTime = LocalTime.of(18, 0), runTime = LocalTime.of(7, 0), leadMinutes = 30)

    private fun s(id: Long, day: Long, run: Boolean = false, pending: Boolean = true) = ReminderSession(id, mon.plusDays(day), run, "S$id", pending)

    @Test
    fun `the next reminder is the earliest pending session, lead time before its start`() {
        val sessions = listOf(s(1, 0), s(2, 1, run = true), s(3, 2))
        val r = Reminders.next(mon.atTime(9, 0), sessions, settings)!!
        assertEquals(1L, r.session.sessionId)
        assertEquals(mon.atTime(18, 0), r.startsAt)
        assertEquals(mon.atTime(17, 30), r.at)
        // After that reminder, the run of the next morning (07:00 - 30 min).
        assertEquals(mon.plusDays(1).atTime(6, 30), Reminders.next(mon.atTime(17, 30), sessions, settings)!!.at)
    }

    @Test
    fun `done, skipped and past sessions are left out, and a passed reminder time too`() {
        val sessions = listOf(s(1, -1), s(2, 0, pending = false), s(3, 0), s(4, 3))
        // 17:45: session 3's reminder (17:30) has passed although it starts at 18:00, so the next one is day 3.
        assertEquals(4L, Reminders.next(mon.atTime(17, 45), sessions, settings)!!.session.sessionId)
        assertNull(Reminders.next(mon.plusDays(4).atTime(0, 0), sessions, settings))
    }

    @Test
    fun `strength and run on the same day each get their own time, and off means none`() {
        val sessions = listOf(s(1, 0), s(2, 0, run = true))
        assertEquals(2L, Reminders.next(mon.atTime(0, 0), sessions, settings.copy(leadMinutes = 0))!!.session.sessionId)
        assertEquals(1L, Reminders.next(mon.atTime(7, 1), sessions, settings.copy(leadMinutes = 0))!!.session.sessionId)
        assertNull(Reminders.next(mon.atTime(0, 0), sessions, settings.copy(enabled = false)))
    }
}
