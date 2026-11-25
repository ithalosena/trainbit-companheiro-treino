package com.ithalo.hybridcoach.ui.block

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.exercises.ExerciseCatalog
import com.ithalo.hybridcoach.core.plan.ExtractResult
import com.ithalo.hybridcoach.core.plan.JsonExtractor
import com.ithalo.hybridcoach.core.plan.PlanDocument
import com.ithalo.hybridcoach.core.plan.PlanValidator
import com.ithalo.hybridcoach.core.plan.RunSessionDef
import com.ithalo.hybridcoach.core.plan.StrengthSessionDef
import com.ithalo.hybridcoach.core.prompt.CorrectionRequest
import com.ithalo.hybridcoach.core.exercises.CatalogEntry
import com.ithalo.hybridcoach.core.block.ContinuityReport
import com.ithalo.hybridcoach.core.block.PlanCheck
import com.ithalo.hybridcoach.data.SettingsRepository
import com.ithalo.hybridcoach.HybridCoachApp
import com.ithalo.hybridcoach.ui.common.appContextViewModel
import kotlinx.coroutines.flow.first
import com.ithalo.hybridcoach.core.exercises.Suggestion
import com.ithalo.hybridcoach.ui.retro.RetroFilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.data.Resolution
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.ErrorWindow
import com.ithalo.hybridcoach.ui.retro.HourglassSpinner
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroDialog
import com.ithalo.hybridcoach.ui.retro.RetroDivider
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.RetroTextButton
import com.ithalo.hybridcoach.ui.retro.RetroTextField
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.common.dayLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportPreview(
    val doc: PlanDocument,
    val rawJson: String,
    val warnings: List<String>,
    /** Exercise name as written in the plan -> canonical name, for recognised ones. */
    val matched: Map<String, String>,
    val unrecognized: List<String>,
    /** The 3 closest catalog exercises of each unrecognised name. */
    val suggestions: Map<String, List<Suggestion>> = emptyMap(),
    /** How the plan continues the closed previous block (null when there is none). */
    val continuity: ContinuityReport? = null,
    val resolutions: Map<String, Resolution> = emptyMap(),
) {
    /** Every unrecognised name has a decision (a suggestion, another exercise or a new one with its muscles). */
    val ready: Boolean get() = unrecognized.all { it in resolutions }
}

data class ImportState(
    val text: String = "",
    val errors: List<String> = emptyList(),
    val preview: ImportPreview? = null,
    val catalog: List<CatalogEntry> = emptyList(),
    val muscleGroups: List<String> = emptyList(),
    val saving: Boolean = false,
)

