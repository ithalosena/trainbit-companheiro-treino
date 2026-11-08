package com.ithalo.hybridcoach.core.summary

import com.ithalo.hybridcoach.core.strength.TimedTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

enum class SessionStatus(val value: String) {
    PENDING("pending"), DONE("done"), SKIPPED("skipped");

    companion object {
        fun fromValue(value: String) = entries.firstOrNull { it.value == value } ?: PENDING
    }
}

data class SessionRecord(val status: SessionStatus, val skipReason: String?)

@Serializable
data class BlockSummary(
    @SerialName("summary_version") val summaryVersion: Int = 1,
    @SerialName("plan_name") val planName: String,
    val period: Period,
    val adherence: Adherence,
    val strength: List<StrengthSummary> = emptyList(),
    val runs: List<RunSummary> = emptyList(),
    @SerialName("user_notes") val userNotes: List<String> = emptyList(),
)

@Serializable
data class Period(val start: String, val end: String)

@Serializable
data class Adherence(
    @SerialName("planned_sessions") val plannedSessions: Int,
    val done: Int,
    val skipped: Int,
    @SerialName("skip_reasons") val skipReasons: List<String>,
)

@Serializable
data class StrengthSummary(
    val exercise: String,
    val planned: PlannedStrength,
    val done: DoneStrength,
)

@Serializable
data class PlannedStrength(val sets: Int, val reps: String, @SerialName("load_kg") val loadKg: Double?)

@Serializable
data class DoneStrength(
    @SerialName("best_set") val bestSet: BestSet?,
    @SerialName("failed_sets") val failedSets: Int,
    @SerialName("avg_rpe") val avgRpe: Double?,
    @SerialName("load_trend_kg") val loadTrendKg: List<Double>,
)

@Serializable
data class BestSet(val reps: Int, @SerialName("load_kg") val loadKg: Double)

@Serializable
data class RunSummary(
    val week: Int,
    val type: String,
    @SerialName("planned_pace_sec_per_km") val plannedPace: PaceSummary?,
    @SerialName("actual_avg_pace_sec_per_km") val actualAvgPace: Int?,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("avg_rpe") val avgRpe: Double?,
)

@Serializable
data class PaceSummary(val min: Int, val max: Int)

/** A recorded run with its planned blocks (kind and pace range) and the executed blocks. */
data class RunRecord(
    val week: Int,
    val plannedBlocks: List<PlannedRunBlock>,
    val distanceM: Double,
    val durationSeconds: Long,
    val steps: List<ExecutedRunStep>,
    val rpe: Int?,
)

data class PlannedRunBlock(val kind: String, val paceMin: Int?, val paceMax: Int?)
data class ExecutedRunStep(val kind: String, val distanceM: Double, val seconds: Long)

object RunSummarizer {
    /**
     * Type is "interval" when the plan has interval blocks, otherwise "steady". The planned and actual pace refer
     * to the main kind (interval or steady blocks); the actual pace falls back to the whole run.
     */
    fun summarize(r: RunRecord): RunSummary {
        val type = if (r.plannedBlocks.any { it.kind == "interval" }) "interval" else "steady"
        val main = r.plannedBlocks.filter { it.kind == type && it.paceMin != null && it.paceMax != null }
        val planned = if (main.isEmpty()) null else PaceSummary(main.minOf { it.paceMin!! }, main.maxOf { it.paceMax!! })
        val mainSteps = r.steps.filter { it.kind == type }
        val (meters, seconds) = if (mainSteps.sumOf { it.distanceM } >= 100) {
            mainSteps.sumOf { it.distanceM } to mainSteps.sumOf { it.seconds }
        } else {
            r.distanceM to r.durationSeconds
        }
        val actual = if (meters < 50) null else (seconds / (meters / 1000.0)).toInt()
        return RunSummary(r.week, type, planned, actual, Math.round(r.distanceM / 100.0) / 10.0, r.rpe?.toDouble())
    }

    /** Merges runs of the same week and type (used when the summary is too long). */
    fun byWeek(runs: List<RunSummary>): List<RunSummary> =
        runs.groupBy { it.week to it.type }.map { (key, list) ->
            val paces = list.mapNotNull { it.actualAvgPace }
            val rpes = list.mapNotNull { it.avgRpe }
            RunSummary(
                week = key.first,
                type = key.second,
                plannedPace = list.firstNotNullOfOrNull { it.plannedPace },
                actualAvgPace = if (paces.isEmpty()) null else paces.average().toInt(),
                distanceKm = Math.round(list.sumOf { it.distanceKm } * 10) / 10.0,
                avgRpe = if (rpes.isEmpty()) null else Math.round(rpes.average() * 10) / 10.0,
            )
        }
}

object SummaryBuilder {
    private val JSON = Json { encodeDefaults = true }

    const val MAX_JSON_CHARS = 6000

