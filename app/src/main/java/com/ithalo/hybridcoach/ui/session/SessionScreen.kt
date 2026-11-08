package com.ithalo.hybridcoach.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.ui.common.StatusBadge
import com.ithalo.hybridcoach.ui.common.typeIcon
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroDialog
import com.ithalo.hybridcoach.ui.retro.RetroDivider
import com.ithalo.hybridcoach.ui.retro.RetroIconKind
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.RetroTextButton
import com.ithalo.hybridcoach.ui.retro.RetroTextField
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.Retro
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.data.PlannedExerciseWithName
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.data.RunBlockEntity
import com.ithalo.hybridcoach.data.SessionFeedbackEntity
import com.ithalo.hybridcoach.data.SetLogRow
import com.ithalo.hybridcoach.ui.player.formatKg
import com.ithalo.hybridcoach.data.VARIANT_GYM
import com.ithalo.hybridcoach.data.VARIANT_HOME
import com.ithalo.hybridcoach.ui.common.ChoiceChips
import com.ithalo.hybridcoach.ui.common.variantLabel
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.setLine
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.long
import com.ithalo.hybridcoach.ui.common.runBlockLine
import com.ithalo.hybridcoach.ui.common.typeLabel
import com.ithalo.hybridcoach.data.RunLogEntity
import com.ithalo.hybridcoach.data.RunSplitEntity
import com.ithalo.hybridcoach.ui.run.RunSummaryLines
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SessionViewModel(private val repository: PlanRepository, private val id: Long) : ViewModel() {
    val session: StateFlow<PlannedSessionEntity?> = repository.observeSession(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val exercises: StateFlow<List<PlannedExerciseWithName>> = repository.observeSessionExercises(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val blocks: StateFlow<List<RunBlockEntity>> = repository.observeRunBlocks(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val setLogs: StateFlow<List<SetLogRow>> = repository.observeSetLogRows(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val feedback: StateFlow<SessionFeedbackEntity?> = repository.observeFeedback(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasHomeVersion: StateFlow<Boolean> = repository.observeHasHomeVersion(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val runLog: StateFlow<RunLogEntity?> = repository.observeRunLog(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val runSplits: StateFlow<List<RunSplitEntity>> = repository.observeRunLog(id)
        .flatMapLatest { log -> if (log == null) flowOf(emptyList()) else repository.observeRunSplits(log.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setVariant(variant: String) = viewModelScope.launch { repository.setVariant(id, variant) }
    fun reschedule(date: LocalDate) = viewModelScope.launch { repository.reschedule(id, date) }
    fun skip(reason: String) = viewModelScope.launch { repository.skip(id, reason) }
    fun markDone() = viewModelScope.launch { repository.markDone(id) }
    fun markPending() = viewModelScope.launch { repository.markPending(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(sessionId: Long, onBack: () -> Unit, onStart: (Long, String) -> Unit) {
    val vm = appViewModel { SessionViewModel(it, sessionId) }
    val session by vm.session.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val setLogs by vm.setLogs.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val hasHomeVersion by vm.hasHomeVersion.collectAsStateWithLifecycle()
    val runLog by vm.runLog.collectAsStateWithLifecycle()
    val runSplits by vm.runSplits.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showSkip by remember { mutableStateOf(false) }

    ScreenScaffold(stringResource(R.string.session_program), onBack) {
        val s = session ?: return@ScreenScaffold
        val c = Retro.colors
        ScreenColumn {
            RetroWindow(typeLabel(s.type), Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(52.dp).bevel(c.windowInset, c.border, c.bevelLight, c.bevelDark, raised = false), contentAlignment = Alignment.Center) {
                        PixelIcon(typeIcon(s.type), Modifier.pixelFloat(), tint = c.accent2, size = 32.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = Retro.type.title)
                        val details = buildList {
                            add(LocalDate.parse(s.date).long())
                            add(stringResource(R.string.session_week, s.weekNumber))
                            s.estimatedMinutes?.let { add("$it min") }
                        }
                        Text(details.joinToString(" · "), style = Retro.type.bodySmall, color = c.inkSoft)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(s.status)
                    s.skipReason?.takeIf { it.isNotBlank() }?.let { Text(it, style = Retro.type.body, color = c.inkSoft) }
                }
            }

            if (s.type == "strength") {
                if (hasHomeVersion) {
                    SectionTitle(stringResource(R.string.session_where))
                    ChoiceChips(listOf(VARIANT_GYM, VARIANT_HOME), s.variant, { variantLabel(it) }) { v ->
                        if (setLogs.isEmpty()) vm.setVariant(v)
                    }
                    if (setLogs.isNotEmpty()) {
                        HelpText(stringResource(R.string.session_where_locked))
                    }
                }
                if (s.status != "skipped") {
                    RetroButton(onClick = { onStart(s.id, s.type) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        PixelIcon("play", size = 16.dp)
                        Text(stringResource(if (setLogs.isEmpty()) R.string.today_start else R.string.session_resume))
                    }
                }
                RetroWindow(stringResource(R.string.session_window_exercises), Modifier.fillMaxWidth()) {
                    exercises.forEachIndexed { i, item ->
                        if (i > 0) RetroDivider(dashed = true)
                        ExerciseRow(item)
                    }
                }
                if (setLogs.isNotEmpty()) {
                    RetroWindow(stringResource(R.string.session_window_sets), Modifier.fillMaxWidth()) {
                        setLogs.groupBy { it.exerciseName }.forEach { (name, rows) ->
                            Text(name, style = Retro.type.heading)
                            rows.forEach { row -> Text(setLine(row.log, row.plannedReps), style = Retro.type.body) }
                        }
                    }
                }
                feedback?.let {
                    Text(stringResource(R.string.session_overall_rpe, it.rpeOverall), style = Retro.type.heading)
                    if (it.note.isNotBlank()) Text(it.note, style = Retro.type.body)
                }
            } else {
                val log = runLog
                if (s.status != "skipped" && log == null) {
                    RetroButton(onClick = { onStart(s.id, s.type) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        PixelIcon("run", size = 16.dp)
                        Text(stringResource(R.string.run_open))
                    }
                }
                RetroWindow(stringResource(R.string.session_window_blocks), Modifier.fillMaxWidth()) {
                    blocks.forEach { Text(runBlockLine(it), style = Retro.type.body) }
                }
                if (log != null) {
                    RetroWindow(stringResource(R.string.session_window_run), Modifier.fillMaxWidth()) {
                        RunSummaryLines(log.distanceM, log.durationSeconds, log.avgPaceSecPerKm, runSplits.map { it.seconds })
                        feedback?.let {
                            Text(stringResource(R.string.session_overall_rpe, it.rpeOverall), style = Retro.type.heading)
                            if (it.note.isNotBlank()) Text(it.note, style = Retro.type.body)
                        }
                    }
                }
            }
            RetroDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RetroOutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    PixelIcon("calendar", size = 16.dp)
                    Text(stringResource(R.string.session_reschedule))
                }
                if (s.status == "skipped") {
                    RetroOutlinedButton(onClick = { vm.markPending() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.session_undo)) }
                } else {
                    RetroOutlinedButton(onClick = { showSkip = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.session_skip)) }
                }
            }
            when (s.status) {
                "done" -> RetroOutlinedButton(onClick = { vm.markPending() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.session_undo)) }
                else -> RetroButton(onClick = { vm.markDone() }, modifier = Modifier.fillMaxWidth()) {
                    PixelIcon("check", size = 16.dp)
                    Text(stringResource(R.string.session_mark_done))
                }
            }
        }

        if (showDatePicker) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = LocalDate.parse(s.date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    RetroTextButton(onClick = {
                        pickerState.selectedDateMillis?.let { vm.reschedule(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        showDatePicker = false
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = { RetroTextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
            ) { DatePicker(state = pickerState) }
        }

        if (showSkip) {
            var reason by remember { mutableStateOf("") }
            RetroDialog(
                onDismissRequest = { showSkip = false },
                title = stringResource(R.string.session_skip_title),
                icon = RetroIconKind.QUESTION,
                text = {
                    RetroTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = stringResource(R.string.session_skip_reason),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    RetroButton(onClick = { vm.skip(reason); showSkip = false }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = { RetroOutlinedButton(onClick = { showSkip = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun ExerciseRow(item: PlannedExerciseWithName) {
    val e = item.exercise
    Column {
        Text(item.exerciseName, style = Retro.type.heading)
        val load = e.loadKg?.let { if (it == it.toLong().toDouble()) "${it.toLong()} kg" else "$it kg" } ?: stringResource(R.string.session_load_free)
        val parts = buildList {
            add("${e.sets} × ${e.reps}")
            add(load)
            add(stringResource(R.string.session_rest, e.restSeconds))
            e.rpeTarget?.let { add("esforço $it") }
        }
        Text(parts.joinToString(" · "), style = Retro.type.body)
        if (e.notes.isNotBlank()) Text(e.notes, style = Retro.type.bodySmall, color = Retro.colors.inkSoft)
    }
}
