package com.ithalo.hybridcoach.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.questionnaire.COMMON_LIFTS
import com.ithalo.hybridcoach.core.questionnaire.IssueCode
import com.ithalo.hybridcoach.core.questionnaire.IssueSeverity
import com.ithalo.hybridcoach.core.questionnaire.KnownLift
import com.ithalo.hybridcoach.core.questionnaire.Location
import com.ithalo.hybridcoach.core.questionnaire.Priority
import com.ithalo.hybridcoach.core.questionnaire.QuestionnaireAnswers
import com.ithalo.hybridcoach.core.questionnaire.QuestionnaireRules
import com.ithalo.hybridcoach.core.questionnaire.RunExperience
import com.ithalo.hybridcoach.core.questionnaire.RunSurface
import com.ithalo.hybridcoach.core.questionnaire.Split
import com.ithalo.hybridcoach.core.questionnaire.WEEK_DAYS
import com.ithalo.hybridcoach.core.schedule.PlanScheduler.nextMonday
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.ui.common.CheckRow
import com.ithalo.hybridcoach.ui.common.ChoiceChips
import com.ithalo.hybridcoach.ui.retro.DitherCrossfade
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroFilterChip
import com.ithalo.hybridcoach.ui.retro.RetroLinearProgress
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.retro.shake
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.common.DateField
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.IntStepper
import com.ithalo.hybridcoach.ui.common.LabeledTextField
import com.ithalo.hybridcoach.ui.common.MultiChips
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SwitchRow
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.dayLabel
import com.ithalo.hybridcoach.ui.common.issueText
import com.ithalo.hybridcoach.ui.common.locationLabel
import com.ithalo.hybridcoach.ui.common.priorityLabel
import com.ithalo.hybridcoach.ui.common.runExperienceLabel
import com.ithalo.hybridcoach.ui.common.splitLabel
import com.ithalo.hybridcoach.ui.common.surfaceLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class QuestionnaireViewModel(private val repository: PlanRepository) : ViewModel() {
    private val _answers = MutableStateFlow(QuestionnaireAnswers(startDate = nextMonday(LocalDate.now()).toString()))
    val answers: StateFlow<QuestionnaireAnswers> = _answers
    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious: StateFlow<Boolean> = _hasPrevious

    init {
        viewModelScope.launch {
            repository.loadQuestionnaire()?.let { saved ->
                // A saved start date from an old block is stale; suggest the next Monday again.
                val start = saved.startDate.takeIf { it >= LocalDate.now().toString() } ?: nextMonday(LocalDate.now()).toString()
                _answers.value = saved.copy(startDate = start)
            }
            _hasPrevious.value = repository.hasExportedSummary()
        }
    }

    fun update(transform: (QuestionnaireAnswers) -> QuestionnaireAnswers) {
        _answers.value = transform(_answers.value)
    }

    /** Toggles a run or strength day and drops a long run / leg day that is no longer on a matching day. */
    fun toggleDay(day: String, run: Boolean) = update { cur ->
        fun toggle(list: List<String>) = WEEK_DAYS.filter { if (it == day) it !in list else it in list }
        if (run) {
            val days = toggle(cur.runDays)
            cur.copy(runDays = days, longRunDay = cur.longRunDay.takeIf { it in days }.orEmpty())
        } else {
            val days = toggle(cur.strengthDays)
            cur.copy(strengthDays = days, legDay = cur.legDay.takeIf { it in days }.orEmpty())
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveQuestionnaire(_answers.value)
            onDone()
        }
    }
}

/** Which step (0-based) each issue is shown on. */
private fun stepOf(code: IssueCode) = when (code) {
    IssueCode.RACE_DATE_INVALID -> 0
    IssueCode.NO_AVAILABLE_DAYS -> 1
    IssueCode.BEGINNER_RUNNER, IssueCode.LONG_RUN_DAY_UNAVAILABLE, IssueCode.NO_RUN_SURFACE -> 2
    IssueCode.LEG_DAY_UNAVAILABLE -> 3
    IssueCode.NO_LOCATION -> 4
    IssueCode.DURATION_OUT_OF_RANGE, IssueCode.START_DATE_INVALID -> 6
}

private val STEP_TITLES = listOf(
    R.string.q_step_goal, R.string.q_step_week, R.string.q_step_running, R.string.q_step_strength,
    R.string.q_step_location, R.string.q_step_you, R.string.q_step_block, R.string.q_step_previous,
)

