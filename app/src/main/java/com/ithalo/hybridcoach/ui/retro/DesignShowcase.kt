package com.ithalo.hybridcoach.ui.retro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.theme.DarkMode
import com.ithalo.hybridcoach.ui.theme.HybridCoachTheme
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.RetroPalette
import kotlin.random.Random

/**
 * Living style guide: palette/mode switchers plus every retro component and animation, with
 * buttons to trigger the one-shot effects. Applies [palette]/[darkMode] itself.
 */
@Composable
fun DesignShowcaseScreen(
    palette: RetroPalette,
    darkMode: DarkMode,
    onPaletteChange: (RetroPalette) -> Unit,
    onDarkModeChange: (DarkMode) -> Unit,
    onBack: () -> Unit,
) {
    HybridCoachTheme(palette = palette, darkMode = darkMode) {
        var scanlines by remember { mutableStateOf(false) }
        var confetti by remember { mutableIntStateOf(0) }
        var trophy by remember { mutableStateOf<BadgeTier?>(null) }
        var lastTier by remember { mutableStateOf(BadgeTier.GOLD) }
        var levelUp by remember { mutableStateOf(false) }
        var level by remember { mutableIntStateOf(7) }
        var xp by remember { mutableIntStateOf(120) }
        var tab by remember { mutableIntStateOf(0) }
        val tabs = listOf(
            RetroTab(stringResource(R.string.retro_tab_today), "home"),
            RetroTab(stringResource(R.string.retro_tab_plan), "calendar"),
            RetroTab(stringResource(R.string.retro_tab_run), "run"),
            RetroTab(stringResource(R.string.retro_tab_trophies), "trophy"),
            RetroTab(stringResource(R.string.retro_tab_settings), "gear"),
        )
        RetroBackground(Modifier.fillMaxSize(), scanlines = scanlines) {
            Column(Modifier.fillMaxSize()) {
                RetroTopBar(title = stringResource(R.string.retro_sc_title), onBack = onBack)
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PaletteSection(palette, darkMode, scanlines, onPaletteChange, onDarkModeChange) { scanlines = it }
                    HudSection(
                        level = level,
                        xp = xp,
                        onAddXp = {
                            xp += 40
                            if (xp >= 300) { xp -= 300; level++; levelUp = true }
                        },
                    )
                    ButtonsSection()
                    FieldsSection()
                    ProgressSection()
                    IconsSection()
                    AnimationsSection(
                        onConfetti = { confetti++ },
                        onTrophy = { lastTier = it; trophy = it },
                        onLevelUp = { level++; levelUp = true },
                    )
                }
                RetroTabBar(tabs, tab, onSelect = { tab = it })
            }
            PixelConfetti(confetti, Modifier.fillMaxSize())
            TrophyUnlockedBanner(
                tier = trophy ?: lastTier,
                title = stringResource(R.string.retro_sc_trophy_title),
                subtitle = stringResource(R.string.retro_sc_trophy_sub),
                visible = trophy != null,
                onFinished = { trophy = null },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        LevelUpOverlay(level, stringResource(R.string.retro_sc_rank), levelUp, onDismiss = { levelUp = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteSection(
    palette: RetroPalette,
    darkMode: DarkMode,
    scanlines: Boolean,
    onPaletteChange: (RetroPalette) -> Unit,
    onDarkModeChange: (DarkMode) -> Unit,
    onScanlines: (Boolean) -> Unit,
) {
    val c = Retro.colors
    RetroWindow(stringResource(R.string.retro_sc_palette_window), Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.retro_sc_palette))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroPalette.entries.forEach { p ->
                RetroFilterChip(selected = p == palette, onClick = { onPaletteChange(p) }, label = { Text(p.label) })
            }
        }
        SectionLabel(stringResource(R.string.retro_sc_mode))
        val modes = listOf(
            DarkMode.SYSTEM to R.string.retro_mode_system,
            DarkMode.LIGHT to R.string.retro_mode_light,
            DarkMode.DARK to R.string.retro_mode_dark,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (mode, label) ->
                RetroFilterChip(selected = mode == darkMode, onClick = { onDarkModeChange(mode) }, label = { Text(stringResource(label)) })
            }
        }
        SwitchLine(stringResource(R.string.retro_sc_scanlines), scanlines, onScanlines)
        SectionLabel(stringResource(R.string.retro_sc_colors))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                c.background, c.window, c.windowInset, c.ink, c.inkSoft, c.inkFaint, c.titleBar, c.accent, c.accent2,
                c.success, c.danger, c.bronze, c.silver, c.gold, c.platinum,
            ).forEach { Swatch(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HudSection(level: Int, xp: Int, onAddXp: () -> Unit) {
    var coins by remember { mutableIntStateOf(42) }
    var hearts by remember { mutableIntStateOf(3) }
    val c = Retro.colors
    RetroWindow(stringResource(R.string.retro_sc_hud_window), Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CoinCounter(coins)
            Box(Modifier.weight(1f))
            HeartMeter(hearts, 5)
        }
        XpBar(level, xp, 300)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("flame", "12", stringResource(R.string.retro_sc_streak), tint = c.accent)
            StatTile("dumbbell", "48", stringResource(R.string.retro_sc_sets), tint = c.accent2)
            StatTile("shoe", "21,5", stringResource(R.string.retro_sc_km), tint = c.accent2)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroOutlinedButton(onClick = onAddXp) { Text(stringResource(R.string.retro_sc_add_xp)) }
            RetroOutlinedButton(onClick = { coins += 10 }) {
                PixelIcon("coin", size = 16.dp)
                Text(stringResource(R.string.retro_sc_add_coins))
            }
            RetroOutlinedButton(onClick = { hearts = (hearts - 1).coerceAtLeast(0) }) { Text(stringResource(R.string.retro_sc_damage)) }
            RetroOutlinedButton(onClick = { hearts = (hearts + 1).coerceAtMost(5) }) { Text(stringResource(R.string.retro_sc_heal)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonsSection() {
    var taps by remember { mutableIntStateOf(0) }
    RetroWindow(stringResource(R.string.retro_sc_buttons_window), Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RetroButton(onClick = { taps++ }) {
                PixelIcon("check", size = 16.dp)
                Text(stringResource(R.string.retro_sc_ok))
            }
            RetroOutlinedButton(onClick = {}) { Text(stringResource(R.string.retro_sc_cancel)) }
            RetroTextButton(onClick = {}) { Text(stringResource(R.string.retro_sc_help)) }
            RetroIconButton(onClick = {}) { PixelIcon("gear") }
            RetroButton(onClick = {}, enabled = false) { Text(stringResource(R.string.retro_sc_disabled)) }
        }
        RetroCard(Modifier.fillMaxWidth(), onClick = { taps++ }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelIcon("dumbbell", tint = Retro.colors.accent2, size = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.retro_sc_card_title), style = Retro.type.heading)
                    Text(stringResource(R.string.retro_sc_card_body), style = Retro.type.bodySmall, color = Retro.colors.inkSoft)
                }
                CountUpText(taps, style = Retro.type.number.copy(fontSize = 16.sp), from = taps)
            }
        }
        RetroDivider()
        RetroDivider(dashed = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldsSection() {
    var name by remember { mutableStateOf("Ithalo") }
    var weight by remember { mutableStateOf("62,5") }
    var warm by remember { mutableStateOf(true) }
    var sound by remember { mutableStateOf(false) }
    var chips by remember { mutableStateOf(setOf(0)) }
    RetroWindow(stringResource(R.string.retro_sc_fields_window), Modifier.fillMaxWidth()) {
        RetroTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.retro_sc_name),
            supportingText = stringResource(R.string.retro_sc_name_hint),
        )
        RetroTextField(
            value = weight,
            onValueChange = { weight = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.retro_sc_weight),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailing = { Text(stringResource(R.string.retro_sc_kg), style = Retro.type.body) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroCheckbox(checked = warm, onCheckedChange = { warm = it })
            Text(stringResource(R.string.retro_sc_check), style = Retro.type.body)
        }
        SwitchLine(stringResource(R.string.retro_sc_switch), sound) { sound = it }
        val labels = listOf(R.string.retro_sc_chip_strength, R.string.retro_sc_chip_run, R.string.retro_sc_chip_mobility)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                RetroFilterChip(
                    selected = i in chips,
                    onClick = { chips = if (i in chips) chips - i else chips + i },
                    label = { Text(stringResource(label)) },
                )
            }
            RetroFilterChip(selected = false, onClick = {}, label = { Text(stringResource(R.string.retro_sc_disabled)) }, enabled = false)
        }
    }
}

@Composable
private fun ProgressSection() {
    var progress by remember { mutableFloatStateOf(0.35f) }
    RetroWindow(stringResource(R.string.retro_sc_progress_window), Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroIconButton(onClick = { progress = (progress - 0.1f).coerceAtLeast(0f) }) { PixelIcon("minus", size = 16.dp) }
            RetroLinearProgress(progress = { progress }, modifier = Modifier.weight(1f))
            RetroIconButton(onClick = { progress = (progress + 0.1f).coerceAtMost(1f) }) { PixelIcon("plus", size = 16.dp) }
        }
        RetroLoadingBar()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HourglassSpinner()
            Text(stringResource(R.string.retro_sc_loading), style = Retro.type.mono)
            BlinkingCursor()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconsSection() {
    val c = Retro.colors
    val keys = remember { PixelIconKeys.sorted() }
    RetroWindow(stringResource(R.string.retro_sc_icons_window), Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEachIndexed { i, key ->
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val tint = when (key) {
                        "heart", "flame", "error", "skull" -> c.danger
                        "coin", "star", "crown", "trophy", "lightning" -> c.gold
                        "medal" -> c.silver
                        else -> c.ink
                    }
                    PixelIcon(key, Modifier.pixelFloat(amplitude = 1.dp, phase = (i % 7) / 7f), tint = tint, size = 32.dp)
                    Text(key, style = Retro.type.label.copy(fontSize = 8.sp, lineHeight = 10.sp), color = c.inkSoft, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimationsSection(onConfetti: () -> Unit, onTrophy: (BadgeTier) -> Unit, onLevelUp: () -> Unit) {
    val c = Retro.colors
    var typeKey by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(1234) }
    var shake by remember { mutableIntStateOf(0) }
    var password by remember { mutableStateOf("") }
    var phaseB by remember { mutableStateOf(false) }
    var child by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<RetroIconKind?>(null) }
    RetroWindow(stringResource(R.string.retro_sc_anim_window), Modifier.fillMaxWidth()) {
        // Typewriter
        Box(Modifier.fillMaxWidth().height(72.dp)) {
            key(typeKey) { TypewriterText(stringResource(R.string.retro_sc_typewriter_text)) }
        }
        RetroOutlinedButton(onClick = { typeKey++ }) { Text(stringResource(R.string.retro_sc_replay)) }
        RetroDivider(dashed = true)

        // Count up
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountUpText(count, Modifier.weight(1f), from = count)
            RetroOutlinedButton(onClick = { count = Random.nextInt(0, 9999) }) { Text(stringResource(R.string.retro_sc_count)) }
        }
        RetroDivider(dashed = true)

        // Shake
        RetroTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth().shake(shake),
            label = stringResource(R.string.retro_sc_password),
            isError = shake > 0,
            supportingText = if (shake > 0) stringResource(R.string.retro_sc_password_error) else null,
        )
        RetroOutlinedButton(onClick = { shake++ }) { Text(stringResource(R.string.retro_sc_shake)) }
        RetroDivider(dashed = true)

        // Dither dissolve + window zoom
        DitherCrossfade(phaseB, Modifier.fillMaxWidth()) { b ->
            RetroCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelIcon(if (b) "mountain" else "map", tint = if (b) c.accent else c.accent2, size = 32.dp)
                    Text(stringResource(if (b) R.string.retro_sc_dissolve_b else R.string.retro_sc_dissolve_a), style = Retro.type.heading)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroOutlinedButton(onClick = { phaseB = !phaseB }) { Text(stringResource(R.string.retro_sc_dissolve)) }
            RetroOutlinedButton(onClick = { child = !child }) { Text(stringResource(R.string.retro_sc_open_window)) }
            RetroButton(onClick = onConfetti) {
                PixelIcon("star", size = 16.dp)
                Text(stringResource(R.string.retro_sc_confetti))
            }
            RetroButton(onClick = onLevelUp) {
                PixelIcon("lightning", size = 16.dp)
                Text(stringResource(R.string.retro_sc_level_up))
            }
        }
        if (child) {
            RetroWindow(
                stringResource(R.string.retro_sc_child_window),
                Modifier.fillMaxWidth().padding(end = 4.dp),
                onClose = { child = false },
            ) {
                Text(stringResource(R.string.retro_sc_child_text), style = Retro.type.body)
            }
        }
        RetroDivider(dashed = true)

        SectionLabel(stringResource(R.string.retro_sc_trophies))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgeTier.entries.forEach { tier ->
                RetroOutlinedButton(onClick = { onTrophy(tier) }) {
                    PixelIcon("trophy", tint = tier.color(), size = 16.dp)
                    Text(tier.label())
                }
            }
        }
        SectionLabel(stringResource(R.string.retro_sc_dialogs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroIconKind.entries.forEach { kind ->
                RetroIconButton(onClick = { dialog = kind }) { PixelIcon(kind.icon) }
            }
        }
    }
    dialog?.let { kind ->
        val (title, text) = when (kind) {
            RetroIconKind.ERROR -> R.string.retro_sc_error_title to R.string.retro_sc_error_text
            RetroIconKind.WARNING -> R.string.retro_sc_warning_title to R.string.retro_sc_warning_text
            RetroIconKind.INFO -> R.string.retro_sc_info_title to R.string.retro_sc_info_text
            RetroIconKind.QUESTION -> R.string.retro_sc_question_title to R.string.retro_sc_question_text
        }
        RetroDialog(
            onDismissRequest = { dialog = null },
            title = stringResource(title),
            icon = kind,
            text = { Text(stringResource(text)) },
            confirmButton = { RetroButton(onClick = { dialog = null }) { Text(stringResource(R.string.retro_sc_ok)) } },
            dismissButton = if (kind == RetroIconKind.QUESTION || kind == RetroIconKind.ERROR) {
                { RetroOutlinedButton(onClick = { dialog = null }) { Text(stringResource(R.string.retro_sc_cancel)) } }
            } else null,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = Retro.type.label, color = Retro.colors.inkSoft)
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = Retro.type.body)
        RetroSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Preview(name = "Bege claro", heightDp = 2400)
@Composable
private fun ShowcaseBegeLight() {
    DesignShowcaseScreen(RetroPalette.BEGE, DarkMode.LIGHT, {}, {}, {})
}

@Preview(name = "Almond escuro", heightDp = 2400)
@Composable
private fun ShowcaseAlmondDark() {
    DesignShowcaseScreen(RetroPalette.ALMOND, DarkMode.DARK, {}, {}, {})
}

@Preview(name = "Floresta claro", heightDp = 2400)
@Composable
private fun ShowcaseFlorestaLight() {
    DesignShowcaseScreen(RetroPalette.FLORESTA, DarkMode.LIGHT, {}, {}, {})
}
