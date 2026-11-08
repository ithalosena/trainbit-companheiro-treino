package com.ithalo.hybridcoach.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ithalo.hybridcoach.core.game.GameState
import com.ithalo.hybridcoach.core.game.ItemCatalog
import com.ithalo.hybridcoach.core.game.ItemUnlock
import com.ithalo.hybridcoach.core.run.RunSettings
import com.ithalo.hybridcoach.ui.theme.DarkMode
import com.ithalo.hybridcoach.ui.theme.RetroPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val voiceEnabled: Boolean = true,
    /** 0 to 1, relative to the media volume. */
    val voiceVolume: Float = 1f,
    /** TTS voice name chosen by the user; null = pick the best installed pt-BR voice automatically. */
    val voiceName: String? = null,
    /** Speech rate (1 = engine default). */
    val voiceRate: Float = 1f,
    val paceToleranceSec: Int = 5,
    val smoothingSeconds: Int = 20,
    val minAlertIntervalSec: Int = 30,
    val storeTrack: Boolean = false,
    val palette: RetroPalette = RetroPalette.BEGE,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    /** First-launch tour finished (or skipped). */
    val onboardingDone: Boolean = false,
    /** How the app greets the player; blank = no name. */
    val playerName: String = "",
    /** Training reminders: off until the player turns them on. Times are minutes since midnight. */
    val remindersEnabled: Boolean = false,
    val reminderStrengthMinutes: Int = 18 * 60,
    val reminderRunMinutes: Int = 7 * 60,
    val reminderLeadMinutes: Int = 30,
) {
    val runSettings: RunSettings
        get() = RunSettings(smoothingSeconds = smoothingSeconds, toleranceSecPerKm = paceToleranceSec, minAlertIntervalSeconds = minAlertIntervalSec)
}

/**
 * What the user has already been shown of the (deterministic) game state, so only news is animated,
 * plus the equipped cosmetics. [initialized] is false until the first game state was seen.
 */
