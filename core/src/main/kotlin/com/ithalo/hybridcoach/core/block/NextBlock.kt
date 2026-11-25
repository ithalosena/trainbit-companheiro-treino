package com.ithalo.hybridcoach.core.block

import com.ithalo.hybridcoach.core.plan.PlanDocument
import com.ithalo.hybridcoach.core.plan.RunSessionDef
import com.ithalo.hybridcoach.core.plan.StrengthSessionDef
import kotlin.math.roundToInt

/**
 * The continuity part of the next block's prompt: the athlete's state, the suggested start, the progression rules
 * and the continuity rules, all from the previous block's [BlockSnapshot]. The AI decides the structure inside these
 * limits.
 */
object NextBlockPrompt {
    fun section(s: BlockSnapshot, rules: ProgressionRules): String = buildString {
        val start = s.suggestedStart
        appendLine("Este é o bloco ${s.number + 1}. O bloco anterior (\"${s.planName}\", ${s.start} a ${s.end}) foi fechado com estes números, calculados pelo app. Use-os como ponto de partida.")
        appendLine()
        appendLine("### Estado do atleta")
        val g = s.general
        appendLine("- Aderência às sessões principais: ${g.adherencePct}% (por semana: ${g.adherenceByWeek.joinToString(", ") { "$it%" }}); semanas sem treino: ${g.skippedWeeks}; deload feito: ${if (g.deloadDone) "sim" else "não"}.")
        g.daysSinceLastWorkout?.let { appendLine("- Dias desde o último treino: $it.") }
        appendLine("- Cansaço ${s.answers.fatigue}/5, motivação ${s.answers.motivation}/5.")
        val limits = listOf(g.limitations, s.answers.newLimitations).filter { it.isNotBlank() }
        if (limits.isNotEmpty()) appendLine("- Limitações e dores: ${limits.joinToString("; ")}.")
        if (s.answers.change.isNotBlank()) appendLine("- O que o atleta quer mudar: ${s.answers.change.trim()}.")
        if (s.answers.availability.isNotBlank()) appendLine("- Mudança de disponibilidade: ${s.answers.availability.trim()}.")
        if (s.answers.carryIncomplete && s.answers.incompleteSessions > 0) {
            appendLine("- Ficaram ${s.answers.incompleteSessions} sessões por fazer no bloco anterior: encaixe o que for importante na primeira semana, sem dobrar o volume.")
        }
        s.exercises.filter { it.lastLoadKg != null }.forEach { e ->
            val parts = buildList {
                add("última carga ${Progression.fmt(e.lastLoadKg!!)} kg x ${e.lastReps}")
                e.e1rmKg?.let { add("1RM estimado ${Progression.fmt(it)} kg" + (e.e1rmTrendPct?.let { t -> " (${if (t >= 0) "+" else ""}$t% no bloco)" } ?: "")) }
                if (e.failedSets > 0) add("${e.failedSets} séries falhadas")
                e.avgRpe?.let { add("esforço médio ${Progression.fmt(it)}") }
                if (e.postponed > 0) add("adiado ${e.postponed}x")
                if (e.pain) add("teve dor")
            }
            appendLine("- ${e.name}${if (e.main) " (principal)" else ""}: ${parts.joinToString(", ")}.")
        }
        s.run?.let { r ->
            val paces = r.avgPaceByType.entries.joinToString(", ") { (k, v) -> "${if (k == "interval") "intervalado" else "contínuo"} ${pace(v)}/km" }
            appendLine("- Corrida: ${paces.ifEmpty { "sem ritmo medido" }}; maior corrida ${Progression.fmt(r.longestKm)} km; km por semana: ${r.weeklyKm.joinToString(", ") { Progression.fmt(it) }}" +
                (r.paceAdherencePct?.let { "; dentro do ritmo-alvo em $it% das corridas" } ?: "") + ".")
        }
        appendLine()
        appendLine("### Partida sugerida (suggested_start)")
        if (start.consolidation) appendLine("- Bloco de CONSOLIDAÇÃO (aderência baixa ou pausa longa): não aumente cargas nem volume; recupere a rotina.")
        start.loads.forEach { appendLine("- ${it.name}: ${Progression.fmt(it.loadKg)} kg em ${it.reps} (${it.rule}).") }
        start.run?.let { r ->
            appendLine("- Corrida: semana 1 com cerca de ${Progression.fmt(r.week1Km)} km; longão de até ${Progression.fmt(r.longRunKm)} km" +
                (if (r.easyPaceMin != null && r.easyPaceMax != null) "; ritmo leve ${pace(r.easyPaceMin)} a ${pace(r.easyPaceMax)}/km" else "") +
                (r.intervalPace?.let { "; intervalado perto de ${pace(it)}/km" } ?: "") + ".")
        }
        appendLine("- Fase sugerida: ${BlockPhase.of(start.phase)?.label ?: start.phase}.")
        appendLine()
        appendLine("### Regras de progressão")
        appendLine("- Força, dupla progressão: só sobe a carga (+${Progression.fmt(rules.incrementKg)} kg em barra e máquina, +${Progression.fmt(rules.smallIncrementKg)} kg abaixo de ${Progression.fmt(rules.heavyFromKg)} kg) quando todas as séries chegarem ao topo da faixa com esforço médio até ${Progression.fmt(rules.maxRpeToIncrease)}.")
        appendLine("- Corrida: o volume semanal cresce no máximo ${rules.maxWeeklyRunIncreasePct}% por semana; ritmos-alvo a partir dos ritmos reais acima.")
        appendLine()
        appendLine("### Regras de continuidade")
        appendLine("- Mantenha a divisão e os exercícios principais; troque no máximo ${rules.maxAccessorySwapPct}% dos acessórios.")
        appendLine("- Comece das cargas e ritmos da partida sugerida. Qualquer carga acima dela exige o campo `justification` (texto curto) no exercício.")
        appendLine("- Respeite as limitações e o deload. Informe a fase do bloco em `plan.phase` (`base`, `progression`, `peak` ou `deload`).")
        if (s.skeleton.isNotEmpty()) {
            appendLine()
            appendLine("### Semana típica do bloco anterior")
            s.skeleton.forEach { k -> appendLine("- ${k.day} ${if (k.type == "run") "corrida" else "musculação"}: ${k.title}" + (if (k.exercises.isNotEmpty()) " (${k.exercises.joinToString(", ")})" else "")) }
        }
    }.trim()

