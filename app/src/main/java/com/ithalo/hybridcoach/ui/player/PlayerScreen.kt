package com.ithalo.hybridcoach.ui.player

import android.Manifest
import com.ithalo.hybridcoach.media.MediaBar
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.exercises.ExerciseCatalog
import com.ithalo.hybridcoach.core.game.GameEngine
import com.ithalo.hybridcoach.core.strength.SummaryExercise
import com.ithalo.hybridcoach.core.strength.SummarySet
import com.ithalo.hybridcoach.core.strength.TimedTarget
import com.ithalo.hybridcoach.core.strength.WorkoutSummaries
import com.ithalo.hybridcoach.core.strength.WorkoutSummary
import com.ithalo.hybridcoach.ui.common.setLine
import com.ithalo.hybridcoach.data.ExerciseEntity
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.LabeledTextField
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.SwitchRow
import com.ithalo.hybridcoach.ui.common.appContextViewModel
import com.ithalo.hybridcoach.ui.retro.CountUpText
import com.ithalo.hybridcoach.ui.retro.HeartMeter
import com.ithalo.hybridcoach.ui.retro.HourglassSpinner
import com.ithalo.hybridcoach.ui.retro.PixelConfetti
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroDialog
import com.ithalo.hybridcoach.ui.retro.RetroDivider
import com.ithalo.hybridcoach.ui.retro.RetroFilterChip
import com.ithalo.hybridcoach.ui.retro.RetroIconButton
import com.ithalo.hybridcoach.ui.retro.RetroIconKind
import com.ithalo.hybridcoach.ui.retro.RetroLinearProgress
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.RetroTextButton
import com.ithalo.hybridcoach.ui.retro.RetroTextField
import com.ithalo.hybridcoach.ui.retro.RetroTopBarAction
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.PixelSprite
import com.ithalo.hybridcoach.ui.retro.RetroLoading
import com.ithalo.hybridcoach.ui.retro.RetroRewardButton
import com.ithalo.hybridcoach.ui.retro.RetroTitleStrip
import com.ithalo.hybridcoach.ui.retro.pixelSprite
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.common.PT_BR
import androidx.compose.foundation.layout.BoxWithConstraints

