package com.ithalo.hybridcoach.ui.common

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ithalo.hybridcoach.HybridCoachApp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.ui.retro.RetroBackground
import com.ithalo.hybridcoach.ui.retro.RetroCard
import com.ithalo.hybridcoach.ui.retro.RetroCheckbox
import com.ithalo.hybridcoach.ui.retro.RetroFilterChip
import com.ithalo.hybridcoach.ui.retro.RetroIconButton
import com.ithalo.hybridcoach.ui.retro.RetroSwitch
import com.ithalo.hybridcoach.ui.retro.RetroTextButton
import com.ithalo.hybridcoach.ui.retro.RetroTextField
import com.ithalo.hybridcoach.ui.retro.LocalUnderHud
import com.ithalo.hybridcoach.ui.retro.RetroTitleBar
import com.ithalo.hybridcoach.ui.retro.RetroTitleStrip
import com.ithalo.hybridcoach.ui.retro.RetroTopBar
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.hardShadow
import com.ithalo.hybridcoach.ui.theme.RetroColors
import com.ithalo.hybridcoach.ui.retro.pixelBorder
import com.ithalo.hybridcoach.ui.retro.shake
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.contrastRatio
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private val SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM", PT_BR)
private val LONG_DATE = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", PT_BR)
private val FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)

fun LocalDate.short(): String = format(SHORT_DATE)
fun LocalDate.long(): String = format(LONG_DATE)
fun LocalDate.full(): String = format(FULL_DATE)

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (PlanRepository) -> VM): VM {
    val app = LocalContext.current.applicationContext as HybridCoachApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container.repository) } })
}

/** Same as [appViewModel] for view models that also need the application (e.g. for text to speech). */
@Composable
inline fun <reified VM : ViewModel> appContextViewModel(crossinline create: (PlanRepository, Application) -> VM): VM {
    val app = LocalContext.current.applicationContext as HybridCoachApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container.repository, app) } })
}

/** The app's dependency container, for composables that read shared state (game, settings). */
@Composable
fun appContainer() = (LocalContext.current.applicationContext as HybridCoachApp).container

/** Ink or window colour, whichever reads better on [bg]. */
@Composable
fun readableOn(bg: Color): Color {
    val c = Retro.colors
    return if (contrastRatio(bg, c.ink) >= contrastRatio(bg, c.window)) c.ink else c.window
}

/**
 * Retro screen: the header and the content below. Tab roots (under the HUD, no back) get the dithered
 * title strip with an optional [subtitle] on its paper end; pushed screens get the [RetroTopBar].
 * The content is padded for the navigation bar and the keyboard.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbarHost: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    subtitle: String? = null,
    /** False on a tab screen that names its sections itself (a sub-menu right under the main tabs). */
    showTitle: Boolean = true,
    content: @Composable () -> Unit,
) {
    RetroBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val underHud = LocalUnderHud.current && onBack == null
            if (underHud && showTitle) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RetroTitleStrip(title, Modifier.weight(1f), trailing = subtitle)
                    actions()
                }
            } else if (!underHud) {
                RetroTopBar(title = title, onBack = onBack, actions = actions)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                content()
                snackbarHost?.let { host ->
                    SnackbarHost(host, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { data ->
                        RetroSnackbar(data.visuals.message)
                    }
                }
            }
        }
    }
}

@Composable
private fun RetroSnackbar(message: String) {
    val c = Retro.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.ink)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PixelIcon("check", tint = c.background, size = 18.dp)
        Text(message, style = Retro.type.body, color = c.background)
    }
}

/** Vertical scrolling column with the standard screen padding: wide margins, airy spacing. */
@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

