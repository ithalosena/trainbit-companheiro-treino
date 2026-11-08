package com.ithalo.hybridcoach.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import com.ithalo.hybridcoach.ui.retro.RetroDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.avatar.AvatarState
import com.ithalo.hybridcoach.core.game.GameState
import com.ithalo.hybridcoach.core.game.XpTable
import com.ithalo.hybridcoach.core.game.TrophyTier
import com.ithalo.hybridcoach.core.strength.TimedTarget
import com.ithalo.hybridcoach.ui.avatar.AvatarView
import com.ithalo.hybridcoach.ui.avatar.NeutralAvatarState
import com.ithalo.hybridcoach.ui.avatar.rememberAvatarState
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.common.short
import com.ithalo.hybridcoach.ui.player.formatKg
import com.ithalo.hybridcoach.ui.run.runRecordLabel
import com.ithalo.hybridcoach.ui.run.runRecordValue
import com.ithalo.hybridcoach.ui.retro.CountUpText
import com.ithalo.hybridcoach.ui.retro.DitherCrossfade
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.PixelSprite
import com.ithalo.hybridcoach.ui.retro.RetroDivider
import com.ithalo.hybridcoach.ui.retro.RetroTab
import com.ithalo.hybridcoach.ui.retro.RetroTabBar
import com.ithalo.hybridcoach.ui.retro.RetroLinearProgress
import com.ithalo.hybridcoach.ui.retro.RetroLoading
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.RetroRewardButton
import com.ithalo.hybridcoach.ui.retro.RetroTitleStrip
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.color
import com.ithalo.hybridcoach.ui.retro.cornerMarks
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.Retro

private enum class ProfileTab(val label: Int, val icon: String) {
    PROFILE(R.string.profile_tab_profile, "face"),
    COLLECTION(R.string.profile_tab_collection, "trophy"),
    RECORDS(R.string.profile_tab_records, "medal"),
    XP(R.string.profile_tab_xp, "star"),
}

@Composable
fun ProfileScreen(onTrophies: () -> Unit, onItems: () -> Unit) {
    val (state, seen) = rememberGame()
    val playerName by appContainer().settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val avatarInput by appContainer().avatarInput.collectAsStateWithLifecycle()
    val avatarState = rememberAvatarState(avatarInput) ?: NeutralAvatarState
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = ProfileTab.entries[tabIndex.coerceIn(0, ProfileTab.entries.size - 1)]
    ScreenScaffold(stringResource(R.string.tab_trophies), showTitle = false) {
        if (state == null) {
            RetroLoading()
            return@ScreenScaffold
        }
        val c = Retro.colors
        val title = state.equipped(seen?.equippedTitle)
        val frameId = state.equipped(seen?.equippedFrame)?.id
        Column(Modifier.fillMaxSize()) {
            // Second-level menu, only on this tab: the same folder tabs as the main menu, one level down.
            val tabs = ProfileTab.entries.map { RetroTab(stringResource(it.label), it.icon) }
            RetroTabBar(tabs, tabIndex, onSelect = { tabIndex = it }, modifier = Modifier.padding(top = 10.dp), showLabels = true)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScreenColumn {
                    when (tab) {
                        ProfileTab.PROFILE -> ProfileSection(state, avatarState, playerName?.playerName, title?.name, seen?.equippedSticker)
                        ProfileTab.COLLECTION -> CollectionSection(state, title?.name, frameId, onTrophies, onItems)
                        ProfileTab.RECORDS -> RecordsSection(state)
                        ProfileTab.XP -> XpLogSection(state)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSection(state: GameState, avatarState: AvatarState, name: String?, title: String?, stickerItemId: String?) {
    val c = Retro.colors
    // Character card: the living pet inside corner marks, its tip, name, rank strip, level.
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 152.dp, height = 152.dp).cornerMarks(c.ink, gap = 0.dp, arm = 16.dp), contentAlignment = Alignment.Center) {
            AvatarView(avatarState, state.equipped(stickerItemId)?.id, pixel = 2.5.dp)
        }
    }
    if (avatarState.tip.isNotBlank()) {
        Text(avatarState.tip, Modifier.fillMaxWidth(), style = Retro.type.bodySmall, color = c.inkSoft, textAlign = TextAlign.Center)
    }
    name?.takeIf { it.isNotBlank() }?.let {
        Text(it, Modifier.fillMaxWidth(), style = Retro.type.title, color = c.ink, textAlign = TextAlign.Center)
    }
    RetroTitleStrip(state.rankTitle)
    // The equipped title has its own line: on the strip it was drawn over the dithered fade.
    title?.let { Text(it, Modifier.fillMaxWidth(), style = Retro.type.body, color = c.inkSoft, textAlign = TextAlign.Center) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.game_level_caps), style = Retro.type.label, color = c.inkSoft)
            Text(state.level.toString(), style = Retro.type.display, color = c.ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RetroLinearProgress(
                progress = { state.xpIntoLevel / state.xpForNextLevel.coerceAtLeast(1).toFloat() },
                segments = 16,
                color = c.reward,
            )
            Text(
                String.format(PT_BR, "%,d / %,d XP", state.xpIntoLevel, state.xpForNextLevel),
                style = Retro.type.bodySmall,
                color = c.inkSoft,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PixelSprite("coin", pixel = 2.dp, reward = true)
                CountUpText(state.xp, style = Retro.type.button, color = c.ink, format = { formatXpTotal(it) })
            }
        }
    }

    // Points table: a button that opens it in a window, so the profile stays short.
    var showPoints by rememberSaveable { mutableStateOf(false) }
    RetroOutlinedButton(onClick = { showPoints = true }, modifier = Modifier.fillMaxWidth()) {
        PixelSprite("coin", pixel = 2.dp, reward = true)
        Text(stringResource(R.string.game_points_button), Modifier.padding(start = 8.dp))
    }
    if (showPoints) PointsDialog(onDismiss = { showPoints = false })

    RetroWindow(stringResource(R.string.game_stats_window), Modifier.fillMaxWidth()) {
        val st = state.stats
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CountStatTile("check", st.sessionsDone, stringResource(R.string.game_stat_sessions))
            CountStatTile("dumbbell", st.totalSets, stringResource(R.string.game_stat_sets))
            CountStatTile("coin", (st.tonnageKg / 100).toInt(), stringResource(R.string.game_stat_tons), tint = c.gold) {
                String.format(PT_BR, "%.1f", it / 10.0)
            }
            CountStatTile("shoe", (st.totalKm * 10).toInt(), stringResource(R.string.game_stat_km)) {
                String.format(PT_BR, "%.1f", it / 10.0)
            }
            CountStatTile("flame", st.currentWeekStreak, stringResource(R.string.game_stat_streak), tint = c.accent)
            CountStatTile("crown", st.bestWeekStreak, stringResource(R.string.game_stat_best_streak), tint = c.gold)
        }
    }
}

