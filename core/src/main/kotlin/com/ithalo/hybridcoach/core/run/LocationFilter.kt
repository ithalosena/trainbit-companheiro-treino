package com.ithalo.hybridcoach.core.run

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A GPS reading; [timeMs] is wall-clock time of the fix. */
@Serializable
data class Fix(val timeMs: Long, val lat: Double, val lon: Double, val accuracyM: Float)

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in meters. */
    fun distance(a: Fix, b: Fix): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

object LocationFilter {
    const val MAX_ACCURACY_M = 20f
    /** 12 m/s is 43 km/h, far beyond running; anything faster is a GPS jump. */
    const val MAX_SPEED_MPS = 12.0
    /** Distance from the previous counted fix before a new segment is counted (filters GPS jitter). */
    const val MIN_STEP_M = 8.0

    sealed interface Result {
        data class Accept(val meters: Double) : Result
        data object Reject : Result
    }

    /** [last] is the previous accepted fix (null for the first one). */
    fun check(last: Fix?, fix: Fix): Result {
        if (fix.accuracyM > MAX_ACCURACY_M) return Result.Reject
        if (last == null) return Result.Accept(0.0)
        val dt = (fix.timeMs - last.timeMs) / 1000.0
        if (dt <= 0) return Result.Reject
        val meters = GeoMath.distance(last, fix)
        if (meters / dt > MAX_SPEED_MPS) return Result.Reject
        return Result.Accept(meters)
    }
}