data class GameSeen(
    val initialized: Boolean = false,
    val level: Int = 1,
    val achievementIds: Set<String> = emptySet(),
    val itemIds: Set<String> = emptySet(),
    val xp: Int = 0,
    val equippedTitle: String? = null,
    val equippedFrame: String? = null,
    /** The sticker the pet avatar wears on its chest. */
    val equippedSticker: String? = null,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val VOICE = booleanPreferencesKey("voice_enabled")
        val VOLUME = floatPreferencesKey("voice_volume")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val VOICE_RATE = floatPreferencesKey("voice_rate")
        val TOLERANCE = intPreferencesKey("pace_tolerance_sec")
        val SMOOTHING = intPreferencesKey("smoothing_seconds")
        val INTERVAL = intPreferencesKey("min_alert_interval_sec")
        val TRACK = booleanPreferencesKey("store_track")
        val PALETTE = stringPreferencesKey("palette")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val PLAYER_NAME = stringPreferencesKey("player_name")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val REMINDER_STRENGTH = intPreferencesKey("reminder_strength_minutes")
        val REMINDER_RUN = intPreferencesKey("reminder_run_minutes")
        val REMINDER_LEAD = intPreferencesKey("reminder_lead_minutes")

        val SEEN_INIT = booleanPreferencesKey("game_seen_initialized")
        val SEEN_LEVEL = intPreferencesKey("game_seen_level")
        val SEEN_ACHIEVEMENTS = stringSetPreferencesKey("game_seen_achievements")
        val SEEN_ITEMS = stringSetPreferencesKey("game_seen_items")
        val SEEN_XP = intPreferencesKey("game_seen_xp")
        val EQUIPPED_TITLE = stringPreferencesKey("game_equipped_title")
        val EQUIPPED_FRAME = stringPreferencesKey("game_equipped_frame")
        val EQUIPPED_STICKER = stringPreferencesKey("game_equipped_sticker")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { read(it) }.distinctUntilChanged()

    val gameSeen: Flow<GameSeen> = context.dataStore.data.map { readSeen(it) }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = transform(read(p))
            p[Keys.VOICE] = s.voiceEnabled
            p[Keys.VOLUME] = s.voiceVolume
            if (s.voiceName != null) p[Keys.VOICE_NAME] = s.voiceName else p.remove(Keys.VOICE_NAME)
            p[Keys.VOICE_RATE] = s.voiceRate
            p[Keys.TOLERANCE] = s.paceToleranceSec
            p[Keys.SMOOTHING] = s.smoothingSeconds
            p[Keys.INTERVAL] = s.minAlertIntervalSec
            p[Keys.TRACK] = s.storeTrack
            p[Keys.PALETTE] = s.palette.name
            p[Keys.DARK_MODE] = s.darkMode.name
            p[Keys.ONBOARDING] = s.onboardingDone
            p[Keys.PLAYER_NAME] = s.playerName
            p[Keys.REMINDERS] = s.remindersEnabled
            p[Keys.REMINDER_STRENGTH] = s.reminderStrengthMinutes
            p[Keys.REMINDER_RUN] = s.reminderRunMinutes
            p[Keys.REMINDER_LEAD] = s.reminderLeadMinutes
        }
    }

    suspend fun updateGameSeen(transform: (GameSeen) -> GameSeen) {
        context.dataStore.edit { p ->
            val s = transform(readSeen(p))
            p[Keys.SEEN_INIT] = s.initialized
            p[Keys.SEEN_LEVEL] = s.level
            p[Keys.SEEN_ACHIEVEMENTS] = s.achievementIds
            p[Keys.SEEN_ITEMS] = s.itemIds
            p[Keys.SEEN_XP] = s.xp
            if (s.equippedTitle != null) p[Keys.EQUIPPED_TITLE] = s.equippedTitle else p.remove(Keys.EQUIPPED_TITLE)
            if (s.equippedFrame != null) p[Keys.EQUIPPED_FRAME] = s.equippedFrame else p.remove(Keys.EQUIPPED_FRAME)
            if (s.equippedSticker != null) p[Keys.EQUIPPED_STICKER] = s.equippedSticker else p.remove(Keys.EQUIPPED_STICKER)
        }
    }

    /** Marks everything in [state] as seen (used on the first run so old progress is not replayed). */
    suspend fun markAllSeen(state: GameState) = updateGameSeen {
        it.copy(
            initialized = true,
            level = state.level,
            achievementIds = state.achievements.filter { a -> a.unlocked }.map { a -> a.def.id }.toSet(),
            itemIds = state.items.filter { i -> i.unlocked }.map { i -> i.def.id }.toSet(),
            xp = state.xp,
        )
    }

    /** After "clear all data": progress starts over, so everything can be celebrated again. */
    suspend fun resetGameSeen() = updateGameSeen {
        GameSeen(initialized = true, itemIds = ItemCatalog.all.filter { i -> i.unlock == ItemUnlock.Level(1) }.map { i -> i.id }.toSet())
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            voiceEnabled = p[Keys.VOICE] ?: d.voiceEnabled,
            voiceVolume = p[Keys.VOLUME] ?: d.voiceVolume,
            voiceName = p[Keys.VOICE_NAME],
            voiceRate = p[Keys.VOICE_RATE] ?: d.voiceRate,
            paceToleranceSec = p[Keys.TOLERANCE] ?: d.paceToleranceSec,
            smoothingSeconds = p[Keys.SMOOTHING] ?: d.smoothingSeconds,
            minAlertIntervalSec = p[Keys.INTERVAL] ?: d.minAlertIntervalSec,
            storeTrack = p[Keys.TRACK] ?: d.storeTrack,
            palette = RetroPalette.entries.firstOrNull { it.name == p[Keys.PALETTE] } ?: d.palette,
            darkMode = DarkMode.entries.firstOrNull { it.name == p[Keys.DARK_MODE] } ?: d.darkMode,
            onboardingDone = p[Keys.ONBOARDING] ?: d.onboardingDone,
            playerName = p[Keys.PLAYER_NAME] ?: d.playerName,
            remindersEnabled = p[Keys.REMINDERS] ?: d.remindersEnabled,
            reminderStrengthMinutes = p[Keys.REMINDER_STRENGTH] ?: d.reminderStrengthMinutes,
            reminderRunMinutes = p[Keys.REMINDER_RUN] ?: d.reminderRunMinutes,
            reminderLeadMinutes = p[Keys.REMINDER_LEAD] ?: d.reminderLeadMinutes,
        )
    }

    private fun readSeen(p: Preferences) = GameSeen(
        initialized = p[Keys.SEEN_INIT] ?: false,
        level = p[Keys.SEEN_LEVEL] ?: 1,
        achievementIds = p[Keys.SEEN_ACHIEVEMENTS] ?: emptySet(),
        itemIds = p[Keys.SEEN_ITEMS] ?: emptySet(),
        xp = p[Keys.SEEN_XP] ?: 0,
        equippedTitle = p[Keys.EQUIPPED_TITLE],
        equippedFrame = p[Keys.EQUIPPED_FRAME],
        equippedSticker = p[Keys.EQUIPPED_STICKER],
    )
}
