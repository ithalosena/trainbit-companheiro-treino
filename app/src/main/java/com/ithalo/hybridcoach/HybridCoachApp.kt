package com.ithalo.hybridcoach

import android.app.Application
import com.ithalo.hybridcoach.core.avatar.AvatarEvent
import com.ithalo.hybridcoach.core.avatar.AvatarInput
import com.ithalo.hybridcoach.core.game.GameState
import com.ithalo.hybridcoach.data.AppDatabase
import com.ithalo.hybridcoach.data.GameSnapshot
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.ui.avatar.AvatarAssets
import com.ithalo.hybridcoach.ui.avatar.AvatarEvents
import kotlinx.coroutines.flow.map
import com.ithalo.hybridcoach.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.ithalo.hybridcoach.core.schedule.Reminder
import com.ithalo.hybridcoach.reminders.TrainingReminders

/** Manual dependency container; no DI framework. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val repository = PlanRepository(AppDatabase.create(app), app)
    val settings = SettingsRepository(app)

    private val snapshot: StateFlow<GameSnapshot?> = repository.observeGameSnapshot()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shared game state for the overlay and the game screens; null until the first evaluation. */
    val gameState: StateFlow<GameState?> = snapshot.map { it?.state }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** What the pet avatar's mood is computed from; null until the first evaluation. */
    val avatarInput: StateFlow<AvatarInput?> = snapshot.map { it?.avatar }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** One-shot avatar animations asked for by other screens (level up, finished sessions). */
    val avatarEvents = AvatarEvents()

    /** The next training reminder that is set, or null (off, or nothing pending). */
    val nextReminder = MutableStateFlow<Reminder?>(null)

    init {
        repository.onSessionFinished = { isRun ->
            avatarEvents.post(if (isRun) AvatarEvent.TRAIN_RUN else AvatarEvent.TRAIN_STRENGTH)
        }
        // The reminder alarm follows the settings and the plan (a session done, moved or a new plan) while the app runs.
        scope.launch {
            val sessions = repository.observeActivePlan().flatMapLatest { plan ->
                if (plan == null) flowOf(emptyList()) else repository.observeSessions(plan.id)
            }
            combine(settings.settings, sessions) { s, list -> s to list }.collect { (s, list) ->
                nextReminder.value = TrainingReminders.schedule(app, s, list)
            }
        }
    }
}

class HybridCoachApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { container.repository.seedExercises() }
        // The pet art made by tools/avatar/build.py; until it is in (or if it fails) the art baked in the core is used.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { AvatarAssets.install(this@HybridCoachApp) }
    }
}
