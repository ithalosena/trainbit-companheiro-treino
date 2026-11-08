package com.ithalo.hybridcoach.ui.settings

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.MenuRow
import com.ithalo.hybridcoach.ui.retro.RetroTextButton
import com.ithalo.hybridcoach.voice.CoachVoices
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.saveable.rememberSaveable
import com.ithalo.hybridcoach.ui.retro.DitherCrossfade
import com.ithalo.hybridcoach.ui.retro.RetroCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.HybridCoachApp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.AppSettings
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.reminders.TrainingReminders
import com.ithalo.hybridcoach.ui.common.ChoiceChips
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.ui.common.IntStepper
import com.ithalo.hybridcoach.ui.common.LabeledTextField
import com.ithalo.hybridcoach.ui.onboarding.PaletteSwatch
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.SwitchRow
import com.ithalo.hybridcoach.ui.common.appContextViewModel
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroDialog
import com.ithalo.hybridcoach.ui.retro.RetroDivider
import com.ithalo.hybridcoach.ui.retro.RetroIconKind
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.ditherDots
import com.ithalo.hybridcoach.ui.retro.hardShadow
import com.ithalo.hybridcoach.ui.retro.pixelBorder
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.DarkMode
import com.ithalo.hybridcoach.ui.theme.HybridCoachTheme
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.RetroPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: PlanRepository, app: Application) : ViewModel() {
    private val store = (app as HybridCoachApp).container.settings
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { store.update(transform) }

    /** Sets the reminder alarm again (after a permission changed in the system settings). */
    fun refreshReminders(app: Application) = viewModelScope.launch {
        (app as HybridCoachApp).container.nextReminder.value = TrainingReminders.reschedule(app)
    }
    fun clearData(onDone: () -> Unit) = viewModelScope.launch {
        repository.clearAllData()
        store.resetGameSeen()
        onDone()
    }
}

/** Sub-pages of the settings menu; MENU is the list itself. */
private enum class SettingsPage { MENU, APPEARANCE, VOICE, RUN, REMINDERS, DATA }

