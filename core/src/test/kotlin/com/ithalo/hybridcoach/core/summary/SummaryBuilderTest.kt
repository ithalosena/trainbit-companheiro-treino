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
}
