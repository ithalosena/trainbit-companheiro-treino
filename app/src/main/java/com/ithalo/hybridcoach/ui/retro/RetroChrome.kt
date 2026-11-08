package com.ithalo.hybridcoach.ui.retro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.theme.Retro
import java.util.Locale

/** Icon shown at the left of a [RetroDialog] (pixel icon key in [icon]). */
enum class RetroIconKind(val icon: String) { ERROR("error"), WARNING("warning"), INFO("info"), QUESTION("question") }

/**
 * Retro error-box style dialog: a [RetroWindow] with an icon, message and right-aligned buttons.
 * Mirrors Material `AlertDialog` slots.
 */
@Composable
fun RetroDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: RetroIconKind? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val c = Retro.colors
    Dialog(onDismissRequest = onDismissRequest) {
        // Room for the corner marks, which the dialog window would otherwise clip.
        Box(Modifier.padding(8.dp)) {
            RetroWindow(title = title, modifier = modifier.fillMaxWidth(), onClose = onDismissRequest, framed = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        val tint = when (icon) {
                            RetroIconKind.ERROR -> c.danger
                            RetroIconKind.WARNING -> c.ink
                            RetroIconKind.INFO, RetroIconKind.QUESTION -> c.accent2
                        }
                        PixelIcon(icon.icon, Modifier.pixelFloat(amplitude = 1.dp), tint = tint, size = 32.dp)
                    }
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalContentColor provides c.ink, LocalTextStyle provides Retro.type.body) { text() }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** One destination of [RetroTabBar]: label plus a [PixelIcon] key. */
@Immutable
data class RetroTab(val label: String, val icon: String)

/**
 * Main navigation as folder tabs (the reference shop's tab strip), placed under the HUD. Unselected
 * tabs are recessed (light fill, mid icon); the selected one is paper, open at the bottom and joined to
 * the page. Icon-only: the screen's title strip names the page; labels go to accessibility.
 * With [showLabels] the tabs show their label instead of the icon: the same folders, one level down
 * (the sub-menu of a tab).
 */
@Composable
fun RetroTabBar(
    items: List<RetroTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false,
) {
    val c = Retro.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(horizontal = 16.dp)
            .height(48.dp)
            .drawBehind {
                // Baseline under every tab; the selected tab paints over it.
                val w = px(2.dp)
                drawRect(c.border, Offset(0f, size.height - w), Size(size.width, w))
            },
    ) {
        items.forEachIndexed { index, tab ->
            FolderTab(tab, index == selectedIndex, showLabels, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun RowScope.FolderTab(tab: RetroTab, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val c = Retro.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = tab.label; this.selected = selected }
            .drawBehind {
                val w = px(2.dp)
                val top = if (selected) 0f else px(6.dp)
                val h = size.height - top
                if (selected) {
                    drawRect(c.window, Offset(0f, top), Size(size.width, size.height))
                    drawRect(c.bevelLight, Offset(w, top + w), Size(size.width - 2 * w, w))
                } else {
                    drawRect(if (pressed) c.windowInset else c.spriteLight, Offset(w, top), Size(size.width - w, h - w))
                }
                // Top and sides (corners clipped); unselected tabs share their left edge.
                drawRect(c.border, Offset(w, top), Size(size.width - 2 * w, w))
                drawRect(c.border, Offset(0f, top + w), Size(w, h - w))
                drawRect(c.border, Offset(size.width - w, top + w), Size(w, h - w))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showLabel) {
            Text(
                tab.label.uppercase(),
                Modifier.padding(top = if (selected) 0.dp else 6.dp, start = 2.dp, end = 2.dp),
                style = Retro.type.label.copy(fontSize = 11.sp),
                color = if (selected) c.ink else c.inkSoft,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            PixelIcon(
                tab.icon,
                Modifier.padding(top = if (selected) 0.dp else 6.dp).then(if (selected) Modifier.pixelFloat() else Modifier),
                tint = if (selected) c.ink else c.inkFaint,
                size = 22.dp,
            )
        }
    }
}

/** Icon button for the [RetroTopBar] actions slot: a dashed HUD box like the back button. */
@Composable
fun RetroTopBarAction(icon: String, contentDescription: String, onClick: () -> Unit, tint: Color = Retro.colors.ink) {
    DashedBoxButton(onClick, contentDescription) { PixelIcon(icon, tint = tint, size = 20.dp) }
}

/** True inside the tab screens, which sit under the HUD (it already pads for the status bar). */
val LocalUnderHud = staticCompositionLocalOf { false }

/** Square dashed HUD button (back, menu, level), 48dp touch target. */
@Composable
fun DashedBoxButton(onClick: (() -> Unit)?, contentDescription: String?, content: @Composable () -> Unit) {
    val c = Retro.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val click = if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier
    Box(
        Modifier
            .size(48.dp)
            .then(click)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(if (pressed) c.windowInset else Color.Transparent)
                .dashedOutline(c.ink),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/**
 * Screen header of pushed screens: back arrow in a dashed box, title centred in caps, actions on the
 * right, like the HUD of the reference art. Pads for the status bar unless under the HUD.
 */
@Composable
fun RetroTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.statusBars,
) {
    val c = Retro.colors
    val ink = c.ink
    val underHud = LocalUnderHud.current
    Row(
        modifier
            .fillMaxWidth()
            .background(c.background)
            .then(if (underHud) Modifier else Modifier.windowInsetsPadding(windowInsets))
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            DashedBoxButton(onBack, stringResource(R.string.retro_back)) {
                PixelIcon("arrow_left", tint = ink, size = 20.dp)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(
            title.uppercase(Locale.ROOT),
            Modifier.weight(1f).padding(horizontal = 4.dp),
            style = Retro.type.button.copy(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Keeps the title centred when there are no actions.
        Row(Modifier.defaultMinSize(minWidth = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            CompositionLocalProvider(LocalContentColor provides ink) { actions() }
        }
    }
}