class ImportViewModel(private val repository: PlanRepository, private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state

    fun onText(text: String) = _state.update { it.copy(text = text, errors = emptyList(), preview = null) }

    fun validate() {
        val text = _state.value.text
        viewModelScope.launch {
            when (val extracted = JsonExtractor.extract(text)) {
                is ExtractResult.Failed -> _state.update { it.copy(errors = listOf("json: ${extracted.message}"), preview = null) }
                is ExtractResult.Found -> {
                    val result = PlanValidator.validate(extracted.json)
                    val doc = result.document
                    if (!result.isValid || doc == null) {
                        _state.update { it.copy(errors = result.errors, preview = null) }
                    } else {
                        val catalog = repository.catalog()
                        val defs = doc.weeks.flatMap { it.sessions }.filterIsInstance<StrengthSessionDef>().flatMap { it.exercises + it.homeExercises }
                        val names = defs.map { it.name }.distinct()
                        // Same rule as the import: the id from the prompt's list, then the name (exact, any order, very similar).
                        val matched = names.mapNotNull { n ->
                            val byId = defs.filter { it.name == n }.firstNotNullOfOrNull { d -> d.exerciseId?.let { catalog.byId(it) } }
                            (byId ?: catalog.resolve(n))?.let { n to it.canonicalName }
                        }.toMap()
                        val unrecognized = names.filter { n -> n !in matched }
                        val ids = names.associateWith { n ->
                            defs.filter { it.name == n }.firstNotNullOfOrNull { d -> d.exerciseId?.let { catalog.byId(it) } }?.id ?: catalog.resolve(n)?.id
                        }
                        val continuity = repository.continuityBase()?.let { base ->
                            PlanCheck.check(doc, base, repository.progressionRules(settings.settings.first().progressionRules)) { ids[it] }
                        }
                        _state.update {
                            it.copy(
                                errors = emptyList(),
                                catalog = catalog.entries,
                                muscleGroups = repository.muscleGroups,
                                preview = ImportPreview(
                                    doc, extracted.json.toString(), result.warnings, matched, unrecognized,
                                    suggestions = unrecognized.associateWith { n -> catalog.suggest(n) },
                                    continuity = continuity,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun resolve(name: String, resolution: Resolution) = _state.update { s ->
        s.copy(preview = s.preview?.let { it.copy(resolutions = it.resolutions + (name to resolution)) })
    }

    fun confirm(onDone: () -> Unit) {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.importPlan(preview.doc, preview.rawJson, preview.resolutions)
            _state.update { ImportState() }
            onDone()
        }
    }
}

@Composable
fun ImportScreen(onBack: () -> Unit, onImported: () -> Unit) {
    val vm = appContextViewModel { repo, app -> ImportViewModel(repo, (app as HybridCoachApp).container.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied)
    var mappingFor by remember { mutableStateOf<String?>(null) }
    var creatingFor by remember { mutableStateOf<String?>(null) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val text = runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }.getOrNull()
            if (text != null) vm.onText(text)
        }
    }

    ScreenScaffold(stringResource(R.string.import_program), onBack, snackbar) {
        ScreenColumn {
            val preview = state.preview
            if (preview == null) {
                RetroWindow(stringResource(R.string.import_window), Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_explain), style = Retro.type.body)
                    RetroTextField(
                        value = state.text,
                        onValueChange = vm::onText,
                        label = stringResource(R.string.import_paste_hint),
                        singleLine = false,
                        minLines = 8,
                        maxLines = 14,
                        textStyle = Retro.type.bodySmall,
                        isError = state.errors.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RetroOutlinedButton(onClick = { clipboard.getText()?.text?.let(vm::onText) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.import_paste)) }
                        RetroOutlinedButton(onClick = { openFile.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.weight(1f)) {
                            PixelIcon("folder", size = 16.dp)
                            Text(stringResource(R.string.import_open_file))
                        }
                    }
                    RetroButton(onClick = vm::validate, enabled = state.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        PixelIcon("check", size = 16.dp)
                        Text(stringResource(R.string.import_validate))
                    }
                }

                if (state.errors.isNotEmpty()) {
                    ErrorWindow(
                        title = stringResource(R.string.import_errors, state.errors.size),
                        message = state.errors.joinToString("\n\n"),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            RetroButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(CorrectionRequest.build(state.errors)))
                                    scope.launch { snackbar.showSnackbar(copiedMessage) }
                                },
                            ) { Text(stringResource(R.string.import_copy_correction)) }
                        }
                    }
                }
            } else {
                PreviewContent(
                    preview = preview,
                    catalog = state.catalog,
                    saving = state.saving,
                    onResolve = vm::resolve,
                    onCreate = { creatingFor = it },
                    onMap = { mappingFor = it },
                    onConfirm = { vm.confirm(onImported) },
                    onEdit = { vm.onText(state.text) },
                    onCopyCorrection = { text ->
                        clipboard.setText(AnnotatedString(text))
                        scope.launch { snackbar.showSnackbar(copiedMessage) }
                    },
                )
            }
        }

        mappingFor?.let { name ->
            MappingDialog(
                name = name,
                exercises = state.catalog,
                onPick = { vm.resolve(name, Resolution.MapTo(it)); mappingFor = null },
                onCreateNew = { creatingFor = name; mappingFor = null },
                onDismiss = { mappingFor = null },
            )
        }
        creatingFor?.let { name ->
            CreateExerciseDialog(
                name = name,
                groups = state.muscleGroups,
                onCreate = { muscles -> vm.resolve(name, Resolution.CreateNew(muscles)); creatingFor = null },
                onDismiss = { creatingFor = null },
            )
        }
    }
}

