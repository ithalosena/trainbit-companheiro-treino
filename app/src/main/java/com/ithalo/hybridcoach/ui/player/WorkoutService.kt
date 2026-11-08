package com.ithalo.hybridcoach.ui.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ithalo.hybridcoach.MainActivity
import com.ithalo.hybridcoach.R

/**
 * Keeps a strength workout "in progress" after the player is left: a foreground service with an ongoing
 * notification (title of the session and the elapsed time) so the app process is not killed in the background
 * while the rest countdown and the coach voice are running. Started when the player opens an unfinished
 * session, stopped when the workout is finished. It stops itself after [MAX_HOURS] in case nobody finishes it.
 */
class WorkoutService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopLater = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Restarted by the system without its data: nothing to show.
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val startedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
        createChannel()
        val notification = notification(title, startedAt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification)
        }
        handler.removeCallbacks(stopLater)
        handler.postDelayed(stopLater, MAX_HOURS * 3_600_000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopLater)
        super.onDestroy()
    }

    private fun notification(title: String, startedAt: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.workout_notification_title))
            .setContentText(title)
            .setUsesChronometer(true)
            .setWhen(startedAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.workout_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "workout"
        private const val ID = 4200
        private const val MAX_HOURS = 4
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_STARTED_AT = "startedAt"

        /** Starts (or refreshes) the notification; [startedAt] is when the workout began, in wall-clock millis. */
        fun start(context: Context, title: String, startedAt: Long) {
            val intent = Intent(context, WorkoutService::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_STARTED_AT, startedAt)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Started while the app cannot start foreground services: the workout itself still works.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
