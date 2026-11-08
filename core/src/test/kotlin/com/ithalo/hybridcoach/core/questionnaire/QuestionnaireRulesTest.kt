package com.ithalo.hybridcoach.core.questionnaire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestionnaireRulesTest {
    private val ok = QuestionnaireAnswers(startDate = "2026-11-09", runExperience = RunExperience.REGULAR, runWeeklyKm = 20)

    private fun codes(a: QuestionnaireAnswers) = QuestionnaireRules.check(a).map { it.code }

    @Test
    fun `defaults with a start date have no problems`() {
        assertEquals(emptyList(), codes(ok))
    }

    @Test
    fun `missing start date is an error`() {
        val issues = QuestionnaireRules.check(ok.copy(startDate = ""))
        assertTrue(QuestionnaireRules.hasErrors(issues))
        assertEquals(listOf(IssueCode.START_DATE_INVALID), issues.map { it.code })
    }

    @Test
    fun `sessions and available days come from the chosen days`() {
        val a = ok.copy(runDays = listOf("sat", "tue"), strengthDays = listOf("mon", "tue", "thu"))
        assertEquals(listOf("mon", "tue", "thu", "sat"), a.availableDays)
        assertEquals(listOf("tue"), a.sameDays)
        assertEquals(2, a.runSessionsPerWeek)
        assertEquals(3, a.strengthSessionsPerWeek)
    }

    @Test
    fun `anyone not running regularly is a beginner`() {
        assertEquals(listOf(IssueCode.BEGINNER_RUNNER), codes(ok.copy(runExperience = RunExperience.WALK_RUN)))
        assertTrue(ok.copy(runExperience = RunExperience.NEVER).isBeginnerRunner)
        assertTrue(ok.copy(runWeeklyKm = 0).isBeginnerRunner)
        assertFalse(ok.copy(runExperience = RunExperience.NEVER, runDays = emptyList()).isBeginnerRunner)
    }

    @Test
    fun `no days and bad duration are errors`() {
        val c = codes(ok.copy(runDays = emptyList(), strengthDays = emptyList(), durationWeeks = 20))
        assertTrue(IssueCode.NO_AVAILABLE_DAYS in c)
        assertTrue(IssueCode.DURATION_OUT_OF_RANGE in c)
    }

    @Test
    fun `long run and leg day must be on a run and a strength day`() {
        val c = codes(ok.copy(longRunDay = "mon", legDay = "sat"))
        assertEquals(listOf(IssueCode.LONG_RUN_DAY_UNAVAILABLE, IssueCode.LEG_DAY_UNAVAILABLE), c)
        assertEquals(emptyList(), codes(ok.copy(longRunDay = "", legDay = "")))
    }

    @Test
    fun `location and surface are required only when used`() {
        assertEquals(listOf(IssueCode.NO_LOCATION, IssueCode.NO_RUN_SURFACE), codes(ok.copy(locations = emptyList(), runSurfaces = emptyList())))
        assertEquals(emptyList(), codes(ok.copy(strengthDays = emptyList(), locations = emptyList())))
    }

    @Test
    fun `home version needs both gym and home`() {
        val both = ok.copy(locations = listOf(Location.FULL_GYM, Location.HOME), wantsHomeVersion = true)
        assertTrue(both.homeVersion)
        assertFalse(both.copy(locations = listOf(Location.HOME)).homeVersion)
        assertFalse(both.copy(wantsHomeVersion = false).homeVersion)
    }

    @Test
    fun `answers survive a json round trip and tolerate missing or old fields`() {
        val a = ok.copy(goalText = "5 km", runDays = listOf("tue"), priority = Priority.RUN, knownLifts = listOf(KnownLift("Stiff", "40", "8")))
        assertEquals(a, QuestionnaireAnswers.fromJson(a.toJson()))
        assertEquals(QuestionnaireAnswers(), QuestionnaireAnswers.fromJson("{}"))
        assertEquals(QuestionnaireAnswers(), QuestionnaireAnswers.fromJson("""{"availableDays":["mon"],"location":"HOME"}"""))
    }
}
