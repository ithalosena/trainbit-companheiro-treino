package com.ithalo.hybridcoach.core.questionnaire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class Priority { RUN, STRENGTH, BALANCED }
enum class Split { AI, FULL_BODY, UPPER_LOWER, PUSH_PULL_LEGS, ABC }
enum class Location { FULL_GYM, LIMITED, HOME }
enum class RunSurface { STREET, TREADMILL }

/** NEVER: never ran; WALK_RUN: walks or alternates walking and jogging; REGULAR: already runs. */
enum class RunExperience { NEVER, WALK_RUN, REGULAR }

val WEEK_DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

/** Well-known lifts offered as a checklist; the names match exercises.json canonical names. */
val COMMON_LIFTS = listOf(
    "Supino reto com barra",
    "Agachamento livre",
    "Leg press 45",
    "Levantamento terra",
    "Desenvolvimento com halteres",
    "Puxada frontal",
    "Remada baixa",
    "Rosca direta com barra",
    "Tríceps na polia",
    "Cadeira extensora",
    "Mesa flexora",
    "Stiff",
)

/** A lift the user has done; load and reps are free text as typed ("40", "8"), blank when unknown. */
@Serializable
data class KnownLift(val name: String, val loadKg: String = "", val reps: String = "")

@Serializable
data class QuestionnaireAnswers(
    // 1. Goal
    val priority: Priority = Priority.BALANCED,
    val goalText: String = "",
    val raceName: String = "",
    val raceDate: String = "",
    // 2. Week: which days get a run and which get strength (both on the same day is allowed)
    val runDays: List<String> = listOf("tue", "sat"),
    val strengthDays: List<String> = listOf("mon", "wed", "fri"),
    val runMinutes: Int = 40,
    val strengthMinutes: Int = 60,
    // 3. Running
    val runExperience: RunExperience = RunExperience.NEVER,
    val runWeeklyKm: Int = 0,
    val longestRunKm: Double = 0.0,
    val recentRaceKm: Int = 0,
    val recentRaceTime: String = "",
    /** Blank lets the AI choose. */
    val longRunDay: String = "",
    // 4. Strength
    val strengthExperience: String = "",
    val split: Split = Split.AI,
    val knownLifts: List<KnownLift> = emptyList(),
    val knownLoads: String = "",
    /** Blank lets the AI choose. */
    val legDay: String = "",
    // 5. Location and equipment
    val locations: List<Location> = listOf(Location.FULL_GYM),
    val runSurfaces: List<RunSurface> = listOf(RunSurface.STREET),
    val hasBike: Boolean = false,
    val wantsHomeVersion: Boolean = false,
    // 6. You and recovery
    val age: String = "",
    val weightKg: String = "",
    val heightCm: String = "",
    val limitations: String = "",
    val avoidExercises: String = "",
    val sleep: Int = 3,
    val stress: Int = 3,
    // 7. Block
    val durationWeeks: Int = 4,
    val startDate: String = "",
    val includeDeload: Boolean = true,
    // 8. Previous block
    val includePreviousSummary: Boolean = true,
) {
    val availableDays: List<String> get() = WEEK_DAYS.filter { it in runDays || it in strengthDays }
    val sameDays: List<String> get() = WEEK_DAYS.filter { it in runDays && it in strengthDays }
    val runSessionsPerWeek: Int get() = runDays.size
    val strengthSessionsPerWeek: Int get() = strengthDays.size
    val isBeginnerRunner: Boolean get() = runDays.isNotEmpty() && (runExperience != RunExperience.REGULAR || runWeeklyKm == 0)
    val hasLimitations: Boolean get() = limitations.isNotBlank() || avoidExercises.isNotBlank()

    /** A home variant only makes sense when the user trains both at a gym and at home. */
    val canHaveHomeVersion: Boolean get() = Location.HOME in locations && locations.any { it != Location.HOME } && strengthDays.isNotEmpty()
    val homeVersion: Boolean get() = wantsHomeVersion && canHaveHomeVersion

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(text: String): QuestionnaireAnswers = JSON.decodeFromString(serializer(), text)
    }
}

enum class IssueCode {
    NO_AVAILABLE_DAYS,
    DURATION_OUT_OF_RANGE,
    START_DATE_INVALID,
    RACE_DATE_INVALID,
    LONG_RUN_DAY_UNAVAILABLE,
    LEG_DAY_UNAVAILABLE,
    NO_LOCATION,
    NO_RUN_SURFACE,
    BEGINNER_RUNNER,
}

enum class IssueSeverity { ERROR, WARNING, INFO }

data class QuestionnaireIssue(val code: IssueCode, val severity: IssueSeverity, val args: List<String> = emptyList())

/** Mechanical expert rules; no AI involved. */
object QuestionnaireRules {
    fun check(a: QuestionnaireAnswers): List<QuestionnaireIssue> {
        val issues = mutableListOf<QuestionnaireIssue>()
        if (a.availableDays.isEmpty()) issues += QuestionnaireIssue(IssueCode.NO_AVAILABLE_DAYS, IssueSeverity.ERROR)
        if (a.durationWeeks !in 4..12) issues += QuestionnaireIssue(IssueCode.DURATION_OUT_OF_RANGE, IssueSeverity.ERROR)
        if (!isIsoDate(a.startDate)) issues += QuestionnaireIssue(IssueCode.START_DATE_INVALID, IssueSeverity.ERROR)
        if (a.raceDate.isNotBlank() && !isIsoDate(a.raceDate)) {
            issues += QuestionnaireIssue(IssueCode.RACE_DATE_INVALID, IssueSeverity.ERROR)
        }
        if (a.longRunDay.isNotBlank() && a.longRunDay !in a.runDays) {
            issues += QuestionnaireIssue(IssueCode.LONG_RUN_DAY_UNAVAILABLE, IssueSeverity.WARNING)
        }
        if (a.legDay.isNotBlank() && a.legDay !in a.strengthDays) {
            issues += QuestionnaireIssue(IssueCode.LEG_DAY_UNAVAILABLE, IssueSeverity.WARNING)
        }
        if (a.strengthDays.isNotEmpty() && a.locations.isEmpty()) {
            issues += QuestionnaireIssue(IssueCode.NO_LOCATION, IssueSeverity.ERROR)
        }
        if (a.runDays.isNotEmpty() && a.runSurfaces.isEmpty()) {
            issues += QuestionnaireIssue(IssueCode.NO_RUN_SURFACE, IssueSeverity.ERROR)
        }
        if (a.isBeginnerRunner) issues += QuestionnaireIssue(IssueCode.BEGINNER_RUNNER, IssueSeverity.INFO)
        return issues
    }

    fun hasErrors(issues: List<QuestionnaireIssue>) = issues.any { it.severity == IssueSeverity.ERROR }

    fun isIsoDate(text: String): Boolean =
        text.length == 10 && try { LocalDate.parse(text); true } catch (e: DateTimeParseException) { false }
}
