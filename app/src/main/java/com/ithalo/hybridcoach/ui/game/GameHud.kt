package com.ithalo.hybridcoach.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.retro.DashedBoxButton
import com.ithalo.hybridcoach.ui.retro.PixelSprite
import com.ithalo.hybridcoach.ui.theme.Retro

/**
 * Game HUD over the tab screens, like the coins / hearts / pause row of the reference game:
 * XP total with the coin on the left, the week streak as flames in the middle and the level in a
 * dashed box on the right. Tapping it opens the profile. Pads for the status bar.
 */
@Composable
fun GameHud(onProfile: () -> Unit, modifier: Modifier = Modifier) {
    val (state, _) = rememberGame()
    val c = Retro.colors
    val xp = state?.xp ?: 0
    val level = state?.level ?: 1
    val streak = state?.stats?.currentWeekStreak ?: 0
    val description = stringResource(R.string.hud_description, xp, streak, level)
    Row(
        modifier
            .fillMaxWidth()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onProfile)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelSprite("coin", pixel = 2.dp, reward = true)
            Text(String.format(PT_BR, "%,d XP", xp), style = Retro.type.button, color = c.ink, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) { i ->
                    PixelSprite("flame", Modifier.alpha(if (i < streak) 1f else 0.25f), pixel = 2.dp)
                }
            }
            Text(
                stringResource(R.string.hud_streak, streak),
                style = Retro.type.label.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = c.inkSoft,
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            DashedBoxButton(onClick = onProfile, contentDescription = null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.hud_level), style = Retro.type.label.copy(fontSize = 9.sp, lineHeight = 10.sp), color = c.inkSoft)
                    Text(level.toString(), style = Retro.type.button.copy(fontSize = 15.sp, lineHeight = 16.sp), color = c.ink)
                }
            }
        }
    }
}
