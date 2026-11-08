package com.ithalo.hybridcoach.core.prompt

import com.ithalo.hybridcoach.core.questionnaire.Location
import com.ithalo.hybridcoach.core.questionnaire.Priority
import com.ithalo.hybridcoach.core.questionnaire.QuestionnaireAnswers
import com.ithalo.hybridcoach.core.questionnaire.RunExperience
import com.ithalo.hybridcoach.core.questionnaire.RunSurface
import com.ithalo.hybridcoach.core.questionnaire.Split

/**
 * Fills `prompt_template.md`. `{{KEY}}` is replaced by its value; `{{#KEY}}...{{/KEY}}` is kept
 * (without the markers) only when KEY has a non-blank value.
 */
class PromptBuilder(private val template: String, private val exampleJson: String) {

    fun build(answers: QuestionnaireAnswers, previousSummaryText: String?): String {
        val values = mapOf(
            "PROFILE" to profile(answers),
            "LIMITATIONS" to limitations(answers),
            "BEGINNER_RUNNER" to if (answers.isBeginnerRunner) "1" else "",
            "PREVIOUS_BLOCK" to if (answers.includePreviousSummary) previousSummaryText.orEmpty().trim() else "",
            "DELOAD_INSTRUCTION" to if (answers.includeDeload) {
                "Inclua uma semana de deload (redução de volume e de carga) na última semana do bloco, com `is_deload: true`."
            } else {
                "Não inclua semana de deload; mantenha a progressão contínua e `is_deload: false` em todas as semanas."
            },
            "HOME_VERSION" to if (answers.homeVersion) "1" else "",
            "BIKE" to if (answers.hasBike) "1" else "",
            "DURATION_WEEKS" to answers.durationWeeks.toString(),
            "START_DATE" to answers.startDate,
        )

        var text = SECTION.replace(template) { match ->
            if (values[match.groupValues[1]].isNullOrBlank()) "" else match.groupValues[2]
        }
        for ((key, value) in values) text = text.replace("{{$key}}", value)
        // The example goes in last so braces inside it are never read as placeholders.
        return text.replace("{{EXAMPLE_JSON}}", exampleJson.trim()).trim() + "\n"
    }

    private fun limitations(a: QuestionnaireAnswers): String = buildString {
        if (a.limitations.isNotBlank()) appendLine("- Lesões ou dores atuais: ${a.limitations.trim()}")
        if (a.avoidExercises.isNotBlank()) appendLine("- Exercícios a evitar: ${a.avoidExercises.trim()}")
    }.trim()

