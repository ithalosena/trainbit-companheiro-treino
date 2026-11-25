package com.ithalo.hybridcoach.core.schedule

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModalityQueueTest {
    private val mon = LocalDate.of(2026, 11, 9)

    private fun s(
        id: Long,
        modality: Modality,
        dayOffset: Long,
        status: String = "pending",
        hard: Boolean = false,
        legs: Boolean = false,
        optional: Boolean = false,
    ) = QueueSession(id, modality, id.toInt(), mon.plusDays(dayOffset), mon.plusDays(dayOffset), status, optional, hard, legs)

    @Test
    fun `a missed session stays the next one and the queue slides keeping the spacing`() {
        // Strength Mon, Wed, Fri. Monday was missed; today is Wednesday.
        val list = listOf(s(1, Modality.STRENGTH, 0), s(2, Modality.STRENGTH, 2), s(3, Modality.STRENGTH, 4))
        assertEquals(1, ModalityQueue.next(list, Modality.STRENGTH)!!.id)
        val moved = ModalityQueue.reschedule(list, mon.plusDays(2))
        assertEquals(mapOf(1L to mon.plusDays(2), 2L to mon.plusDays(4), 3L to mon.plusDays(6)), moved)
    }

    @Test
    fun `rescheduling twice changes nothing more`() {
        val list = listOf(s(1, Modality.STRENGTH, 0), s(2, Modality.STRENGTH, 2), s(3, Modality.STRENGTH, 4))
        val today = mon.plusDays(2)
        val moved = ModalityQueue.reschedule(list, today)
        val after = list.map { it.copy(date = moved[it.id] ?: it.date) }
        assertTrue(ModalityQueue.reschedule(after, today).isEmpty())
    }

    @Test
    fun `the two modalities are independent`() {
        val list = listOf(
            s(1, Modality.RUN, 0), s(2, Modality.RUN, 3),
            s(10, Modality.STRENGTH, 0, status = "done"), s(11, Modality.STRENGTH, 2),
        )
        val moved = ModalityQueue.reschedule(list, mon.plusDays(1))
        assertEquals(mon.plusDays(1), moved[1])
        assertEquals(mon.plusDays(4), moved[2])
        assertNull(moved[11])
        assertEquals(11, ModalityQueue.next(list, Modality.STRENGTH)!!.id)
    }

    @Test
    fun `nothing moves when on time`() {
        val list = listOf(s(1, Modality.STRENGTH, 0, "done"), s(2, Modality.STRENGTH, 2))
        assertTrue(ModalityQueue.reschedule(list, mon.plusDays(1)).isEmpty())
    }

    @Test
    fun `never two sessions of a modality on one day`() {
        // Done today already: the next one goes to tomorrow even if it was planned today.
        val list = listOf(s(1, Modality.STRENGTH, 2, "done"), s(2, Modality.STRENGTH, 2))
        assertEquals(mapOf(2L to mon.plusDays(3)), ModalityQueue.reschedule(list, mon.plusDays(2)))
    }

    @Test
    fun `hard runs stay two days apart`() {
        val list = listOf(s(1, Modality.RUN, 0, hard = true), s(2, Modality.RUN, 1, hard = true))
        val moved = ModalityQueue.reschedule(list, mon.plusDays(3))
        assertEquals(mon.plusDays(3), moved[1])
        assertEquals(mon.plusDays(5), moved[2])
    }

    @Test
    fun `a moved hard run avoids the day before legs`() {
        // Long run missed on Monday; legs on Thursday. Sliding by 2 would put it on Wednesday.
        val list = listOf(s(1, Modality.RUN, 0, hard = true), s(10, Modality.STRENGTH, 3, legs = true))
        val moved = ModalityQueue.reschedule(list, mon.plusDays(2))
        assertEquals(mon.plusDays(3), moved[1])
    }

    @Test
    fun `complementary sessions are not in the queue`() {
        val list = listOf(s(1, Modality.STRENGTH, 0, optional = true), s(2, Modality.STRENGTH, 3))
        assertEquals(2, ModalityQueue.next(list, Modality.STRENGTH)!!.id)
        assertTrue(ModalityQueue.reschedule(list, mon.plusDays(2)).isEmpty())
        assertEquals(0 to 1, ModalityQueue.progress(list, Modality.STRENGTH))
    }

    @Test
    fun `race mode keeps the calendar until the race`() {
        val list = listOf(s(1, Modality.RUN, 0), s(2, Modality.RUN, 2))
        assertTrue(ModalityQueue.reschedule(list, mon.plusDays(3), raceDate = mon.plusDays(20)).isEmpty())
        assertTrue(ModalityQueue.reschedule(list, mon.plusDays(3), raceDate = mon.plusDays(1)).isNotEmpty())
    }

    @Test
    fun `progress, remaining and projected end`() {
        val list = listOf(
            s(1, Modality.RUN, 0, "done"), s(2, Modality.RUN, 2), s(3, Modality.RUN, 9, "skipped"),
            s(10, Modality.STRENGTH, 1, "done"), s(11, Modality.STRENGTH, 5),
        )
        assertEquals(1 to 3, ModalityQueue.progress(list, Modality.RUN))
        assertEquals(1 to 2, ModalityQueue.progress(list, Modality.STRENGTH))
        assertEquals(2, ModalityQueue.remaining(list))
        assertEquals(mon.plusDays(5), ModalityQueue.projectedEnd(list))
    }

    @Test
    fun `ceiling is one and a half times the plan`() {
        assertEquals(mon.plusWeeks(12).minusDays(1), ModalityQueue.ceiling(mon, 8))
        assertEquals(mon.plusDays(41), ModalityQueue.ceiling(mon, 4))
    }

    @Test
    fun `closing starts with about 20 percent left or past the ceiling`() {
        val ceiling = mon.plusWeeks(6)
        assertFalse(ModalityQueue.closing(20, 5, mon, ceiling))
        assertTrue(ModalityQueue.closing(20, 4, mon, ceiling))
        assertTrue(ModalityQueue.closing(3, 1, mon, ceiling))
        assertTrue(ModalityQueue.closing(20, 10, ceiling.plusDays(1), ceiling))
        assertFalse(ModalityQueue.closing(0, 0, mon, ceiling))
    }

    @Test
    fun `days late`() {
        assertEquals(0, ModalityQueue.daysLate(mon, mon))
        assertEquals(0, ModalityQueue.daysLate(mon, mon.minusDays(1)))
        assertEquals(3, ModalityQueue.daysLate(mon, mon.plusDays(3)))
    }

    @Test
    fun `reentry bands`() {
        assertEquals(Reentry.NONE, Reentry.of(0))
        assertEquals(Reentry.NONE, Reentry.of(7))
        assertEquals(Reentry.SHORT, Reentry.of(8))
        assertEquals(Reentry.SHORT, Reentry.of(14))
        assertEquals(Reentry.MEDIUM, Reentry.of(15))
        assertEquals(Reentry.MEDIUM, Reentry.of(28))
        assertEquals(Reentry.LONG, Reentry.of(29))
        assertEquals(Reentry.LONG, Reentry.of(56))
        assertEquals(Reentry.RESTART, Reentry.of(57))
        assertEquals(0.0, Reentry.SHORT.strengthLoadCut())
        assertEquals(0.75, Reentry.SHORT.runVolumeShare())
        assertEquals(Reentry.LONG, Reentry.fromCode("long"))
    }

    @Test
    fun `gap counts from the last done session of the modality`() {
        val list = listOf(s(1, Modality.RUN, 0, "done"), s(10, Modality.STRENGTH, 5, "done"), s(2, Modality.RUN, 2))
        assertEquals(20, Reentry.gapDays(list, Modality.RUN, mon.plusDays(20)))
        assertEquals(15, Reentry.gapDays(list, Modality.STRENGTH, mon.plusDays(20)))
        assertNull(Reentry.gapDays(emptyList(), Modality.RUN, mon))
        assertEquals(3, Reentry.gapDays(emptyList(), Modality.RUN, mon, lastDoneBefore = mon.minusDays(3)))
    }
}