@Composable
fun QuestionnaireScreen(onBack: () -> Unit, onFinished: () -> Unit) {
    val vm = appViewModel { QuestionnaireViewModel(it) }
    val a by vm.answers.collectAsStateWithLifecycle()
    val hasPrevious by vm.hasPrevious.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = if (hasPrevious) 7 else 6
    val issues = QuestionnaireRules.check(a)
    val stepIssues = issues.filter { stepOf(it.code) == step }
    val blocked = stepIssues.any { it.severity == IssueSeverity.ERROR }

    ScreenScaffold(stringResource(R.string.q_program), onBack = { if (step > 0) step-- else onBack() }) {
        ScreenColumn {
            RetroWindow(stringResource(R.string.q_install_window), Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelIcon("floppy", Modifier.pixelFloat(), tint = Retro.colors.accent2, size = 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.q_step_of, step + 1, lastStep + 1), style = Retro.type.label, color = Retro.colors.inkSoft)
                        Text(stringResource(STEP_TITLES[step]), style = Retro.type.heading, color = Retro.colors.ink)
                    }
                }
                RetroLinearProgress(progress = { (step + 1) / (lastStep + 1f) }, segments = (lastStep + 1) * 2)
            }

            DitherCrossfade(step, Modifier.fillMaxWidth()) { s ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (s) {
                        0 -> GoalStep(a, vm)
                        1 -> WeekStep(a, vm)
                        2 -> RunningStep(a, vm)
                        3 -> StrengthStep(a, vm)
                        4 -> LocationStep(a, vm)
                        5 -> YouStep(a, vm)
                        6 -> BlockStep(a, vm)
                        else -> PreviousStep(a, vm)
                    }
                }
            }

            val errorCodes = stepIssues.filter { it.severity == IssueSeverity.ERROR }.map { it.code }
            Column(Modifier.shake(errorCodes.takeIf { it.isNotEmpty() }), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stepIssues.forEach { InfoCard(issueText(it), isError = it.severity == IssueSeverity.ERROR) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroOutlinedButton(onClick = { if (step > 0) step-- else onBack() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (step > 0) R.string.q_previous else R.string.action_cancel))
                }
                if (step < lastStep) {
                    RetroButton(onClick = { step++ }, enabled = !blocked, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.q_next)) }
                } else {
                    RetroButton(
                        onClick = { vm.save(onFinished) },
                        enabled = !QuestionnaireRules.hasErrors(issues),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.q_generate_prompt)) }
                }
            }
        }
    }
}

@Composable
private fun GoalStep(a: QuestionnaireAnswers, vm: QuestionnaireViewModel) {
    Text(stringResource(R.string.q_priority))
    ChoiceChips(Priority.entries, a.priority, { priorityLabel(it) }) { p -> vm.update { it.copy(priority = p) } }
    LabeledTextField(stringResource(R.string.q_goal_text), a.goalText, { v -> vm.update { it.copy(goalText = v) } }, supporting = stringResource(R.string.q_goal_text_hint))

    var hasRace by rememberSaveable { mutableStateOf(a.raceName.isNotBlank() || a.raceDate.isNotBlank()) }
    SwitchRow(stringResource(R.string.q_has_race), hasRace) { on ->
        hasRace = on
        if (!on) vm.update { it.copy(raceName = "", raceDate = "") }
    }
    HelpText(stringResource(R.string.q_has_race_help))
    if (hasRace) {
        LabeledTextField(stringResource(R.string.q_race_name), a.raceName, { v -> vm.update { it.copy(raceName = v) } }, supporting = stringResource(R.string.q_race_name_hint))
        DateField(stringResource(R.string.q_race_date), a.raceDate, { v -> vm.update { it.copy(raceDate = v) } }, onClear = { vm.update { it.copy(raceDate = "") } })
    }
}

@Composable
private fun WeekStep(a: QuestionnaireAnswers, vm: QuestionnaireViewModel) {
    HelpText(stringResource(R.string.q_week_help))
    WEEK_DAYS.forEach { day ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dayLabel(day), Modifier.weight(1f), style = Retro.type.heading)
            RetroFilterChip(selected = day in a.runDays, onClick = { vm.toggleDay(day, run = true) }, label = { Text(stringResource(R.string.type_run)) })
            RetroFilterChip(selected = day in a.strengthDays, onClick = { vm.toggleDay(day, run = false) }, label = { Text(stringResource(R.string.type_strength)) })
        }
    }
    InfoCard(
        stringResource(R.string.q_week_summary, a.runDays.size, a.strengthDays.size, a.sameDays.size, WEEK_DAYS.size - a.availableDays.size),
    )
    if (a.runDays.isNotEmpty()) {
        IntStepper(stringResource(R.string.q_run_minutes), a.runMinutes, 15..180, step = 5, suffix = " min") { v -> vm.update { it.copy(runMinutes = v) } }
    }
    if (a.strengthDays.isNotEmpty()) {
        IntStepper(stringResource(R.string.q_strength_minutes), a.strengthMinutes, 20..180, step = 5, suffix = " min") { v -> vm.update { it.copy(strengthMinutes = v) } }
    }
}