@Composable
private fun PreviewContent(
    preview: ImportPreview,
    catalog: List<CatalogEntry>,
    saving: Boolean,
    onResolve: (String, Resolution) -> Unit,
    onCreate: (String) -> Unit,
    onMap: (String) -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCopyCorrection: (String) -> Unit,
) {
    val c = Retro.colors
    val plan = preview.doc.plan
    Text(plan.name, style = Retro.type.title)
    Text(stringResource(R.string.import_plan_info, plan.startDate.toString(), plan.durationWeeks), style = Retro.type.body, color = c.inkSoft)
    if (plan.goal.isNotBlank()) Text(plan.goal, style = Retro.type.body)
    InfoCard(stringResource(R.string.import_review_warning), isError = true)
    preview.warnings.forEach { InfoCard(it) }
    val names = remember(catalog) { catalog.associate { it.id to it.canonicalName } }
    preview.continuity?.let { ContinuityWindow(it, onCopyCorrection) }
    if (preview.unrecognized.isNotEmpty()) {
        UnrecognizedWindow(preview, names, onResolve, onCreate, onMap)
    }

    preview.doc.weeks.forEach { group ->
        val weeks = group.weeks.sorted().joinToString(", ")
        RetroWindow(
            stringResource(R.string.import_weeks, weeks) +
                (group.focus.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "") +
                if (group.isDeload) " (deload)" else "",
            Modifier.fillMaxWidth(),
        ) {
            group.sessions.forEachIndexed { index, session ->
                if (index > 0) RetroDivider(dashed = true)
                Column(Modifier.padding(bottom = 4.dp)) {
                    val day = session.dayOfWeek.name.take(3).lowercase()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelIcon(if (session is RunSessionDef) "run" else "dumbbell", tint = c.accent2, size = 18.dp)
                        Text("${dayLabel(day)} · ${session.title}", style = Retro.type.heading)
                    }
                    when (session) {
                        is StrengthSessionDef -> (session.exercises + session.homeExercises).forEachIndexed { i, e ->
                            if (i == session.exercises.size) {
                                Text(stringResource(R.string.import_home_version), style = Retro.type.label, color = c.accent2)
                            }
                            val canonical = preview.matched[e.name]
                            val resolution = preview.resolutions[e.name]
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${e.name} — ${e.sets}×${e.reps}", style = Retro.type.body)
                                    when {
                                        canonical != null -> if (canonical != e.name) Text("= $canonical", style = Retro.type.bodySmall, color = c.inkSoft)
                                        resolution != null -> Text(resolutionText(resolution, names), style = Retro.type.bodySmall, color = c.accent2)
                                        else -> Text(stringResource(R.string.import_not_recognized), style = Retro.type.bodySmall, color = c.danger)
                                    }
                                }
                            }
                        }
                        is RunSessionDef -> Text(stringResource(R.string.import_run_blocks, session.blocks.size), style = Retro.type.body)
                    }
                }
            }
        }
    }

    if (!preview.ready) InfoCard(stringResource(R.string.import_resolve_first), isError = true)
    RetroButton(onClick = onConfirm, enabled = !saving && preview.ready, modifier = Modifier.fillMaxWidth()) {
        if (saving) HourglassSpinner(size = 20.dp) else PixelIcon("floppy", size = 16.dp)
        Text(stringResource(R.string.import_confirm))
    }
    RetroOutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_back_to_edit)) }
}

