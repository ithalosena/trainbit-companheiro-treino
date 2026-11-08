package com.ithalo.hybridcoach.ui.run

import android.Manifest
import com.ithalo.hybridcoach.media.MediaBar
import com.ithalo.hybridcoach.core.game.RunBests
import com.ithalo.hybridcoach.core.game.RunFact
import com.ithalo.hybridcoach.core.game.RunRecord
import com.ithalo.hybridcoach.ui.retro.RetroDivider
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import kotlin.math.abs
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.ithalo.hybridcoach.core.game.GameEngine
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.player.XpReward
import com.ithalo.hybridcoach.ui.retro.HourglassSpinner
import com.ithalo.hybridcoach.ui.retro.PixelConfetti
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroCard
import com.ithalo.hybridcoach.ui.retro.RetroDialog
import com.ithalo.hybridcoach.ui.retro.RetroFilterChip
import com.ithalo.hybridcoach.ui.retro.RetroIconKind
import com.ithalo.hybridcoach.ui.retro.RetroLinearProgress
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.RetroTextButton
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.Retro
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.run.LocationFilter
import com.ithalo.hybridcoach.core.run.RunEngine
import com.ithalo.hybridcoach.run.formatKmh
import com.ithalo.hybridcoach.ui.common.ChoiceChips
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.data.RunBlockEntity
import com.ithalo.hybridcoach.data.RunProgressEntity
import com.ithalo.hybridcoach.run.RunService
import com.ithalo.hybridcoach.run.RunTracker
import com.ithalo.hybridcoach.run.RunUi
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.LabeledTextField
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.paceText
import com.ithalo.hybridcoach.ui.common.runBlockLine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RunViewModel(private val repository: PlanRepository, private val id: Long) : ViewModel() {
    val session: StateFlow<PlannedSessionEntity?> = repository.observeSession(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val blocks: StateFlow<List<RunBlockEntity>> = repository.observeRunBlocks(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress: StateFlow<RunProgressEntity?> = repository.observeRunProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun discardProgress() = viewModelScope.launch { repository.discardRunProgress() }

    /** Running records the just-saved run beat; shown on the summary only (the phone is in a pocket during the run). */
    val newRecords = MutableStateFlow<List<RunRecord>>(emptyList())

    fun loadNewRecords(ui: RunUi) = viewModelScope.launch {
        val run = RunFact(ui.sessionId, LocalDate.now(), ui.distanceM, ui.activeSeconds, ui.avgPaceSecPerKm, ui.splits)
        // The run itself is already saved: leave it out of the history it is compared with.
        val earlier = repository.gameFacts().runs.filterNot { it.sessionId == ui.sessionId && abs(it.distanceM - ui.distanceM) < 1.0 }
        newRecords.value = RunBests.beaten(run, earlier)
    }

    fun saveFeedback(rpe: Int, note: String, onDone: () -> Unit) = viewModelScope.launch {
        repository.finishSession(id, rpe, note)
        onDone()
    }
}

@Composable
fun RunScreen(sessionId: Long, onBack: () -> Unit) {
    val vm = appViewModel { RunViewModel(it, sessionId) }
    val session by vm.session.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val ui by RunTracker.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confetti by remember { mutableIntStateOf(0) }

    val running = ui?.takeIf { it.sessionId == sessionId }
    if (running != null && !running.finished) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    ScreenScaffold(session?.title ?: stringResource(R.string.run_title), onBack) {
        ScreenColumn {
            val other = ui?.takeIf { it.sessionId != sessionId && !it.finished }
            when {
                running?.finished == true -> {
                    LaunchedEffect(running.runLogId != null) {
                        if (running.runLogId != null) {
                            confetti++
                            vm.loadNewRecords(running)
                        }
                    }
                    val records by vm.newRecords.collectAsStateWithLifecycle()
                    FinishedContent(running, records) { rpe, note ->
                        vm.saveFeedback(rpe, note) {
                            RunTracker.clear()
                            onBack()
                        }
                    }
                }
                running != null -> RunningContent(running, context)
                other != null -> InfoCard(stringResource(R.string.run_other_active, other.title))
                progress != null && progress?.sessionId == sessionId -> {
                    InfoCard(stringResource(R.string.run_interrupted), isError = true)
                    RetroButton(onClick = { RunService.start(context, sessionId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        PixelIcon("play", size = 16.dp)
                        Text(stringResource(R.string.run_continue))
                    }
                    RetroOutlinedButton(onClick = { vm.discardProgress() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.run_discard)) }
                }
                progress != null -> InfoCard(stringResource(R.string.run_other_saved))
                else -> SetupContent(blocks, context) { kmh -> RunService.start(context, sessionId, kmh) }
            }
        }
        PixelConfetti(confetti, Modifier.fillMaxSize(), particleCount = 90)
    }
}

@Composable
private fun SetupContent(blocks: List<RunBlockEntity>, context: Context, onStart: (treadmillKmh: Double?) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    var skippedBackground by rememberSaveable { mutableStateOf(false) }

    // Read again after each permission result or return from the system settings.
    val fine = remember(refresh) { granted(context, Manifest.permission.ACCESS_FINE_LOCATION) }
    val notifications = remember(refresh) { Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(context, Manifest.permission.POST_NOTIFICATIONS) }
    val background = remember(refresh) { Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    val battery = remember(refresh) { (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName) }

    RetroWindow(stringResource(R.string.session_window_blocks), Modifier.fillMaxWidth()) {
        blocks.forEach { Text(runBlockLine(it), style = Retro.type.body) }
    }

    var treadmill by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable { mutableStateOf(defaultSpeed(blocks)) }
    SectionTitle(stringResource(R.string.run_mode))
    ChoiceChips(listOf(false, true), treadmill, { stringResource(if (it) R.string.run_mode_treadmill else R.string.run_mode_gps) }) { treadmill = it }
    if (treadmill) {
        SpeedStepper(speed) { speed = it }
        HelpText(stringResource(R.string.run_mode_treadmill_help))
    }

    SectionTitle(stringResource(R.string.run_setup_title))
    PermissionCard(stringResource(R.string.run_perm_location), stringResource(R.string.run_perm_location_why), fine, stringResource(R.string.run_perm_allow)) {
        fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionCard(stringResource(R.string.run_perm_notifications), stringResource(R.string.run_perm_notifications_why), notifications, stringResource(R.string.run_perm_allow)) {
            singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fine && !skippedBackground) {
        PermissionCard(stringResource(R.string.run_perm_background), stringResource(R.string.run_perm_background_why), background, stringResource(R.string.run_perm_allow_always), onSkip = { skippedBackground = true }) {
            singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    PermissionCard(stringResource(R.string.run_perm_battery), stringResource(R.string.run_perm_battery_why, stringResource(R.string.app_name)), battery, stringResource(R.string.run_perm_open_settings)) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    RetroButton(onClick = { onStart(if (treadmill) speed else null) }, enabled = fine, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        PixelIcon("play", size = 20.dp)
        Text(stringResource(R.string.run_start), style = Retro.type.button.copy(fontSize = 16.sp))
    }
    if (!fine) Text(stringResource(R.string.run_need_location), style = Retro.type.bodySmall, color = Retro.colors.danger)
}

@Composable
private fun PermissionCard(title: String, why: String, done: Boolean, action: String, onSkip: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = Retro.colors
    RetroCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelIcon(if (done) "check" else "warning", tint = if (done) c.success else c.accent, size = 20.dp)
            Text(title, style = Retro.type.heading)
        }
        if (!done) {
            Text(why, style = Retro.type.bodySmall, color = c.inkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroOutlinedButton(onClick = onClick) { Text(action) }
                if (onSkip != null) RetroTextButton(onClick = onSkip) { Text(stringResource(R.string.run_perm_skip)) }
            }
        }
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Blinking red "REC" while running, steady "PAUSA" while paused. */
@Composable
private fun RecIndicator(paused: Boolean) {
    val c = Retro.colors
    val blink by rememberInfiniteTransition(label = "rec").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "recBlink",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (paused) {
            PixelIcon("pause", tint = c.inkSoft, size = 16.dp)
        } else {
            Box(Modifier.size(12.dp).graphicsLayer { alpha = if (blink < 0.6f) 1f else 0f }.background(c.danger))
        }
        Text(
            stringResource(if (paused) R.string.run_paused_tag else R.string.run_rec),
            style = Retro.type.button,
            color = if (paused) c.inkSoft else c.danger,
        )
    }
}

@Composable
private fun RunningContent(ui: RunUi, context: Context) {
    val c = Retro.colors
    var confirmFinish by remember { mutableStateOf(false) }

    RetroWindow(stringResource(R.string.run_hud_window), Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RecIndicator(ui.paused)
            Spacer(Modifier.weight(1f))
            if (ui.treadmillKmh == null) {
                val accuracy = ui.gpsAccuracyM
                PixelIcon("map", tint = if (accuracy != null && accuracy <= LocationFilter.MAX_ACCURACY_M) c.success else c.inkFaint, size = 18.dp)
            } else {
                PixelIcon("shoe", tint = c.accent2, size = 18.dp)
            }
        }
        Text(
            RunService.elapsedText(ui.activeSeconds),
            Modifier.fillMaxWidth(),
            style = Retro.type.display.copy(fontSize = 36.sp, lineHeight = 44.sp),
            color = if (ui.paused) c.inkSoft else c.ink,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Metric(stringResource(R.string.run_distance_km), "%.2f".format(PT_BR, ui.distanceM / 1000), Modifier.weight(1f))
            Metric(stringResource(R.string.run_pace_now), ui.paceSecPerKm?.let { paceText(it) } ?: "--:--", Modifier.weight(1f))
            Metric(stringResource(R.string.run_pace_avg), ui.avgPaceSecPerKm?.let { paceText(it) } ?: "--:--", Modifier.weight(1f))
        }
        RetroLinearProgress(
            progress = { if (ui.blocksDone) 1f else if (ui.totalSteps <= 0) 0f else (ui.stepNumber - 1).coerceAtLeast(0) / ui.totalSteps.toFloat() },
            segments = ui.totalSteps.coerceIn(1, 20),
            color = c.accent2,
        )
    }
    if (ui.paused) InfoCard(stringResource(if (ui.autoPaused) R.string.run_auto_paused_banner else R.string.run_paused_banner))

    // Stood still mid-run: ask once per automatic pause whether everything is fine.
    var askedFor by remember { mutableStateOf<Long?>(null) }
    if (!ui.autoPaused) askedFor = null
    if (ui.autoPaused && askedFor == null) {
        RetroDialog(
            onDismissRequest = { askedFor = ui.activeSeconds },
            title = stringResource(R.string.run_auto_paused_title),
            icon = RetroIconKind.QUESTION,
            text = { Text(stringResource(R.string.run_auto_paused_text)) },
            confirmButton = {
                RetroButton(onClick = { askedFor = ui.activeSeconds; RunService.send(context, RunService.ACTION_RESUME) }) {
                    Text(stringResource(R.string.run_auto_paused_resume))
                }
            },
            dismissButton = {
                RetroOutlinedButton(onClick = { askedFor = ui.activeSeconds; RunService.send(context, RunService.ACTION_HOLD) }) {
                    Text(stringResource(R.string.run_auto_paused_hold))
                }
            },
        )
    }

    if (ui.blocksDone) {
        RetroWindow(stringResource(R.string.run_blocks_done_window), Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.run_blocks_done_title), style = Retro.type.title)
            Text(stringResource(R.string.run_blocks_done_text), style = Retro.type.body, color = c.inkSoft)
            Text(
                stringResource(R.string.run_blocks_done_extra, "%.2f".format(PT_BR, ui.stepDistanceM / 1000), RunService.elapsedText(ui.stepSeconds)),
                style = Retro.type.number.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = c.ink,
            )
        }
    }

    ui.step?.let { step ->
        val name = stepName(step.kind) + if (step.repetitions > 1) " ${step.repetition}/${step.repetitions}" else ""
        RetroWindow(stringResource(R.string.run_block_of, ui.stepNumber, ui.totalSteps), Modifier.fillMaxWidth()) {
            Text(name, style = Retro.type.title)
            val seconds = step.durationSeconds
            val meters = step.distanceM
            val remaining = when {
                seconds != null -> RunService.elapsedText((seconds - ui.stepSeconds).coerceAtLeast(0))
                meters != null -> "${(meters - ui.stepDistanceM).coerceAtLeast(0.0).toInt()} m"
                else -> ""
            }
            Text(stringResource(R.string.run_remaining_label), style = Retro.type.label, color = c.inkSoft)
            Text(remaining, style = Retro.type.number.copy(fontSize = 24.sp, lineHeight = 30.sp), color = c.ink)
            val blockProgress = when {
                seconds != null && seconds > 0 -> ui.stepSeconds / seconds.toFloat()
                meters != null && meters > 0 -> (ui.stepDistanceM / meters).toFloat()
                else -> 0f
            }
            RetroLinearProgress(progress = { blockProgress }, segments = 20)
            Text(
                step.pace?.let { stringResource(R.string.run_target, paceText(it.min), paceText(it.max)) } ?: stringResource(R.string.run_no_target),
                style = Retro.type.body,
            )
        }
    }

    ui.treadmillKmh?.let { kmh ->
        SpeedStepper(kmh) { RunService.setSpeed(context, it) }
        ui.step?.pace?.let { p ->
            Text(stringResource(R.string.run_target_speed, formatKmh(RunEngine.kmhForPace(p.max)), formatKmh(RunEngine.kmhForPace(p.min))), style = Retro.type.body)
        }
    }
    val accuracy = ui.gpsAccuracyM
    if (ui.treadmillKmh == null) Text(
        when {
            accuracy == null -> stringResource(R.string.run_gps_waiting)
            accuracy > LocationFilter.MAX_ACCURACY_M -> stringResource(R.string.run_gps_weak, accuracy.toInt())
            else -> stringResource(R.string.run_gps_ok, accuracy.toInt())
        },
        style = Retro.type.bodySmall,
        color = c.inkSoft,
    )

    MediaBar()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RetroButton(
            onClick = { RunService.send(context, if (ui.paused) RunService.ACTION_RESUME else RunService.ACTION_PAUSE) },
            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
        ) {
            PixelIcon(if (ui.paused) "play" else "pause", size = 18.dp)
            Text(stringResource(if (ui.paused) R.string.run_resume else R.string.run_pause))
        }
        if (!ui.blocksDone) {
            RetroOutlinedButton(onClick = { RunService.send(context, RunService.ACTION_SKIP) }, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) {
                Text(stringResource(R.string.run_skip_block))
            }
        }
    }
    RetroOutlinedButton(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        PixelIcon("x", size = 16.dp)
        Text(stringResource(R.string.run_finish))
    }

    if (ui.splits.isNotEmpty()) {
        RetroWindow(stringResource(R.string.run_splits), Modifier.fillMaxWidth()) {
            ui.splits.forEachIndexed { i, s -> Text(stringResource(R.string.run_split_line, i + 1, paceText(s)), style = Retro.type.body) }
        }
    }

    if (confirmFinish) {
        RetroDialog(
            onDismissRequest = { confirmFinish = false },
            title = stringResource(R.string.run_finish_title),
            icon = RetroIconKind.QUESTION,
            text = { Text(stringResource(R.string.run_finish_text)) },
            confirmButton = {
                RetroButton(onClick = { confirmFinish = false; RunService.send(context, RunService.ACTION_FINISH) }) { Text(stringResource(R.string.run_finish)) }
            },
            dismissButton = { RetroOutlinedButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Treadmill speed with 0.5 km/h steps, the usual increment on gym machines. */
@Composable
private fun SpeedStepper(kmh: Double, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RetroOutlinedButton(onClick = { onChange((kmh - 0.5).coerceAtLeast(1.0)) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("-0,5") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.run_speed_value, formatKmh(kmh)), style = Retro.type.number.copy(fontSize = 16.sp, lineHeight = 20.sp))
            Text(stringResource(R.string.run_speed_pace, RunEngine.paceForKmh(kmh)?.let { paceText(it) } ?: "--:--"), style = Retro.type.label, color = Retro.colors.inkSoft)
        }
        RetroOutlinedButton(onClick = { onChange((kmh + 0.5).coerceAtMost(RunEngine.MAX_TREADMILL_KMH)) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("+0,5") }
    }
}

/** Middle of the first pace target, as a treadmill speed rounded to 0.5 km/h; 8 km/h without targets. */
private fun defaultSpeed(blocks: List<RunBlockEntity>): Double {
    val b = blocks.firstOrNull { it.paceMin != null && it.paceMax != null } ?: return 8.0
    val kmh = RunEngine.kmhForPace((b.paceMin!! + b.paceMax!!) / 2)
    return Math.round(kmh * 2) / 2.0
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    val c = Retro.colors
    Column(
        modifier.bevel(c.windowInset, c.border, c.bevelLight, c.bevelDark, raised = false).padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = Retro.type.number.copy(fontSize = 15.sp, lineHeight = 20.sp), color = c.ink, maxLines = 1)
        Text(label, style = Retro.type.label.copy(fontSize = 10.sp), color = c.inkSoft, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishedContent(ui: RunUi, records: List<RunRecord>, onSave: (Int, String) -> Unit) {
    val c = Retro.colors
    var rpe by rememberSaveable { mutableIntStateOf(0) }
    var note by rememberSaveable { mutableStateOf("") }
    RetroWindow(stringResource(R.string.player_finish_window), Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PixelIcon("medal", Modifier.pixelFloat(amplitude = 3.dp), tint = c.gold, size = 56.dp)
        }
        Text(stringResource(R.string.run_done), Modifier.fillMaxWidth(), style = Retro.type.title, textAlign = TextAlign.Center)
        if (ui.runLogId == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                HourglassSpinner()
                Text(stringResource(R.string.run_saving), Modifier.padding(start = 12.dp), style = Retro.type.mono)
            }
            return@RetroWindow
        }
        XpReward(GameEngine.XP_SESSION + (ui.distanceM / 200).toInt() * GameEngine.XP_PER_200M + records.size * GameEngine.XP_PR)
        Text(stringResource(R.string.run_xp_hint), Modifier.fillMaxWidth(), style = Retro.type.bodySmall, color = c.inkSoft, textAlign = TextAlign.Center)
    }
    if (ui.runLogId == null) return
    if (records.isNotEmpty()) {
        RetroWindow(stringResource(R.string.run_new_records_window), Modifier.fillMaxWidth()) {
            records.forEachIndexed { i, r ->
                if (i > 0) RetroDivider(dashed = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelIcon("medal", Modifier.pixelFloat(amplitude = 1.dp), tint = c.gold, size = 18.dp)
                    Text(runRecordLabel(r.kind), Modifier.weight(1f), style = Retro.type.heading, color = c.ink)
                    Text(runRecordValue(r), style = Retro.type.body, color = c.ink)
                }
            }
        }
    }
    RunSummaryLines(ui.distanceM, ui.activeSeconds, ui.avgPaceSecPerKm, ui.splits)
    Text(stringResource(R.string.player_overall_rpe), style = Retro.type.heading)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..10).forEach { value -> RetroFilterChip(selected = rpe == value, onClick = { rpe = value }, label = { Text(value.toString()) }) }
    }
    LabeledTextField(stringResource(R.string.player_note), note, { note = it }, singleLine = false)
    RetroButton(onClick = { onSave(rpe, note) }, enabled = rpe > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        PixelIcon("floppy", size = 18.dp)
        Text(stringResource(R.string.player_finish))
    }
}

/** Distance, time, average pace and km splits of a finished run. */
@Composable
fun RunSummaryLines(distanceM: Double, seconds: Long, avgPace: Int?, splits: List<Int>) {
    Text(
        stringResource(R.string.run_summary_line, "%.2f".format(PT_BR, distanceM / 1000), RunService.elapsedText(seconds), avgPace?.let { paceText(it) } ?: "--:--"),
        style = Retro.type.heading,
    )
    splits.forEachIndexed { i, s -> Text(stringResource(R.string.run_split_line, i + 1, paceText(s)), style = Retro.type.body) }
}

@Composable
private fun stepName(kind: com.ithalo.hybridcoach.core.plan.RunBlockKind): String = stringResource(
    when (kind) {
        com.ithalo.hybridcoach.core.plan.RunBlockKind.WARMUP -> R.string.kind_warmup
        com.ithalo.hybridcoach.core.plan.RunBlockKind.STEADY -> R.string.kind_steady
        com.ithalo.hybridcoach.core.plan.RunBlockKind.INTERVAL -> R.string.kind_interval
        com.ithalo.hybridcoach.core.plan.RunBlockKind.RECOVERY -> R.string.kind_recovery
        com.ithalo.hybridcoach.core.plan.RunBlockKind.COOLDOWN -> R.string.kind_cooldown
    },
)
