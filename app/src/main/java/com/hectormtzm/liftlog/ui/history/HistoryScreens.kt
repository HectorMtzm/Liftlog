package com.hectormtzm.liftlog.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.data.Settings
import com.hectormtzm.liftlog.data.WeightUnit
import com.hectormtzm.liftlog.data.WorkoutEntity
import com.hectormtzm.liftlog.data.WorkoutExerciseFull
import com.hectormtzm.liftlog.data.WorkoutSummary
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import com.hectormtzm.liftlog.ui.components.ConfirmDialog
import com.hectormtzm.liftlog.ui.components.EmptyState
import com.hectormtzm.liftlog.ui.components.ExerciseThumb
import com.hectormtzm.liftlog.ui.components.PrBadge
import com.hectormtzm.liftlog.ui.components.StatBlock
import com.hectormtzm.liftlog.ui.formatDate
import com.hectormtzm.liftlog.ui.formatDuration
import com.hectormtzm.liftlog.ui.formatTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(c: AppContainer) : ViewModel() {
    val history: StateFlow<List<WorkoutSummary>?> =
        c.workouts.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<Settings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())
}

class SessionViewModel(private val c: AppContainer, val workoutId: Long) : ViewModel() {
    val workout: StateFlow<WorkoutEntity?> =
        c.workouts.observeWorkout(workoutId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val exercises: StateFlow<List<WorkoutExerciseFull>> =
        c.workouts.observeExercises(workoutId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<Settings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.workouts.delete(workoutId); onDone() }
}

private fun volumeText(kg: Double, unit: WeightUnit): String {
    val v = unit.fromKg(kg)
    return if (v >= 10_000) String.format(java.util.Locale.US, "%.1fk", v / 1000) else WeightUnit.formatNumber(v)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavController) {
    val vm = appViewModel { HistoryViewModel(it) }
    val history by vm.history.collectAsState()
    val settings by vm.settings.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("History") }, scrollBehavior = scroll) },
    ) { padding ->
        val list = history
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.History,
                title = "No workouts yet",
                message = "Finished workouts show up here with their sets, volume and personal records.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { w ->
                    Card(
                        onClick = { nav.navigate(Routes.session(w.id)) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    w.name, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                if (w.prCount > 0) {
                                    PrBadge()
                                    if (w.prCount > 1) Text(
                                        " x${w.prCount}", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            Text(
                                "${formatDate(w.startedAt)}  ·  ${formatTime(w.startedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row {
                                StatBlock("Duration", formatDuration((w.finishedAt ?: w.startedAt) - w.startedAt), Modifier.weight(1f))
                                StatBlock("Volume (${settings.unit.label})", volumeText(w.volumeKg, settings.unit), Modifier.weight(1f))
                                StatBlock("Sets", "${w.setCount}", Modifier.weight(0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(nav: NavController, workoutId: Long) {
    val vm = appViewModel(key = "session$workoutId") { SessionViewModel(it, workoutId) }
    val workout by vm.workout.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val settings by vm.settings.collectAsState()
    val unit = settings.unit
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(workout?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        workout?.let {
                            Text(
                                "${formatDate(it.startedAt)}  ·  ${formatTime(it.startedAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete workout") }
                },
            )
        },
    ) { padding ->
        val w = workout ?: return@Scaffold
        val done = exercises.flatMap { it.sets }.filter { it.completed }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        StatBlock("Duration", formatDuration((w.finishedAt ?: System.currentTimeMillis()) - w.startedAt), Modifier.weight(1f))
                        StatBlock("Volume (${unit.label})", volumeText(done.sumOf { it.weightKg * it.reps }, unit), Modifier.weight(1f))
                        StatBlock("Sets", "${done.size}", Modifier.weight(0.6f))
                        StatBlock("PRs", "${done.count { it.isPr }}", Modifier.weight(0.6f))
                    }
                }
            }
            items(exercises, key = { it.item.id }) { we ->
                Card(
                    onClick = { nav.navigate(Routes.exercise(we.item.exerciseId)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseThumb(we.exercise, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                we.exercise?.name ?: "Deleted exercise",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        we.sets.filter { it.completed }.sortedBy { it.position }.forEachIndexed { i, s ->
                            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${i + 1}", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp),
                                )
                                Text("${unit.format(s.weightKg)} ${unit.label} x ${s.reps}", style = MaterialTheme.typography.bodyLarge)
                                if (s.isPr) { Spacer(Modifier.width(8.dp)); PrBadge() }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete workout?",
            message = "This workout and its sets will be permanently removed from your history.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete { nav.popBackStack() } },
        )
    }
}
