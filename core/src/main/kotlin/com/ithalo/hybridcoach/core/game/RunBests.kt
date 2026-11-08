package com.ithalo.hybridcoach.core.game

/**
 * The running records rule, shared by the game engine (XP, the Recordes tab) and the run summary:
 * more is better for distance and time, less for the paces. A kind with no earlier value is only a baseline.
 */
object RunBests {
    /** Average pace (sec/km), from the run or from distance and time. */
    fun pace(run: RunFact): Int? =
        run.avgPaceSecPerKm ?: if (run.distanceM >= 50) (run.durationSeconds / (run.distanceM / 1000)).toInt() else null

    /** What [run] scores in each kind; kinds it says nothing about (pace of a run under 1 km, no full km) are missing. */
    fun values(run: RunFact): Map<RunRecordKind, Double> = buildMap {
        if (run.distanceM > 0) put(RunRecordKind.LONGEST_DISTANCE, run.distanceM)
        if (run.durationSeconds > 0) put(RunRecordKind.LONGEST_TIME, run.durationSeconds.toDouble())
        pace(run)?.takeIf { run.distanceM >= 1000 && it > 0 }?.let { put(RunRecordKind.BEST_PACE, it.toDouble()) }
        run.splitSeconds.filter { it > 0 }.minOrNull()?.let { put(RunRecordKind.FASTEST_KM, it.toDouble()) }
    }

    fun better(kind: RunRecordKind, value: Double, than: Double): Boolean = when (kind) {
        RunRecordKind.LONGEST_DISTANCE, RunRecordKind.LONGEST_TIME -> value > than
        RunRecordKind.BEST_PACE, RunRecordKind.FASTEST_KM -> value < than
    }

    /** The records [run] beats against the [earlier] runs, in [RunRecordKind] order. */
    fun beaten(run: RunFact, earlier: List<RunFact>): List<RunRecord> {
        val best = HashMap<RunRecordKind, Double>()
        earlier.forEach { r -> values(r).forEach { (k, v) -> if (best[k]?.let { better(k, v, it) } != false) best[k] = v } }
        val mine = values(run)
        return RunRecordKind.entries.mapNotNull { k ->
            val v = mine[k] ?: return@mapNotNull null
            val old = best[k] ?: return@mapNotNull null
            if (better(k, v, old)) RunRecord(k, v, run.date) else null
        }
    }

    /** Short pt-BR name of a kind, for the XP log. */
    fun label(kind: RunRecordKind): String = when (kind) {
        RunRecordKind.LONGEST_DISTANCE -> "maior distância"
        RunRecordKind.LONGEST_TIME -> "maior tempo"
        RunRecordKind.BEST_PACE -> "melhor ritmo"
        RunRecordKind.FASTEST_KM -> "km mais rápido"
    }
}