@Composable
fun PlayerScreen(sessionId: Long, onBack: () -> Unit) {
    val vm = appContextViewModel { repository, app -> PlayerViewModel(repository, Speaker(app), sessionId, RestNotifier(app)) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    // The rest countdown lives in a notification; ask once (Android 13+) so it can show.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!RestNotifier.canNotify(context)) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var confetti by remember { mutableIntStateOf(0) }

    ScreenScaffold(
        ui.session?.title ?: stringResource(R.string.player_title),
        onBack,
        actions = {
            RetroTopBarAction(
                "speaker",
                stringResource(if (ui.voiceOn) R.string.player_voice_on else R.string.player_voice_off),
                onClick = { vm.toggleVoice(!ui.voiceOn) },
                tint = if (ui.voiceOn) Retro.colors.ink else Retro.colors.inkFaint,
            )
            if (ui.current != null) {
                RetroTopBarAction("dots", stringResource(R.string.player_more), onClick = { dialog = PlayerDialog.More })
            }
        },
    ) {
        if (!ui.loaded) {
            RetroLoading()
            return@ScreenScaffold
        }
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        ScreenColumn {
            if (!ui.voiceAvailable && ui.voiceOn) InfoCard(stringResource(R.string.player_voice_missing))

            val current = ui.current
            if (current == null) {
                val alreadyDone = ui.session?.status == "done"
                LaunchedEffect(Unit) { if (!alreadyDone) confetti++ }
                val records by vm.sessionRecords.collectAsStateWithLifecycle()
                val previousVolume by vm.previousVolumeKg.collectAsStateWithLifecycle()
                val summary = remember(ui.exercises, records, previousVolume) { workoutSummary(ui.exercises, records, previousVolume) }
                FinishPanel(
                    alreadyDone = alreadyDone,
                    summary = summary,
                    setsLogged = ui.exercises.sumOf { it.logs.size },
                    onFinish = { rpe, note -> vm.finish(rpe, note, onBack) },
                    onExtraSet = vm::addExtraSet,
                    onUndo = vm::undoLast,
                )
            } else {
                ExerciseHeader(
                    ui, current, onGoTo = vm::goTo,
                    onRestMinus = { vm.adjustRest(-15) }, onRestPlus = { vm.adjustRest(15) }, onRestSkip = vm::skipRest,
                )
                ui.newRecord?.let { rec ->
                    LaunchedEffect(rec.serial) { confetti++ }
                    NewRecordWindow(rec, onDismiss = vm::dismissRecord)
                }
                SetInputs(ui, vm)
                LoggedSets(current)
                NextExercise(ui, onGoTo = vm::goTo)
            }
        }
        }
        // The log button stays on screen: it is what is tapped after every set.
        if (ui.current != null) LogBar(ui, vm)
        }
        PixelConfetti(confetti, Modifier.fillMaxSize(), particleCount = 90)

        when (dialog) {
            PlayerDialog.More -> MoreDialog(
                canUndo = ui.exercises.any { it.logs.isNotEmpty() },
                onSubstitute = { dialog = PlayerDialog.Substitute },
                onSkip = { dialog = PlayerDialog.Skip },
                onExtraSet = { vm.addExtraSet(); dialog = null },
                onUndo = { vm.undoLast(); dialog = null },
                onDismiss = { dialog = null },
            )
            PlayerDialog.Skip -> ReasonDialog(
                title = stringResource(R.string.player_skip_title),
                onConfirm = { vm.skipExercise(it); dialog = null },
                onDismiss = { dialog = null },
            )
            PlayerDialog.Substitute -> SubstituteDialog(
                exercises = ui.catalog,
                onConfirm = { id, reason -> vm.substitute(id, reason); dialog = null },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}

private enum class PlayerDialog { More, Skip, Substitute }

/** "⋯" menu of the player: the occasional exercise actions, each with a one-line explanation. */
@Composable
private fun MoreDialog(
    canUndo: Boolean,
    onSubstitute: () -> Unit,
    onSkip: () -> Unit,
    onExtraSet: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    RetroDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_more),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MenuItem("list", stringResource(R.string.player_substitute), stringResource(R.string.player_more_substitute_desc), onClick = onSubstitute)
                RetroDivider(dashed = true)
                MenuItem("arrow_right", stringResource(R.string.player_skip), stringResource(R.string.player_more_skip_desc), onClick = onSkip)
                RetroDivider(dashed = true)
                MenuItem("plus", stringResource(R.string.player_extra_set), stringResource(R.string.player_more_extra_desc), onClick = onExtraSet)
                RetroDivider(dashed = true)
                MenuItem("x", stringResource(R.string.player_undo), stringResource(R.string.player_more_undo_desc), enabled = canUndo, onClick = onUndo)
            }
        },
        confirmButton = { RetroOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Plain tappable menu row: icon, title and a short description; no frame. */
@Composable
private fun MenuItem(icon: String, title: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Retro.colors
    val ink = if (enabled) c.ink else c.disabled
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PixelIcon(icon, tint = if (enabled) c.inkSoft else c.disabled, size = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = Retro.type.heading, color = ink)
            Text(description, style = Retro.type.bodySmall, color = if (enabled) c.inkSoft else c.disabled)
        }
    }
}

