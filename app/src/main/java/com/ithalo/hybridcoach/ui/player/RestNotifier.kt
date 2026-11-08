package com.ithalo.hybridcoach.ui.player

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ithalo.hybridcoach.MainActivity
import com.ithalo.hybridcoach.R

/**
 * The rest between sets as a notification with a countdown the system draws itself, so it keeps running when the
 * player screen is left or the app goes to the background. An alarm posts "rest over" at the end even if the app
 * process is gone. Showing it needs the notification permission (Android 13+); without it nothing is posted.
 */
class RestNotifier(context: Context) {
    private val app = context.applicationContext

    /** Starts (or restarts, after an adjustment) the countdown of [remainingMs]. */
    @SuppressLint("MissingPermission")
    fun show(remainingMs: Long) {
        if (remainingMs <= 0) return
        createChannels(app)
        val endAt = System.currentTimeMillis() + remainingMs
        val notification = NotificationCompat.Builder(app, CHANNEL_REST)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(app.getString(R.string.rest_notification_title))
            .setContentText(app.getString(R.string.rest_notification_text))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(app))
            .setTimeoutAfter(remainingMs + 60_000)
            .build()
        if (canNotify(app)) NotificationManagerCompat.from(app).notify(ID, notification)
        app.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, doneIntent(app))
    }

    /** The workout is open and unfinished: keeps it "in progress" in the notifications and the app alive. */
    fun workoutStarted(title: String, startedAt: Long) = WorkoutService.start(app, title, startedAt)

    fun workoutEnded() = WorkoutService.stop(app)

    /** Rest skipped or the session ended: drops the countdown and the pending alarm. */
    fun cancel() {
        app.getSystemService(AlarmManager::class.java).cancel(doneIntent(app))
        NotificationManagerCompat.from(app).cancel(ID)
    }

    /** The countdown reached zero while the app was alive: same "rest over" notice the alarm would post. */
    fun done() {
        app.getSystemService(AlarmManager::class.java).cancel(doneIntent(app))
        postDone(app)
    }

    /** Receives the alarm that marks the end of the rest. */
    class DoneReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = postDone(context)
    }

    companion object {
        private const val CHANNEL_REST = "rest"
        private const val CHANNEL_REST_DONE = "rest_done"
        private const val ID = 4201

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_REST, context.getString(R.string.rest_channel), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_REST_DONE, context.getString(R.string.rest_done_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }

        @SuppressLint("MissingPermission")
        private fun postDone(context: Context) {
            createChannels(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_REST_DONE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.rest_done_title))
                .setContentText(context.getString(R.string.rest_done_text))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setTimeoutAfter(30_000)
                .setContentIntent(openApp(context))
                .build()
            if (canNotify(context)) NotificationManagerCompat.from(context).notify(ID, notification)
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )

        private fun doneIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1, Intent(context, DoneReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