@Composable
private fun RunningStep(a: QuestionnaireAnswers, vm: QuestionnaireViewModel) {
    if (a.runDays.isEmpty()) {
        InfoCard(stringResource(R.string.q_no_run_days))
        return
    }
    Text(stringResource(R.string.q_run_experience))
    ChoiceChips(RunExperience.entries, a.runExperience, { runExperienceLabel(it) }) { e ->
        vm.update { it.copy(runExperience = e, longRunDay = if (e == RunExperience.REGULAR) it.longRunDay else "") }
    }
    HelpText(
        stringResource(
            when (a.runExperience) {
                RunExperience.NEVER -> R.string.run_exp_never_help
                RunExperience.WALK_RUN -> R.string.run_exp_walk_run_help
                RunExperience.REGULAR -> R.string.run_exp_regular_help
            },
        ),
    )
    if (a.runExperience == RunExperience.REGULAR) {
        IntStepper(stringResource(R.string.q_weekly_km), a.runWeeklyKm, 0..200, step = 5, suffix = " km") { v -> vm.update { it.copy(runWeeklyKm = v) } }
        IntStepper(stringResource(R.string.q_longest_run), a.longestRunKm.toInt(), 0..60, suffix = " km") { v -> vm.update { it.copy(longestRunKm = v.toDouble()) } }
        Text(stringResource(R.string.q_recent_race))
        ChoiceChips(listOf(0, 5, 10), a.recentRaceKm, { if (it == 0) stringResource(R.string.q_none) else "$it km" }) { v -> vm.update { it.copy(recentRaceKm = v) } }
        if (a.recentRaceKm > 0) {
            LabeledTextField(stringResource(R.string.q_recent_time), a.recentRaceTime, { v -> vm.update { it.copy(recentRaceTime = v) } }, supporting = stringResource(R.string.q_recent_time_hint))
        }
        Text(stringResource(R.string.q_long_run_day))
        ChoiceChips(listOf("") + a.runDays, a.longRunDay, { if (it.isEmpty()) stringResource(R.string.q_ai_decides) else dayLabel(it) }) { d -> vm.update { it.copy(longRunDay = d) } }
        HelpText(stringResource(R.string.q_long_run_help))
    }
    Text(stringResource(R.string.q_surface))
    MultiChips(RunSurface.entries, a.runSurfaces.toSet(), { surfaceLabel(it) }) { s ->
        vm.update { cur -> cur.copy(runSurfaces = RunSurface.entries.filter { if (it == s) it !in cur.runSurfaces else it in cur.runSurfaces }) }
    }
}

@Composable
private fun StrengthStep(a: QuestionnaireAnswers, vm: QuestionnaireViewModel) {
    if (a.strengthDays.isEmpty()) {
        InfoCard(stringResource(R.string.q_no_strength_days))
        return
    }
    LabeledTextField(stringResource(R.string.q_strength_experience), a.strengthExperience, { v -> vm.update { it.copy(strengthExperience = v) } }, supporting = stringResource(R.string.q_strength_experience_hint))
    Text(stringResource(R.string.q_split))
    ChoiceChips(Split.entries, a.split, { splitLabel(it) }) { s -> vm.update { it.copy(split = s) } }
    Text(stringResource(R.string.q_leg_day))
    ChoiceChips(listOf("") + a.strengthDays, a.legDay, { if (it.isEmpty()) stringResource(R.string.q_ai_decides) else dayLabel(it) }) { d -> vm.update { it.copy(legDay = d) } }

    Text(stringResource(R.string.q_known_lifts))
    HelpText(stringResource(R.string.q_known_lifts_help))
    COMMON_LIFTS.forEach { name ->
        val lift = a.knownLifts.firstOrNull { it.name == name }
        LiftRow(name, lift) { updated ->
            vm.update { cur ->
                val others = cur.knownLifts.filter { it.name != name }
                // Keep the checklist order in the prompt.
                val all = if (updated == null) others else others + updated
                cur.copy(knownLifts = all.sortedBy { COMMON_LIFTS.indexOf(it.name) })
            }
        }
    }
    LabeledTextField(stringResource(R.string.q_known_loads), a.knownLoads, { v -> vm.update { it.copy(knownLoads = v) } }, singleLine = false, supporting = stringResource(R.string.q_known_loads_hint))
}