@Composable
fun SettingsScreen(onBack: (() -> Unit)?, onDesign: () -> Unit) {
    val vm = appContextViewModel { repo, app -> SettingsViewModel(repo, app) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(SettingsPage.MENU) }
    var confirmClear by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    BackHandler(enabled = page != SettingsPage.MENU) { page = SettingsPage.MENU }

    val title = when (page) {
        SettingsPage.MENU -> stringResource(R.string.settings_program)
        SettingsPage.APPEARANCE -> stringResource(R.string.settings_appearance)
        SettingsPage.VOICE -> stringResource(R.string.settings_voice)
        SettingsPage.RUN -> stringResource(R.string.settings_run)
        SettingsPage.REMINDERS -> stringResource(R.string.settings_reminders)
        SettingsPage.DATA -> stringResource(R.string.settings_data)
    }
    // As a tab there is no way back from the menu itself; sub-pages always return to the menu.
    ScreenScaffold(title, onBack = if (page == SettingsPage.MENU) onBack else ({ page = SettingsPage.MENU })) {
        DitherCrossfade(page, Modifier.fillMaxSize()) { current ->
            ScreenColumn {
                when (current) {
                    SettingsPage.MENU -> {
                        MenuRow("star", stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_desc)) { page = SettingsPage.APPEARANCE }
                        MenuRow("speaker", stringResource(R.string.settings_voice), stringResource(R.string.settings_voice_desc)) { page = SettingsPage.VOICE }
                        MenuRow("run", stringResource(R.string.settings_run), stringResource(R.string.settings_run_desc)) { page = SettingsPage.RUN }
                        MenuRow("clock", stringResource(R.string.settings_reminders), stringResource(R.string.settings_reminders_desc)) { page = SettingsPage.REMINDERS }
                        MenuRow("folder", stringResource(R.string.settings_data), stringResource(R.string.settings_data_desc)) { page = SettingsPage.DATA }
                        MenuRow("info", stringResource(R.string.settings_tour), stringResource(R.string.settings_tour_desc)) {
                            vm.update { it.copy(onboardingDone = false) }
                        }
                        MenuRow("list", stringResource(R.string.settings_showcase), stringResource(R.string.settings_showcase_help), onClick = onDesign)
                        HelpText(stringResource(R.string.settings_units))
                    }
                    SettingsPage.APPEARANCE -> {
                        var name by remember(s.playerName) { mutableStateOf(s.playerName) }
                        LabeledTextField(
                            stringResource(R.string.onboarding_name),
                            name,
                            { v -> name = v.take(24); vm.update { it.copy(playerName = name.trim()) } },
                        )
                        SectionTitle(stringResource(R.string.settings_palette))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RetroPalette.entries.forEach { p ->
                                PaletteSwatch(p, s.darkMode, selected = p == s.palette, modifier = Modifier.weight(1f)) {
                                    vm.update { it.copy(palette = p) }
                                }
                            }
                        }
                        SectionTitle(stringResource(R.string.settings_mode))
                        ChoiceChips(DarkMode.entries, s.darkMode, { modeLabel(it) }) { m -> vm.update { it.copy(darkMode = m) } }
                    }
                    SettingsPage.VOICE -> VoicePage(s) { transform -> vm.update(transform) }
                    SettingsPage.RUN -> {
                        IntStepper(stringResource(R.string.settings_tolerance), s.paceToleranceSec, 0..30, suffix = " s") { v -> vm.update { it.copy(paceToleranceSec = v) } }
                        HelpText(stringResource(R.string.settings_tolerance_help))
                        RetroDivider()
                        Text(stringResource(R.string.settings_smoothing), style = Retro.type.body)
                        ChoiceChips(listOf(10, 20, 30), s.smoothingSeconds, { "$it s" }) { v -> vm.update { it.copy(smoothingSeconds = v) } }
                        HelpText(stringResource(R.string.settings_smoothing_help))
                        RetroDivider()
                        IntStepper(stringResource(R.string.settings_interval), s.minAlertIntervalSec, 15..120, step = 15, suffix = " s") { v -> vm.update { it.copy(minAlertIntervalSec = v) } }
                        HelpText(stringResource(R.string.settings_interval_help))
                        RetroDivider()
                        SwitchRow(stringResource(R.string.settings_track), s.storeTrack) { v -> vm.update { it.copy(storeTrack = v) } }
                        HelpText(stringResource(R.string.settings_track_help))
                    }
                    SettingsPage.REMINDERS -> RemindersPage(s, { transform -> vm.update(transform) }) {
                        vm.refreshReminders(context.applicationContext as Application)
                    }
                    SettingsPage.DATA -> {
                        Text(stringResource(R.string.settings_clear_confirm), style = Retro.type.body, color = Retro.colors.inkSoft)
                        RetroOutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                            PixelIcon("skull", size = 16.dp)
                            Text(stringResource(R.string.settings_clear))
                        }
                        if (cleared) Text(stringResource(R.string.settings_cleared), style = Retro.type.bodySmall)
                    }
                }
            }
        }

        if (confirmClear) {
            RetroDialog(
                onDismissRequest = { confirmClear = false },
                title = stringResource(R.string.settings_clear),
                icon = RetroIconKind.WARNING,
                text = { Text(stringResource(R.string.settings_clear_confirm)) },
                confirmButton = {
                    RetroButton(onClick = {
                        confirmClear = false
                        vm.clearData { cleared = true }
                    }) { Text(stringResource(R.string.settings_clear_yes)) }
                },
                dismissButton = { RetroOutlinedButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

private val RATES = listOf(0.85f, 1f, 1.15f, 1.3f)
private const val PREVIEW_PHRASE = "Bora! Próximo: agachamento, série 2 de 4. Ritmo ótimo, continue assim."

/**
 * Voice settings: on/off, the installed pt-BR voices (best first) to hear and pick, speed and volume,
 * plus a shortcut to the system screen where better voices can be downloaded.
 */
@Composable
private fun VoicePage(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val c = Retro.colors
    val context = LocalContext.current
    var voices by remember { mutableStateOf<List<Voice>?>(null) }
    val engineRef = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            val e = engine
            voices = if (status == TextToSpeech.SUCCESS && e != null) {
                e.setLanguage(PT_BR)
                CoachVoices.available(e)
            } else emptyList()
        }
        engineRef[0] = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            engineRef[0] = null
        }
    }
    fun preview(voice: Voice?, rate: Float = s.voiceRate) {
        val e = engineRef[0] ?: return
        if (voice != null) runCatching { e.voice = voice }
        e.setSpeechRate(rate)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, s.voiceVolume) }
        e.speak(PREVIEW_PHRASE, TextToSpeech.QUEUE_FLUSH, params, "preview")
    }

    SwitchRow(stringResource(R.string.settings_voice_enabled), s.voiceEnabled) { v -> update { it.copy(voiceEnabled = v) } }

    SectionTitle(stringResource(R.string.settings_voice_pick))
    val list = voices
    when {
        list == null -> HelpText(stringResource(R.string.settings_voice_loading))
        list.isEmpty() -> InfoCard(stringResource(R.string.settings_voice_none))
        else -> {
            val selected = s.voiceName?.takeIf { n -> list.any { it.name == n } } ?: list.first().name
            list.forEachIndexed { i, voice ->
                VoiceRow(
                    label = CoachVoices.label(i, voice),
                    recommended = i == 0,
                    selected = voice.name == selected,
                    onSelect = {
                        update { it.copy(voiceName = voice.name) }
                        preview(voice)
                    },
                    onListen = { preview(voice) },
                )
            }
            HelpText(stringResource(R.string.settings_voice_pick_help))
        }
    }

    SectionTitle(stringResource(R.string.settings_voice_speed))
    ChoiceChips(RATES, RATES.minBy { kotlin.math.abs(it - s.voiceRate) }, { rateLabel(it) }) { r ->
        update { it.copy(voiceRate = r) }
        preview(list?.firstOrNull { it.name == s.voiceName } ?: list?.firstOrNull(), r)
    }

    SectionTitle(stringResource(R.string.settings_voice_volume_title))
    Text(stringResource(R.string.settings_voice_volume, (s.voiceVolume * 100).toInt()), style = Retro.type.body)
    Slider(value = s.voiceVolume, onValueChange = { v -> update { it.copy(voiceVolume = v) } }, valueRange = 0.2f..1f)
    HelpText(stringResource(R.string.settings_voice_help))

    RetroDivider()
    RetroOutlinedButton(
        onClick = {
            runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        PixelIcon("speaker", size = 16.dp)
        Text(stringResource(R.string.settings_voice_more))
    }
    Text(stringResource(R.string.settings_voice_more_help), style = Retro.type.bodySmall, color = c.inkSoft)
}