@Composable
private fun MappingDialog(
    name: String,
    exercises: List<CatalogEntry>,
    onPick: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, exercises) {
        val q = ExerciseCatalog.normalize(query)
        exercises.filter { q.isEmpty() || ExerciseCatalog.normalize(it.canonicalName).contains(q) || ExerciseCatalog.normalize(it.nameEn.orEmpty()).contains(q) }
            .sortedBy { it.autoTranslated }
    }
    RetroDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.import_map_title, name),
        text = {
            Column {
                RetroTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.import_map_search),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { exercise ->
                        Text(
                            exercise.canonicalName,
                            Modifier.fillMaxWidth().clickable { onPick(exercise.id) }.padding(vertical = 12.dp),
                            style = Retro.type.body,
                        )
                    }
                }
            }
        },
        confirmButton = { RetroTextButton(onClick = onCreateNew) { Text(stringResource(R.string.import_map_create)) } },
        dismissButton = { RetroTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun resolutionText(r: Resolution, names: Map<String, String>): String = when (r) {
    is Resolution.MapTo -> "→ " + (names[r.exerciseId] ?: r.exerciseId)
    is Resolution.CreateNew -> "+ novo (" + r.muscles.joinToString(", ") + ")"
}

/**
 * The names the app could not match for sure, each with its 3 closest exercises (one tap), "Outro..." (search the
 * whole catalog) or "Criar novo" (the user picks the muscle groups). Import waits until every one is decided.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnrecognizedWindow(
    preview: ImportPreview,
    names: Map<String, String>,
    onResolve: (String, Resolution) -> Unit,
    onCreate: (String) -> Unit,
    onMap: (String) -> Unit,
) {
    val c = Retro.colors
    RetroWindow(stringResource(R.string.import_unrecognized_window, preview.unrecognized.size), Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_unrecognized_help), style = Retro.type.bodySmall, color = c.inkSoft)
        preview.unrecognized.forEachIndexed { i, name ->
            if (i > 0) RetroDivider(dashed = true)
            val chosen = preview.resolutions[name]
            Text(name, style = Retro.type.heading)
            if (chosen != null) Text(resolutionText(chosen, names), style = Retro.type.bodySmall, color = c.accent2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.suggestions[name].orEmpty().forEach { s ->
                    RetroFilterChip(
                        selected = (chosen as? Resolution.MapTo)?.exerciseId == s.entry.id,
                        onClick = { onResolve(name, Resolution.MapTo(s.entry.id)) },
                        label = { Text(s.entry.canonicalName) },
                    )
                }
                RetroTextButton(onClick = { onMap(name) }) { Text(stringResource(R.string.import_other)) }
                RetroTextButton(onClick = { onCreate(name) }) { Text(stringResource(R.string.import_map_create)) }
            }
        }
    }
}

/** A new exercise needs at least one muscle group, so it shows its muscles and counts in the weekly volume. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateExerciseDialog(name: String, groups: List<String>, onCreate: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    RetroDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.import_create_title),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_create_text, name), style = Retro.type.body)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        RetroFilterChip(selected = g in picked, onClick = { picked = if (g in picked) picked - g else picked + g }, label = { Text(g) })
                    }
                }
            }
        },
        confirmButton = {
            RetroButton(onClick = { onCreate(groups.filter { it in picked }) }, enabled = picked.isNotEmpty()) { Text(stringResource(R.string.import_create_confirm)) }
        },
        dismissButton = { RetroTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * T6c: the new plan against the closed previous block. Jumps without a justification are warnings, with a ready
 * message for the AI; justified ones are shown for the record.
 */
@Composable
private fun ContinuityWindow(report: ContinuityReport, onCopy: (String) -> Unit) {
    val c = Retro.colors
    RetroWindow(stringResource(R.string.import_continuity_window), Modifier.fillMaxWidth()) {
        report.mainKeptPct?.let { Text(stringResource(R.string.import_continuity_main, it), style = Retro.type.body) }
        report.daysKeptPct?.let { Text(stringResource(R.string.import_continuity_days, it), style = Retro.type.body) }
        if (report.issues.isEmpty()) {
            Text(stringResource(R.string.import_continuity_ok), style = Retro.type.bodySmall, color = c.success)
        }
        report.issues.forEach { issue ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelIcon(if (issue.justified) "info" else "warning", tint = if (issue.justified) c.inkSoft else c.danger, size = 16.dp)
                Text(issue.text, style = Retro.type.bodySmall, color = if (issue.justified) c.inkSoft else c.ink)
            }
        }
        if (report.warnings.isNotEmpty()) {
            RetroButton(onClick = { onCopy(PlanCheck.correctionRequest(report)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_continuity_fix))
            }
            Text(stringResource(R.string.import_continuity_fix_help), style = Retro.type.label, color = c.inkFaint)
        }
    }
}