    fun pace(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
}

/** A point where the new plan breaks with the previous block. */
data class ContinuityIssue(val text: String, val justified: Boolean = false)

/** How the new plan continues the previous block: issues to show in the preview and the share of main exercises kept. */
data class ContinuityReport(val issues: List<ContinuityIssue>, val mainKeptPct: Int?, val daysKeptPct: Int?) {
    val warnings: List<ContinuityIssue> get() = issues.filter { !it.justified }
}

/**
 * Compares a new plan with the previous block's snapshot (T6c): loads above the suggested start without a
 * justification, running volume jumps, main exercises dropped, too many accessories swapped, different days.
 * [idOf] gives the exercise id the app resolved for a name in the plan.
 */
object PlanCheck {
    private const val DEFAULT_PACE = 360 // 6:00/km, to turn a timed block into km when it has no target

    fun check(plan: PlanDocument, s: BlockSnapshot, rules: ProgressionRules, idOf: (String) -> String?): ContinuityReport {
        val issues = ArrayList<ContinuityIssue>()
        val week1 = plan.weeks.firstOrNull { 1 in it.weeks }?.sessions.orEmpty()
        val strength = plan.weeks.flatMap { it.sessions }.filterIsInstance<StrengthSessionDef>()
        val suggested = s.suggestedStart.loads.associateBy { it.id }
        // Loads: the first time each exercise appears, against the suggested start.
        val seen = HashSet<String>()
        for (session in plan.weeks.sortedBy { it.weeks.minOrNull() ?: 0 }.flatMap { it.sessions }.filterIsInstance<StrengthSessionDef>()) {
            for (e in session.exercises) {
                val id = idOf(e.name) ?: continue
                if (!seen.add(id)) continue
                val start = suggested[id] ?: continue
                val load = e.loadKg ?: continue
                if (start.loadKg <= 0) continue
                val pct = ((load / start.loadKg - 1) * 100).roundToInt()
                if (pct > rules.loadJumpWarnPct) {
                    val text = "${e.name}: +$pct% de carga (sugerido ${Progression.fmt(start.loadKg)} kg, plano ${Progression.fmt(load)} kg)"
                    issues += ContinuityIssue(if (e.justification.isNullOrBlank()) text else "$text, justificado: ${e.justification}", justified = !e.justification.isNullOrBlank())
                }
            }
        }
        // Running volume of week 1 against the suggested start.
        val runKm = week1.filterIsInstance<RunSessionDef>().sumOf { r ->
            r.blocks.sumOf { b ->
                val one = b.distanceM?.toDouble()?.div(1000) ?: ((b.durationSeconds ?: 0) / ((b.targetPace?.let { (it.min + it.max) / 2 } ?: DEFAULT_PACE).toDouble()))
                one * b.repeat
            }
        }
        s.suggestedStart.run?.let { r ->
            if (r.week1Km > 0 && runKm > 0) {
                val pct = ((runKm / r.week1Km - 1) * 100).roundToInt()
                if (pct > rules.maxWeeklyRunIncreasePct) issues += ContinuityIssue("Volume de corrida da semana 1: +$pct% (sugerido ${Progression.fmt(r.week1Km)} km, plano ${Progression.fmt((runKm * 10).roundToInt() / 10.0)} km)")
            }
        }
        // Main exercises and accessories.
        val newIds = strength.flatMap { it.exercises }.mapNotNull { idOf(it.name) }.toSet()
        val main = s.exercises.filter { it.main }.map { it.id }.toSet()
        val accessories = s.exercises.filter { !it.main }.map { it.id }.toSet()
        val dropped = s.exercises.filter { it.main && it.id !in newIds && !it.pain }
        dropped.forEach { issues += ContinuityIssue("Exercício principal fora do plano: ${it.name}") }
        if (accessories.isNotEmpty()) {
            val swapped = accessories.count { it !in newIds } * 100 / accessories.size
            if (swapped > rules.maxAccessorySwapPct) issues += ContinuityIssue("$swapped% dos acessórios trocados (máximo ${rules.maxAccessorySwapPct}%)")
        }
        val oldDays = s.skeleton.map { it.day to it.type }.toSet()
        val newDays = week1.map { it.dayOfWeek.name.take(3).lowercase() to (if (it is RunSessionDef) "run" else "strength") }.toSet()
        val daysKept = if (oldDays.isEmpty()) null else (oldDays intersect newDays).size * 100 / oldDays.size
        return ContinuityReport(
            issues = issues,
            mainKeptPct = if (main.isEmpty()) null else main.count { it in newIds } * 100 / main.size,
            daysKeptPct = daysKept,
        )
    }

    /** The message to paste back into the AI so it fixes the jumps. */
    fun correctionRequest(report: ContinuityReport): String =
        "O plano que você enviou quebra a continuidade com o bloco anterior nestes pontos: " +
            report.warnings.joinToString("; ") { it.text } +
            ". Ajuste partindo das cargas e ritmos da partida sugerida, ou inclua `justification` (texto curto) em cada exercício acima dela. " +
            "Responda somente com o JSON completo e válido, sem texto antes ou depois."
}
