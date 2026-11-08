package com.ithalo.hybridcoach.core.strength

/**
 * Exercises whose target is a duration ("8min", "30s", "1:30", "30-45 s") instead of reps.
 * For timed sets the seconds actually done are stored in the reps column of the set log.
 */
object TimedTarget {
    private val UNIT = Regex("^(\\d+)(?:\\s*[-–]\\s*(\\d+))?\\s*(min|mins|minutos?|m|s|seg|segs|segundos?|sec|\")$", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("^(\\d{1,2}):(\\d{2})$")

    /** Target duration in seconds (lower bound of a range), or null when the target is not a duration. */
    fun seconds(target: String): Int? {
        val t = target.trim()
        CLOCK.matchEntire(t)?.let { m ->
            val sec = m.groupValues[2].toInt()
            return if (sec < 60) m.groupValues[1].toInt() * 60 + sec else null
        }
        val m = UNIT.matchEntire(t) ?: return null
        val n = m.groupValues[1].toInt()
        val minutes = m.groupValues[3].lowercase().startsWith("m")
        return (if (minutes) n * 60 else n).takeIf { it > 0 }
    }

    fun isTimed(target: String): Boolean = seconds(target) != null

    /** "8:00", "0:45", "1:02:03". */
    fun format(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
