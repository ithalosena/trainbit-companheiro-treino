package com.ithalo.hybridcoach.core.summary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryBuilderTest {
    private val records = listOf(
        SessionRecord(SessionStatus.DONE, null),
        SessionRecord(SessionStatus.DONE, null),
        SessionRecord(SessionStatus.SKIPPED, "cansaço"),
        SessionRecord(SessionStatus.SKIPPED, " cansaço "),
        SessionRecord(SessionStatus.SKIPPED, ""),
        SessionRecord(SessionStatus.SKIPPED, "chuva"),
        SessionRecord(SessionStatus.PENDING, null),
    )

    private val summary = SummaryBuilder.build("Bloco 1", LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 6), records)

    @Test
    fun `adherence counts statuses and distinct non blank reasons`() {
        assertEquals(7, summary.adherence.plannedSessions)
        assertEquals(2, summary.adherence.done)
        assertEquals(4, summary.adherence.skipped)
        assertEquals(listOf("cansaço", "chuva"), summary.adherence.skipReasons)
    }

    @Test
    fun `lateness and comebacks reach the summary`() {
        val s = SummaryBuilder.build(
            "Bloco", LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 6),
            listOf(
                SessionRecord(SessionStatus.DONE, null, daysLate = 0),
                SessionRecord(SessionStatus.DONE, null, daysLate = 2, reentry = "short"),
                SessionRecord(SessionStatus.DONE, null, daysLate = 5, reentry = "declined"),
                SessionRecord(SessionStatus.DONE, null, optional = true, daysLate = 9),
            ),
        )
        assertEquals(2, s.adherence.doneLate)
        assertEquals(3.5, s.adherence.avgDaysLate)
        assertEquals(listOf("short"), s.adherence.reentries)
        assertEquals(0, summary.adherence.doneLate)
        assertEquals(null, summary.adherence.avgDaysLate)
    }

    @Test
    fun `json uses the documented field names`() {
        val json = Json.parseToJsonElement(SummaryBuilder.toJson(summary)).jsonObject
        assertEquals(1, json.getValue("summary_version").jsonPrimitive.int)
        assertEquals("Bloco 1", json.getValue("plan_name").jsonPrimitive.content)
        assertEquals("2026-12-06", json.getValue("period").jsonObject.getValue("end").jsonPrimitive.content)
        val adherence = json.getValue("adherence").jsonObject
        assertEquals(7, adherence.getValue("planned_sessions").jsonPrimitive.int)
        assertEquals(2, adherence.getValue("skip_reasons").jsonArray.size)
        assertEquals(0, json.getValue("strength").jsonArray.size)
        assertEquals(0, json.getValue("runs").jsonArray.size)
        assertEquals(0, json.getValue("user_notes").jsonArray.size)
    }

    @Test
    fun `skeleton is far below the size target`() {
        assertTrue(SummaryBuilder.toJson(summary).length < 6000)
    }

    @Test
    fun `text version mentions adherence and empty sections`() {
        val text = SummaryBuilder.toText(summary)
        assertTrue("2 de 7 sessões feitas, 4 puladas (motivos: cansaço, chuva)" in text, text)
        assertTrue("Musculação: sem registros." in text)
        assertTrue("Corridas: sem registros." in text)
    }

    @Test
    fun `text version describes recorded strength and runs`() {
        val full = summary.copy(
            strength = listOf(
                StrengthSummary(
                    "Supino reto com barra",
                    PlannedStrength(4, "6-8", 40.0),
                    DoneStrength(BestSet(8, 45.0), 2, 8.5, listOf(40.0, 42.5, 45.0)),
                ),
            ),
            runs = listOf(RunSummary(2, "interval", PaceSummary(300, 330), 338, 5.2, 8.0)),
            userNotes = listOf("joelho incomodou"),
        )
        val text = SummaryBuilder.toText(full)
        assertTrue("planejado 4x6-8 @ 40 kg; melhor série 8 x 45 kg; 2 falhas, RPE médio 8.5, cargas: 40 > 42.5 > 45" in text, text)
        assertTrue("Semana 2, interval: 5.2 km, pace médio 5:38/km (alvo 5:00-5:30/km), RPE 8" in text, text)
        assertTrue("- joelho incomodou" in text)
    }

    @Test
    fun `unknown status value falls back to pending`() {
        assertEquals(SessionStatus.PENDING, SessionStatus.fromValue("???"))
        assertEquals(SessionStatus.SKIPPED, SessionStatus.fromValue("skipped"))
    }

    @Test
    fun `extra home sessions stay out of the adherence and are counted apart`() {
        val withExtras = records + listOf(
            SessionRecord(SessionStatus.DONE, null, optional = true),
            SessionRecord(SessionStatus.SKIPPED, "sem tempo", optional = true),
            SessionRecord(SessionStatus.PENDING, null, optional = true),
        )
        val s = SummaryBuilder.build("Bloco 1", LocalDate.of(2026, 11, 9), LocalDate.of(2026, 12, 6), withExtras)
        assertEquals(7, s.adherence.plannedSessions)
        assertEquals(2, s.adherence.done)
        assertEquals(4, s.adherence.skipped)
        assertEquals(listOf("cansaço", "chuva"), s.adherence.skipReasons)
        assertEquals(3, s.adherence.extrasPlanned)
        assertEquals(1, s.adherence.extrasDone)
        val json = Json.parseToJsonElement(SummaryBuilder.toJson(s)).jsonObject.getValue("adherence").jsonObject
        assertEquals(3, json.getValue("extras_planned").jsonPrimitive.int)
        assertEquals(1, json.getValue("extras_done").jsonPrimitive.int)
        assertTrue("Extras em casa (bônus, fora da aderência): 1 de 3 feitos." in SummaryBuilder.toText(s))
        assertTrue("Extras em casa" !in SummaryBuilder.toText(summary))
    }
}
