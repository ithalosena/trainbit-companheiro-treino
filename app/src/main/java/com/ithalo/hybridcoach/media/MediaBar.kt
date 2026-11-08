package com.ithalo.hybridcoach.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroIconButton
import com.ithalo.hybridcoach.ui.theme.Retro

/** Thin music strip for the workout screens: whether music is playing, play/pause and next. */
@Composable
fun MediaBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = Retro.colors
    val playing by NowPlaying.rememberPlaying()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelIcon("speaker", tint = c.inkSoft, size = 16.dp)
        Text(
            stringResource(if (playing) R.string.media_playing else R.string.media_nothing),
            Modifier.weight(1f),
            style = Retro.type.bodySmall,
            color = if (playing) c.ink else c.inkSoft,
            maxLines = 1,
        )
        RetroIconButton(onClick = { NowPlaying.playPause(context) }) {
            PixelIcon(if (playing) "pause" else "play", size = 16.dp, contentDescription = stringResource(if (playing) R.string.media_pause else R.string.media_play))
        }
        RetroIconButton(onClick = { NowPlaying.next(context) }) {
            PixelIcon("arrow_right", size = 16.dp, contentDescription = stringResource(R.string.media_next))
        }
    }
}
