package com.ithalo.hybridcoach.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.core.game.ItemKind
import com.ithalo.hybridcoach.core.game.ItemState
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.common.readableOn
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroDialog
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.bevel
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.retro.shake
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.launch

private const val COLUMNS = 4

@Composable
fun ItemsScreen(onBack: () -> Unit) {
    val (state, seen) = rememberGame()
    val settings = appContainer().settings
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(stringResource(R.string.items_program), onBack) {
        val s = state ?: return@ScreenScaffold
        val c = Retro.colors
        val equippedTitle = s.equipped(seen?.equippedTitle)?.id
        val equippedFrame = s.equipped(seen?.equippedFrame)?.id ?: "frame_classic"
        val equippedSticker = s.equipped(seen?.equippedSticker)?.id

        fun equip(item: ItemState) {
            when (item.def.kind) {
                ItemKind.TITLE -> scope.launch {
                    settings.updateGameSeen { it.copy(equippedTitle = if (it.equippedTitle == item.def.id) null else item.def.id) }
                }
                ItemKind.FRAME -> scope.launch { settings.updateGameSeen { it.copy(equippedFrame = item.def.id) } }
                ItemKind.STICKER -> scope.launch {
                    settings.updateGameSeen { it.copy(equippedSticker = if (it.equippedSticker == item.def.id) null else item.def.id) }
                }
            }
        }

        ScreenColumn {
            val got = s.items.count { it.unlocked }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelIcon("folder", tint = c.accent2, size = 20.dp)
                Text(stringResource(R.string.items_count, got, s.items.size), style = Retro.type.body)
            }
            Text(stringResource(R.string.items_help), style = Retro.type.bodySmall, color = c.inkSoft)

            ItemKind.entries.forEach { kind ->
                val list = s.items.filter { it.def.kind == kind }
                if (list.isEmpty()) return@forEach
                SectionTitle(stringResource(kindSection(kind)))
                list.chunked(COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { i, item ->
                            val equipped = item.def.id == equippedTitle || item.def.id == equippedFrame || item.def.id == equippedSticker
                            InventoryCell(
                                item = item,
                                selected = item.def.id == selectedId,
                                equipped = equipped,
                                phase = i / COLUMNS.toFloat(),
                                modifier = Modifier.weight(1f),
                            ) {
                                selectedId = item.def.id
                            }
                        }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

        }

        // The details open in a window in front of the grid, so there is no scrolling to find them.
        s.items.firstOrNull { it.def.id == selectedId }?.let { item ->
            ItemDialog(item, equippedTitle, equippedFrame, equippedSticker, onEquip = { equip(item) }, onDismiss = { selectedId = null })
        }
    }
}

private fun kindSection(kind: ItemKind) = when (kind) {
    ItemKind.TITLE -> R.string.items_titles
    ItemKind.FRAME -> R.string.items_frames
    ItemKind.STICKER -> R.string.items_stickers
}

/** RPG inventory slot: sunken square, item icon (silhouette + lock when locked), "E" plate when equipped. */
@Composable
private fun InventoryCell(item: ItemState, selected: Boolean, equipped: Boolean, phase: Float, modifier: Modifier, onClick: () -> Unit) {
    val c = Retro.colors
    val tint = itemTint(item.def.kind)
    Box(
        modifier
            .aspectRatio(1f)
            .bevel(if (selected) c.window else c.windowInset, if (equipped) c.accent else c.border, c.bevelLight, c.bevelDark, raised = selected)
            .drawBehind {
                if (equipped) {
                    val w = 2.dp.toPx()
                    drawRect(c.accent, Offset(w, w), Size(size.width - 2 * w, w))
                    drawRect(c.accent, Offset(w, size.height - 2 * w), Size(size.width - 2 * w, w))
                }
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(
            item.def.icon,
            if (item.unlocked) Modifier.pixelFloat(amplitude = 1.dp, phase = phase) else Modifier,
            tint = if (item.unlocked) tint else c.inkFaint.copy(alpha = 0.6f),
            size = 32.dp,
        )
        if (!item.unlocked) {
            PixelIcon("lock", Modifier.align(Alignment.BottomEnd).padding(4.dp), tint = c.inkSoft, size = 14.dp)
        }
        if (equipped) {
            Text(
                "E",
                Modifier.align(Alignment.TopStart).padding(4.dp).background(c.accent).padding(horizontal = 3.dp),
                style = Retro.type.label.copy(fontSize = 9.sp),
                color = readableOn(c.accent),
            )
        }
    }
}

@Composable
private fun ItemDialog(
    item: ItemState,
    equippedTitle: String?,
    equippedFrame: String,
    equippedSticker: String?,
    onEquip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Retro.colors
    val def = item.def
    RetroDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.items_details_window),
        confirmButton = { RetroOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).bevel(c.windowInset, c.border, c.bevelLight, c.bevelDark, raised = false), contentAlignment = Alignment.Center) {
                PixelIcon(def.icon, tint = if (item.unlocked) itemTint(def.kind) else c.inkFaint, size = 36.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(if (item.unlocked) def.name else "???", style = Retro.type.heading)
                Text(itemKindLabel(def.kind), style = Retro.type.label, color = c.inkSoft)
            }
        }
        if (item.unlocked) {
            Text(def.description, style = Retro.type.body)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelIcon("lock", tint = c.inkSoft, size = 16.dp)
                Text(unlockText(def), style = Retro.type.body, color = c.inkSoft)
            }
        }
        if (item.unlocked && def.kind == ItemKind.TITLE) {
            val on = equippedTitle == def.id
            if (on) {
                RetroOutlinedButton(onClick = onEquip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.items_unequip)) }
            } else {
                RetroButton(onClick = onEquip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.items_equip)) }
            }
        }
        if (item.unlocked && def.kind == ItemKind.STICKER) {
            Text(stringResource(R.string.items_sticker_hint), style = Retro.type.bodySmall, color = c.inkSoft)
            if (equippedSticker == def.id) {
                RetroOutlinedButton(onClick = onEquip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.items_unequip)) }
            } else {
                RetroButton(onClick = onEquip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.items_equip)) }
            }
        }
        if (item.unlocked && def.kind == ItemKind.FRAME) {
            if (equippedFrame == def.id) {
                Text(stringResource(R.string.items_equipped), style = Retro.type.button, color = c.accent)
            } else {
                RetroButton(onClick = onEquip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.items_equip)) }
            }
            FramedWindow(stringResource(R.string.program_profile), frameStyleOf(def.id), Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.items_frame_preview), style = Retro.type.bodySmall, color = c.inkSoft)
            }
        }
        }
        },
    )
}