    fun build(
        planName: String,
        start: LocalDate,
        end: LocalDate,
        sessions: List<SessionRecord>,
        strength: List<StrengthSummary> = emptyList(),
        userNotes: List<String> = emptyList(),
        runs: List<RunSummary> = emptyList(),
    ): BlockSummary {
        val skipped = sessions.filter { it.status == SessionStatus.SKIPPED }
        return fit(
            BlockSummary(
                planName = planName,
                period = Period(start.toString(), end.toString()),
                adherence = Adherence(
                    plannedSessions = sessions.size,
                    done = sessions.count { it.status == SessionStatus.DONE },
                    skipped = skipped.size,
                    skipReasons = skipped.mapNotNull { it.skipReason?.trim()?.takeIf(String::isNotEmpty) }.distinct(),
                ),
                strength = strength,
                runs = runs,
                userNotes = userNotes,
            ),
        )
    }

    /**
     * Keeps the JSON under [MAX_JSON_CHARS]: merges runs by week, shortens load trends, then drops the last notes,
     * exercises and runs.
     */
    private fun fit(summary: BlockSummary): BlockSummary {
        fun size(s: BlockSummary) = toJson(s).length
        if (size(summary) <= MAX_JSON_CHARS) return summary

        var current = summary.copy(runs = RunSummarizer.byWeek(summary.runs))
        if (size(current) <= MAX_JSON_CHARS) return current
        val runs = current.runs
        for (keep in listOf(6, 4, 2, 1)) {
            current = current.copy(runs = runs, strength = summary.strength.map { it.copy(done = it.done.copy(loadTrendKg = it.done.loadTrendKg.takeLast(keep))) })
            if (size(current) <= MAX_JSON_CHARS) return current
        }
        while (size(current) > MAX_JSON_CHARS && current.userNotes.size > 5) current = current.copy(userNotes = current.userNotes.dropLast(1))
        while (size(current) > MAX_JSON_CHARS && current.strength.isNotEmpty()) current = current.copy(strength = current.strength.dropLast(1))
        while (size(current) > MAX_JSON_CHARS && current.runs.isNotEmpty()) current = current.copy(runs = current.runs.dropLast(1))
        return current
    }

    fun toJson(summary: BlockSummary): String = JSON.encodeToString(BlockSummary.serializer(), summary)

    /** Plain-text version meant to be pasted into the next prompt. */
    fun toText(s: BlockSummary): String = buildString {
        appendLine("Bloco \"${s.planName}\" (${s.period.start} a ${s.period.end})")
        val a = s.adherence
        append("Aderência: ${a.done} de ${a.plannedSessions} sessões feitas, ${a.skipped} puladas")
        if (a.skipReasons.isNotEmpty()) append(" (motivos: ${a.skipReasons.joinToString(", ")})")
        appendLine(".")

        if (s.strength.isEmpty()) {
            appendLine("Musculação: sem registros.")
        } else {
            appendLine("Musculação:")
            for (e in s.strength) {
                val planned = "${e.planned.sets}x${e.planned.reps}" + (e.planned.loadKg?.let { " @ ${fmt(it)} kg" } ?: "")
                val timed = TimedTarget.isTimed(e.planned.reps)
                val best = e.done.bestSet?.let {
                    if (timed) "melhor série ${TimedTarget.format(it.reps)}" + (if (it.loadKg > 0) " com ${fmt(it.loadKg)} kg" else "")
                    else "melhor série ${it.reps} x ${fmt(it.loadKg)} kg"
                } ?: "sem séries registradas"
                val rpe = e.done.avgRpe?.let { ", RPE médio ${fmt(it)}" }.orEmpty()
                val trend = if (e.done.loadTrendKg.isNotEmpty()) ", cargas: ${e.done.loadTrendKg.joinToString(" > ") { fmt(it) }}" else ""
                appendLine("- ${e.exercise}: planejado $planned; $best; ${e.done.failedSets} falhas$rpe$trend")
            }
        }

        if (s.runs.isEmpty()) {
            appendLine("Corridas: sem registros.")
        } else {
            appendLine("Corridas:")
            for (r in s.runs) {
                val target = r.plannedPace?.let { "alvo ${pace(it.min)}-${pace(it.max)}/km" } ?: "sem alvo de pace"
                val actual = r.actualAvgPace?.let { "pace médio ${pace(it)}/km" } ?: "sem pace registrado"
                val rpe = r.avgRpe?.let { ", RPE ${fmt(it)}" }.orEmpty()
                appendLine("- Semana ${r.week}, ${r.type}: ${fmt(r.distanceKm)} km, $actual ($target)$rpe")
            }
        }

        if (s.userNotes.isNotEmpty()) {
            appendLine("Observações do usuário:")
            s.userNotes.forEach { appendLine("- $it") }
        }
    }.trim()

    private fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, d)

    private fun pace(secPerKm: Int) = "%d:%02d".format(secPerKm / 60, secPerKm % 60)
}
