package com.ithalo.hybridcoach.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.theme.Retro

/**
 * The muscles an exercise works, as small tags: the main ones framed, the secondary ones faint. Nothing when the
 * exercise has no muscles (an old custom exercise).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleChips(exerciseId: String, modifier: Modifier = Modifier) {
    val all by appContainer().muscles.collectAsStateWithLifecycle()
    val m = all[exerciseId] ?: return
    if (m.isEmpty) return
    val c = Retro.colors
    val description = stringResource(R.string.muscles_description, m.primary.joinToString(", "), m.secondary.joinToString(", ").ifEmpty { "—" })
    FlowRow(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        m.primary.forEach { name ->
            Text(
                name,
                Modifier.background(c.windowInset).border(1.dp, c.ink).padding(horizontal = 6.dp, vertical = 2.dp),
                style = Retro.type.label,
                color = c.ink,
            )
        }
        m.secondary.forEach { name ->
            Text(
                name,
                Modifier.border(1.dp, c.inkFaint).padding(horizontal = 6.dp, vertical = 2.dp),
                style = Retro.type.label,
                color = c.inkSoft,
            )
        }
    }
}
