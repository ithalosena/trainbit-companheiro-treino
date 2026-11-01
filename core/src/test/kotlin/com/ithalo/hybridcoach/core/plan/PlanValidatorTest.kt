package com.ithalo.hybridcoach.core.plan

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanValidatorTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader.getResource(name)) { "fixture $name not found" }.readText()

    @Test
    fun `valid_full is accepted with every week covered`() {
        val result = PlanValidator.validateText(fixture("valid_full.json"))
        assertEquals(emptyList(), result.errors)
        assertEquals(emptyList(), result.warnings)
        val doc = assertNotNull(result.document)
        assertEquals(LocalDate.of(2026, 11, 9), doc.plan.startDate)
        assertEquals(4, doc.plan.durationWeeks)
        assertEquals(listOf(listOf(1, 2), listOf(3), listOf(4)), doc.weeks.map { it.weeks })
        assertTrue(doc.weeks.last().isDeload)
        assertTrue(doc.weeks.all { it.sessions.isNotEmpty() })
    }

    @Test
    fun `valid_full parses strength and run details`() {
        val doc = assertNotNull(PlanValidator.validateText(fixture("valid_full.json")).document)
        val monday = doc.weeks[0].sessions[0] as StrengthSessionDef
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals("6-8", monday.exercises[0].reps)
        assertEquals(40.0, monday.exercises[0].loadKg)
        assertNull(monday.exercises[1].loadKg)
        assertNull(monday.exercises[3].rpeTarget)

        val tuesday = doc.weeks[0].sessions[1] as RunSessionDef
        val interval = tuesday.blocks[1]
        assertEquals(RunBlockKind.INTERVAL, interval.kind)
        assertEquals(400, interval.distanceM)
        assertNull(interval.durationSeconds)
        assertEquals(PaceRange(300, 330), interval.targetPace)
        assertNull(tuesday.blocks[2].targetPace)
    }

    @Test
    fun `valid_min is accepted and fills defaults`() {
        val result = PlanValidator.validateText(fixture("valid_min.json"))
        assertEquals(emptyList(), result.errors)
        val doc = assertNotNull(result.document)
        assertEquals("", doc.plan.goal)
        assertEquals("", doc.weeks[0].focus)
        assertEquals(false, doc.weeks[0].isDeload)
        val exercise = (doc.weeks[0].sessions[0] as StrengthSessionDef).exercises[0]
        assertNull(exercise.loadKg)
        assertNull(exercise.rpeTarget)
    }

    @Test
    fun `missing required field is rejected with its path`() {
        val result = PlanValidator.validateText(fixture("invalid_missing_field.json"))
        assertNull(result.document)
        assertEquals(listOf("weeks[0].sessions[0].exercises[0].sets: campo obrigatório ausente"), result.errors)
    }

    @Test
    fun `inverted pace range is rejected`() {
        val result = PlanValidator.validateText(fixture("invalid_pace_range.json"))
        assertNull(result.document)
        assertEquals(1, result.errors.size)
        assertTrue(
            result.errors.single().startsWith("weeks[0].sessions[1].blocks[1].target_pace_sec_per_km: min (330) deve ser menor ou igual a max (300)"),
            result.errors.single(),
        )
    }

    @Test
    fun `week out of range and gap are both reported`() {
        val result = PlanValidator.validateText(fixture("invalid_week_gap.json"))
        assertNull(result.document)
        assertTrue(result.errors.any { it.startsWith("weeks[1].weeks[0]: esperado número inteiro entre 1 e 4, veio 5") }, result.errors.toString())
    }

    @Test
    fun `missing week is reported on the weeks list`() {
        val text = fixture("valid_full.json").replace("\"weeks\": [1, 2]", "\"weeks\": [1]")
        val result = PlanValidator.validateText(text)
        assertEquals(listOf("weeks: faltam as semanas 2; todas de 1 a 4 devem aparecer exatamente uma vez"), result.errors)
    }

    @Test
    fun `duplicate week is reported`() {
        val text = fixture("valid_full.json").replace("\"weeks\": [3]", "\"weeks\": [2]")
        val result = PlanValidator.validateText(text)
        assertTrue(result.errors.any { it.startsWith("weeks[1].weeks[0]: semana 2 já aparece em weeks[0]") }, result.errors.toString())
        assertTrue(result.errors.any { it.startsWith("weeks: faltam as semanas 3") }, result.errors.toString())
    }

    @Test
    fun `markdown fenced answer is extracted`() {
        val result = PlanValidator.validateText(fixture("wrapped_in_markdown.json"))
        assertEquals(emptyList(), result.errors)
        val doc = assertNotNull(result.document)
        assertEquals("Mínimo {embrulhado}", doc.plan.name)
        assertEquals(DayOfWeek.WEDNESDAY, doc.weeks[0].sessions[0].dayOfWeek)
    }

    @Test
    fun `wrong type message shows received value`() {
        val text = fixture("valid_full.json").replaceFirst("\"sets\": 4", "\"sets\": \"três\"")
        val result = PlanValidator.validateText(text)
        assertEquals(
            listOf("weeks[0].sessions[0].exercises[0].sets: esperado número inteiro entre 1 e 10, veio \"três\""),
            result.errors,
        )
    }

    @Test
    fun `fractional and out of range numbers are rejected`() {
        val text = fixture("valid_full.json")
            .replaceFirst("\"sets\": 4", "\"sets\": 11")
            .replaceFirst("\"rest_seconds\": 120", "\"rest_seconds\": 601")
            .replaceFirst("\"rpe_target\": 8", "\"rpe_target\": 7.5")
        val errors = PlanValidator.validateText(text).errors
        assertEquals(3, errors.size, errors.toString())
        assertTrue(errors.any { it.endsWith("sets: esperado número inteiro entre 1 e 10, veio 11") })
        assertTrue(errors.any { it.endsWith("rest_seconds: esperado número inteiro entre 0 e 600, veio 601") })
        assertTrue(errors.any { it.endsWith("rpe_target: esperado número inteiro entre 1 e 10, veio 7.5") })
    }

    @Test
    fun `reps must stay a string`() {
        val text = fixture("valid_min.json").replace("\"reps\": \"8\"", "\"reps\": 8")
        val result = PlanValidator.validateText(text)
        assertEquals(listOf("weeks[0].sessions[0].exercises[0].reps: esperado texto (ex.: \"8\" ou \"6-8\"), veio 8"), result.errors)
    }

    @Test
    fun `unknown schema version is rejected`() {
        val text = fixture("valid_min.json").replace("\"schema_version\": 1", "\"schema_version\": 2")
        val result = PlanValidator.validateText(text)
        assertNull(result.document)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().startsWith("schema_version: versão 2 não suportada"))
    }

    @Test
    fun `unknown fields only warn`() {
        val text = fixture("valid_min.json").replace("\"duration_weeks\": 1", "\"duration_weeks\": 1, \"coach\": \"x\"")
        val result = PlanValidator.validateText(text)
        assertNotNull(result.document)
        assertEquals(listOf("plan.coach: campo desconhecido, ignorado"), result.warnings)
    }

    @Test
    fun `non monday start date warns`() {
        val text = fixture("valid_min.json").replace("2026-11-09", "2026-11-11")
        val result = PlanValidator.validateText(text)
        assertNotNull(result.document)
        assertTrue(result.warnings.single().startsWith("plan.start_date: 2026-11-11 não é uma segunda-feira"))
    }

    @Test
    fun `invalid date is rejected`() {
        val text = fixture("valid_min.json").replace("2026-11-09", "09/11/2026")
        val result = PlanValidator.validateText(text)
        assertEquals(listOf("plan.start_date: esperado data no formato AAAA-MM-DD, veio \"09/11/2026\""), result.errors)
    }

    @Test
    fun `duration must be 1 to 12`() {
        val text = fixture("valid_min.json").replace("\"duration_weeks\": 1", "\"duration_weeks\": 13")
        val errors = PlanValidator.validateText(text).errors
        assertTrue(errors.any { it == "plan.duration_weeks: esperado número inteiro entre 1 e 12, veio 13" }, errors.toString())
    }

    @Test
    fun `run block needs exactly one of duration or distance`() {
        val both = fixture("wrapped_in_markdown.json").replace("\"duration_seconds\": 1800,", "\"duration_seconds\": 1800, \"distance_m\": 5000,")
        assertTrue(PlanValidator.validateText(both).errors.single().contains("informe exatamente um entre duration_seconds e distance_m, vieram os dois"))

        val neither = fixture("wrapped_in_markdown.json").replace("\"duration_seconds\": 1800,", "")
        assertTrue(PlanValidator.validateText(neither).errors.single().contains("veio nenhum"))
    }

    @Test
    fun `pace outside plausible range is rejected`() {
        val text = fixture("wrapped_in_markdown.json").replace("\"min\": 380", "\"min\": 100")
        val errors = PlanValidator.validateText(text).errors
        assertEquals(1, errors.size)
        assertTrue(errors.single().endsWith("target_pace_sec_per_km.min: esperado número entre 150 e 900, veio 100"), errors.single())
    }

    @Test
    fun `pace null and absent both mean no target`() {
        val absent = fixture("wrapped_in_markdown.json").replace(", \"target_pace_sec_per_km\": { \"min\": 380, \"max\": 430 }", "")
        val doc = assertNotNull(PlanValidator.validateText(absent).document)
        assertNull((doc.weeks[0].sessions[0] as RunSessionDef).blocks[0].targetPace)
    }

    @Test
    fun `more than two sessions on the same day is rejected`() {
        val session = """{ "day_of_week": "mon", "type": "run", "title": "R", "blocks": [ { "kind": "steady", "duration_seconds": 600 } ] }"""
        val text = """{ "schema_version": 1, "plan": { "name": "n", "start_date": "2026-11-09", "duration_weeks": 1 },
            "weeks": [ { "weeks": [1], "sessions": [ $session, $session, $session ] } ] }"""
        assertEquals(listOf("weeks[0].sessions: no máximo 2 sessões no mesmo dia, mas mon tem 3"), PlanValidator.validateText(text).errors)
    }

    @Test
    fun `two sessions on the same day are accepted`() {
        val session = """{ "day_of_week": "mon", "type": "run", "title": "R", "blocks": [ { "kind": "steady", "duration_seconds": 600 } ] }"""
        val text = """{ "schema_version": 1, "plan": { "name": "n", "start_date": "2026-11-09", "duration_weeks": 1 },
            "weeks": [ { "weeks": [1], "sessions": [ $session, $session ] } ] }"""
        assertNotNull(PlanValidator.validateText(text).document)
    }

    @Test
    fun `bad type and bad day are reported`() {
        val text = fixture("valid_min.json").replace("\"strength\"", "\"swim\"")
        assertEquals(listOf("weeks[0].sessions[0].type: esperado \"strength\" ou \"run\", veio \"swim\""), PlanValidator.validateText(text).errors)

        val day = fixture("valid_min.json").replace("\"mon\"", "\"segunda\"")
        assertEquals(listOf("weeks[0].sessions[0].day_of_week: esperado um entre mon, tue, wed, thu, fri, sat, sun, veio \"segunda\""), PlanValidator.validateText(day).errors)
    }

    @Test
    fun `strength session without exercises is rejected`() {
        val text = fixture("valid_min.json").replace(Regex("\"exercises\": \\[.*?\\]\\s*}", RegexOption.DOT_MATCHES_ALL), "\"blocks\": [] }")
        val errors = PlanValidator.validateText(text).errors
        assertTrue(errors.any { it == "weeks[0].sessions[0].exercises: campo obrigatório ausente" }, errors.toString())
    }

    @Test
    fun `trailing comma is reported as malformed json`() {
        val text = fixture("valid_min.json").replace("\"duration_weeks\": 1 }", "\"duration_weeks\": 1, }")
        val result = PlanValidator.validateText(text)
        assertNull(result.document)
        assertTrue(result.errors.single().startsWith("json: JSON malformado"), result.errors.toString())
    }

    @Test
    fun `truncated answer is reported as incomplete`() {
        val text = fixture("valid_full.json").take(400)
        assertTrue(PlanValidator.validateText(text).errors.single().startsWith("json: JSON incompleto"))
    }

    @Test
    fun `text without json is reported`() {
        assertEquals(listOf("json: nenhum objeto JSON encontrado no texto"), PlanValidator.validateText("desculpe, não consigo").errors)
    }

    @Test
    fun `two json objects are ambiguous`() {
        val one = fixture("valid_min.json")
        val result = PlanValidator.validateText("$one\n\nou então:\n$one")
        assertNull(result.document)
        assertTrue(result.errors.single().startsWith("json: mais de um objeto JSON"), result.errors.toString())
    }

    @Test
    fun `prose braces next to the plan do not cause ambiguity`() {
        val result = PlanValidator.validateText("Use o formato {schema} abaixo:\n" + fixture("valid_min.json"))
        assertEquals(emptyList(), result.errors)
    }

    @Test
    fun `error list can be rendered for the correction request`() {
        val errors = PlanValidator.validateText(fixture("invalid_missing_field.json")).errors
        assertTrue(errors.all { it.contains(": ") })
    }
}
