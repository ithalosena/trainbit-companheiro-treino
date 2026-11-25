package com.ithalo.hybridcoach.ui.block

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.HybridCoachApp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.block.BlockPhase
import com.ithalo.hybridcoach.core.block.BlockSnapshot
import com.ithalo.hybridcoach.core.block.CloseAnswers
import com.ithalo.hybridcoach.core.block.NextBlockPrompt
import com.ithalo.hybridcoach.core.block.Progression
import com.ithalo.hybridcoach.core.summary.BlockSummary
import com.ithalo.hybridcoach.data.PlanEntity
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.data.SettingsRepository
import com.ithalo.hybridcoach.ui.common.ChoiceChips
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.LabeledTextField
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.common.appContextViewModel
import com.ithalo.hybridcoach.ui.retro.HourglassSpinner
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroLoading
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CloseBlockState(
    val loading: Boolean = true,
    val plan: PlanEntity? = null,
    val summary: BlockSummary? = null,
    val incomplete: Int = 0,
    val snapshot: BlockSnapshot? = null,
    val closing: Boolean = false,
)

class CloseBlockViewModel(private val repository: PlanRepository, private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(CloseBlockState())
    val state: StateFlow<CloseBlockState> = _state

    init {
        viewModelScope.launch {
            val plan = repository.activePlan() ?: repository.observeLatestPlan().first()
            _state.value = if (plan == null) {
                CloseBlockState(loading = false)
            } else {
                CloseBlockState(
                    loading = false,
                    plan = plan,
                    summary = repository.buildSummary(plan),
                    incomplete = repository.incompleteSessions(plan),
                    snapshot = repository.snapshot(plan.id),
                )
            }
        }
    }

    fun close(answers: CloseAnswers) {
        val plan = _state.value.plan ?: return
        _state.value = _state.value.copy(closing = true)
        viewModelScope.launch {
            val rules = repository.progressionRules(settings.settings.first().progressionRules)
            val snapshot = repository.closeBlock(plan, answers.copy(incompleteSessions = _state.value.incomplete), rules)
            _state.value = _state.value.copy(closing = false, snapshot = snapshot)
        }
    }
}

/**
 * "Fechar bloco": the block's results, a short questionnaire (fatigue, motivation, new pains, what to change,
 * availability) and what to do with the sessions left. Closing freezes the snapshot the next block starts from
 * and shows the suggested start. A closed block only shows its frozen snapshot.
 */
@Composable
fun CloseBlockScreen(onBack: () -> Unit, onQuestionnaire: () -> Unit) {
    val vm = appContextViewModel { repo, app: Application -> CloseBlockViewModel(repo, (app as HybridCoachApp).container.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.close_title), onBack) {
        if (state.loading) {
            RetroLoading()
            return@ScreenScaffold
        }
        val plan = state.plan
        ScreenColumn {
            if (plan == null) {
                InfoCard(stringResource(R.string.close_no_plan))
                return@ScreenColumn
            }
            Text(stringResource(R.string.close_block_name, plan.number, plan.name), style = Retro.type.title)
            state.summary?.let { BlockResults(it, plan) }
            val snapshot = state.snapshot
            if (snapshot == null) {
                CloseForm(state.incomplete, state.closing, vm::close)
            } else {
                SuggestedStartWindow(snapshot)
                RetroButton(onClick = onQuestionnaire, modifier = Modifier.fillMaxWidth()) {
                    PixelIcon("list", size = 16.dp)
                    Text(stringResource(R.string.close_next))
                }
                HelpText(stringResource(R.string.close_next_help))
            }
        }
    }
}

