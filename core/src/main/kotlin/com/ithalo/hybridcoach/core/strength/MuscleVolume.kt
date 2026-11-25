package com.ithalo.hybridcoach.core.strength

/** Weekly volume per muscle group, counted in sets. */
object MuscleVolume {
    const val SECONDARY_WEIGHT = 0.5

    /**
     * Sets per muscle group, most worked first: each logged set counts 1 for every main muscle of its exercise and
     * [SECONDARY_WEIGHT] for every secondary one. Exercises without muscles are left out.
     * [muscles] gives the (main, secondary) muscles of an exercise id.
     */
    fun sets(exerciseIds: List<String>, muscles: (String) -> Pair<List<String>, List<String>>?): List<Pair<String, Double>> {
        val total = LinkedHashMap<String, Double>()
        for (id in exerciseIds) {
            val (main, secondary) = muscles(id) ?: continue
            main.forEach { total[it] = (total[it] ?: 0.0) + 1.0 }
            secondary.forEach { total[it] = (total[it] ?: 0.0) + SECONDARY_WEIGHT }
        }
        return total.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key }).map { it.key to it.value }
    }
}