@Composable
private fun ExerciseHeader(
    ui: PlayerUi,
    current: PlayerExercise,
    onGoTo: (Int) -> Unit,
    onRestMinus: () -> Unit,
    onRestPlus: () -> Unit,
    onRestSkip: () -> Unit,
) {
    val c = Retro.colors
    val index = ui.currentIndex ?: 0
    val total = ui.exercises.size
    val p = current.planned
    val timed = TimedTarget.isTimed(p.reps)
    // Hero: the rest countdown while resting; otherwise the barbell (or an hourglass for timed work),
    // scaled down on narrow screens.
    val rest = ui.rest
    val context = LocalContext.current
    val photoId = current.item.effectiveExerciseId
    if (rest != null) {
        RestHero(rest, onRestMinus, onRestPlus, onRestSkip)
    } else if (photoId in remember { ExercisePhotos.ids(context) }) {
        // The exercise itself, start and end of the movement, instead of the generic barbell.
        ExercisePhoto(photoId, current.item.exerciseName, Modifier.fillMaxWidth())
    } else {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val sprite = if (timed) "hourglass" else "barbell"
            val width = pixelSprite(sprite).width
            val pixel = if (timed) 5.dp else minOf(2.dp, maxWidth / width)
            PixelSprite(sprite, Modifier.pixelFloat(amplitude = 1.dp), pixel = pixel)
        }
    }
    RetroTitleStrip(
        stringResource(R.string.player_set_of, current.logs.size + 1, current.totalSets),
        trailing = stringResource(R.string.player_exercise_short, index + 1, total),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroIconButton(onClick = { onGoTo(previousOpen(ui, index)) }, enabled = previousOpen(ui, index) != index) {
                PixelIcon("arrow_left", size = 18.dp, contentDescription = stringResource(R.string.calendar_previous))
            }
            RetroLinearProgress(
                progress = { ui.exercises.count { it.progress.isComplete } / total.toFloat() },
                modifier = Modifier.weight(1f),
                segments = total.coerceIn(1, 20),
            )
            RetroIconButton(onClick = { onGoTo(nextOpen(ui, index)) }, enabled = nextOpen(ui, index) != index) {
                PixelIcon("arrow_right", size = 18.dp, contentDescription = stringResource(R.string.calendar_next))
            }
        }
        Text(current.item.exerciseName, style = Retro.type.title.copy(fontSize = 22.sp, lineHeight = 26.sp), color = c.ink)
        if (current.substituted) {
            Text(stringResource(R.string.player_substituted, current.item.originalName), style = Retro.type.bodySmall, color = c.inkSoft)
        }
        WatchExecutionLink(current.item.exerciseName)
        // Remaining sets as "lives" (only while they fit on one line).
        if (current.totalSets in 1..10) {
            HeartMeter(current = current.totalSets - current.logs.size, max = current.totalSets, size = 18.dp)
        }
        val target = buildList {
            val seconds = TimedTarget.seconds(p.reps)
            add(if (seconds != null) TimedTarget.format(seconds) else stringResource(R.string.player_target_reps, p.reps))
            if (p.substituteExerciseId == null) p.loadKg?.let { add("${formatKg(it)} kg") }
            p.rpeTarget?.let { add("esforço $it") }
            add(stringResource(R.string.session_rest, p.restSeconds))
        }
        Text(stringResource(R.string.player_target, target.joinToString(" · ")), style = Retro.type.body)
        if (p.notes.isNotBlank()) Text(p.notes, style = Retro.type.bodySmall, color = c.inkSoft)
    }
}

/**
 * "Ver execução": opens a YouTube search for the exercise (the YouTube app when installed, else the browser).
 * Only an intent: the app itself needs no internet permission for it.
 */
