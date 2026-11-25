package com.ithalo.hybridcoach.core.prompt

import com.ithalo.hybridcoach.core.exercises.CatalogEntry
import com.ithalo.hybridcoach.core.questionnaire.ExtraKind
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
class PromptBuilder(
    private val template: String,
    private val exampleJson: String,
    /** The app's own exercises (reviewed names): the AI is asked to use only these, by id. */
    private val exercises: List<CatalogEntry> = emptyList(),
) {

    /** [nextBlock] is the continuity section from the closed previous block (core block.NextBlockPrompt), when there is one. */
    fun build(answers: QuestionnaireAnswers, previousSummaryText: String?, nextBlock: String? = null): String {
        val values = mapOf(
            "PROFILE" to profile(answers),
            "LIMITATIONS" to limitations(answers),
            "BEGINNER_RUNNER" to if (answers.isBeginnerRunner) "1" else "",
            "NEXT_BLOCK" to nextBlock.orEmpty().trim(),
            "PREVIOUS_BLOCK" to if (answers.includePreviousSummary) previousSummaryText.orEmpty().trim() else "",
            "DELOAD_INSTRUCTION" to if (answers.includeDeload) {
                "Inclua uma semana de deload (redução de volume e de carga) na última semana do bloco, com `is_deload: true`."
            } else {
                "Não inclua semana de deload; mantenha a progressão contínua e `is_deload: false` em todas as semanas."
            },
            "HOME_VERSION" to if (answers.homeVersion) "1" else "",
            "BIKE" to if (answers.hasBike) "1" else "",
            "EXTRAS" to extras(answers),
            "EXERCISE_LIST" to exerciseList(answers),
            "EXERCISE_RULE" to if (exerciseList(answers).isNotEmpty()) {
                "Use os exercícios da lista \"Exercícios válidos\": copie o nome exatamente como está em `name` e o id em `exercise_id`. " +
                    "Só quando nenhum servir, use outro exercício comum, com nome em português, e marque `\"custom\": true`."
            } else {
                "Use apenas exercícios comuns de academia, com nomes em português."
            },
            "EXTRAS_INSTRUCTION" to if (answers.extraSessions > 0) {
                "Sessões extras em casa: siga a seção \"Treinos extras em casa\"."
            } else {
                "Não inclua sessões extras: nenhuma sessão com `optional`."
            },
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

    /**
     * "`id`: name" of the exercises the user can do where they train: at home only, just body weight, dumbbells,
     * bands, kettlebell and the bike (when they have one).
     */
    private fun exerciseList(a: QuestionnaireAnswers): String {
        val homeOnly = a.locations.isNotEmpty() && a.locations.all { it == Location.HOME }
        return exercises
            .filter { e ->
                when {
                    !homeOnly -> true
                    e.id == BIKE_ID -> a.hasBike // a "machine" in the catalog, but the user's own at home
                    else -> e.equipment == null || e.equipment in HOME_EQUIPMENT
                }
            }
            .joinToString("\n") { "- `${it.id}`: ${it.canonicalName}" }
    }

    /** The extra home sessions section; blank when the user wants none. */
    private fun extras(a: QuestionnaireAnswers): String {
        val n = a.extraSessions.coerceIn(0, QuestionnaireAnswers.MAX_EXTRA_SESSIONS)
        if (n == 0) return ""
        val kinds = a.extraKindsForPlan
        return buildString {
            appendLine(
                "O usuário quer $n ${if (n == 1) "sessão extra" else "sessões extras"} por semana, feitas em casa, além das sessões principais. " +
                    "Marque cada uma com `\"optional\": true`: é um bônus, não fazer não conta como falta.",
            )
            appendLine("- Tipos que ele aceita (todos como sessão `strength`):")
            kinds.forEach { appendLine("  - ${extraKind(it)}") }
            appendLine("- Leves: `estimated_minutes` de 15 a 30 e esforço baixo (`rpe_target` até 6). Nunca substituem nem compensam as sessões principais.")
            appendLine("- Podem ficar num dia sem treino ou como segunda sessão de um dia que só tem uma (no máximo 2 sessões no dia). Evite a véspera do longão e do treino pesado de pernas.")
            appendLine("- Na semana de deload, no máximo 1 extra.")
            append("- Título curto que diga o que é, por exemplo \"Bike em casa\" ou \"Abdominais em casa\".")
        }
    }

    private fun extraKind(k: ExtraKind) = when (k) {
        ExtraKind.BIKE -> "Bike ergométrica: um exercício \"Bike ergométrica\" com `sets: 1`, `reps` em tempo (ex.: `\"25min\"`), `load_kg: null`, ritmo leve a moderado."
        ExtraKind.CORE -> "Abdominais: 4 a 6 exercícios de core sem aparelho (ex.: Prancha, Prancha lateral, Abdominal crunch, Elevação de pernas, Bird dog, Rotação russa), `load_kg: null`, `reps` em repetições ou tempo (`\"30s\"`), descanso de 30 a 45 s."
        ExtraKind.MOBILITY -> "Alongamento e mobilidade: 6 a 8 alongamentos ou exercícios de mobilidade, `sets` 1 ou 2, `reps` em tempo (`\"30s\"` ou `\"45s\"`), `load_kg: null`, `rest_seconds` até 15."
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
        if (a.hasBike) lines += "- Tem bike ergométrica em casa"
        if (a.extraSessions > 0) {
            lines += "- Treinos extras em casa: ${a.extraSessions.coerceAtMost(QuestionnaireAnswers.MAX_EXTRA_SESSIONS)} por semana (${a.extraKindsForPlan.joinToString(", ") { extraKindName(it) }})"
        }
        lines += "- Qualidade média do sono: ${a.sleep}/5; nível de estresse: ${a.stress}/5"
        lines += "- Bloco: ${a.durationWeeks} semanas, começando em ${a.startDate}"
        return lines.joinToString("\n")
    }

    private fun extraKindName(k: ExtraKind) = when (k) {
        ExtraKind.BIKE -> "bike"
        ExtraKind.CORE -> "abdominais"
        ExtraKind.MOBILITY -> "alongamento"
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
        val HOME_EQUIPMENT = setOf("peso do corpo", "halteres", "elástico", "kettlebell", "nenhum", "bola suíça", "outro")
        const val BIKE_ID = "bike-ergometrica"


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
