package com.ithalo.hybridcoach.core.schedule

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The two independent queues of a block: a missed run never moves the strength queue, and the other way round. */
enum class Modality {
    STRENGTH, RUN;

    companion object {
        fun of(type: String): Modality = if (type == "run") RUN else STRENGTH
    }
}

/**
 * A session as the queue sees it. [date] is the current (maybe moved) date, [plannedDate] the one the plan gave.
 * [status] is "pending", "done" or "skipped". [hardRun] is an interval or long run; [legs] a strength session
 * that trains the legs.
 */
data class QueueSession(
    val id: Long,
    val modality: Modality,
    val sequence: Int,
    val date: LocalDate,
    val plannedDate: LocalDate,
    val status: String,
    val optional: Boolean = false,
    val hardRun: Boolean = false,
    val legs: Boolean = false,
) {
    val pending: Boolean get() = status == "pending"
    val done: Boolean get() = status == "done"
}

/**
 * Sequential queues per modality (Item 8): the next session of a modality is its first one not done, a missed
 * session is never skipped over, and the dates of the ones still to do slide forward together.
 *
 * Complementary sessions (`optional`) are not part of the queues: they keep their date and missing them is no miss.
 */
object ModalityQueue {
    /** Hard runs (interval or long) stay at least this many days apart when they are moved. */
    const val HARD_RUN_GAP_DAYS = 2

    private fun queue(sessions: List<QueueSession>, modality: Modality) =
        sessions.filter { it.modality == modality && !it.optional }.sortedWith(compareBy({ it.sequence }, { it.id }))

    /** The next session of [modality]: its first one not done (a missed one stays the next). */
    fun next(sessions: List<QueueSession>, modality: Modality): QueueSession? = queue(sessions, modality).firstOrNull { it.pending }

    /**
     * New dates for the sessions still to do, only for the ones that change.
     *
     * Per modality: when the first pending session is overdue (before today, or before tomorrow when a session of
     * that modality was already done today), every pending session moves forward by the same number of days, which
     * keeps the plan's spacing; no two sessions of one modality share a day; hard runs stay [HARD_RUN_GAP_DAYS]
     * apart; a hard run that was moved does not land on the day before a leg workout. With [raceDate] set and not
     * yet past, the calendar wins: nothing moves.
     */
    fun reschedule(sessions: List<QueueSession>, today: LocalDate, raceDate: LocalDate? = null): Map<Long, LocalDate> {
        if (raceDate != null && !today.isAfter(raceDate)) return emptyMap()
        val moved = HashMap<Long, LocalDate>()
        // Strength first, so a moved hard run can avoid the (possibly moved) leg days.
        for (modality in listOf(Modality.STRENGTH, Modality.RUN)) {
            val list = queue(sessions, modality)
            val pending = list.filter { it.pending }
            if (pending.isEmpty()) continue
            val lastDone = list.filter { it.done }.maxOfOrNull { it.date }
            val floor = if (lastDone != null && !lastDone.isBefore(today)) lastDone.plusDays(1) else today
            val shift = maxOf(0L, ChronoUnit.DAYS.between(pending.first().date, floor))
            val legDays = if (modality == Modality.RUN) {
                sessions.filter { it.modality == Modality.STRENGTH && it.legs && !it.optional && it.status != "skipped" }
                    .map { moved[it.id] ?: it.date }.toSet()
            } else emptySet()
            var previous = floor.minusDays(1)
            var lastHard = list.filter { it.done && it.hardRun }.maxOfOrNull { it.date }
            for (s in pending) {
                var d = s.date.plusDays(shift)
                if (!d.isAfter(previous)) d = previous.plusDays(1)
                if (s.hardRun && lastHard != null && ChronoUnit.DAYS.between(lastHard, d) < HARD_RUN_GAP_DAYS) d = lastHard.plusDays(HARD_RUN_GAP_DAYS.toLong())
                if (s.hardRun && d != s.date) {
                    var tries = 0
                    while (d.plusDays(1) in legDays && tries < 2) { d = d.plusDays(1); tries++ }
                }
                if (d != s.date) moved[s.id] = d
                previous = d
                if (s.hardRun) lastHard = d
            }
        }
        return moved
    }