@Composable
private fun WatchExecutionLink(exerciseName: String) {
    val context = LocalContext.current
    val c = Retro.colors
    Row(
        Modifier
            .clickable {
                val query = URLEncoder.encode("$exerciseName execução", "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
                runCatching { context.startActivity(intent) }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PixelIcon("play", tint = c.inkSoft, size = 14.dp)
        Text(stringResource(R.string.player_watch_execution), style = Retro.type.label, color = c.inkSoft)
    }
}

private fun previousOpen(ui: PlayerUi, from: Int): Int =
    (from - 1 downTo 0).firstOrNull { !ui.exercises[it].progress.isComplete } ?: from

private fun nextOpen(ui: PlayerUi, from: Int): Int =
    (from + 1 until ui.exercises.size).firstOrNull { !ui.exercises[it].progress.isComplete } ?: from

/** The rest countdown, shown where the barbell is: big time, segmented bar, -15 s / +15 s / skip. */
@Composable
private fun RestHero(rest: RestUi, onMinus: () -> Unit, onPlus: () -> Unit, onSkip: () -> Unit) {
    val c = Retro.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            HourglassSpinner(size = 40.dp, tint = c.accent2)
            Text(
                "%d:%02d".format(rest.remaining / 60, rest.remaining % 60),
                Modifier.padding(start = 16.dp),
                style = Retro.type.display.copy(fontSize = 44.sp, lineHeight = 52.sp),
                color = if (rest.remaining <= 3) c.accent else c.ink,
            )
        }
        RetroLinearProgress(
            progress = { rest.remaining / rest.total.coerceAtLeast(1).toFloat() },
            segments = 20,
            color = c.accent2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            RetroOutlinedButton(onClick = onMinus, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)) { Text("-15 s") }
            RetroOutlinedButton(onClick = onPlus, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)) { Text("+15 s") }
            RetroButton(onClick = onSkip, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)) { Text(stringResource(R.string.player_skip_rest)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetInputs(ui: PlayerUi, vm: PlayerViewModel) {
    val c = Retro.colors
    val inputs = ui.inputs
    val timer = ui.workTimer
    val bigField = Retro.type.number.copy(fontSize = 22.sp, lineHeight = 28.sp)
    if (timer != null) WorkTimerCard(timer, onStart = vm::startWork, onPause = vm::pauseWork, onReset = vm::resetWork)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        RetroTextField(
            value = inputs.reps,
            onValueChange = vm::onReps,
            label = stringResource(if (timer != null) R.string.player_time_done else R.string.player_reps_done),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = bigField,
            readOnly = timer?.running == true,
            modifier = Modifier.weight(1f),
        )
        RetroTextField(
            value = inputs.load,
            onValueChange = vm::onLoad,
            label = stringResource(if (timer != null) R.string.player_load_optional else R.string.player_load_used),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = bigField,
            supportingText = ui.loadHint?.let { stringResource(R.string.player_last_load, formatKg(it)) },
            modifier = Modifier.weight(1f),
        )
    }
    if (timer == null && ui.current?.planned?.loadKg == null && inputs.loadValue == null) {
        Text(stringResource(R.string.player_ask_load), style = Retro.type.bodySmall, color = c.danger)
    }
    SwitchRow(stringResource(R.string.player_failed), inputs.failed, vm::onFailed)
    Text(stringResource(R.string.player_rpe_optional), style = Retro.type.label, color = c.inkSoft)
    RpeGrid(inputs.rpe) { value -> vm.onRpe(if (inputs.rpe == value) null else value) }
}

/** Fixed bottom bar of the player: the record to beat and the button that logs the set. */
@Composable
private fun LogBar(ui: PlayerUi, vm: PlayerViewModel) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaBar()
        RecordLine(ui.record)
        RetroRewardButton(
            stringResource(R.string.player_log_set).uppercase(PT_BR),
            stringResource(R.string.game_xp_plus, GameEngine.XP_PER_SET),
            onClick = vm::logSet,
            modifier = Modifier.heightIn(min = 60.dp),
            icon = "check",
            enabled = ui.inputs.canLog,
        )
    }
}

/** "RECORDE 60 kg × 8" under the inputs, so the player knows what beats it. */
@Composable
private fun RecordLine(record: RecordUi?) {
    val c = Retro.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        PixelSprite("medal", pixel = 2.dp)
        Text(
            if (record == null) stringResource(R.string.player_record_none)
            else stringResource(R.string.player_record, recordText(record)),
            style = Retro.type.label,
            color = c.ink,
        )
    }
}

private fun recordText(r: RecordUi): String = when {
    r.timed -> TimedTarget.format(r.reps)
    r.loadKg <= 0.0 -> "${r.reps} reps"
    else -> "${formatKg(r.loadKg)} kg × ${r.reps}"
}