@Composable
private fun rateLabel(rate: Float) = stringResource(
    when (rate) {
        0.85f -> R.string.settings_rate_slow
        1f -> R.string.settings_rate_normal
        1.15f -> R.string.settings_rate_fast
        else -> R.string.settings_rate_faster
    },
)

/** One installed voice: radio square, label, "recommended" tag and a listen button. */
@Composable
private fun VoiceRow(label: String, recommended: Boolean, selected: Boolean, onSelect: () -> Unit, onListen: () -> Unit) {
    val c = Retro.colors
    RetroCard(Modifier.fillMaxWidth(), onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(18.dp).pixelBorder(if (selected) c.ink else c.inkFaint), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(8.dp).background(c.ink))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = Retro.type.body, color = c.ink)
                if (recommended) Text(stringResource(R.string.settings_voice_recommended), style = Retro.type.label, color = c.accent2)
            }
            RetroTextButton(onClick = onListen) {
                PixelIcon("play", size = 14.dp)
                Text(stringResource(R.string.settings_voice_listen))
            }
        }
    }
}

@Composable
private fun modeLabel(mode: DarkMode) = stringResource(
    when (mode) {
        DarkMode.SYSTEM -> R.string.retro_mode_system
        DarkMode.LIGHT -> R.string.retro_mode_light
        DarkMode.DARK -> R.string.retro_mode_dark
    },
)

