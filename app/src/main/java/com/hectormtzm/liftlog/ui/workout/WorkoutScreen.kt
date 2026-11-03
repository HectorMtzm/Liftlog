package com.hectormtzm.liftlog.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hectormtzm.liftlog.data.WeightUnit
import com.hectormtzm.liftlog.data.WorkoutExerciseFull
import com.hectormtzm.liftlog.data.WorkoutSetEntity
import com.hectormtzm.liftlog.timer.RestTimer
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import com.hectormtzm.liftlog.ui.components.ConfirmDialog
import com.hectormtzm.liftlog.ui.components.EmptyState
import com.hectormtzm.liftlog.ui.components.ExerciseThumb
import com.hectormtzm.liftlog.ui.components.NumberField
import com.hectormtzm.liftlog.ui.components.PrBadge
import com.hectormtzm.liftlog.ui.components.RestChip
import com.hectormtzm.liftlog.ui.components.RestPickerDialog
import com.hectormtzm.liftlog.ui.components.TextInputDialog
import com.hectormtzm.liftlog.ui.exercises.PickMode
import com.hectormtzm.liftlog.ui.formatClock
import com.hectormtzm.liftlog.ui.plans.HeaderText
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(nav: NavController) {
    val vm = appViewModel { WorkoutViewModel(it) }
    val loaded by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    val timer by RestTimer.state.collectAsState()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    // Set while finishing/discarding so the auto-close below does not pop twice.
    var exiting by remember { mutableStateOf(false) }

    // Keep the screen on while logging a workout.
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val state = loaded
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val ui = state.value
    if (ui == null) {
        if (!exiting) LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val workout = ui.workout
    val totalSets = ui.exercises.sumOf { it.sets.size }
    val doneSets = ui.exercises.sumOf { ex -> ex.sets.count { it.completed } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { rename = true }) {
                        Text(workout.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ElapsedText(workout.startedAt, doneSets, totalSets)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    Button(onClick = { confirmFinish = true }) { Text("Finish") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename workout") }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text("Discard workout") }, onClick = { menu = false; confirmDiscard = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            timer?.let { t ->
                RestTimerBar(
                    state = t,
                    onAdjust = { RestTimer.adjust(context, it) },
                    onSkip = { RestTimer.stop(context) },
                )
            }
        },
    ) { padding ->
        if (ui.exercises.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = "No exercises",
                message = "Add exercises to start logging sets.",
                modifier = Modifier.padding(padding),
                action = {
                    OutlinedButton(onClick = { nav.navigate(Routes.picker(PickMode.WORKOUT, workout.id)) }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add exercises")
                    }
                },
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ui.exercises, key = { it.item.id }) { we ->
                WorkoutExerciseCard(
                    we = we,
                    previous = ui.previous[we.item.exerciseId].orEmpty(),
                    unit = settings.unit,
                    isFirst = we == ui.exercises.first(),
                    isLast = we == ui.exercises.last(),
                    vm = vm,
                    onOpenExercise = { nav.navigate(Routes.exercise(we.item.exerciseId)) },
                    onReplace = { nav.navigate(Routes.picker(PickMode.REPLACE, we.item.id)) },
                )
            }
            item {
                OutlinedButton(
                    onClick = { nav.navigate(Routes.picker(PickMode.WORKOUT, workout.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add exercise")
                }
            }
        }
    }

    if (confirmFinish) {
        val remaining = totalSets - doneSets
        ConfirmDialog(
            title = "Finish workout?",
            message = if (doneSets == 0) "No sets are marked done, so nothing will be saved."
            else if (remaining > 0) "$doneSets of $totalSets sets done. The $remaining unfinished sets will not be saved."
            else "All $totalSets sets done. Nice work.",
            confirmLabel = "Finish",
            onDismiss = { confirmFinish = false },
            onConfirm = {
                confirmFinish = false
                exiting = true
                vm.finish(workout.id) { saved ->
                    if (saved) nav.navigate(Routes.session(workout.id)) { popUpTo(Routes.WORKOUT) { inclusive = true } }
                    else nav.popBackStack()
                }
            },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            message = "Everything logged in this workout will be deleted.",
            confirmLabel = "Discard",
            destructive = true,
            onDismiss = { confirmDiscard = false },
            onConfirm = { confirmDiscard = false; exiting = true; vm.discard(workout.id) { nav.popBackStack() } },
        )
    }
    if (rename) {
        TextInputDialog(
            title = "Rename workout",
            initial = workout.name,
            confirmLabel = "Save",
            onDismiss = { rename = false },
            onConfirm = { rename = false; vm.rename(workout, it) },
        )
    }
}

@Composable
private fun ElapsedText(startedAt: Long, done: Int, total: Int) {
    val elapsed by produceState(System.currentTimeMillis() - startedAt, startedAt) {
        while (true) {
            value = System.currentTimeMillis() - startedAt
            delay(1000)
        }
    }
    Text(
        "${formatClock(elapsed / 1000)}  ·  $done/$total sets",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WorkoutExerciseCard(
    we: WorkoutExerciseFull,
    previous: List<WorkoutSetEntity>,
    unit: WeightUnit,
    isFirst: Boolean,
    isLast: Boolean,
    vm: WorkoutViewModel,
    onOpenExercise: () -> Unit,
    onReplace: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var restPicker by remember { mutableStateOf(false) }
    val sets = we.sets.sortedBy { it.position }
    val name = we.exercise?.name ?: "Deleted exercise"
    val allDone = sets.isNotEmpty() && sets.all { it.completed }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(we.exercise, size = 40.dp, modifier = Modifier.clickable(onClick = onOpenExercise))
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenExercise)
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Exercise options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { menu = false; onReplace() })
                        if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.move(we.item.workoutId, we.item.id, -1) })
                        if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.move(we.item.workoutId, we.item.id, 1) })
                        DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { menu = false; vm.removeExercise(we.item.id) })
                    }
                }
            }
            RestChip(we.item.restSeconds, onClick = { restPicker = true }, modifier = Modifier.padding(top = 2.dp))

            Row(Modifier.padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderText("SET", Modifier.width(36.dp))
                HeaderText("PREVIOUS", Modifier.weight(1.1f))
                HeaderText(unit.label.uppercase(), Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                HeaderText("REPS", Modifier.weight(0.8f))
                Spacer(Modifier.width(52.dp))
            }
            sets.forEachIndexed { index, s ->
                SetRow(
                    index = index,
                    set = s,
                    previous = previous.getOrNull(index),
                    unit = unit,
                    canRemove = sets.size > 1,
                    onToggle = { vm.toggleSet(s, name) },
                    onWeight = { vm.updateSet(s, s.reps, unit.toKg(it)) },
                    onReps = { vm.updateSet(s, it.toInt(), s.weightKg) },
                    onUsePrevious = { p -> vm.updateSet(s, p.reps, p.weightKg) },
                    onRemove = { vm.removeSet(s.id) },
                )
            }
            TextButton(onClick = { vm.addSet(we.item.id) }) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add set")
            }
        }
    }

    if (restPicker) {
        RestPickerDialog(
            current = we.item.restSeconds,
            onDismiss = { restPicker = false },
            onPick = { restPicker = false; vm.setRest(we.item.id, it) },
        )
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: WorkoutSetEntity,
    previous: WorkoutSetEntity?,
    unit: WeightUnit,
    canRemove: Boolean,
    onToggle: () -> Unit,
    onWeight: (Double) -> Unit,
    onReps: (Double) -> Unit,
    onUsePrevious: (WorkoutSetEntity) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val rowBg by animateColorAsState(
        if (set.completed) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        label = "rowBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menu = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.titleSmall)
                if (set.isPr) PrBadge()
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (previous != null) DropdownMenuItem(
                    text = { Text("Use previous (${unit.format(previous.weightKg)} x ${previous.reps})") },
                    onClick = { menu = false; onUsePrevious(previous) },
                )
                DropdownMenuItem(
                    text = { Text("Remove set") },
                    enabled = canRemove,
                    onClick = { menu = false; onRemove() },
                )
            }
        }
        Text(
            previous?.let { "${unit.format(it.weightKg)} x ${it.reps}" } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .weight(1.1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = previous != null) { previous?.let(onUsePrevious) }
                .padding(vertical = 8.dp),
        )
        NumberField(
            value = unit.fromKg(set.weightKg),
            onValueChange = onWeight,
            highlighted = set.completed,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        NumberField(
            value = set.reps.toDouble(),
            onValueChange = onReps,
            decimal = false,
            highlighted = set.completed,
            modifier = Modifier.weight(0.8f),
        )
        Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
            val bg by animateColorAsState(
                if (set.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "checkBg",
            )
            val fg = if (set.completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, if (set.completed) "Mark set not done" else "Mark set done", tint = fg)
            }
        }
    }
}

