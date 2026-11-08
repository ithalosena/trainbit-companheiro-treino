package com.ithalo.hybridcoach.core.run

/** Meters per degree of latitude used by [GeoMath] (2πR / 360). */
const val METERS_PER_DEGREE = 2 * Math.PI * 6_371_000.0 / 360

/** A fix [meters] north of (0, 0) at [timeMs]. */
fun fixAt(timeMs: Long, meters: Double, accuracy: Float = 5f) = Fix(timeMs, meters / METERS_PER_DEGREE, 0.0, accuracy)

/** Feeds one fix per second at a constant pace (sec/km) for [seconds]; returns all cues and the final time and distance. */
class Runner(val engine: RunEngine, var timeMs: Long = 0, var meters: Double = 0.0) {
    val cues = mutableListOf<Cue>()

    fun run(seconds: Int, paceSecPerKm: Int): Runner {
        repeat(seconds) {
            timeMs += 1000
            meters += 1000.0 / paceSecPerKm
            cues += engine.onFix(fixAt(timeMs, meters), timeMs)
        }
        return this
    }

    fun tick(seconds: Int): Runner {
        repeat(seconds) {
            timeMs += 1000
            cues += engine.onTick(timeMs)
        }
        return this
    }

    inline fun <reified T : Cue> of(): List<T> = cues.filterIsInstance<T>()
}
