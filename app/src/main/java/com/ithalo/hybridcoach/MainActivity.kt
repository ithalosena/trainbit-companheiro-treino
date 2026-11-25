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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ithalo.hybridcoach.ui.boot.ColdStart
import com.ithalo.hybridcoach.ui.boot.IntroScreen
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
            // The opening plays on a cold start only: not on rotation (saveable) nor when the app is reopened (ColdStart).
            var booted by rememberSaveable { mutableStateOf(!ColdStart.consume()) }
            // Wait for the stored palette (a few ms) instead of flashing the default one.
            val s = settings
            if (s != null) {
                HybridCoachTheme(palette = s.palette, darkMode = s.darkMode) {
                    SystemBarIcons()
                    // The app loads beneath the opening, which never delays it.
                    Box(Modifier.fillMaxSize()) {
                        if (!s.onboardingDone) OnboardingScreen(s) else AppNav(s)
                        if (!booted && s.introAnimation) IntroScreen(onDone = { booted = true })
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
