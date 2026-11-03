package com.hectormtzm.liftlog.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hectormtzm.liftlog.LiftlogApp
import com.hectormtzm.liftlog.ui.exercises.ExerciseDetailScreen
import com.hectormtzm.liftlog.ui.exercises.ExerciseLibraryScreen
import com.hectormtzm.liftlog.ui.exercises.PickMode
import com.hectormtzm.liftlog.ui.history.HistoryScreen
import com.hectormtzm.liftlog.ui.history.SessionDetailScreen
import com.hectormtzm.liftlog.ui.plans.PlanEditScreen
import com.hectormtzm.liftlog.ui.plans.PlansScreen
import com.hectormtzm.liftlog.ui.settings.SettingsScreen
import com.hectormtzm.liftlog.ui.workout.WorkoutScreen
import kotlinx.coroutines.delay

object Routes {
    const val PLANS = "plans"
    const val HISTORY = "history"
    const val EXERCISES = "exercises"
    const val WORKOUT = "workout"
    const val SETTINGS = "settings"
    fun plan(id: Long) = "plan/$id"
    fun session(id: Long) = "session/$id"
    fun exercise(id: String) = "exercise/${Uri.encode(id)}"
    fun picker(mode: PickMode, targetId: Long) = "picker/${mode.name}/$targetId"
}

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

private val topDestinations = listOf(
    TopDest(Routes.PLANS, "Plans", Icons.Outlined.FitnessCenter),
    TopDest(Routes.HISTORY, "History", Icons.Outlined.History),
    TopDest(Routes.EXERCISES, "Exercises", Icons.AutoMirrored.Outlined.MenuBook),
)

@Composable
fun LiftlogRoot(openWorkoutRequests: Int) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as LiftlogApp
    val active by app.container.workouts.observeActive().collectAsState(initial = null)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTop = topDestinations.any { it.route == route }

    LaunchedEffect(openWorkoutRequests) {
        if (openWorkoutRequests > 0 && nav.currentDestination?.route != Routes.WORKOUT) {
            nav.navigate(Routes.WORKOUT) { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            if (isTop) {
                Column {
                    val w = active
                    AnimatedVisibility(visible = w != null) {
                        if (w != null) ActiveWorkoutBanner(w.name, w.startedAt) {
                            nav.navigate(Routes.WORKOUT) { launchSingleTop = true }
                        }
                    }
                    NavigationBar {
                        topDestinations.forEach { d ->
                            NavigationBarItem(
                                selected = route == d.route,
                                onClick = { nav.navigateTop(d.route) },
                                icon = { Icon(d.icon, null) },
                                label = { Text(d.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.PLANS,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.PLANS) { PlansScreen(nav) }
            composable(Routes.HISTORY) { HistoryScreen(nav) }
            composable(Routes.EXERCISES) { ExerciseLibraryScreen(nav, PickMode.BROWSE, -1L) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.WORKOUT) { WorkoutScreen(nav) }
            composable(
                "plan/{planId}",
                arguments = listOf(navArgument("planId") { type = NavType.LongType }),
            ) { entry -> PlanEditScreen(nav, entry.arguments!!.getLong("planId")) }
            composable(
                "session/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
            ) { entry -> SessionDetailScreen(nav, entry.arguments!!.getLong("workoutId")) }
            composable(
                "exercise/{exerciseId}",
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
            ) { entry -> ExerciseDetailScreen(nav, Uri.decode(entry.arguments!!.getString("exerciseId")!!)) }
            composable(
                "picker/{mode}/{targetId}",
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("targetId") { type = NavType.LongType },
                ),
            ) { entry ->
                ExerciseLibraryScreen(
                    nav,
                    PickMode.valueOf(entry.arguments!!.getString("mode")!!),
                    entry.arguments!!.getLong("targetId"),
                )
            }
        }
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ActiveWorkoutBanner(name: String, startedAt: Long, onClick: () -> Unit) {
    val elapsed by produceState(initialValue = System.currentTimeMillis() - startedAt, startedAt) {
        while (true) {
            value = System.currentTimeMillis() - startedAt
            delay(1000)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Workout in progress", style = MaterialTheme.typography.labelMedium)
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatClock(elapsed / 1000), style = MaterialTheme.typography.titleMedium)
        }
    }
}
