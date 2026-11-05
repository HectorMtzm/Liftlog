package com.hectormtzm.liftlog.ui.exercises

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.hectormtzm.liftlog.data.ExerciseEntity
import com.hectormtzm.liftlog.data.ExerciseRepository
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import com.hectormtzm.liftlog.ui.components.EmptyState
import com.hectormtzm.liftlog.ui.components.ExerciseThumb
import com.hectormtzm.liftlog.ui.components.OptionPickerDialog
import com.hectormtzm.liftlog.ui.titleCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(nav: NavController, mode: PickMode, targetId: Long) {
    val vm = appViewModel(key = "lib-$mode-$targetId") { ExerciseLibraryViewModel(it, mode, targetId) }
    val results by vm.results.collectAsState()
    val filter by vm.filter.collectAsState()
    val selected by vm.selected.collectAsState()
    var showCustom by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val picking = mode != PickMode.BROWSE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            PickMode.BROWSE -> "Exercises"
                            PickMode.REPLACE -> "Replace with"
                            else -> "Add exercises"
                        }
                    )
                },
                navigationIcon = {
                    if (picking) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (picking) TextButton(onClick = { showCustom = true }) { Text("New custom") }
                },
            )
        },
        floatingActionButton = {
            if (!picking) FloatingActionButton(onClick = { showCustom = true }) {
                Icon(Icons.Filled.Add, "Create custom exercise")
            }
        },
        bottomBar = {
            if (picking && mode != PickMode.REPLACE && selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Button(
                        onClick = { vm.confirm(selected) { nav.popBackStack() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text("Add ${selected.size} exercise" + if (selected.size == 1) "" else "s")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search ${results?.size?.takeIf { filter.query.isEmpty() && filter.muscle == null && filter.equipment == null }?.let { "$it " } ?: ""}exercises") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    DropdownFilterChip(
                        label = "Muscle",
                        value = filter.muscle,
                        options = ExerciseRepository.MUSCLES,
                        onChange = vm::setMuscle,
                    )
                }
                item {
                    DropdownFilterChip(
                        label = "Equipment",
                        value = filter.equipment,
                        options = ExerciseRepository.EQUIPMENT,
                        onChange = vm::setEquipment,
                    )
                }
                item {
                    FilterChip(
                        selected = filter.customOnly,
                        onClick = { vm.setCustomOnly(!filter.customOnly) },
                        label = { Text("Custom") },
                    )
                }
            }
            val list = results
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No matches",
                    message = "Try a different search or filter, or create a custom exercise.",
                    action = { TextButton(onClick = { showCustom = true }) { Text("Create custom exercise") } },
                )
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(list, key = { it.id }) { ex ->
                        ExerciseRow(
                            ex = ex,
                            checked = if (picking && mode != PickMode.REPLACE) ex.id in selected else null,
                            onClick = {
                                focus.clearFocus()
                                when (mode) {
                                    PickMode.BROWSE -> nav.navigate(Routes.exercise(ex.id))
                                    PickMode.REPLACE -> vm.confirm(listOf(ex.id)) { nav.popBackStack() }
                                    else -> vm.toggle(ex.id)
                                }
                            },
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 80.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }

    if (showCustom) {
        CustomExerciseDialog(
            initial = null,
            initialName = filter.query.trim().titleCase(),
            onDismiss = { showCustom = false },
            onSave = { name, muscle, equipment, category, instructions ->
                showCustom = false
                vm.saveCustom(null, name, muscle, equipment, category, instructions) { id ->
                    when (mode) {
                        PickMode.BROWSE -> nav.navigate(Routes.exercise(id))
                        PickMode.REPLACE -> vm.confirm(listOf(id)) { nav.popBackStack() }
                        else -> vm.toggle(id)
                    }
                }
            },
        )
    }
}

@Composable
private fun ExerciseRow(ex: ExerciseEntity, checked: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumb(ex, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ex.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                ex.primaryMuscleList.joinToString(", ").takeIf { it.isNotBlank() },
                ex.equipment.takeIf { it.isNotBlank() },
                if (ex.isCustom) "custom" else null,
            ).joinToString("  ·  ").titleCase()
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (checked != null) Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}

@Composable
private fun DropdownFilterChip(
    label: String,
    value: String?,
    options: List<String>,
    onChange: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    FilterChip(
        selected = value != null,
        onClick = { open = true },
        label = { Text(value?.titleCase() ?: label) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
    )
    if (open) {
        OptionPickerDialog(
            title = label,
            options = options,
            selected = value,
            label = { it.titleCase() },
            anyLabel = "Any ${label.lowercase()}",
            onDismiss = { open = false },
            onPick = { open = false; onChange(it) },
        )
    }
}
