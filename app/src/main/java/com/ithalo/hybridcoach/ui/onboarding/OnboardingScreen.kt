package com.ithalo.hybridcoach.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ithalo.hybridcoach.ui.avatar.AvatarView
import com.ithalo.hybridcoach.ui.avatar.NeutralAvatarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.AppSettings
import com.ithalo.hybridcoach.ui.common.ChoiceChips
import com.ithalo.hybridcoach.ui.common.LabeledTextField
import com.ithalo.hybridcoach.ui.common.PT_BR
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.retro.DitherCrossfade
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.PixelSprite
import com.ithalo.hybridcoach.ui.retro.RetroBackground
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroTextButton
import com.ithalo.hybridcoach.ui.retro.RetroTitleStrip
import com.ithalo.hybridcoach.ui.retro.pixelBorder
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.DarkMode
import com.ithalo.hybridcoach.ui.theme.HybridCoachTheme
import com.ithalo.hybridcoach.ui.theme.Retro
import com.ithalo.hybridcoach.ui.theme.RetroPalette
import kotlinx.coroutines.launch

private const val PAGES = 4

/**
 * First-launch tour in four "cartridge manual" pages: welcome, how the AI-plan loop works, how the
 * game rewards training, and the player's name + look. Palette and mode apply live; finishing (or
 * skipping) stores [AppSettings.onboardingDone].
 */
@Composable
fun OnboardingScreen(settings: AppSettings) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(settings.playerName) }
    BackHandler(enabled = page > 0) { page-- }

    fun update(transform: (AppSettings) -> AppSettings) = scope.launch { container.settings.update(transform) }
    fun finish() = update { it.copy(onboardingDone = true, playerName = name.trim().take(24)) }

    RetroBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.onboarding_page, page + 1, PAGES), Modifier.weight(1f), style = Retro.type.label, color = Retro.colors.inkSoft)
                if (page < PAGES - 1) RetroTextButton(onClick = ::finish) { Text(stringResource(R.string.onboarding_skip)) }
            }
            DitherCrossfade(page, Modifier.weight(1f).fillMaxWidth()) { p ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (p) {
                        0 -> WelcomePage()
                        1 -> HowPage()
                        2 -> GamePage()
                        else -> YouPage(name, { name = it }, settings, onPalette = { pal -> update { it.copy(palette = pal) } }, onMode = { m -> update { it.copy(darkMode = m) } })
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PageDots(page)
                RetroButton(
                    onClick = { if (page < PAGES - 1) page++ else finish() },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    PixelIcon(if (page < PAGES - 1) "arrow_right" else "play", size = 16.dp)
                    Text(stringResource(if (page < PAGES - 1) R.string.onboarding_next else R.string.onboarding_start).uppercase(PT_BR))
                }
            }
        }
    }
}

@Composable
private fun PageDots(page: Int) {
    val c = Retro.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGES) { i ->
            Box(Modifier.size(10.dp).then(if (i == page) Modifier.background(c.ink) else Modifier.pixelBorder(c.inkFaint)))
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun WelcomePage() {
    val c = Retro.colors
    Centered { PixelSprite("logo", Modifier.pixelFloat(), pixel = 3.dp) }
    RetroTitleStrip(stringResource(R.string.onboarding_welcome_title))
    Text(stringResource(R.string.onboarding_welcome_body), style = Retro.type.body, color = c.ink)
    Text(stringResource(R.string.onboarding_welcome_note), style = Retro.type.bodySmall, color = c.inkSoft)
}

@Composable
private fun HowPage() {
    val c = Retro.colors
    RetroTitleStrip(stringResource(R.string.onboarding_how_title))
    listOf(
        Triple("list", R.string.onboarding_how_1, R.string.onboarding_how_1_desc),
        Triple("lightning", R.string.onboarding_how_2, R.string.onboarding_how_2_desc),
        Triple("floppy", R.string.onboarding_how_3, R.string.onboarding_how_3_desc),
        Triple("play", R.string.onboarding_how_4, R.string.onboarding_how_4_desc),
    ).forEachIndexed { i, (icon, title, desc) ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).pixelBorder(c.ink), contentAlignment = Alignment.Center) {
                Text((i + 1).toString(), style = Retro.type.button, color = c.ink)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelIcon(icon, tint = c.inkSoft, size = 16.dp)
                    Text(stringResource(title), style = Retro.type.heading, color = c.ink)
                }
                Text(stringResource(desc), style = Retro.type.bodySmall, color = c.inkSoft)
            }
        }
    }
    Text(stringResource(R.string.disclaimer), style = Retro.type.bodySmall, color = c.inkSoft)
}

@Composable
private fun GamePage() {
    val c = Retro.colors
    RetroTitleStrip(stringResource(R.string.onboarding_game_title))
    listOf(
        "coin" to R.string.onboarding_game_xp,
        "flame" to R.string.onboarding_game_streak,
        "medal" to R.string.onboarding_game_missions,
        "trophy" to R.string.onboarding_game_trophies,
    ).forEach { (sprite, text) ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                PixelSprite(sprite, Modifier.pixelFloat(amplitude = 1.dp), pixel = 3.dp, reward = sprite == "coin")
            }
            Text(stringResource(text), Modifier.weight(1f), style = Retro.type.body, color = c.ink)
        }
    }
}

@Composable
private fun YouPage(name: String, onName: (String) -> Unit, settings: AppSettings, onPalette: (RetroPalette) -> Unit, onMode: (DarkMode) -> Unit) {
    val c = Retro.colors
    Centered { AvatarView(NeutralAvatarState, null, pixel = 2.5.dp) }
    RetroTitleStrip(stringResource(R.string.onboarding_you_title))
    LabeledTextField(stringResource(R.string.onboarding_name), name, onName, supporting = stringResource(R.string.onboarding_name_help))
    SectionTitle(stringResource(R.string.settings_palette))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RetroPalette.entries.forEach { p ->
            PaletteSwatch(p, settings.darkMode, selected = p == settings.palette, Modifier.weight(1f)) { onPalette(p) }
        }
    }
    SectionTitle(stringResource(R.string.settings_mode))
    ChoiceChips(DarkMode.entries, settings.darkMode, { modeName(it) }, onMode)
    Text(stringResource(R.string.onboarding_you_note), style = Retro.type.bodySmall, color = c.inkSoft, textAlign = TextAlign.Start)
}

@Composable
private fun modeName(m: DarkMode) = stringResource(
    when (m) {
        DarkMode.SYSTEM -> R.string.retro_mode_system
        DarkMode.LIGHT -> R.string.retro_mode_light
        DarkMode.DARK -> R.string.retro_mode_dark
    },
)

/** Palette sample painted with its own theme: paper, a coin and the four sprite tones. */
@Composable
fun PaletteSwatch(palette: RetroPalette, darkMode: DarkMode, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val outer = Retro.colors
    Column(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HybridCoachTheme(palette = palette, darkMode = darkMode) {
            val c = Retro.colors
            Column(
                Modifier.fillMaxWidth().background(c.background).pixelBorder(if (selected) c.ink else c.inkFaint, if (selected) 4.dp else 2.dp).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PixelSprite("coin", pixel = 2.dp, reward = true)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(c.spriteHighlight, c.spriteLight, c.spriteMid, c.ink).forEach {
                        Box(Modifier.size(10.dp).background(it))
                    }
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(c.ink))
            }
        }
        Text(
            palette.label.uppercase(PT_BR),
            style = Retro.type.label,
            color = if (selected) outer.ink else outer.inkSoft,
        )
    }
}