/**
 * "NOVO RECORDE!" pop-up in the XP window style: trophy, exercise, the new mark and the bonus.
 * Closes itself after a few seconds or with its X.
 */
@Composable
private fun NewRecordWindow(rec: NewRecordUi, onDismiss: () -> Unit) {
    val c = Retro.colors
    LaunchedEffect(rec.serial) {
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }
    RetroWindow(stringResource(R.string.player_new_record), Modifier.fillMaxWidth(), onClose = onDismiss, framed = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PixelSprite("trophy", Modifier.pixelFloat(), pixel = 3.dp)
            Column(Modifier.weight(1f)) {
                Text(rec.exercise, style = Retro.type.heading, color = c.ink)
                Text(recordText(rec.record), style = Retro.type.body, color = c.inkSoft)
            }
            Text(stringResource(R.string.game_xp_plus, GameEngine.XP_PR), style = Retro.type.button, color = c.ink)
        }
    }
}

/** RPE 1–10 as an even 5 × 2 grid of equal cells. */
@Composable
private fun RpeGrid(selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1..5, 6..10).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value ->
                    RetroFilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(value.toString()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Stopwatch for timed exercises: big count-up with the target beside it, block progress toward the
 * target, and start/pause + reset. Past the target it keeps counting and switches to the accent.
 */
@Composable
private fun WorkTimerCard(timer: WorkTimerUi, onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit) {
    val c = Retro.colors
    RetroWindow(stringResource(R.string.player_timer_window), Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (timer.running && !timer.reached) HourglassSpinner(size = 36.dp, tint = c.inkSoft)
            Text(
                TimedTarget.format(timer.elapsed),
                Modifier.padding(start = 16.dp),
                style = Retro.type.display.copy(fontSize = 52.sp, lineHeight = 58.sp),
                color = if (timer.reached) c.accent else c.ink,
            )
        }
        Text(
            if (timer.reached) stringResource(R.string.player_timer_reached)
            else stringResource(R.string.player_timer_target, TimedTarget.format(timer.target)),
            Modifier.fillMaxWidth(),
            style = Retro.type.body,
            color = if (timer.reached) c.accent else c.inkSoft,
            textAlign = TextAlign.Center,
        )
        RetroLinearProgress(progress = { timer.elapsed / timer.target.toFloat() }, segments = 20, color = c.inkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            RetroButton(onClick = if (timer.running) onPause else onStart, modifier = Modifier.weight(2f).heightIn(min = 56.dp)) {
                PixelIcon(if (timer.running) "pause" else "play", size = 18.dp)
                Text(
                    stringResource(
                        when {
                            timer.running -> R.string.player_timer_pause
                            timer.elapsed > 0 -> R.string.player_timer_resume
                            else -> R.string.player_timer_start
                        },
                    ),
                )
            }
            RetroOutlinedButton(onClick = onReset, enabled = timer.elapsed > 0 || timer.running, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.player_timer_reset))
            }
        }
    }
}

@Composable
private fun LoggedSets(current: PlayerExercise) {
    if (current.logs.isEmpty()) return
    SectionTitle(stringResource(R.string.player_logged))
    current.logs.forEach { log ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelIcon(if (log.failed) "x" else "check", tint = if (log.failed) Retro.colors.danger else Retro.colors.success, size = 16.dp)
            Text(setLine(log, current.planned.reps), style = Retro.type.body)
        }
    }
}

/**
 * At the end of the page: which exercise comes after this one (tap to go to it), or that this is the last one.
 * "After" is the next unfinished exercise in the plan order, wrapping around to ones left behind.
 */