fun shareText(context: Context, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/** Message box: pixel icon + text on a tinted well (no frame). The error variant gets a danger frame and shakes when its text changes. */
@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    val c = Retro.colors
    Row(
        modifier
            .fillMaxWidth()
            .shake(if (isError) text else null)
            .background(c.windowInset)
            .then(if (isError) Modifier.pixelBorder(c.danger) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PixelIcon(if (isError) "error" else "info", tint = if (isError) c.danger else c.accent2, size = 24.dp)
        Text(text, Modifier.weight(1f), style = Retro.type.body, color = c.ink)
    }
}

/**
 * Error box like the inspiration "ERROR" windows: striped title, error icon, message and actions.
 * Shakes whenever [message] changes.
 */
@Composable
fun ErrorWindow(title: String, message: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    val c = Retro.colors
    RetroWindow(title = title, modifier = modifier.fillMaxWidth().shake(message)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            PixelIcon("error", tint = c.danger, size = 32.dp)
            Text(message, Modifier.weight(1f), style = Retro.type.body, color = c.ink)
        }
        actions()
    }
}

/** Same small caps + dotted rule as a section window header, so headings look alike everywhere. */
@Composable
fun SectionTitle(text: String) {
    RetroTitleBar(text.uppercase(PT_BR), Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            RetroFilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChips(options: List<T>, selected: Set<T>, label: @Composable (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            RetroFilterChip(selected = option in selected, onClick = { onToggle(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
fun IntStepper(label: String, value: Int, range: IntRange, step: Int = 1, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = Retro.type.body)
        RetroIconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) { PixelIcon("minus", size = 16.dp) }
        Text("$value$suffix", Modifier.width(80.dp).wrapContentSize(), style = Retro.type.heading, color = Retro.colors.ink)
        RetroIconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) { PixelIcon("plus", size = 16.dp) }
    }
}

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
) {
    RetroTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = supporting,
    )
}

/** Small explanatory text under a question. */
@Composable
fun HelpText(text: String) {
    Text(text, style = Retro.type.bodySmall, color = Retro.colors.inkSoft)
}

/** Read-only field that opens a date picker; [value] is ISO `yyyy-MM-dd` or blank. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, supporting: String? = null, onClear: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
    Box {
        RetroTextField(
            value = date?.long().orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = label,
            trailing = { PixelIcon("calendar", size = 20.dp) },
            supportingText = supporting,
            modifier = Modifier.fillMaxWidth(),
        )
        // The read-only field swallows taps, so an invisible layer on top opens the picker.
        Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, indication = null) { open = true })
    }
    if (onClear != null && date != null) {
        RetroTextButton(onClick = onClear) { Text(stringResource(R.string.date_clear)) }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                RetroTextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { RetroTextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = Retro.type.body)
        RetroSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** Checkbox with its label; the whole row is tappable. */
@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RetroCheckbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = Retro.type.body)
    }
}

/** Sunken well used for terminal-like monospace text. */
fun Modifier.terminalFrame(c: RetroColors): Modifier =
    bevel(c.windowInset, c.border, c.bevelLight, c.bevelDark, raised = false).padding(12.dp)

/**
 * One entry of a menu screen: icon (or a step number when [step] is set), name, one-line
 * description and an arrow. [highlight] fills the icon slot to mark the suggested next step.
 */
@Composable
fun MenuRow(icon: String, title: String, description: String, step: Int? = null, highlight: Boolean = false, onClick: () -> Unit) {
    val c = Retro.colors
    RetroCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(36.dp).then(if (highlight) Modifier.background(c.accent2) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (step != null) {
                    Text(step.toString(), style = Retro.type.number.copy(fontSize = 20.sp, lineHeight = 24.sp), color = if (highlight) c.onAccent2 else c.inkSoft)
                } else {
                    PixelIcon(icon, tint = if (highlight) c.onAccent2 else c.inkSoft, size = 24.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Retro.type.heading, color = c.ink)
                Text(description, style = Retro.type.bodySmall, color = c.inkSoft)
            }
            PixelIcon("arrow_right", tint = c.inkFaint, size = 16.dp)
        }
    }
}

/** "Label ...... value" line used in summaries. */
@Composable
fun KeyValueLine(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(key, Modifier.weight(1f), style = Retro.type.body, color = Retro.colors.inkSoft)
        Text(value, style = Retro.type.heading, color = Retro.colors.ink)
    }
}
