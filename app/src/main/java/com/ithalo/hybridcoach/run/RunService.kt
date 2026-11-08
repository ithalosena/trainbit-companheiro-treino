package com.ithalo.hybridcoach.run

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ithalo.hybridcoach.HybridCoachApp
import com.ithalo.hybridcoach.MainActivity
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.run.Cue
import com.ithalo.hybridcoach.core.run.Fix
import com.ithalo.hybridcoach.core.run.LocationFilter
import com.ithalo.hybridcoach.core.run.RunEngine
import com.ithalo.hybridcoach.core.run.RunState
import com.ithalo.hybridcoach.ui.common.paceText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service (type location) that runs the [RunEngine] with GPS fixes and a 1 s ticker, speaks the cues
 * and saves progress every few seconds, so the run keeps going with the screen locked and can be resumed if the
 * process dies. Everything touching the engine runs on the main thread.
 */
class RunService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as HybridCoachApp).container

    private var engine: RunEngine? = null
    private var sessionId = 0L
    private var title = ""
    private var batteryStart: Int? = null
    private var storeTrack = false
    private val track = mutableListOf<Fix>()
    private var lastAccuracy: Float? = null
    private var lastPersist = 0L
    private var lastNotify = 0L
    private var ticker: Job? = null
    private var completing = false
    /** True between a start request and the engine being ready, so a double tap does not start twice. */
    private var starting = false

    private lateinit var voice: RunVoice
    private var speech: RunSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val e = engine ?: return
            for (loc in result.locations) {
                val fix = loc.toFix()
                lastAccuracy = fix.accuracyM
                if (storeTrack && !e.state.paused && fix.accuracyM <= LocationFilter.MAX_ACCURACY_M) track += fix
                handle(e.onFix(fix, System.currentTimeMillis()))
            }
            publish()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        voice = RunVoice(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = System.currentTimeMillis()
        when (intent?.action) {
            null, ACTION_START -> {
                if (!goForeground()) return START_NOT_STICKY
                if (engine == null && !starting) {
                    starting = true
                    val kmh = intent?.getDoubleExtra(EXTRA_TREADMILL_KMH, -1.0)?.takeIf { it > 0 }
                    scope.launch { begin(intent?.getLongExtra(EXTRA_SESSION, 0L) ?: 0L, kmh) }
                }
            }
            ACTION_PAUSE -> engine?.let {
                it.pause(now)
                voice.speak(getString(R.string.voice_paused))
                afterChange()
            }
            ACTION_RESUME -> engine?.let {
                it.resume(now)
                voice.speak(getString(R.string.voice_resumed))
                afterChange()
            }
            ACTION_HOLD -> engine?.let {
                it.holdPause()
                voice.speak(getString(R.string.voice_hold_pause))
                afterChange()
            }
            ACTION_SKIP -> engine?.let { handle(it.skipStep(now)); afterChange() }
            ACTION_SPEED -> engine?.let { it.setTreadmillSpeed(intent?.getDoubleExtra(EXTRA_TREADMILL_KMH, 0.0) ?: 0.0, now); afterChange() }
            ACTION_FINISH -> engine?.let { handle(it.finish(now)) }
        }
        // A control action without a running run (e.g. a stale notification button) has nothing to act on.
        if (engine == null && !starting && intent?.action != null && intent.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private suspend fun begin(requested: Long, treadmillKmh: Double?) {
        val repo = container.repository
        val progress = repo.runProgress()
        val recovered = progress != null
        val state: RunState
        if (progress != null) {
            sessionId = progress.sessionId
            state = RunState.fromJson(progress.stateJson)
            batteryStart = progress.batteryStart
        } else {
            if (requested <= 0) {
                starting = false
                stopSelf()
                return
            }
            sessionId = requested
            state = RunState(startedAtMs = System.currentTimeMillis(), treadmillKmh = treadmillKmh)
            batteryStart = battery()
        }
        title = repo.observeSession(sessionId).first()?.title.orEmpty()
        val settings = container.settings.current()
        voice.enabled = settings.voiceEnabled
        voice.volume = settings.voiceVolume
        voice.configure(settings.voiceName, settings.voiceRate)
        storeTrack = settings.storeTrack
        val e = RunEngine(repo.runSteps(sessionId), settings.runSettings, state)
        engine = e
        starting = false

        acquireWakeLock()
        speech = RunSpeech(resources, treadmill = state.treadmillKmh != null)
        if (state.treadmillKmh == null) startLocation()
        if (recovered) {
            voice.speak(getString(R.string.voice_recovered))
        } else {
            voice.speak(getString(R.string.voice_started))
            handle(e.start(System.currentTimeMillis()))
        }
        persist()
        publish()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val current = engine ?: break
                handle(current.onTick(System.currentTimeMillis()))
                publish()
                val now = System.currentTimeMillis()
                if (now - lastPersist >= PERSIST_EVERY_MS) persist()
            }
        }
    }

    private fun handle(cues: List<Cue>) {
        cues.forEach { cue -> speech?.let { voice.speak(it.text(cue)) } }
        if (cues.any { it is Cue.Finished }) complete()
    }

    private fun afterChange() {
        publish()
        scope.launch { persist() }
    }

    private fun complete() {
        val e = engine ?: return
        if (completing) return
        completing = true
        ticker?.cancel()
        fused.removeLocationUpdates(callback)
        scope.launch {
            val id = withContext(NonCancellable) {
                container.repository.saveTrackPoints(sessionId, track.toList())
                track.clear()
                container.repository.saveRun(sessionId, e.state, batteryStart, battery())
            }
            publish(runLogId = id)
            engine = null
            ServiceCompat.stopForeground(this@RunService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // Let the final summary finish speaking before the voice is shut down with the service.
            withTimeoutOrNull(FINAL_SPEECH_MAX_MS) { while (voice.speaking) delay(200) }
            releaseWakeLock()
            stopSelf()
        }
    }

    private suspend fun persist() {
        val e = engine ?: return
        if (e.state.finished) return
        lastPersist = System.currentTimeMillis()
        val points = track.toList()
        track.clear()
        container.repository.saveRunProgress(sessionId, e.state, batteryStart)
        container.repository.saveTrackPoints(sessionId, points)
    }

    private fun publish(runLogId: Long? = null) {
        val e = engine ?: return
        val s = e.state
        val now = System.currentTimeMillis()
        val active = s.activeMs(s.endedAtMs ?: now)
        val ui = RunUi(
            sessionId = sessionId,
            title = title,
            activeSeconds = active / 1000,
            distanceM = s.distanceM,
            paceSecPerKm = if (s.paused) null else e.currentPace(),
            avgPaceSecPerKm = RunEngine.averagePace(s.distanceM, active),
            step = e.currentStep,
            stepNumber = (s.stepIndex + 1).coerceAtMost(e.steps.size),
            totalSteps = e.steps.size,
            stepSeconds = (active - s.stepStartActiveMs) / 1000,
            stepDistanceM = s.distanceM - s.stepStartDistanceM,
            paused = s.paused,
            gpsAccuracyM = lastAccuracy,
            splits = s.splitSeconds,
            finished = s.finished,
            runLogId = runLogId,
            treadmillKmh = s.treadmillKmh,
            autoPaused = s.autoPaused,
            blocksDone = s.blocksDone,
        )
        RunTracker.mutable.value = ui
        if (!s.finished && (now - lastNotify >= NOTIFY_EVERY_MS || runLogId != null)) {
            lastNotify = now
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(ui))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!hasLocationPermission(this)) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMinUpdateIntervalMillis(1000L).build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    /** Returns false (and stops) when the system refuses the foreground service, e.g. without location permission. */
    private fun goForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(RunTracker.state.value), type)
        true
    } catch (e: Exception) {
        // Typically a background restart without "allow all the time": the run stays saved and the app offers to resume it.
        stopSelf()
        false
    }

    private fun notification(ui: RunUi?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val text = ui?.let {
            getString(R.string.run_notification_text, "%.2f".format(com.ithalo.hybridcoach.ui.common.PT_BR, it.distanceM / 1000), elapsedText(it.activeSeconds)) +
                (it.paceSecPerKm?.let { p -> " · ${paceText(p)}/km" } ?: "")
        } ?: getString(R.string.run_notification_starting)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(if (ui?.paused == true) R.string.run_notification_paused else R.string.run_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (ui != null && !ui.finished) {
            val action = if (ui.paused) ACTION_RESUME else ACTION_PAUSE
            val pi = PendingIntent.getService(this, action.hashCode(), Intent(this, RunService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, getString(if (ui.paused) R.string.run_resume else R.string.run_pause), pi)
        }
        return builder.build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.run_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HybridCoach:run")
            .apply { acquire(MAX_RUN_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun battery(): Int? =
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        ticker?.cancel()
        // Save the latest state if the service is destroyed mid-run (it is resumed from it later).
        engine?.let { e -> if (!e.state.finished) CoroutineScope(Dispatchers.IO).launch { container.repository.saveRunProgress(sessionId, e.state, batteryStart) } }
        releaseWakeLock()
        voice.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ithalo.hybridcoach.run.START"
        const val ACTION_PAUSE = "com.ithalo.hybridcoach.run.PAUSE"
        const val ACTION_RESUME = "com.ithalo.hybridcoach.run.RESUME"
        const val ACTION_SKIP = "com.ithalo.hybridcoach.run.SKIP"
        /** After an automatic pause: "I need a break", so moving around does not resume the run. */
        const val ACTION_HOLD = "com.ithalo.hybridcoach.run.HOLD"
        const val ACTION_FINISH = "com.ithalo.hybridcoach.run.FINISH"
        const val ACTION_SPEED = "com.ithalo.hybridcoach.run.SPEED"
        const val EXTRA_TREADMILL_KMH = "treadmillKmh"
        const val EXTRA_SESSION = "sessionId"
        private const val CHANNEL_ID = "run"
        private const val NOTIFICATION_ID = 42
        private const val PERSIST_EVERY_MS = 5_000L
        private const val NOTIFY_EVERY_MS = 5_000L
        private const val MAX_RUN_MS = 4 * 60 * 60 * 1000L
        private const val FINAL_SPEECH_MAX_MS = 15_000L

        /**
         * Starts a new run (GPS, or treadmill at [treadmillKmh]), or resumes the saved one when there is progress
         * in the database.
         */
        fun start(context: Context, sessionId: Long, treadmillKmh: Double? = null) {
            val intent = Intent(context, RunService::class.java).setAction(ACTION_START).putExtra(EXTRA_SESSION, sessionId)
            treadmillKmh?.let { intent.putExtra(EXTRA_TREADMILL_KMH, it) }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setSpeed(context: Context, kmh: Double) {
            context.startService(Intent(context, RunService::class.java).setAction(ACTION_SPEED).putExtra(EXTRA_TREADMILL_KMH, kmh))
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, RunService::class.java).setAction(action))
        }

        fun hasLocationPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun elapsedText(totalSeconds: Long): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }
    }
}

private fun Location.toFix() = Fix(time, latitude, longitude, if (hasAccuracy()) accuracy else Float.MAX_VALUE)