    /** Done and planned sessions of [modality] (complementary ones left out), e.g. 7 of 12. */
    fun progress(sessions: List<QueueSession>, modality: Modality): Pair<Int, Int> {
        val list = queue(sessions, modality)
        return list.count { it.done } to list.size
    }

    /** Sessions of every queue still to do (skipped on purpose ones are out of the queue). */
    fun remaining(sessions: List<QueueSession>): Int = sessions.count { !it.optional && it.pending }

    /** Last date of the queues as they are now: the projected end of the block. */
    fun projectedEnd(sessions: List<QueueSession>): LocalDate? = sessions.filter { !it.optional && it.status != "skipped" }.maxOfOrNull { it.date }

    /** Days a session was done after its planned date (0 when on time or early). */
    fun daysLate(plannedDate: LocalDate, doneOn: LocalDate): Int = maxOf(0L, ChronoUnit.DAYS.between(plannedDate, doneOn)).toInt()

    /** Last day the block may run: [factor] times the planned weeks (1.5 by default: 8 weeks give 12). */
    fun ceiling(start: LocalDate, weeks: Int, factor: Double = DEFAULT_CEILING_FACTOR): LocalDate =
        start.plusDays(ceil(weeks * 7 * factor).toLong() - 1)

    /** The block is closing when about 20% of its sessions (at least one) are left, or past its ceiling. */
    fun closing(total: Int, remaining: Int, today: LocalDate, ceiling: LocalDate): Boolean =
        total > 0 && (remaining <= maxOf(1, (total * CLOSING_SHARE).roundToInt()) || today.isAfter(ceiling))

    const val DEFAULT_CEILING_FACTOR = 1.5
    const val CLOSING_SHARE = 0.2
}

/**
 * Coming back after a pause, per modality, counted from the last session of that modality that was done.
 * The values are common coaching heuristics (detraining reviews), not proven universal rules; the app only
 * proposes them and the user confirms.
 */
enum class Reentry(val code: String) {
    NONE("none"),
    /** 8 to 14 days. */
    SHORT("short"),
    /** 15 to 28 days. */
    MEDIUM("medium"),
    /** 29 to 56 days. */
    LONG("long"),
    /** More than 56 days: ask whether to restart the block or ask the AI for a new one. */
    RESTART("restart");

    companion object {
        fun of(gapDays: Int): Reentry = when {
            gapDays <= 7 -> NONE
            gapDays <= 14 -> SHORT
            gapDays <= 28 -> MEDIUM
            gapDays <= 56 -> LONG
            else -> RESTART
        }

        fun fromCode(code: String?): Reentry? = entries.firstOrNull { it.code == code }

        /** Days since the last done session of [modality] before [today] (null: never trained it). */
        fun gapDays(sessions: List<QueueSession>, modality: Modality, today: LocalDate, lastDoneBefore: LocalDate? = null): Int? {
            val lastInBlock = sessions.filter { it.modality == modality && it.done && it.date.isBefore(today) }.maxOfOrNull { it.date }
            val last = listOfNotNull(lastInBlock, lastDoneBefore).maxOrNull() ?: return null
            return ChronoUnit.DAYS.between(last, today).toInt()
        }
    }

    /** Share taken off the planned strength loads for the first workout back (0 when nothing changes). */
    fun strengthLoadCut(): Double = when (this) {
        NONE, SHORT -> 0.0
        MEDIUM -> 0.075
        LONG, RESTART -> 0.15
    }

    /** Share of the planned run volume for the first run back (1 when nothing changes). */
    fun runVolumeShare(): Double = when (this) {
        NONE -> 1.0
        SHORT -> 0.75
        MEDIUM -> 0.55
        LONG, RESTART -> 0.4
    }
}
