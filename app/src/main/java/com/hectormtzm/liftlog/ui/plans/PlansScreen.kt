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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.navigation.NavController
import com.hectormtzm.liftlog.data.PlanSummary
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import com.hectormtzm.liftlog.ui.components.ConfirmDialog
import com.hectormtzm.liftlog.ui.components.EmptyState
import com.hectormtzm.liftlog.ui.components.TextInputDialog
import com.hectormtzm.liftlog.ui.relativeDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(nav: NavController) {
    val vm = appViewModel { PlansViewModel(it) }
    val plans by vm.plans.collectAsState()
    val active by vm.active.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var confirmStart by remember { mutableStateOf<Long?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun startPlan(id: Long) {
        if (active != null) confirmStart = id
        else vm.start(id) { nav.navigate(Routes.WORKOUT) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("Plans") },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, "Settings")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New plan") },
            )
        },
    ) { padding ->
        val list = plans
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = "No plans yet",
                message = "Create a plan, add exercises with your target sets, reps and weight, then start it to log a workout.",
                modifier = Modifier.padding(padding),
                action = {
                    OutlinedButton(onClick = { vm.startEmpty { nav.navigate(Routes.WORKOUT) } }) {
                        Text("Or start an empty workout")
                    }
                },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { p ->
                    PlanCard(
                        plan = p,
                        onOpen = { nav.navigate(Routes.plan(p.id)) },
                        onStart = { startPlan(p.id) },
                    )
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (active == null) vm.startEmpty { nav.navigate(Routes.WORKOUT) } else nav.navigate(Routes.WORKOUT) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Start an empty workout", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "New plan",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                vm.create(name) { id -> nav.navigate(Routes.plan(id)) }
            },
        )
    }
    confirmStart?.let {
        ConfirmDialog(
            title = "Workout in progress",
            message = "Finish or discard \"${active?.name}\" before starting another plan. Resume it now?",
            confirmLabel = "Resume",
            onDismiss = { confirmStart = null },
            onConfirm = { confirmStart = null; nav.navigate(Routes.WORKOUT) },
        )
    }
}

@Composable
private fun PlanCard(plan: PlanSummary, onOpen: () -> Unit, onStart: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val count = "${plan.exerciseCount} exercise" + if (plan.exerciseCount == 1) "" else "s"
                val last = plan.lastPerformed?.let { "Last: ${relativeDay(it)}" } ?: "Not done yet"
                Text(
                    "$count  ·  $last",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledIconButton(
                onClick = onStart,
                enabled = plan.exerciseCount > 0,
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(Icons.Filled.PlayArrow, "Start ${plan.name}")
            }
        }
    }
}

