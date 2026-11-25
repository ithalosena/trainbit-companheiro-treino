package com.ithalo.hybridcoach.core.block

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The progression rules, with defaults in app/src/main/assets/progression_rules.json and every number adjustable in
 * Ajustes. They are common heuristics, not a prescription.
 */
@Serializable
data class ProgressionRules(
    /** Smallest step for loads of [heavyFromKg] or more (barbells, machines), and below it (dumbbells). */
    @SerialName("increment_kg") val incrementKg: Double = 2.5,
    @SerialName("small_increment_kg") val smallIncrementKg: Double = 1.0,
    @SerialName("heavy_from_kg") val heavyFromKg: Double = 20.0,
    /** Double progression: every set at the top of the range with mean effort up to this goes up one step. */
    @SerialName("max_rpe_to_increase") val maxRpeToIncrease: Double = 8.0,
    /** Two or more failed sets in each of the last two sessions: the load drops this much. */
    @SerialName("reduce_after_failures_pct") val reduceAfterFailuresPct: Int = 5,
    /** Most the weekly running volume should grow. There is no single number that fits everyone; 10% is a common default. */
    @SerialName("max_weekly_run_increase_pct") val maxWeeklyRunIncreasePct: Int = 10,
    /** Below this adherence, or after [longPauseDays] without training, the next block consolidates. */
    @SerialName("min_adherence_pct") val minAdherencePct: Int = 60,
    @SerialName("long_pause_days") val longPauseDays: Int = 14,
    /** Most of the accessories (not the main exercises) the next block may swap. */
    @SerialName("max_accessory_swap_pct") val maxAccessorySwapPct: Int = 33,
    /** A planned load this much above the suggested start needs a justification. */
    @SerialName("load_jump_warn_pct") val loadJumpWarnPct: Int = 10,
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }

        fun fromJson(text: String): ProgressionRules = JSON.decodeFromString(serializer(), text)
    }
}

/** What the double progression decided for one exercise. */
data class LoadDecision(val loadKg: Double, val rule: String)

object Progression {
    private val RANGE = Regex("^(\\d+)(?:\\s*[-–]\\s*(\\d+))?$")

    /** Top of a rep range ("6-8" -> 8, "10" -> 10); null for "AMRAP", "30s"... */
    fun topReps(target: String): Int? = RANGE.matchEntire(target.trim())?.let { (it.groupValues[2].ifEmpty { it.groupValues[1] }).toInt() }

    fun roundTo(value: Double, step: Double): Double = floor(value / step + 0.5) * step

    /**
     * Double progression for an exercise from its sessions, oldest first (each the list of its sets):
     * all sets of the last session at the top of the range, none failed and mean effort up to the limit -> up one
     * step; 2+ failed sets in each of the last two sessions -> down; otherwise the last working load.
     * In a [consolidation] block nothing goes up.
     */
    fun decide(sessions: List<List<BlockSet>>, target: String, rules: ProgressionRules, consolidation: Boolean): LoadDecision? {
        val last = sessions.lastOrNull()?.filter { !it.timed } ?: return null
        if (last.isEmpty()) return null
        val working = last.maxOf { it.loadKg }
        if (working <= 0.0) return null
        val previous = sessions.getOrNull(sessions.size - 2)
        val failing = last.count { it.failed } >= 2 && (previous?.count { it.failed } ?: 0) >= 2
        if (failing) {
            val reduced = roundTo(working * (1 - rules.reduceAfterFailuresPct / 100.0), 0.5)
            return LoadDecision(reduced, "falhou 2+ séries em duas sessões seguidas: -${rules.reduceAfterFailuresPct}%")
        }
        val top = topReps(target)
        val rpes = last.mapNotNull { it.rpe }
        val effortOk = rpes.isEmpty() || rpes.average() <= rules.maxRpeToIncrease
        val allTop = top != null && last.all { !it.failed && it.reps >= top }
        if (allTop && effortOk && !consolidation) {
            val step = if (working >= rules.heavyFromKg) rules.incrementKg else rules.smallIncrementKg
            return LoadDecision(working + step, "todas as séries no topo da faixa: +${fmt(step)} kg")
        }
        val why = when {
            consolidation -> "bloco de consolidação: mantém"
            top == null -> "faixa sem número: mantém"
            !allTop -> "ainda não chegou ao topo da faixa: mantém"
            else -> "esforço médio acima de ${fmt(rules.maxRpeToIncrease)}: mantém"
        }
        return LoadDecision(working, why)
    }

    /** Estimated one-rep max (Epley): load x (1 + reps / 30). */
    fun epley(loadKg: Double, reps: Int): Double = if (reps <= 1) loadKg else loadKg * (1 + reps / 30.0)

    fun fmt(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
}