/** The points table, read-only, built from the engine's own constants. Scrolls on short screens. */
@Composable
private fun PointsDialog(onDismiss: () -> Unit) {
    val c = Retro.colors
    RetroDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.game_points_window),
        confirmButton = { RetroOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                XpTable.rules.forEachIndexed { i, rule ->
                    if (i > 0) RetroDivider(dashed = true)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.label, Modifier.weight(1f), style = Retro.type.body, color = c.ink)
                        Text(stringResource(R.string.game_xp_plus_text, rule.amount), style = Retro.type.button, color = c.accent2)
                    }
                }
            }
        },
    )
}

/** Collection, in this order: the four tiers, "almost there", the full list button, title and frame with Items. */
@Composable
private fun CollectionSection(state: GameState, title: String?, frameId: String?, onTrophies: () -> Unit, onItems: () -> Unit) {
    val c = Retro.colors
    val unlocked = state.achievements.count { it.unlocked }
    RetroWindow(stringResource(R.string.game_collection_window), Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TrophyTier.entries.forEach { tier ->
                val n = state.achievements.count { it.unlocked && it.def.tier == tier }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelIcon("trophy", tint = tier.badge().color(), size = 28.dp)
                    Text(n.toString(), style = Retro.type.display.copy(fontSize = 30.sp, lineHeight = 34.sp), color = c.ink)
                    Text(tierLabel(tier), style = Retro.type.label.copy(fontSize = 10.sp), color = c.inkSoft)
                }
            }
        }
    }

    AlmostThere(state)

    RetroRewardButton(
        stringResource(R.string.game_trophies).uppercase(PT_BR),
        "$unlocked/${state.achievements.size}",
        onClick = onTrophies,
        icon = "trophy",
    )

    // The equipped frame item still decorates its own window.
    DitherCrossfade(frameStyleOf(frameId), Modifier.fillMaxWidth()) { style ->
        FramedWindow(stringResource(R.string.game_frame_window), style, Modifier.fillMaxWidth()) {
            Text(
                title ?: stringResource(R.string.game_no_title),
                style = Retro.type.body,
                color = if (title != null) c.ink else c.inkFaint,
            )
            RetroOutlinedButton(onClick = onItems, modifier = Modifier.fillMaxWidth()) {
                PixelIcon("folder", size = 16.dp)
                Text(stringResource(R.string.game_items))
            }
        }
    }
}

