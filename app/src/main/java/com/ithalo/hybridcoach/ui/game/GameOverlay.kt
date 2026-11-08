package com.ithalo.hybridcoach.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.game.AchievementState
import com.ithalo.hybridcoach.core.game.GameEngine
import com.ithalo.hybridcoach.core.game.ItemState
import com.ithalo.hybridcoach.core.game.Unlock
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.core.avatar.AvatarEvent
import com.ithalo.hybridcoach.ui.retro.LevelUpOverlay
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.TrophyUnlockedBanner
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.hardShadow
import com.ithalo.hybridcoach.ui.retro.pixelBorder
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

private sealed interface OverlayEvent {
    val key: String
}

private data class LevelEvent(val level: Int, val rank: String) : OverlayEvent {
    override val key get() = "level:$level"
}

private data class TrophyEvent(val achievement: AchievementState) : OverlayEvent {
    override val key get() = "trophy:${achievement.def.id}"
}

private data class ItemEvent(val item: ItemState) : OverlayEvent {
    override val key get() = "item:${item.def.id}"
}

private data class WelcomeEvent(val level: Int, val trophies: Int) : OverlayEvent {
    override val key get() = "welcome"
}

private data class XpPop(val id: Long, val amount: Int)

/**
 * App-root layer that celebrates what is new in the game state: one event at a time (level up, trophy
 * banners, new items), plus a floating "+N XP". Each event is persisted as seen after it was shown.
 * On the very first run, existing progress is marked as seen with a single welcome banner.
 * [holdLevelUp] keeps the full-screen level up waiting (banners still show) while a player is open.
 */