    private fun profile(a: QuestionnaireAnswers): String {
        val lines = mutableListOf<String>()
        lines += "- Prioridade: ${priority(a.priority)}"
        if (a.goalText.isNotBlank()) lines += "- Objetivo: ${a.goalText.trim()}"
        if (a.raceName.isNotBlank() || a.raceDate.isNotBlank()) {
            lines += "- Prova-alvo: ${listOf(a.raceName.trim(), a.raceDate.trim()).filter { it.isNotEmpty() }.joinToString(" em ")}"
        }
        val body = listOfNotNull(
            a.age.trim().takeIf { it.isNotEmpty() }?.let { "$it anos" },
            a.weightKg.trim().takeIf { it.isNotEmpty() }?.let { "$it kg" },
            a.heightCm.trim().takeIf { it.isNotEmpty() }?.let { "$it cm" },
        )
        if (body.isNotEmpty()) lines += "- Sobre o usuário: ${body.joinToString(", ")}"

        lines += "- Semana: corrida em ${days(a.runDays)}; musculação em ${days(a.strengthDays)}"
        if (a.sameDays.isNotEmpty()) lines += "- Corrida e musculação no mesmo dia em: ${days(a.sameDays)}"
        lines += "- Minutos por sessão: corrida ${a.runMinutes}, musculação ${a.strengthMinutes}"

        if (a.runDays.isNotEmpty()) {
            lines += "- Corrida hoje: ${runExperience(a)}"
            if (a.recentRaceKm > 0 && a.recentRaceTime.isNotBlank()) {
                lines += "- Tempo recente: ${a.recentRaceKm} km em ${a.recentRaceTime.trim()}"
            }
            if (a.longRunDay.isNotBlank()) lines += "- Dia preferido do longão (corrida mais longa da semana): ${dayName(a.longRunDay)}"
            lines += "- Onde corre: ${a.runSurfaces.joinToString(" e ") { if (it == RunSurface.TREADMILL) "esteira" else "rua" }}"
        }

        if (a.strengthDays.isNotEmpty()) {
            lines += "- Musculação hoje: ${a.strengthExperience.ifBlank { "tempo de treino não informado" }.trim()}"
            lines += "- Divisão preferida: ${split(a.split)}"
            val loads = a.knownLifts.map { lift ->
                val load = lift.loadKg.trim().takeIf { it.isNotEmpty() }?.let { "$it kg" } ?: "carga não lembrada"
                val reps = lift.reps.trim().takeIf { it.isNotEmpty() }?.let { " x $it" }.orEmpty()
                "${lift.name}: $load$reps"
            } + listOfNotNull(a.knownLoads.trim().takeIf { it.isNotEmpty() })
            lines += "- Exercícios já feitos e cargas: ${loads.joinToString("; ").ifEmpty { "não informado" }}"
            if (a.legDay.isNotBlank()) lines += "- Dia preferido do treino de pernas: ${dayName(a.legDay)}"
            lines += "- Local da musculação: ${a.locations.joinToString(", ") { location(it) }}"
        }
        if (a.hasBike) lines += "- Tem acesso a bike ergométrica"
        lines += "- Qualidade média do sono: ${a.sleep}/5; nível de estresse: ${a.stress}/5"
        lines += "- Bloco: ${a.durationWeeks} semanas, começando em ${a.startDate}"
        return lines.joinToString("\n")
    }

    private fun runExperience(a: QuestionnaireAnswers) = when (a.runExperience) {
        RunExperience.NEVER -> "nunca correu"
        RunExperience.WALK_RUN -> "faz caminhadas e algumas corridas leves intercaladas, sem correr de forma contínua"
        RunExperience.REGULAR -> "corre ${a.runWeeklyKm} km por semana; maior corrida recente: ${format(a.longestRunKm)} km"
    }

    private fun days(list: List<String>) = list.joinToString(", ") { dayName(it) }.ifEmpty { "nenhum dia" }

    private fun priority(p: Priority) = when (p) {
        Priority.RUN -> "corrida"
        Priority.STRENGTH -> "musculação"
        Priority.BALANCED -> "equilibrado entre corrida e musculação"
    }

    private fun split(s: Split) = when (s) {
        Split.AI -> "decida você (sem preferência)"
        Split.FULL_BODY -> "corpo inteiro"
        Split.UPPER_LOWER -> "superiores/inferiores"
        Split.PUSH_PULL_LEGS -> "empurrar/puxar/pernas"
        Split.ABC -> "ABC"
    }

    private fun location(l: Location) = when (l) {
        Location.FULL_GYM -> "academia completa"
        Location.LIMITED -> "equipamentos limitados"
        Location.HOME -> "em casa"
    }

    private fun dayName(code: String) = when (code) {
        "mon" -> "segunda"
        "tue" -> "terça"
        "wed" -> "quarta"
        "thu" -> "quinta"
        "fri" -> "sexta"
        "sat" -> "sábado"
        "sun" -> "domingo"
        else -> code
    }

    private fun format(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    internal companion object {
        // Every brace is escaped: Android's ICU regex engine rejects a bare `}` that the JVM accepts.
        const val SECTION_PATTERN = "\\{\\{#(\\w+)\\}\\}(.*?)\\{\\{/\\1\\}\\}"
        val SECTION = Regex(SECTION_PATTERN, RegexOption.DOT_MATCHES_ALL)
    }
}

object CorrectionRequest {
    fun build(errors: List<String>): String =
        "O JSON que você enviou falhou na validação com estes erros: ${errors.joinToString("; ")}. " +
            "Corrija e responda somente com o JSON completo e válido, sem texto antes ou depois."
}