@Composable
private fun LiftRow(name: String, lift: KnownLift?, onChange: (KnownLift?) -> Unit) {
    Column {
        CheckRow(name, lift != null) { onChange(if (it) KnownLift(name) else null) }
        if (lift != null) {
            Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledTextField(stringResource(R.string.q_lift_load), lift.loadKg, { onChange(lift.copy(loadKg = it)) }, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                LabeledTextField(stringResource(R.string.q_lift_reps), lift.reps, { onChange(lift.copy(reps = it)) }, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LocationStep(a: QuestionnaireAnswers, vm: QuestionnaireViewModel) {
    if (a.strengthDays.isNotEmpty()) {
        Text(stringResource(R.string.q_locations))
        MultiChips(Location.entries, a.locations.toSet(), { locationLabel(it) }) { l ->
            vm.update { cur -> cur.copy(locations = Location.entries.filter { if (it == l) it !in cur.locations else it in cur.locations }) }
        }
        if (a.canHaveHomeVersion) {
            SwitchRow(stringResource(R.string.q_home_version), a.wantsHomeVersion) { v -> vm.update { it.copy(wantsHomeVersion = v) } }
            HelpText(stringResource(R.string.q_home_version_help))
        } else {
            HelpText(stringResource(R.string.q_home_version_hint))
        }
    }
    SwitchRow(stringResource(R.string.q_bike), a.hasBike) { v -> vm.update { it.copy(hasBike = v) } }
    HelpText(stringResource(R.string.q_bike_help))
}

@Composable
private fun YouStep(a: QuestionnaireAnswers, vm: QuestionnaireViewModel) {
    InfoCard(stringResource(R.string.q_limitations_privacy))
    Text(stringResource(R.string.q_about_you))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledTextField(stringResource(R.string.q_age), a.age, { v -> vm.update { it.copy(age = v) } }, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
        LabeledTextField(stringResource(R.string.q_weight), a.weightKg, { v -> vm.update { it.copy(weightKg = v) } }, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
        LabeledTextField(stringResource(R.string.q_height), a.heightCm, { v -> vm.update { it.copy(heightCm = v) } }, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
    }
    LabeledTextField(stringResource(R.string.q_limitations), a.limitations, { v -> vm.update { it.copy(limitations = v) } }, singleLine = false, supporting = stringResource(R.string.q_limitations_hint))
    LabeledTextField(stringResource(R.string.q_avoid), a.avoidExercises, { v -> vm.update { it.copy(avoidExercises = v) } }, singleLine = false, supporting = stringResource(R.string.q_avoid_hint))
    Text(stringResource(R.string.q_sleep))
    ChoiceChips((1..5).toList(), a.sleep, { it.toString() }) { v -> vm.update { it.copy(sleep = v) } }
    HelpText(stringResource(R.string.q_sleep_help))
    Text(stringResource(R.string.q_stress))
    ChoiceChips((1..5).toList(), a.stress, { it.toString() }) { v -> vm.update { it.copy(stress = v) } }
    HelpText(stringResource(R.string.q_stress_help))
}

@Composable
private fun BlockStep(a: QuestionnaireAnswers, vm: QuestionnaireViewModel) {
    IntStepper(stringResource(R.string.q_duration), a.durationWeeks, 4..12, suffix = " sem") { v -> vm.update { it.copy(durationWeeks = v) } }
    HelpText(stringResource(R.string.q_duration_help))
    DateField(stringResource(R.string.q_start_date), a.startDate, { v -> vm.update { it.copy(startDate = v) } }, supporting = stringResource(R.string.q_start_date_help))
    SwitchRow(stringResource(R.string.q_include_deload), a.includeDeload) { v -> vm.update { it.copy(includeDeload = v) } }
    HelpText(stringResource(R.string.q_deload_help))
}

@Composable
private fun PreviousStep(a: QuestionnaireAnswers, vm: QuestionnaireViewModel) {
    Text(stringResource(R.string.q_previous_summary_explain))
    SwitchRow(stringResource(R.string.q_include_previous), a.includePreviousSummary) { v -> vm.update { it.copy(includePreviousSummary = v) } }
}