@Composable
fun GameOverlay(holdLevelUp: Boolean) {
    val container = appContainer()
    val settings = container.settings
    val state by container.gameState.collectAsStateWithLifecycle()
    val seen by settings.gameSeen.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    val queue = remember { mutableStateListOf<OverlayEvent>() }
    val queuedAchievements = remember { mutableSetOf<String>() }
    val queuedItems = remember { mutableSetOf<String>() }
    var queuedLevel by remember { mutableIntStateOf(0) }
    var welcomed by remember { mutableStateOf(false) }
    var shownXp by remember { mutableStateOf<Int?>(null) }
    var xpPop by remember { mutableStateOf<XpPop?>(null) }

    // Synchronous bookkeeping only; DataStore writes go to `scope` so a new emission cannot cancel them.
    LaunchedEffect(state, seen) {
        val s = state ?: return@LaunchedEffect
        val sn = seen ?: return@LaunchedEffect
        if (!sn.initialized) {
            if (!welcomed) {
                welcomed = true
                shownXp = s.xp
                queue += WelcomeEvent(s.level, s.achievements.count { it.unlocked })
                scope.launch { settings.markAllSeen(s) }
            }
            return@LaunchedEffect
        }
        val baseXp = shownXp ?: sn.xp
        if (s.xp != baseXp) {
            if (s.xp > baseXp) xpPop = XpPop(System.nanoTime(), s.xp - baseXp)
            shownXp = s.xp
            val lowerLevel = s.level < maxOf(sn.level, queuedLevel)
            if (lowerLevel) queuedLevel = 0
            scope.launch {
                settings.updateGameSeen { it.copy(xp = s.xp, level = if (lowerLevel) s.level else it.level) }
            }
        }
        val unlocks = GameEngine.unlocksSince(
            seenLevel = maxOf(sn.level, queuedLevel),
            seenAchievementIds = sn.achievementIds + queuedAchievements,
            seenItemIds = sn.itemIds + queuedItems,
            current = s,
        )
        for (u in unlocks) {
            when (u) {
                is Unlock.LevelUp -> {
                    queuedLevel = u.level
                    queue += LevelEvent(u.level, u.rankTitle)
                }
                is Unlock.AchievementUnlocked -> {
                    queuedAchievements += u.achievement.def.id
                    queue += TrophyEvent(u.achievement)
                }
                is Unlock.ItemUnlocked -> {
                    queuedItems += u.item.def.id
                    queue += ItemEvent(u.item)
                }
            }
        }
    }

    fun finish(event: OverlayEvent) {
        queue.remove(event)
        if (event is LevelEvent || event is TrophyEvent) container.avatarEvents.post(AvatarEvent.CELEBRATE)
        scope.launch {
            when (event) {
                is LevelEvent -> settings.updateGameSeen { it.copy(level = maxOf(it.level, event.level)) }
                is TrophyEvent -> settings.updateGameSeen { it.copy(achievementIds = it.achievementIds + event.achievement.def.id) }
                is ItemEvent -> settings.updateGameSeen { it.copy(itemIds = it.itemIds + event.item.def.id) }
                is WelcomeEvent -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val head = queue.firstOrNull { !(holdLevelUp && it is LevelEvent) }
        if (head != null) {
            key(head.key) {
                when (head) {
                    is LevelEvent -> LevelUpOverlay(head.level, head.rank, visible = true, onDismiss = { finish(head) })
                    is TrophyEvent -> TimedBanner(onDone = { finish(head) }) { visible, onFinished ->
                        val a = head.achievement.def
                        TrophyUnlockedBanner(
                            tier = a.tier.badge(),
                            title = a.title,
                            subtitle = stringResource(R.string.game_banner_trophy_sub, a.description, a.xp),
                            visible = visible,
                            onFinished = onFinished,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    is ItemEvent -> TimedBanner(onDone = { finish(head) }) { visible, onFinished ->
                        NoticeBanner(
                            icon = head.item.def.icon,
                            tint = itemTint(head.item.def.kind),
                            header = stringResource(R.string.game_banner_item),
                            title = head.item.def.name,
                            subtitle = itemKindLabel(head.item.def.kind) + " · " + head.item.def.description,
                            visible = visible,
                            onFinished = onFinished,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    is WelcomeEvent -> TimedBanner(onDone = { finish(head) }) { visible, onFinished ->
                        NoticeBanner(
                            icon = "floppy",
                            tint = Retro.colors.accent2,
                            header = stringResource(R.string.game_banner_welcome_header),
                            title = stringResource(R.string.game_banner_welcome, head.level, head.trophies),
                            subtitle = stringResource(R.string.game_banner_welcome_sub),
                            visible = visible,
                            onFinished = onFinished,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
        xpPop?.let { pop ->
            key(pop.id) { XpPopText(pop.amount, Modifier.align(Alignment.TopEnd)) { if (xpPop?.id == pop.id) xpPop = null } }
        }
    }
}

/** Slides a banner in, lets it call `onFinished` (after its own timer) and waits for the exit animation. */
@Composable
private fun TimedBanner(onDone: () -> Unit, content: @Composable (visible: Boolean, onFinished: () -> Unit) -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(phase) {
        when (phase) {
            0 -> phase = 1
            2 -> {
                delay(320)
                done()
            }
        }
    }
    content(phase == 1) { if (phase == 1) phase = 2 }
}

/** Banner in the style of [TrophyUnlockedBanner] for items and notices; tap to dismiss early. */
@Composable
private fun BoxScope.NoticeBanner(
    icon: String,
    tint: Color,
    header: String,
    title: String,
    subtitle: String,
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Retro.colors
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(visible) {
        if (visible) {
            delay(3500)
            finished()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(320, easing = LinearEasing)) { -it * 2 },
        exit = slideOutVertically(tween(240, easing = LinearEasing)) { -it * 2 },
    ) {
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .hardShadow(c.shadow)
                    .background(c.window)
                    .pixelBorder(c.border)
                    .drawBehind { drawRect(tint, Offset(2.dp.toPx(), 2.dp.toPx()), Size(6.dp.toPx(), size.height - 4.dp.toPx())) }
                    .clickable(remember { MutableInteractionSource() }, indication = null) { finished() }
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(56.dp).bevel(c.windowInset, c.border, c.bevelLight, c.bevelDark, raised = false),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelIcon(icon, Modifier.pixelFloat(), tint = tint, size = 32.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(header, style = Retro.type.label, color = c.inkSoft)
                    Text(title, style = Retro.type.heading, color = c.ink, maxLines = 2)
                    Text(subtitle, style = Retro.type.bodySmall, color = c.inkSoft, maxLines = 2)
                }
            }
        }
    }
}

/** "+N XP" that rises in pixel steps and blinks out. */
@Composable
private fun XpPopText(amount: Int, modifier: Modifier, onDone: () -> Unit) {
    val c = Retro.colors
    val t = remember { Animatable(0f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1600, easing = LinearEasing))
        done()
    }
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 60.dp, end = 16.dp)
            .graphicsLayer {
                val p = t.value
                translationY = -(floor(p * 8f) * 4.dp.toPx()).roundToInt().toFloat()
                // Solid, then blinks in the last third (hard on/off, no soft fade).
                alpha = if (p < 0.66f || ((p * 20f).toInt() % 2 == 0)) 1f else 0f
            }
            .hardShadow(c.shadow, 2.dp)
            .background(c.gold)
            .pixelBorder(c.border)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val ink = if (c.isDark) c.background else c.ink
        PixelIcon("coin", tint = ink, size = 16.dp)
        Text(stringResource(R.string.game_xp_plus, amount), style = Retro.type.number.copy(fontSize = 12.sp, lineHeight = 14.sp), color = ink)
    }
}
