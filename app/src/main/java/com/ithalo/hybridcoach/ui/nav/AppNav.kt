package com.ithalo.hybridcoach.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.AppSettings
import com.ithalo.hybridcoach.ui.block.BlockScreen
import com.ithalo.hybridcoach.ui.block.ExportScreen
import com.ithalo.hybridcoach.ui.block.ImportScreen
import com.ithalo.hybridcoach.ui.block.PromptScreen
import com.ithalo.hybridcoach.ui.block.QuestionnaireScreen
import com.ithalo.hybridcoach.ui.calendar.CalendarScreen
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.game.GameHud
import com.ithalo.hybridcoach.ui.game.GameOverlay
import com.ithalo.hybridcoach.ui.retro.LocalUnderHud
import com.ithalo.hybridcoach.ui.game.ItemsScreen
import com.ithalo.hybridcoach.ui.game.ProfileScreen
import com.ithalo.hybridcoach.ui.game.TrophiesScreen
import com.ithalo.hybridcoach.ui.history.ExerciseHistoryScreen
import com.ithalo.hybridcoach.ui.history.HistoryScreen
import com.ithalo.hybridcoach.ui.player.PlayerScreen
import com.ithalo.hybridcoach.ui.retro.DesignShowcaseScreen
import com.ithalo.hybridcoach.ui.retro.RetroTab
import com.ithalo.hybridcoach.ui.retro.RetroTabBar
import com.ithalo.hybridcoach.ui.run.RunScreen
import com.ithalo.hybridcoach.ui.run.RunsScreen
import com.ithalo.hybridcoach.ui.session.SessionScreen
import com.ithalo.hybridcoach.ui.settings.SettingsScreen
import com.ithalo.hybridcoach.ui.today.TodayScreen
import kotlinx.coroutines.launch
import kotlin.math.floor

private data class Tab(val route: String, val label: Int, val icon: String)

private val TABS = listOf(
    Tab("today", R.string.tab_today, "home"),
    Tab("calendar", R.string.tab_calendar, "calendar"),
    Tab("runs", R.string.tab_runs, "run"),
    Tab("profile", R.string.tab_trophies, "face"),
    Tab("settings", R.string.tab_settings, "gear"),
)

/** Hard 6-step easing so screen changes move like a low-frame-rate sprite. */
private val PixelSteps = Easing { x -> floor(x * 6f) / 6f }

private const val TRANSITION_MS = 240

private fun enter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(TRANSITION_MS, easing = PixelSteps)) + slideInHorizontally(tween(TRANSITION_MS, easing = PixelSteps)) { it / 10 }
}

private fun exit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(TRANSITION_MS / 2, easing = PixelSteps))
}

private fun popEnter(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(TRANSITION_MS, easing = PixelSteps)) + slideInHorizontally(tween(TRANSITION_MS, easing = PixelSteps)) { -it / 10 }
}

private fun popExit(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(TRANSITION_MS / 2, easing = PixelSteps)) + slideOutHorizontally(tween(TRANSITION_MS / 2, easing = PixelSteps)) { it / 10 }
}

@Composable
fun AppNav(settings: AppSettings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val container = appContainer()
    val scope = rememberCoroutineScope()

    fun goTab(target: String) = nav.navigate(target) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val showTabs = TABS.any { it.route == route }
    // Players should not be interrupted by the full-screen level-up; it waits until the user leaves.
    val inPlayer = route?.startsWith("player/") == true || route?.startsWith("run/") == true

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (showTabs) {
                // HUD + folder tabs on top (they own the status bar inset); screens below skip it.
                GameHud(onProfile = { goTab("profile") })
                val items = TABS.map { RetroTab(stringResource(it.label), it.icon) }
                RetroTabBar(items, TABS.indexOfFirst { it.route == route }, onSelect = { goTab(TABS[it].route) })
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (showTabs) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
            ) {
                CompositionLocalProvider(LocalUnderHud provides showTabs) {
                NavHost(
                    nav,
                    startDestination = "today",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = enter(),
                    exitTransition = exit(),
                    popEnterTransition = popEnter(),
                    popExitTransition = popExit(),
                ) {
                    composable("today") {
                        TodayScreen(
                            onOpenSession = { nav.navigate("session/$it") },
                            onStart = { id, type -> nav.navigate(if (type == "run") "run/$id" else "player/$id") },
                            onCreateBlock = { nav.navigate("block") },
                            onProfile = { goTab("profile") },
                        )
                    }
                    composable("calendar") { CalendarScreen(onOpenSession = { nav.navigate("session/$it") }, onBlock = { nav.navigate("block") }) }
                    composable("runs") { RunsScreen(onStart = { nav.navigate("run/$it") }, onOpenSession = { nav.navigate("session/$it") }) }
                    composable("block") {
                        BlockScreen(
                            onBack = { nav.popBackStack() },
                            onQuestionnaire = { nav.navigate("questionnaire") },
                            onPrompt = { nav.navigate("prompt") },
                            onImport = { nav.navigate("import") },
                            onExport = { nav.navigate("export") },
                            onHistory = { nav.navigate("history") },
                        )
                    }
                    composable("profile") {
                        ProfileScreen(onTrophies = { nav.navigate("trophies") }, onItems = { nav.navigate("items") })
                    }
                    composable("trophies") { TrophiesScreen(onBack = { nav.popBackStack() }) }
                    composable("items") { ItemsScreen(onBack = { nav.popBackStack() }) }
                    composable("questionnaire") {
                        QuestionnaireScreen(onBack = { nav.popBackStack() }, onFinished = { nav.navigate("prompt") { popUpTo("block") } })
                    }
                    composable("prompt") {
                        PromptScreen(
                            onBack = { nav.popBackStack() },
                            onImport = { nav.navigate("import") },
                            onQuestionnaire = { nav.navigate("questionnaire") },
                        )
                    }
                    composable("import") {
                        ImportScreen(onBack = { nav.popBackStack() }, onImported = { goTab("calendar") })
                    }
                    composable("export") { ExportScreen(onBack = { nav.popBackStack() }) }
                    composable("session/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        SessionScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onStart = { id, type -> nav.navigate(if (type == "run") "run/$id" else "player/$id") })
                    }
                    composable("player/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        PlayerScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                    }
                    composable("run/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        RunScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                    }
                    composable("settings") { SettingsScreen(onBack = null, onDesign = { nav.navigate("design") }) }
                    composable("design") {
                        DesignShowcaseScreen(
                            palette = settings.palette,
                            darkMode = settings.darkMode,
                            onPaletteChange = { p -> scope.launch { container.settings.update { it.copy(palette = p) } } },
                            onDarkModeChange = { m -> scope.launch { container.settings.update { it.copy(darkMode = m) } } },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("history") {
                        HistoryScreen(onBack = { nav.popBackStack() }, onExercise = { id -> nav.navigate("history/$id") }, onSession = { id -> nav.navigate("session/$id") })
                    }
                    composable("history/{exerciseId}", arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })) {
                        ExerciseHistoryScreen(it.arguments!!.getString("exerciseId")!!, onBack = { nav.popBackStack() })
                    }
                }
                }
            }
        }
        GameOverlay(holdLevelUp = inPlayer)
    }
}