@Composable
private fun NextExercise(ui: PlayerUi, onGoTo: (Int) -> Unit) {
    val c = Retro.colors
    val index = ui.currentIndex ?: return
    val open = ui.exercises.indices.filter { it != index && !ui.exercises[it].progress.isComplete }
    val next = open.firstOrNull { it > index } ?: open.firstOrNull()
    RetroDivider(dashed = true)
    if (next == null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelIcon("flame", tint = c.inkSoft, size = 16.dp)
            Text(stringResource(R.string.player_last_exercise), style = Retro.type.body, color = c.inkSoft)
        }
        return
    }
    val ex = ui.exercises[next]
    val p = ex.planned
    val target = buildList {
        add("${ex.totalSets - ex.logs.size} × " + (TimedTarget.seconds(p.reps)?.let { TimedTarget.format(it) } ?: p.reps))
        if (p.substituteExerciseId == null) p.loadKg?.let { add("${formatKg(it)} kg") }
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable { onGoTo(next) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PixelIcon("arrow_right", tint = c.inkSoft, size = 16.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.player_next_exercise), style = Retro.type.label, color = c.inkSoft)
            Text(ex.item.exerciseName, style = Retro.type.heading, color = c.ink)
            Text(target, style = Retro.type.bodySmall, color = c.inkSoft)
        }
    }
}

/** "+N XP" reward box shown on the finish screens. */
@Composable
fun XpReward(xp: Int) {
    val c = Retro.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PixelIcon("coin", Modifier.pixelFloat(), tint = c.gold, size = 32.dp)
        CountUpText(
            target = xp,
            modifier = Modifier.padding(start = 12.dp),
            style = Retro.type.number.copy(fontSize = 26.sp, lineHeight = 32.sp),
            color = c.ink,
            durationMs = 1200,
            format = { "+$it XP" },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishPanel(
    alreadyDone: Boolean,
    summary: WorkoutSummary,
    setsLogged: Int,
    onFinish: (Int, String) -> Unit,
    onExtraSet: () -> Unit,
    onUndo: () -> Unit,
) {
    val c = Retro.colors
    // The effort starts at the mean of the sets that had one (7 when none did); it can still be changed.
    val suggested = WorkoutSummaries.suggestedRpe(summary.avgRpe)
    var rpe by rememberSaveable(suggested) { mutableIntStateOf(suggested ?: 7) }
    var note by rememberSaveable { mutableStateOf("") }
    RetroWindow(stringResource(R.string.player_finish_window), Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PixelIcon("trophy", Modifier.pixelFloat(amplitude = 3.dp), tint = c.gold, size = 56.dp)
        }
        Text(stringResource(R.string.player_all_done), Modifier.fillMaxWidth(), style = Retro.type.title, textAlign = TextAlign.Center)
        if (!alreadyDone) {
            XpReward(GameEngine.XP_SESSION + setsLogged * GameEngine.XP_PER_SET)
            Text(stringResource(R.string.player_xp_hint), Modifier.fillMaxWidth(), style = Retro.type.bodySmall, color = c.inkSoft, textAlign = TextAlign.Center)
        }
    }
    if (alreadyDone) InfoCard(stringResource(R.string.player_already_done))
    WorkoutSummaryWindow(summary)
    Text(stringResource(R.string.player_overall_rpe), style = Retro.type.heading)
    summary.avgRpe?.let {
        Text(stringResource(R.string.player_effort_avg, String.format(PT_BR, "%.1f", it)), style = Retro.type.bodySmall, color = c.inkSoft)
    }
    RpeGrid(rpe) { rpe = it }
    LabeledTextField(stringResource(R.string.player_note), note, { note = it }, singleLine = false)
    RetroButton(onClick = { onFinish(rpe, note) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        PixelIcon("floppy", size = 18.dp)
        Text(stringResource(R.string.player_finish))
    }
    // Both buttons take the height of the taller one.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        RetroOutlinedButton(onClick = onExtraSet, modifier = Modifier.weight(1f).fillMaxHeight()) { Text(stringResource(R.string.player_extra_set)) }
        RetroOutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f).fillMaxHeight()) { Text(stringResource(R.string.player_undo)) }
    }
}

