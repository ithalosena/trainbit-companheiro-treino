package com.ithalo.hybridcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ithalo.hybridcoach.ui.boot.BootScreen
import com.ithalo.hybridcoach.ui.onboarding.OnboardingScreen
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ithalo.hybridcoach.ui.nav.AppNav
import com.ithalo.hybridcoach.ui.theme.HybridCoachTheme
import com.ithalo.hybridcoach.ui.theme.Retro

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = (application as HybridCoachApp).container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            // The boot screen plays once per launch (not again on rotation).
            var booted by rememberSaveable { mutableStateOf(false) }
            // Wait for the stored palette (a few ms) instead of flashing the default one.
            val s = settings
            if (s != null) {
                HybridCoachTheme(palette = s.palette, darkMode = s.darkMode) {
                    SystemBarIcons()
                    when {
                        !booted -> BootScreen(onDone = { booted = true })
                        !s.onboardingDone -> OnboardingScreen(s)
                        else -> AppNav(s)
                    }
                }
            }
        }
    }

    /** Status bar icons follow the title bar colour, navigation bar icons the screen background. */
    @Composable
    private fun SystemBarIcons() {
        val c = Retro.colors
        val lightStatus = c.titleBar.luminance() > 0.5f
        val lightNav = c.background.luminance() > 0.5f
        SideEffect {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightStatus
                isAppearanceLightNavigationBars = lightNav
            }
        }
    }
}
