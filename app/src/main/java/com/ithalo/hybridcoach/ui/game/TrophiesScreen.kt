package com.ithalo.hybridcoach.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.game.AchievementState
import com.ithalo.hybridcoach.core.game.TrophyTier
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.full
import com.ithalo.hybridcoach.ui.common.readableOn
import com.ithalo.hybridcoach.ui.retro.CountUpText
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroCard
import com.ithalo.hybridcoach.ui.retro.RetroLinearProgress
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.color
import com.ithalo.hybridcoach.ui.retro.label
import com.ithalo.hybridcoach.ui.retro.pixelBorder
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.Retro

@Composable
fun TrophiesScreen(onBack: () -> Unit) {
    val (state, _) = rememberGame()
    ScreenScaffold(stringResource(R.string.trophies_program), onBack) {
        val s = state ?: return@ScreenScaffold
        val c = Retro.colors
        val all = s.achievements
        val unlocked = all.count { it.unlocked }
        ScreenColumn {
            RetroWindow(stringResource(R.string.game_collection_window), Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TrophyTier.entries.forEach { tier ->
                        val total = all.count { it.def.tier == tier }
                        val got = all.count { it.def.tier == tier && it.unlocked }
                        val platinum = tier == TrophyTier.PLATINUM
                        Column(
                            Modifier
                                .then(if (platinum) Modifier.bevel(c.windowInset, tier.badge().color(), c.bevelLight, c.bevelDark, raised = false) else Modifier)
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PixelIcon(
                                "trophy",
                                if (platinum && got > 0) Modifier.pixelFloat() else Modifier,
                                tint = tier.badge().color(),
                                size = if (platinum) 36.dp else 28.dp,
                            )
                            Text("$got/$total", style = Retro.type.heading, color = c.ink)
                            Text(tier.badge().label(), style = Retro.type.label.copy(fontSize = 9.sp), color = c.inkSoft)
                        }
                    }
                }
                val percent = if (all.isEmpty()) 0 else unlocked * 100 / all.size
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.trophies_completion), style = Retro.type.label, color = c.inkSoft)
                    CountUpText(percent, style = Retro.type.heading, color = c.ink, format = { "$it%" })
                }
                RetroLinearProgress(progress = { percent / 100f }, segments = 20, color = c.gold)
            }

            // Platinum first, like the trophy list of a console game.
            TrophyTier.entries.reversed().forEach { tier ->
                val list = all.filter { it.def.tier == tier }
                if (list.isEmpty()) return@forEach
                SectionTitle(tier.badge().label())
                list.sortedWith(compareByDescending<AchievementState> { it.unlocked }.thenBy { it.def.hidden })
                    .forEach { TrophyRow(it) }
            }
        }
    }
}

@Composable
private fun TrophyRow(a: AchievementState) {
    val c = Retro.colors
    val tierColor = a.def.tier.badge().color()
    val secret = a.def.hidden && !a.unlocked
    RetroCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(48.dp).bevel(c.windowInset, if (a.unlocked) tierColor else c.border, c.bevelLight, c.bevelDark, raised = false),
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(if (secret) "question" else a.def.icon, tint = if (a.unlocked) tierColor else c.inkFaint, size = 30.dp)
                if (!a.unlocked) {
                    Box(Modifier.align(Alignment.BottomEnd).background(c.window).pixelBorder(c.border, 1.dp).padding(1.dp)) {
                        PixelIcon("lock", tint = c.inkSoft, size = 12.dp)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (secret) "???" else a.def.title,
                        Modifier.weight(1f, fill = false),
                        style = Retro.type.heading,
                        color = if (a.unlocked) c.ink else c.inkSoft,
                    )
                    Text(
                        "+${a.def.xp}",
                        Modifier.background(if (a.unlocked) tierColor else c.windowInset).padding(horizontal = 4.dp),
                        style = Retro.type.label.copy(fontSize = 10.sp),
                        color = if (a.unlocked) readableOn(tierColor) else c.inkSoft,
                    )
                }
                Text(
                    if (secret) stringResource(R.string.trophies_hidden) else a.def.description,
                    style = Retro.type.bodySmall,
                    color = c.inkSoft,
                )
                when {
                    a.unlocked -> Text(
                        stringResource(R.string.trophies_unlocked_on, a.unlockedOn!!.full()),
                        style = Retro.type.label.copy(fontSize = 10.sp),
                        color = c.success,
                    )
                    !secret && a.def.target > 1 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetroLinearProgress(
                            progress = { a.progress / a.def.target.toFloat() },
                            modifier = Modifier.weight(1f),
                            segments = a.def.target.coerceIn(2, 12),
                            color = tierColor,
                        )
                        Text("${a.progress}/${a.def.target}", style = Retro.type.label, color = c.inkSoft)
                    }
                }
            }
        }
    }
}
