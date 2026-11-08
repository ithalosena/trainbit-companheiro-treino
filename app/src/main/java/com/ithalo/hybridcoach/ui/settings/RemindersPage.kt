package com.ithalo.hybridcoach.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.schedule.Reminders
import com.ithalo.hybridcoach.data.AppSettings
import com.ithalo.hybridcoach.reminders.TrainingReminders
import com.ithalo.hybridcoach.ui.common.ChoiceChips
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.SwitchRow
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.player.RestNotifier
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroDivider
import com.ithalo.hybridcoach.ui.retro.RetroIconButton
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.theme.Retro
import java.time.format.DateTimeFormatter

private const val STEP_MINUTES = 15

/**
 * Training reminders: on/off, the usual start time of the gym session and of the run, how long before to warn,
 * and the next reminder that is set. Asks for the notification permission when turned on, and offers the
 * system's "alarms and reminders" switch when exact alarms are not allowed.
 */
@Composable
fun RemindersPage(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    val next by appContainer().nextReminder.collectAsStateWithLifecycle()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var exact by remember { mutableStateOf(TrainingReminders.exactAllowed(context)) }
    var canNotify by remember { mutableStateOf(RestNotifier.canNotify(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // Coming back from the system settings: read the permissions again and re-set the alarm.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            exact = TrainingReminders.exactAllowed(context)
            canNotify = RestNotifier.canNotify(context)
            refresh()
        }
    }

    SwitchRow(stringResource(R.string.reminders_switch), s.remindersEnabled) { on ->
        update { it.copy(remindersEnabled = on) }
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canNotify) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    HelpText(stringResource(R.string.reminders_help))
    if (!s.remindersEnabled) return

    RetroDivider()
    SectionTitle(stringResource(R.string.reminders_times))
    TimeStepper(stringResource(R.string.reminders_strength), s.reminderStrengthMinutes) { v -> update { it.copy(reminderStrengthMinutes = v) } }
    TimeStepper(stringResource(R.string.reminders_run), s.reminderRunMinutes) { v -> update { it.copy(reminderRunMinutes = v) } }
    SectionTitle(stringResource(R.string.reminders_lead))
    ChoiceChips(Reminders.LEAD_CHOICES, s.reminderLeadMinutes, { leadLabel(it) }) { v -> update { it.copy(reminderLeadMinutes = v) } }

    RetroDivider()
    Text(
        next?.let { r ->
            stringResource(
                R.string.reminders_next,
                r.at.format(DateTimeFormatter.ofPattern("EEE, dd/MM 'às' HH:mm", PT_BR)),
                r.session.title,
            )
        } ?: stringResource(R.string.reminders_none),
        style = Retro.type.body,
        color = Retro.colors.ink,
    )
    if (!canNotify) InfoCard(stringResource(R.string.reminders_no_permission), isError = true)
    if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        InfoCard(stringResource(R.string.reminders_inexact))
        RetroOutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            PixelIcon("clock", size = 16.dp)
            Text(stringResource(R.string.reminders_allow_exact))
        }
    }
}

/** A time of day in 15-minute steps, wrapping around midnight. */
@Composable
private fun TimeStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val day = 24 * 60
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = Retro.type.body)
        RetroIconButton(onClick = { onChange(Math.floorMod(minutes - STEP_MINUTES, day)) }) { PixelIcon("minus", size = 16.dp) }
        Text("%02d:%02d".format(minutes / 60, minutes % 60), Modifier.width(80.dp).wrapContentSize(), style = Retro.type.heading, color = Retro.colors.ink)
        RetroIconButton(onClick = { onChange((minutes + STEP_MINUTES) % day) }) { PixelIcon("plus", size = 16.dp) }
    }
}

@Composable
private fun leadLabel(minutes: Int) =
    if (minutes == 0) stringResource(R.string.reminders_lead_now) else stringResource(R.string.reminders_lead_min, minutes)
