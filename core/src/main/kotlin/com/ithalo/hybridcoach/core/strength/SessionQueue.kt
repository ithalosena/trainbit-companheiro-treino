package com.ithalo.hybridcoach.core.strength

/**
 * The order the exercises are done in during one workout. The plan keeps its own order; this one changes
 * when an exercise is postponed (equipment taken), brought forward ("Fazer agora") or dragged. Every
 * function takes and returns the planned-exercise ids in queue order.
 */
object SessionQueue {
    /** [id] goes to the end of the queue; its logged sets and timer are untouched. */
    fun postpone(order: List<Long>, id: Long): List<Long> = if (id !in order) order else order - id + id

    /** [id] becomes the next exercise: placed right before the first one that is not [complete]. */
    fun doNow(order: List<Long>, id: Long, complete: Set<Long>): List<Long> {
        if (id !in order) return order
        val rest = order - id
        val at = rest.indexOfFirst { it !in complete }.let { if (it < 0) rest.size else it }
        return rest.take(at) + id + rest.drop(at)
    }

    /** Drag: the exercise at [from] moves to [to] (both indices in [order]). */
    fun move(order: List<Long>, from: Int, to: Int): List<Long> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        val list = order.toMutableList()
        list.add(to, list.removeAt(from))
        return list
    }

    /** The order the exercises were actually started in: by the time of their first logged set. */
    fun executedOrder(firstSetAt: Map<Long, Long>): List<Long> = firstSetAt.entries.sortedBy { it.value }.map { it.key }
}

/**
 * Stopwatch of a timed exercise, kept as timestamps so it survives leaving the screen, the screen lock and the
 * process being killed: [accumulatedMs] of finished runs plus, while running, the time since [startedAt].
 */
data class WorkClock(val accumulatedMs: Long = 0, val startedAt: Long? = null) {
    val running: Boolean get() = startedAt != null

    fun elapsedMs(now: Long): Long = accumulatedMs + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)

    fun elapsedSeconds(now: Long): Int = (elapsedMs(now) / 1000).toInt()

    fun start(now: Long): WorkClock = if (running) this else copy(startedAt = now)

    fun pause(now: Long): WorkClock = if (!running) this else WorkClock(elapsedMs(now), null)

    /** True when the target was reached while nobody was looking (from [seenSeconds] to [now]). */
    fun reachedSince(seenSeconds: Int, targetSeconds: Int, now: Long): Boolean =
        seenSeconds < targetSeconds && elapsedSeconds(now) >= targetSeconds
}

/**
 * Effort of a set (RPE) on a 10-segment bar: 0 means "not logged" and is stored as null, never filled in
 * from the target.
 */
object EffortScale {
    const val MAX = 10

    /** The bar position for a stored value. */
    fun position(rpe: Int?): Int = (rpe ?: 0).coerceIn(0, MAX)

    /** The stored value for a bar position: 0 is no data. */
    fun stored(position: Int): Int? = position.coerceIn(0, MAX).takeIf { it > 0 }

    /** One step of the "<" (-1) or ">" (+1) buttons. */
    fun step(rpe: Int?, delta: Int): Int? = stored(position(rpe) + delta)
}
