package com.ithalo.hybridcoach.core.strength

data class ExerciseProgress(val skipped: Boolean, val totalSets: Int, val loggedSets: Int) {
    val isComplete: Boolean get() = skipped || loggedSets >= totalSets
}

object SessionProgress {
    /** Index of the first exercise that still has sets to do, or null when the session is complete. */
    fun nextExercise(exercises: List<ExerciseProgress>): Int? =
        exercises.indexOfFirst { !it.isComplete }.takeIf { it >= 0 }

    /** Index of the exercise after [current] that still has sets to do, wrapping to the start; null if none. */
    fun nextIncompleteAfter(exercises: List<ExerciseProgress>, current: Int): Int? {
        for (offset in 1..exercises.size) {
            val i = (current + offset) % exercises.size
            if (!exercises[i].isComplete) return i
        }
        return null
    }

    private val REPS = Regex("^(\\d+)(?:\\s*[-–]\\s*(\\d+))?$")

    /** Reps to prefill for a target such as "8" or "6-8" (the upper bound); null for "AMRAP", "30s", etc. */
    fun defaultReps(target: String): Int? =
        REPS.matchEntire(target.trim())?.let { (it.groupValues[2].ifEmpty { it.groupValues[1] }).toInt() }
}