private fun workoutSummary(exercises: List<PlayerExercise>, records: List<String>, previousVolumeKg: Double?): WorkoutSummary {
    val sets = exercises.flatMap { ex ->
        val timed = TimedTarget.isTimed(ex.planned.reps)
        ex.logs.map { SummarySet(ex.item.exerciseName, it.repsDone, it.loadKg, it.failed, it.rpe, it.timestamp, timed) }
    }
    val items = exercises.map { SummaryExercise(it.totalSets, it.logs.size, it.skipped, it.substituted) }
    return WorkoutSummaries.build(sets, items, records, previousVolumeKg)
}

/** The recap of the workout: time, sets, volume, exercises, best set, mean effort and records beaten. */
@Composable
private fun WorkoutSummaryWindow(s: WorkoutSummary) {
    val c = Retro.colors
    @Composable
    fun line(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.padding(end = 12.dp), style = Retro.type.label, color = c.inkSoft)
            Text(value, style = Retro.type.body, color = c.ink, textAlign = TextAlign.End)
        }
    }
    RetroWindow(stringResource(R.string.summary_window), Modifier.fillMaxWidth()) {
        if (s.durationMinutes > 0) line(stringResource(R.string.summary_duration), stringResource(R.string.summary_minutes, s.durationMinutes))
        line(stringResource(R.string.summary_sets), "${s.setsDone}/${s.setsPlanned}")
        if (s.volumeKg > 0) {
            val volume = if (s.volumeKg >= 1000) String.format(PT_BR, "%.1f t", s.volumeKg / 1000) else "${formatKg(s.volumeKg)} kg"
            line(
                stringResource(R.string.summary_volume),
                s.volumeChangePercent?.let { stringResource(R.string.summary_volume_vs_last, volume, if (it > 0) "+$it" else "$it") } ?: volume,
            )
        }
        line(stringResource(R.string.summary_exercises), stringResource(R.string.summary_exercises_line, s.exercisesDone, s.exercisesSkipped, s.exercisesSubstituted))
        s.bestSet?.let { line(stringResource(R.string.summary_best), "${it.exercise}: ${formatKg(it.loadKg)} kg × ${it.reps}") }
        s.avgRpe?.let { line(stringResource(R.string.summary_effort), String.format(PT_BR, "%.1f", it)) }
        if (s.records.isNotEmpty()) {
            RetroDivider(dashed = true)
            Text(stringResource(R.string.summary_records), style = Retro.type.label, color = c.inkSoft)
            s.records.forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelIcon("medal", tint = c.gold, size = 16.dp)
                    Text(name, style = Retro.type.body, color = c.ink)
                }
            }
        }
    }
}

@Composable
private fun ReasonDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    RetroDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = RetroIconKind.QUESTION,
        text = { LabeledTextField(stringResource(R.string.session_skip_reason), reason, { reason = it }) },
        confirmButton = { RetroButton(onClick = { onConfirm(reason) }) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { RetroOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SubstituteDialog(exercises: List<ExerciseEntity>, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    val c = Retro.colors
    var query by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ExerciseEntity?>(null) }
    val filtered = remember(query, exercises) {
        val q = ExerciseCatalog.normalize(query)
        exercises.filter { q.isEmpty() || ExerciseCatalog.normalize(it.canonicalName).contains(q) }
    }
    RetroDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_substitute_title),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledTextField(stringResource(R.string.session_skip_reason), reason, { reason = it })
                LabeledTextField(stringResource(R.string.import_map_search), query, { query = it })
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(filtered, key = { it.id }) { exercise ->
                        val isSelected = exercise == selected
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) c.accent2 else c.window)
                                .clickable { selected = exercise }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        ) {
                            Text(
                                exercise.canonicalName,
                                Modifier.widthIn(max = 600.dp),
                                style = Retro.type.body,
                                color = if (isSelected) c.onAccent2 else c.ink,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            RetroButton(onClick = { selected?.let { onConfirm(it.id, reason) } }, enabled = selected != null) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = { RetroTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

fun formatKg(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, v)
