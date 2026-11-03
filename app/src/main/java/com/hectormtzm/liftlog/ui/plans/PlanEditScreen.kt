package com.hectormtzm.liftlog.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hectormtzm.liftlog.data.PlanExerciseFull
import com.hectormtzm.liftlog.data.WeightUnit
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import com.hectormtzm.liftlog.ui.components.ConfirmDialog
import com.hectormtzm.liftlog.ui.components.EmptyState
import com.hectormtzm.liftlog.ui.components.ExerciseThumb
import com.hectormtzm.liftlog.ui.components.NumberField
import com.hectormtzm.liftlog.ui.components.RestChip
import com.hectormtzm.liftlog.ui.components.RestPickerDialog
import com.hectormtzm.liftlog.ui.components.TextInputDialog
import com.hectormtzm.liftlog.ui.exercises.PickMode
import com.hectormtzm.liftlog.ui.titleCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(nav: NavController, planId: Long) {
    val vm = appViewModel(key = "plan$planId") { PlanEditViewModel(it, planId) }
    val plan by vm.plan.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val settings by vm.settings.collectAsState()
    val active by vm.active.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busyWorkout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        plan?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { rename = true },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { rename = true }) { Icon(Icons.Outlined.Edit, "Rename") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Duplicate plan") }, onClick = {
                                menu = false
                                vm.duplicate { id -> nav.navigate(Routes.plan(id)) { popUpTo(Routes.PLANS) } }
                            })
                            DropdownMenuItem(text = { Text("Delete plan") }, onClick = {
                                menu = false; confirmDelete = true
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!exercises.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (active != null && active?.planId != planId) busyWorkout = true
                        else vm.start { nav.navigate(Routes.WORKOUT) }
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text(if (active?.planId == planId) "Resume workout" else "Start workout") },
                )
            }
        },
    ) { padding ->
        val list = exercises ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                title = "Add exercises",
                message = "Pick exercises from the library. You can set the target sets, reps, weight and rest for each.",
                modifier = Modifier.padding(padding),
                action = {
                    OutlinedButton(onClick = { nav.navigate(Routes.picker(PickMode.PLAN, planId)) }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add exercises")
                    }
                },
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.item.id }) { pe ->
                PlanExerciseCard(
                    pe = pe,
                    unit = settings.unit,
                    isFirst = pe == list.first(),
                    isLast = pe == list.last(),
                    vm = vm,
                    onOpenExercise = { nav.navigate(Routes.exercise(pe.item.exerciseId)) },
                )
            }
            item {
                OutlinedButton(
                    onClick = { nav.navigate(Routes.picker(PickMode.PLAN, planId)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add exercises")
                }
            }
        }
    }

    if (rename) {
        TextInputDialog(
            title = "Rename plan",
            initial = plan?.name ?: "",
            confirmLabel = "Save",
            onDismiss = { rename = false },
            onConfirm = { rename = false; vm.rename(it) },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete plan?",
            message = "\"${plan?.name}\" will be removed. Your workout history is kept.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete { nav.popBackStack() } },
        )
    }
    if (busyWorkout) {
        ConfirmDialog(
            title = "Workout in progress",
            message = "Finish or discard \"${active?.name}\" before starting this plan. Resume it now?",
            confirmLabel = "Resume",
            onDismiss = { busyWorkout = false },
            onConfirm = { busyWorkout = false; nav.navigate(Routes.WORKOUT) },
        )
    }
}

@Composable
private fun PlanExerciseCard(
    pe: PlanExerciseFull,
    unit: WeightUnit,
    isFirst: Boolean,
    isLast: Boolean,
    vm: PlanEditViewModel,
    onOpenExercise: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var restPicker by remember { mutableStateOf(false) }
    var setAll by remember { mutableStateOf(false) }
    val sets = pe.sets.sortedBy { it.position }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseThumb(pe.exercise, size = 44.dp, modifier = Modifier.clickable(onClick = onOpenExercise))
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenExercise)
                ) {
                    Text(
                        pe.exercise?.name ?: "Deleted exercise",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pe.exercise?.primaryMuscleList?.joinToString(", ")?.titleCase() ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Exercise options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Set all sets...") }, onClick = { menu = false; setAll = true })
                        if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; vm.move(pe.item.id, -1) })
                        if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; vm.move(pe.item.id, 1) })
                        DropdownMenuItem(text = { Text("Remove from plan") }, onClick = { menu = false; vm.removeExercise(pe.item.id) })
                    }
                }
            }
            RestChip(pe.item.restSeconds, onClick = { restPicker = true }, modifier = Modifier.padding(top = 4.dp))

            Row(Modifier.padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderText("SET", Modifier.width(40.dp))
                HeaderText(unit.label.uppercase(), Modifier.weight(1f))
                Spacer(Modifier.width(24.dp))
                HeaderText("REPS", Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
            }
            sets.forEachIndexed { index, s ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(40.dp),
                    )
                    NumberField(
                        value = unit.fromKg(s.weightKg),
                        onValueChange = { vm.updateSet(s.copy(weightKg = unit.toKg(it))) },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "x", textAlign = TextAlign.Center, modifier = Modifier.width(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NumberField(
                        value = s.reps.toDouble(),
                        onValueChange = { vm.updateSet(s.copy(reps = it.toInt())) },
                        decimal = false,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.removeSet(s.id) }, enabled = sets.size > 1, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, "Remove set", modifier = Modifier.size(18.dp))
                    }
                }
            }
            TextButton(onClick = { vm.addSet(pe.item.id) }) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add set")
            }
        }
    }

    if (restPicker) {
        RestPickerDialog(
            current = pe.item.restSeconds,
            onDismiss = { restPicker = false },
            onPick = { restPicker = false; vm.setRest(pe.item.id, it) },
        )
    }
    if (setAll) {
        val first = sets.firstOrNull()
        var count by remember { mutableIntStateOf(sets.size.coerceAtLeast(1)) }
        var reps by remember { mutableIntStateOf(first?.reps ?: 10) }
        var weight by remember { mutableDoubleStateOf(unit.fromKg(first?.weightKg ?: 0.0)) }
        AlertDialog(
            onDismissRequest = { setAll = false },
            title = { Text("Set all sets") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledField("Sets", Modifier.weight(1f)) {
                        NumberField(count.toDouble(), { count = it.toInt().coerceIn(1, 20) }, decimal = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    LabeledField("Reps", Modifier.weight(1f)) {
                        NumberField(reps.toDouble(), { reps = it.toInt() }, decimal = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    LabeledField(unit.label, Modifier.weight(1.2f)) {
                        NumberField(weight, { weight = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    setAll = false
                    vm.applyAll(pe.item.id, count.coerceAtLeast(1), reps, unit.toKg(weight))
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { setAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LabeledField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))
        content()
    }
}

@Composable
fun HeaderText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

