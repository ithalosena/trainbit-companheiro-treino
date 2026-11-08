package com.ithalo.hybridcoach.core.run

import com.ithalo.hybridcoach.core.plan.PaceRange

/** User-adjustable coaching parameters (defaults from the spec). */
data class RunSettings(
    val smoothingSeconds: Int = 20,
    val toleranceSecPerKm: Int = 5,
    val minAlertIntervalSeconds: Int = 30,
    val outOfRangeSeconds: Int = 10,
    val onPaceMinIntervalSeconds: Int = 60,
)

enum class PaceAdvice { SPEED_UP, SLOW_DOWN, ON_PACE }

/**
 * Pace alert rules. Times are active (non-paused) milliseconds.
 * - Out of range (beyond the tolerance) for [RunSettings.outOfRangeSeconds] in a row: "speed up" / "slow down".
 * - At most one alert every [RunSettings.minAlertIntervalSeconds].
 * - "On pace" when coming back after an alert, at most once every [RunSettings.onPaceMinIntervalSeconds].
 * - No target range or no pace: no alerts.
 */
class PaceCoach(private val settings: RunSettings) {
    private var outSince: Long? = null
    private var outDirection: PaceAdvice? = null
    private var lastAlert: Long? = null
    private var lastOnPace: Long? = null
    private var warned = false

    /** Called when the block changes: pending out-of-range time does not carry over. */
    fun reset() {
        outSince = null
        outDirection = null
        warned = false
    }

    fun evaluate(nowMs: Long, pace: Int?, range: PaceRange?): PaceAdvice? {
        if (range == null || pace == null) {
            outSince = null
            return null
        }
        val tol = settings.toleranceSecPerKm
        val direction = when {
            pace > range.max + tol -> PaceAdvice.SPEED_UP
            pace < range.min - tol -> PaceAdvice.SLOW_DOWN
            else -> null
        }
        if (direction == null) {
            outSince = null
            outDirection = null
            if (warned && lastOnPace.let { it == null || nowMs - it >= settings.onPaceMinIntervalSeconds * 1000L }) {
                warned = false
                lastOnPace = nowMs
                return PaceAdvice.ON_PACE
            }
            return null
        }
        if (direction != outDirection) {
            outDirection = direction
            outSince = nowMs
        }
        val since = outSince ?: nowMs.also { outSince = it }
        val longEnough = nowMs - since >= settings.outOfRangeSeconds * 1000L
        val spaced = lastAlert.let { it == null || nowMs - it >= settings.minAlertIntervalSeconds * 1000L }
        if (longEnough && spaced) {
            lastAlert = nowMs
            warned = true
            return direction
        }
        return null
    }
}