@Composable
private fun BlockResults(summary: BlockSummary, plan: PlanEntity) {
    val c = Retro.colors
    val game by appContainer().gameState.collectAsStateWithLifecycle()
    val start = LocalDate.parse(plan.startDate)
    val records = game?.xpLog?.count { it.reason.startsWith("Recorde") && !it.date.isBefore(start) } ?: 0
    val a = summary.adherence
    val failed = summary.strength.sumOf { it.done.failedSets }
    val rpes = summary.strength.mapNotNull { it.done.avgRpe }
    @Composable
    fun line(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Retro.type.label, color = c.inkSoft)
            Text(value, style = Retro.type.body, color = c.ink, textAlign = TextAlign.End)
        }
    }
    RetroWindow(stringResource(R.string.close_results), Modifier.fillMaxWidth()) {
        line(stringResource(R.string.close_adherence), "${a.done}/${a.plannedSessions}" + if (a.plannedSessions > 0) " (${a.done * 100 / a.plannedSessions}%)" else "")
        if (a.extrasPlanned > 0) line(stringResource(R.string.close_extras), "${a.extrasDone}/${a.extrasPlanned}")
        line(stringResource(R.string.close_records), records.toString())
        line(stringResource(R.string.close_failed), failed.toString())
        line(stringResource(R.string.close_rpe), if (rpes.isEmpty()) "—" else Progression.fmt((rpes.average() * 10).toInt() / 10.0))
        line(stringResource(R.string.close_skipped), a.skipped.toString())
        if (a.skipReasons.isNotEmpty()) Text(a.skipReasons.joinToString(" · "), style = Retro.type.bodySmall, color = c.inkSoft)
    }
}

@Composable
private fun CloseForm(incomplete: Int, closing: Boolean, onClose: (CloseAnswers) -> Unit) {
    var fatigue by rememberSaveable { mutableIntStateOf(3) }
    var motivation by rememberSaveable { mutableIntStateOf(3) }
    var limitations by rememberSaveable { mutableStateOf("") }
    var change by rememberSaveable { mutableStateOf("") }
    var availability by rememberSaveable { mutableStateOf("") }
    var carry by rememberSaveable { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.close_questions))
    Text(stringResource(R.string.close_fatigue), style = Retro.type.body)
    ChoiceChips((1..5).toList(), fatigue, { it.toString() }) { fatigue = it }
    Text(stringResource(R.string.close_motivation), style = Retro.type.body)
    ChoiceChips((1..5).toList(), motivation, { it.toString() }) { motivation = it }
    LabeledTextField(stringResource(R.string.close_limitations), limitations, { limitations = it }, singleLine = false, supporting = stringResource(R.string.close_limitations_hint))
    LabeledTextField(stringResource(R.string.close_change), change, { change = it }, singleLine = false)
    LabeledTextField(stringResource(R.string.close_availability), availability, { availability = it }, singleLine = false, supporting = stringResource(R.string.close_availability_hint))
    if (incomplete > 0) {
        Text(stringResource(R.string.close_incomplete, incomplete), style = Retro.type.body)
        ChoiceChips(listOf(false, true), carry, { stringResource(if (it) R.string.close_carry else R.string.close_drop) }) { carry = it }
    }
    InfoCard(stringResource(R.string.close_warning))
    RetroButton(
        onClick = { onClose(CloseAnswers(fatigue, motivation, limitations.trim(), change.trim(), availability.trim(), carry)) },
        enabled = !closing,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        if (closing) HourglassSpinner(size = 20.dp) else PixelIcon("floppy", size = 16.dp)
        Text(stringResource(R.string.close_confirm))
    }
}

/** What the next block starts from: loads, running and the phase, with the rule behind each number. */
@Composable
private fun SuggestedStartWindow(s: BlockSnapshot) {
    val c = Retro.colors
    val start = s.suggestedStart
    RetroWindow(stringResource(R.string.close_suggested), Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.close_closed_on, s.closedAt), style = Retro.type.bodySmall, color = c.inkSoft)
        if (start.consolidation) InfoCard(stringResource(R.string.close_consolidation))
        Text(stringResource(R.string.close_phase, BlockPhase.of(start.phase)?.label ?: start.phase), style = Retro.type.heading)
        start.loads.forEach { l ->
            Text("${l.name}: ${Progression.fmt(l.loadKg)} kg × ${l.reps}", style = Retro.type.body, color = c.ink)
            Text(l.rule, style = Retro.type.bodySmall, color = c.inkSoft)
        }
        start.run?.let { r ->
            Text(
                stringResource(R.string.close_run, Progression.fmt(r.week1Km), Progression.fmt(r.longRunKm)) +
                    (r.easyPaceMin?.let { lo -> r.easyPaceMax?.let { hi -> " · " + stringResource(R.string.close_run_easy, NextBlockPrompt.pace(lo), NextBlockPrompt.pace(hi)) } } ?: ""),
                style = Retro.type.body,
            )
        }
    }
}
