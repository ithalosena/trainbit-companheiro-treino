package com.ithalo.hybridcoach.core.run

import kotlin.math.roundToInt

/**
 * Moving pace over the last [windowSeconds] of active time, from (active time, total distance) samples.
 * Uses the least-squares slope of distance over time in the window, so the GPS error of single fixes is
 * averaged out instead of weighing fully on the first and last points.
 * Returns null until half the window has data or when the runner is practically stopped.
 */
class PaceSmoother(private val windowSeconds: Int) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    fun add(activeMs: Long, distanceM: Double) {
        samples.addLast(activeMs to distanceM)
        while (samples.isNotEmpty() && samples.first().first < activeMs - windowSeconds * 1000L) samples.removeFirst()
    }

    fun clear() = samples.clear()

    /** Seconds per km. */
    fun pace(): Int? {
        if (samples.size < 3) return null
        val span = (samples.last().first - samples.first().first) / 1000.0
        if (span < windowSeconds / 2.0) return null
        val meanT = samples.sumOf { it.first } / samples.size.toDouble()
        val meanD = samples.sumOf { it.second } / samples.size
        var num = 0.0
        var den = 0.0
        for ((t, d) in samples) {
            num += (t - meanT) * (d - meanD)
            den += (t - meanT) * (t - meanT)
        }
        if (den == 0.0) return null
        val metersPerSecond = num / den * 1000
        if (metersPerSecond <= 0) return null
        val pace = 1000 / metersPerSecond
        return if (pace > STOPPED_PACE) null else pace.roundToInt()
    }

    companion object {
        /** Slower than 20 min/km is treated as stopped (no pace, no alerts). */
        const val STOPPED_PACE = 1200
    }
}
