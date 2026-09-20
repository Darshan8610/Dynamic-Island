package dev.island

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.island.core.AppGraph
import dev.island.core.ui.theme.IslandAppTheme
import dev.island.domain.model.IslandSettings
import dev.island.feature.app.CustomizeScreen
import dev.island.feature.app.DashboardScreen
import dev.island.feature.app.DemoScreen
import dev.island.feature.app.DiagnosticsScreen
import dev.island.feature.app.EventLogScreen
import dev.island.feature.app.HistoryScreen
import dev.island.feature.app.OnboardingScreen
import dev.island.feature.app.PerAppScreen
import dev.island.feature.app.PerfScreen
import dev.island.feature.app.Routes
import dev.island.feature.app.SettingsScreen
import dev.island.feature.app.TimersScreen
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The app behind the overlay: dashboard, settings, onboarding, timers, demo, diagnostics, history.
 *
 * The Activity is intentionally a shell. All state lives in the repositories and the engine (which
 * outlive the Activity), so there is nothing to restore after a configuration change and no
 * ViewModel is needed — screens observe flows and write through the same repository the overlay
 * service uses, which is why a toggle here changes the island immediately.
 */
class MainActivity : ComponentActivity() {

    /** Deep links (notification tap, overlay long press, timer notification) land here. */
    private val navigationRequests = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        navigationRequests.value = routeFor(intent)

        setContent {
            val settings by AppGraph.settingsRepository.settings.collectAsState(initial = IslandSettings.Default)
            IslandAppTheme(appearance = settings.appearance, reduceMotion = settings.behavior.reduceMotion) {
                IslandApp(
                    settings = settings,
                    navigationRequests = navigationRequests,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFor(intent)?.let { navigationRequests.value = it }
    }

    private fun routeFor(intent: Intent?): String? = when {
        intent == null -> null
        intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) -> Routes.SETTINGS
        intent.hasExtra(EXTRA_OPEN_TIMER) -> Routes.TIMERS
        intent.getBooleanExtra(EXTRA_OPEN_DEMO, false) -> Routes.DEMO
        intent.getBooleanExtra(EXTRA_OPEN_DIAGNOSTICS, false) -> Routes.DIAGNOSTICS
        intent.getBooleanExtra(EXTRA_OPEN_ONBOARDING, false) -> Routes.ONBOARDING
        else -> null
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "dev.island.extra.OPEN_SETTINGS"
        const val EXTRA_OPEN_TIMER = "dev.island.extra.OPEN_TIMER"
        const val EXTRA_OPEN_DEMO = "dev.island.extra.OPEN_DEMO"
        const val EXTRA_OPEN_DIAGNOSTICS = "dev.island.extra.OPEN_DIAGNOSTICS"
        const val EXTRA_OPEN_ONBOARDING = "dev.island.extra.OPEN_ONBOARDING"
    }
}

@Composable
private fun IslandApp(
    settings: IslandSettings,
    navigationRequests: MutableStateFlow<String?>,
) {
    val navController = rememberNavController()
    val startDestination = if (settings.onboardingComplete) Routes.DASHBOARD else Routes.ONBOARDING

    // A deep link must not fight the user's own navigation: it is consumed exactly once.
    LaunchedEffect(navController) {
        navigationRequests.collect { route ->
            if (route != null) {
                navController.navigate(route) { launchSingleTop = true }
                navigationRequests.value = null
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                settings = settings,
                onFinished = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                settings = settings,
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                onBack = null,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
            )
        }
        composable(Routes.CUSTOMIZE) {
            CustomizeScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PER_APP) {
            PerAppScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TIMERS) {
            TimersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DEMO) {
            DemoScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EVENT_LOG) {
            EventLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PERF) {
            PerfScreen(onBack = { navController.popBackStack() })
        }
    }
}
