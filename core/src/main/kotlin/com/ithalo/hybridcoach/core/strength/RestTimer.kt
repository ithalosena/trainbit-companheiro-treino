package com.ithalo.hybridcoach.core.strength

enum class RestCue(val spoken: String) {
    THREE("três"), TWO("dois"), ONE("um"), DONE("Descanso terminado")
}

/** Rest countdown driven by a monotonic clock; [endAtMs] moves when the user adds or removes time. */
class RestTimer(startMs: Long, val totalSeconds: Int) {
    var endAtMs: Long = startMs + totalSeconds * 1000L
        private set

    fun remainingSeconds(nowMs: Long): Int =
        if (nowMs >= endAtMs) 0 else ((endAtMs - nowMs + 999) / 1000).toInt()

    fun adjust(deltaSeconds: Int, nowMs: Long) {
        // Never let the end fall before "now"; removing time past zero just ends the rest.
        endAtMs = maxOf(nowMs, endAtMs + deltaSeconds * 1000L)
    }

    companion object {
        /** Cue to speak when the visible countdown goes from [previous] to [current]; null when none is due. */
        fun cue(previous: Int, current: Int): RestCue? {
            if (previous == current) return null
            return when (current) {
                3 -> RestCue.THREE
                2 -> RestCue.TWO
                1 -> RestCue.ONE
                0 -> RestCue.DONE
                else -> null
            }
        }
    }
}
