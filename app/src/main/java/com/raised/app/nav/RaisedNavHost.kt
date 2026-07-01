package com.raised.app.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.raised.app.ui.config.ConfigScreen
import com.raised.app.ui.home.HomeScreen
import com.raised.app.ui.session.SessionScreen
import com.raised.core.WorkoutType

/** Route constants for the nav graph (issue #4 owns the graph). */
object Routes {
    const val HOME = "home"

    /** Config editor for one workout type. `type` ∈ {HIIT, RAISED}. */
    const val CONFIG = "config/{type}"
    fun config(type: WorkoutType) = "config/${type.name}"

    /**
     * Session player for one workout type. `type` ∈ {HIIT, RAISED}.
     */
    const val SESSION = "session/{type}"
    fun session(type: WorkoutType) = "session/${type.name}"
}

/**
 * Top-level app composable + nav graph. Three routes:
 *  - [Routes.HOME] — the two workout cards.
 *  - [Routes.CONFIG] — editor for the chosen workout's working config.
 *  - [Routes.SESSION] — the running-workout screen driving the timer engine.
 *
 * Start navigates to `session/{type}` with just the type arg; the Session
 * rebuilds the plan from the current stored config (D3/D8).
 */
@Composable
fun RaisedApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenConfig = { type -> navController.navigate(Routes.config(type)) },
                onStart = { type -> navController.navigate(Routes.session(type)) },
            )
        }
        composable(
            route = Routes.CONFIG,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) {
            ConfigScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.SESSION,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { backStackEntry ->
            SessionScreen(onExit = { navController.popBackStack(Routes.HOME, false) })
        }
    }
}
