package com.hectormtzm.liftlog.ui.exercises

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.data.ExerciseEntity
import com.hectormtzm.liftlog.data.ExerciseSetRow
import com.hectormtzm.liftlog.data.Settings
import com.hectormtzm.liftlog.data.WeightUnit
import com.hectormtzm.liftlog.data.estimatedOneRepMax
import com.hectormtzm.liftlog.data.imageUrl
import com.hectormtzm.liftlog.data.gifUrl
import com.hectormtzm.liftlog.data.ExerciseRepository
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import com.hectormtzm.liftlog.ui.components.ChartPoint
import com.hectormtzm.liftlog.ui.components.ConfirmDialog
import com.hectormtzm.liftlog.ui.components.LineChart
import com.hectormtzm.liftlog.ui.components.PrBadge
import com.hectormtzm.liftlog.ui.components.StatBlock
import com.hectormtzm.liftlog.ui.formatDate
import com.hectormtzm.liftlog.ui.titleCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExerciseDetailViewModel(private val c: AppContainer, val exerciseId: String) : ViewModel() {
    val exercise: StateFlow<ExerciseEntity?> =
        c.exercises.observe(exerciseId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val history: StateFlow<List<ExerciseSetRow>> =
        c.workouts.observeExerciseHistory(exerciseId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<Settings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun saveCustom(name: String, muscle: String, equipment: String, category: String, instructions: String) =
        viewModelScope.launch { c.exercises.saveCustom(exerciseId, name, muscle, equipment, category, instructions) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.exercises.deleteCustom(exerciseId); onDone() }
}

private enum class Metric(val label: String) { WEIGHT("Top weight"), E1RM("Est. 1RM"), VOLUME("Volume") }

private data class Session(val workoutId: Long, val name: String, val date: Long, val sets: List<ExerciseSetRow>)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(nav: NavController, exerciseId: String) {
    val vm = appViewModel(key = "ex-$exerciseId") { ExerciseDetailViewModel(it, exerciseId) }
    val ex by vm.exercise.collectAsState()
    val history by vm.history.collectAsState()
    val settings by vm.settings.collectAsState()
    val unit = settings.unit
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var metric by remember { mutableStateOf(Metric.WEIGHT) }

    val sessions = remember(history) {
        history.groupBy { it.workoutId }.map { (id, rows) ->
            Session(id, rows.first().workoutName, rows.first().startedAt, rows.sortedBy { it.position })
        }.sortedByDescending { it.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ex?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (ex?.isCustom == true) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Edit") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                    }
                },
            )
        },
    ) { padding ->
        val e = ex ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ExerciseImages(e) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    e.primaryMuscleList.forEach { Tag(it.titleCase(), strong = true) }
                    e.secondaryMuscleList.forEach { Tag(it.titleCase()) }
                    listOf(e.category, e.equipment, e.level, e.mechanic).filter { it.isNotBlank() }.distinct().forEach { Tag(it.titleCase()) }
                }
            }

            if (sessions.isNotEmpty()) {
                item {
                    val bestWeight = history.maxOf { it.weightKg }
                    val bestE1rm = history.maxOf { estimatedOneRepMax(it.weightKg, it.reps) }
                    val bestReps = history.filter { it.weightKg == bestWeight }.maxOf { it.reps }
                    SectionCard("Records") {
                        Row(Modifier.fillMaxWidth()) {
                            StatBlock("Best weight", "${unit.format(bestWeight)} ${unit.label}", Modifier.weight(1f))
                            StatBlock("Est. 1RM", "${unit.format(bestE1rm)} ${unit.label}", Modifier.weight(1f))
                            StatBlock("Sessions", "${sessions.size}", Modifier.weight(0.7f))
                        }
                        Text(
                            "Best set: ${unit.format(bestWeight)} ${unit.label} x $bestReps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                item {
                    SectionCard("Progress") {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            Metric.entries.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = metric == m,
                                    onClick = { metric = m },
                                    shape = SegmentedButtonDefaults.itemShape(i, Metric.entries.size),
                                    label = { Text(m.label, maxLines = 1) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val points = sessions.reversed().map { s ->
                            val v = when (metric) {
                                Metric.WEIGHT -> s.sets.maxOf { it.weightKg }
                                Metric.E1RM -> s.sets.maxOf { estimatedOneRepMax(it.weightKg, it.reps) }
                                Metric.VOLUME -> s.sets.sumOf { it.weightKg * it.reps }
                            }
                            ChartPoint(s.date, unit.fromKg(v))
                        }
                        LineChart(points, formatValue = { "${WeightUnit.formatNumber(it)} ${unit.label}" })
                    }
                }
                item { Text("History", style = MaterialTheme.typography.titleMedium) }
                items(sessions, key = { it.workoutId }) { s ->
                    SessionCard(s, unit) { nav.navigate(Routes.session(s.workoutId)) }
                }
            }

            if (e.instructionList.isNotEmpty()) {
                item {
                    SectionCard(if (e.isCustom) "Notes" else "How to") {
                        e.instructionList.forEachIndexed { i, step ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                if (!e.isCustom) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(22.dp),
                                    )
                                }
                                Text(step, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        CustomExerciseDialog(
            initial = ex,
            onDismiss = { editing = false },
            onSave = { name, muscle, equipment, category, instructions ->
                editing = false
                vm.saveCustom(name, muscle, equipment, category, instructions)
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete exercise?",
            message = "It will be removed from your plans. Logged sets stay in history.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete { nav.popBackStack() } },
        )
    }
}

/** Animated demo for dataset exercises (with the required attribution); legacy photos otherwise. */
@Composable
private fun ExerciseImages(e: ExerciseEntity) {
    val shape = RoundedCornerShape(16.dp)
    val gif = e.gifUrl()
    if (gif != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
                    .clip(shape)
                    .background(androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = gif,
                    contentDescription = e.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                ExerciseRepository.MEDIA_ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }
    val urls = e.imageList.indices.mapNotNull { e.imageUrl(it) }
    if (urls.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.FitnessCenter, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(urls) {
        while (urls.size > 1) {
            delay(1200)
            index = (index + 1) % urls.size
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Crossfade(targetState = index, label = "exerciseImage") { i ->
            AsyncImage(
                model = urls[i],
                contentDescription = e.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Tag(text: String, strong: Boolean = false) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text,
                color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}

@Composable
private fun SessionCard(s: Session, unit: WeightUnit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(s.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(s.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            s.sets.forEachIndexed { i, set ->
                Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 1}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp),
                    )
                    Text("${unit.format(set.weightKg)} ${unit.label} x ${set.reps}", style = MaterialTheme.typography.bodyMedium)
                    if (set.isPr) { Spacer(Modifier.width(8.dp)); PrBadge() }
                }
            }
        }
    }
}
