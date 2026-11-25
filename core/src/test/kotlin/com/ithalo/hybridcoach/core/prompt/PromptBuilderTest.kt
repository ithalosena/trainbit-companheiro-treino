package com.ithalo.hybridcoach.core.prompt

import com.ithalo.hybridcoach.core.plan.PlanValidator
import com.ithalo.hybridcoach.core.questionnaire.ExtraKind
import com.ithalo.hybridcoach.core.questionnaire.KnownLift
import com.ithalo.hybridcoach.core.questionnaire.Location
import com.ithalo.hybridcoach.core.questionnaire.QuestionnaireAnswers
import com.ithalo.hybridcoach.core.questionnaire.RunExperience
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromptBuilderTest {
    private fun resource(name: String) = checkNotNull(javaClass.classLoader.getResource(name)).readText()

    private val builder = PromptBuilder(resource("prompt_template.md"), resource("example_plan.json"))
    private val answers = QuestionnaireAnswers(startDate = "2026-11-09", runExperience = RunExperience.REGULAR, runWeeklyKm = 15)

    @Test
    fun `example shipped in assets matches the validated fixture`() {
        assertEquals(resource("valid_full.json").trim(), resource("example_plan.json").trim())
    }

    @Test
    fun `no placeholder is left in the output`() {
        val prompt = builder.build(answers.copy(limitations = "joelho", runWeeklyKm = 0), "Resumo anterior")
        assertFalse(Regex("\\{\\{[#/]?[A-Z_]+}}").containsMatchIn(prompt), prompt)
    }

    @Test
    fun `profile reflects answers`() {
        val prompt = builder.build(answers.copy(goalText = "correr 5 km", recentRaceKm = 5, recentRaceTime = "28:30"), null)
        assertContains(prompt, "- Objetivo: correr 5 km")
        assertContains(prompt, "15 km por semana")
        assertContains(prompt, "Tempo recente: 5 km em 28:30")
        assertContains(prompt, "Semana: corrida em terça, sábado; musculação em segunda, quarta, sexta")
        assertContains(prompt, "Bloco: 4 semanas, começando em 2026-11-09")
    }

    @Test
    fun `days, lifts and body data are listed`() {
        val prompt = builder.build(
            answers.copy(
                runDays = listOf("mon"), strengthDays = listOf("mon", "wed"), age = "34", weightKg = "88",
                knownLifts = listOf(KnownLift("Supino reto com barra", "40", "8"), KnownLift("Stiff")), knownLoads = "rosca 12 kg",
            ),
            null,
        )
        assertContains(prompt, "Corrida e musculação no mesmo dia em: segunda")
        assertContains(prompt, "Sobre o usuário: 34 anos, 88 kg")
        assertContains(prompt, "Supino reto com barra: 40 kg x 8; Stiff: carga não lembrada; rosca 12 kg")
    }

    @Test
    fun `walk-run beginner is described and gets the beginner section`() {
        val prompt = builder.build(answers.copy(runExperience = RunExperience.WALK_RUN), null)
        assertContains(prompt, "faz caminhadas e algumas corridas leves intercaladas")
        assertContains(prompt, "Corredor iniciante")
    }

    @Test
    fun `home version and bike sections follow the answers`() {
        val plain = builder.build(answers, null)
        assertFalse("## Versão em casa" in plain)
        assertFalse("## Bike ergométrica" in plain)
        val full = builder.build(answers.copy(locations = listOf(Location.FULL_GYM, Location.HOME), wantsHomeVersion = true, hasBike = true), null)
        assertContains(full, "## Versão em casa")
        assertContains(full, "## Bike ergométrica")
    }

    @Test
    fun `section pattern escapes every brace for the Android regex engine`() {
        // A bare `}` compiles on the JVM but crashed the app on Android (ICU) when generating the prompt.
        val pattern = PromptBuilder.SECTION_PATTERN
        assertFalse(Regex("(?<!\\\\)[{}]").containsMatchIn(pattern), pattern)
    }

    @Test
    fun `optional sections only appear when filled`() {
        val plain = builder.build(answers, null)
        assertFalse("Limitações e exercícios a evitar" in plain)
        assertFalse("Corredor iniciante" in plain)
        assertFalse("Resumo do bloco anterior" in plain)

        val full = builder.build(answers.copy(limitations = "dor no joelho", runExperience = RunExperience.NEVER), "Aderência: 13 de 16")
        assertContains(full, "Limitações e exercícios a evitar")
        assertContains(full, "- Lesões ou dores atuais: dor no joelho")
        assertContains(full, "Corredor iniciante")
        assertContains(full, "Aderência: 13 de 16")
    }

    @Test
    fun `previous summary is dropped when the user opts out`() {
        val prompt = builder.build(answers.copy(includePreviousSummary = false), "Aderência: 13 de 16")
        assertFalse("Aderência: 13 de 16" in prompt)
    }

    @Test
    fun `deload instruction follows the preference`() {
        assertContains(builder.build(answers, null), "Inclua uma semana de deload")
        assertContains(builder.build(answers.copy(includeDeload = false), null), "Não inclua semana de deload")
    }

    @Test
    fun `embedded example is complete and valid`() {
        val prompt = builder.build(answers, null)
        assertContains(prompt, resource("valid_full.json").trim())
        assertTrue(PlanValidator.validateText(resource("example_plan.json")).isValid)
    }

    @Test
    fun `correction request lists the errors`() {
        val text = CorrectionRequest.build(listOf("a: erro 1", "b: erro 2"))
        assertEquals(
            "O JSON que você enviou falhou na validação com estes erros: a: erro 1; b: erro 2. " +
                "Corrija e responda somente com o JSON completo e válido, sem texto antes ou depois.",
            text,
        )
        assertNotNull(text)
    }

    @Test
    fun `without extras the AI is told not to add optional sessions`() {
        val prompt = builder.build(answers, null)
        assertFalse("## Treinos extras em casa" in prompt)
        assertContains(prompt, "Não inclua sessões extras: nenhuma sessão com `optional`.")
        assertFalse("Treinos extras em casa:" in prompt)
    }

    @Test
    fun `extras section lists only the chosen kinds`() {
        val prompt = builder.build(answers.copy(extraSessions = 2, extraKinds = listOf(ExtraKind.CORE)), null)
        assertContains(prompt, "## Treinos extras em casa")
        assertContains(prompt, "O usuário quer 2 sessões extras por semana, feitas em casa")
        assertContains(prompt, "`\"optional\": true`")
        assertContains(prompt, "- Abdominais:")
        assertFalse("- Bike ergométrica:" in prompt)
        assertFalse("- Alongamento e mobilidade:" in prompt)
        assertContains(prompt, "- Treinos extras em casa: 2 por semana (abdominais)")
        assertContains(prompt, "siga a seção \"Treinos extras em casa\"")
    }

    @Test
    fun `no chosen kind offers every available one, the bike only to who has it`() {
        val withBike = builder.build(answers.copy(extraSessions = 1, hasBike = true), null)
        assertContains(withBike, "O usuário quer 1 sessão extra por semana")
        assertContains(withBike, "- Bike ergométrica:")
        assertContains(withBike, "- Abdominais:")
        assertContains(withBike, "- Alongamento e mobilidade:")
        assertContains(withBike, "(bike, abdominais, alongamento)")
        val noBike = builder.build(answers.copy(extraSessions = 1, extraKinds = listOf(ExtraKind.BIKE)), null)
        assertFalse("- Bike ergométrica:" in noBike)
        assertContains(noBike, "(abdominais, alongamento)")
    }

    private val appExercises = com.ithalo.hybridcoach.core.exercises.ExerciseCatalog.parseCatalog(resource("exercises_catalog.json")).filter { !it.autoTranslated }

    @Test
    fun `the prompt lists the valid exercises and asks for their ids`() {
        val prompt = PromptBuilder(resource("prompt_template.md"), resource("example_plan.json"), appExercises).build(answers, null)
        assertContains(prompt, "## Exercícios válidos")
        assertContains(prompt, "- `supino-reto-barra`: Supino reto com barra")
        assertContains(prompt, "- `leg-press-45`: Leg press 45")
        assertContains(prompt, "copie o nome exatamente como está em `name` e o id em `exercise_id`")
        assertContains(prompt, "`\"custom\": true`")
        assertFalse("Use apenas exercícios comuns de academia" in prompt)
    }

    @Test
    fun `training only at home lists only what can be done at home`() {
        val home = answers.copy(locations = listOf(Location.HOME))
        val prompt = PromptBuilder(resource("prompt_template.md"), resource("example_plan.json"), appExercises).build(home, null)
        assertContains(prompt, "- `flexao-braco`:")
        assertContains(prompt, "- `rosca-alternada-halteres`:")
        assertFalse("`leg-press-45`" in prompt)
        assertFalse("`bike-ergometrica`" in prompt)
        val withBike = PromptBuilder(resource("prompt_template.md"), resource("example_plan.json"), appExercises).build(home.copy(hasBike = true), null)
        assertContains(withBike, "- `bike-ergometrica`:")
    }

    @Test
    fun `without a list the old rule stays`() {
        assertContains(builder.build(answers, null), "Use apenas exercícios comuns de academia, com nomes em português.")
    }
}