@Composable
private fun RecordsSection(state: GameState) {
    val c = Retro.colors
    RetroWindow(stringResource(R.string.game_run_records_window), Modifier.fillMaxWidth()) {
        if (state.runRecords.isEmpty()) {
            Text(stringResource(R.string.game_run_records_empty), style = Retro.type.body, color = c.inkSoft)
        }
        state.runRecords.forEachIndexed { i, r ->
            if (i > 0) RetroDivider(dashed = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelIcon("shoe", tint = c.gold, size = 18.dp)
                Column(Modifier.weight(1f)) {
                    Text(runRecordLabel(r.kind), style = Retro.type.heading, color = c.ink)
                    Text(r.date.short(), style = Retro.type.bodySmall, color = c.inkSoft)
                }
                Text(runRecordValue(r), style = Retro.type.body, color = c.ink)
            }
        }
    }
    RetroWindow(stringResource(R.string.game_records_window), Modifier.fillMaxWidth()) {
        if (state.records.isEmpty()) {
            Text(stringResource(R.string.game_records_empty), style = Retro.type.body, color = c.inkSoft)
        }
        state.records.forEachIndexed { i, r ->
            if (i > 0) RetroDivider(dashed = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelIcon("medal", tint = c.gold, size = 18.dp)
                Column(Modifier.weight(1f)) {
                    Text(r.exerciseName, style = Retro.type.heading, color = c.ink)
                    Text(r.date.short(), style = Retro.type.bodySmall, color = c.inkSoft)
                }
                Text(
                    when {
                        r.timed -> TimedTarget.format(r.reps)
                        r.loadKg <= 0.0 -> stringResource(R.string.game_record_reps, r.reps)
                        else -> "${formatKg(r.loadKg)} kg × ${r.reps}"
                    },
                    style = Retro.type.body,
                    color = c.ink,
                )
            }
        }
    }
}

@Composable
private fun XpLogSection(state: GameState) {
    val c = Retro.colors
    RetroWindow(stringResource(R.string.game_log_window), Modifier.fillMaxWidth()) {
        if (state.xpLog.isEmpty()) {
            Text(stringResource(R.string.game_log_empty), style = Retro.type.body, color = c.inkSoft)
        }
        state.xpLog.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.game_xp_plus, e.amount),
                    style = Retro.type.button,
                    color = c.accent2,
                )
                Text("· ${e.reason}", Modifier.weight(1f), style = Retro.type.body, color = c.ink)
                Text(e.date.short(), style = Retro.type.bodySmall, color = c.inkSoft)
            }
        }
    }
}

/** Formats the total XP line ("1.234 XP"). Not a composable: used inside CountUpText's format lambda. */
private fun formatXpTotal(xp: Int): String = String.format(PT_BR, "%,d XP", xp)

@Composable
private fun tierLabel(tier: TrophyTier) = stringResource(
    when (tier) {
        TrophyTier.BRONZE -> R.string.game_tier_bronze
        TrophyTier.SILVER -> R.string.game_tier_silver
        TrophyTier.GOLD -> R.string.game_tier_gold
        TrophyTier.PLATINUM -> R.string.game_tier_platinum
    },
)

/** "Quase lá": the three visible trophies closest to unlocking, with their progress bars. */
@Composable
private fun AlmostThere(state: GameState) {
    val c = Retro.colors
    val close = state.achievements
        .filter { !it.unlocked && !it.def.hidden && it.def.tier != TrophyTier.PLATINUM && it.def.target > 1 && it.progress > 0 }
        .sortedByDescending { it.progress / it.def.target.toFloat() }
        .take(3)
    if (close.isEmpty()) return
    SectionTitle(stringResource(R.string.game_almost_there))
    close.forEachIndexed { i, a ->
        if (i > 0) RetroDivider(dashed = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PixelIcon(a.def.icon, tint = c.ink, size = 22.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(a.def.title, style = Retro.type.body, color = c.ink)
                RetroLinearProgress(
                    progress = { a.progress / a.def.target.toFloat() },
                    modifier = Modifier.height(14.dp),
                    segments = 16,
                    color = c.reward,
                )
            }
            Text("${a.progress}/${a.def.target}", style = Retro.type.button, color = c.ink)
        }
    }
}
