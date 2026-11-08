package com.ithalo.hybridcoach.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ithalo.hybridcoach.HybridCoachApp
import com.ithalo.hybridcoach.MainActivity
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.schedule.Reminder
import com.ithalo.hybridcoach.core.schedule.ReminderSession
import com.ithalo.hybridcoach.core.schedule.ReminderSettings
import com.ithalo.hybridcoach.core.schedule.Reminders
import com.ithalo.hybridcoach.data.AppSettings
import com.ithalo.hybridcoach.data.PlannedSessionEntity
import com.ithalo.hybridcoach.ui.player.RestNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Training reminders: one system alarm at a time, set for the next pending session (see [Reminders.next]).
 * When it fires, [Receiver] posts the notification and sets the following one. The alarm is exact when the
 * system allows it (Android 12+ asks the player for "alarms and reminders"); otherwise it may come a few
 * minutes late. Rescheduled when the app starts, when the settings or the plan change, and after a reboot.
 */
object TrainingReminders {
    private const val TAG = "Reminders"
    private const val CHANNEL = "reminders"
    private const val NOTIFICATION_ID = 7301
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_TIME = "time"
    private const val EXTRA_RUN = "run"
    private const val EXTRA_LEAD = "lead"

    fun settingsOf(s: AppSettings) = ReminderSettings(
        enabled = s.remindersEnabled,
        strengthTime = LocalTime.of(s.reminderStrengthMinutes / 60, s.reminderStrengthMinutes % 60),
        runTime = LocalTime.of(s.reminderRunMinutes / 60, s.reminderRunMinutes % 60),
        leadMinutes = s.reminderLeadMinutes,
    )

    fun sessionsOf(list: List<PlannedSessionEntity>) = list.mapNotNull { e ->
        val date = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@mapNotNull null
        // Complementary sessions get no reminder: they are a bonus, not an appointment.
        ReminderSession(e.id, date, e.type == "run", e.title, e.status == "pending" && !e.optional)
    }

    /** True when alarms can be exact (always before Android 12). */
    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Sets the alarm for the next reminder (or cancels it); returns that reminder. */
    fun schedule(context: Context, settings: AppSettings, sessions: List<PlannedSessionEntity>): Reminder? {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java)
        val next = Reminders.next(LocalDateTime.now(), sessionsOf(sessions), settingsOf(settings))
        if (next == null) {
            alarms.cancel(alarmIntent(app, null, settings.reminderLeadMinutes))
            return null
        }
        val at = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = alarmIntent(app, next, settings.reminderLeadMinutes)
        try {
            if (exactAllowed(app)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused; using an inexact one", e)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        return next
    }

    /** Reads the settings and the active plan, then [schedule]s. For receivers and the app start. */
    suspend fun reschedule(context: Context): Reminder? {
        val container = (context.applicationContext as HybridCoachApp).container
        val settings = container.settings.current()
        val plan = container.repository.activePlan()
        val sessions = plan?.let { container.repository.observeSessions(it.id).first() }.orEmpty()
        return schedule(context, settings, sessions)
    }

    private fun alarmIntent(app: Context, r: Reminder?, lead: Int): PendingIntent {
        val intent = Intent(app, Receiver::class.java)
        if (r != null) {
            intent.putExtra(EXTRA_TITLE, r.session.title)
                .putExtra(EXTRA_TIME, "%02d:%02d".format(r.startsAt.hour, r.startsAt.minute))
                .putExtra(EXTRA_RUN, r.session.isRun)
                .putExtra(EXTRA_LEAD, lead)
        }
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    private fun notify(app: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()
        val lead = intent.getIntExtra(EXTRA_LEAD, 0)
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val heading = app.getString(
            if (intent.getBooleanExtra(EXTRA_RUN, false)) R.string.reminder_title_run else R.string.reminder_title_strength,
            time,
        )
        val text = if (lead > 0) app.getString(R.string.reminder_text_lead, title, lead) else app.getString(R.string.reminder_text_now, title)
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(heading)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        if (RestNotifier.canNotify(app)) NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
    }

    /** The alarm went off: show the reminder, then set the next one. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            notify(context.applicationContext, intent)
            rescheduleAsync(context)
        }
    }

    /** Alarms are wiped by a reboot, an app update or a clock change: set the next one again. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = rescheduleAsync(context)
    }

    private fun BroadcastReceiver.rescheduleAsync(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                reschedule(context)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set the next reminder", e)
            } finally {
                pending.finish()
            }
        }
    }
}
