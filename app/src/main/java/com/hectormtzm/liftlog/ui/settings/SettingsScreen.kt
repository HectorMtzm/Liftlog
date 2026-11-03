package com.hectormtzm.liftlog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.BuildConfig
import com.hectormtzm.liftlog.data.Settings
import com.hectormtzm.liftlog.data.WeightUnit
import com.hectormtzm.liftlog.ui.appViewModel
import com.hectormtzm.liftlog.ui.components.RestPickerDialog
import com.hectormtzm.liftlog.ui.formatRest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun setUnit(u: WeightUnit) = viewModelScope.launch { c.settings.setUnit(u) }
    fun setRest(s: Int) = viewModelScope.launch { c.settings.setDefaultRest(s) }
    fun setUpdatePlan(v: Boolean) = viewModelScope.launch { c.settings.setUpdatePlanWeights(v) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val vm = appViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    var restPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionLabel("Workout")
            SettingRow("Weight unit", "Existing logs are converted automatically") {
                SingleChoiceSegmentedButtonRow {
                    WeightUnit.entries.forEachIndexed { i, u ->
                        SegmentedButton(
                            selected = s.unit == u,
                            onClick = { vm.setUnit(u) },
                            shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.entries.size),
                            label = { Text(u.label) },
                        )
                    }
                }
            }
            SettingRow(
                "Default rest time",
                "Used when adding an exercise. Each exercise can have its own.",
                onClick = { restPicker = true },
            ) {
                Text(formatRest(s.defaultRestSeconds), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            SettingRow(
                "Update plan weights",
                "After finishing a workout, save the weights you lifted as the plan's new targets",
                onClick = { vm.setUpdatePlan(!s.updatePlanWeights) },
            ) {
                Switch(checked = s.updatePlanWeights, onCheckedChange = vm::setUpdatePlan)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("About")
            SettingRow("Liftlog", "Version ${BuildConfig.VERSION_NAME}") {}
            SettingRow(
                "Exercise library",
                "Exercise data and images from free-exercise-db (github.com/yuhonas/free-exercise-db), released into the public domain.",
            ) {}
        }
    }

    if (restPicker) {
        RestPickerDialog(
            current = s.defaultRestSeconds,
            onDismiss = { restPicker = false },
            onPick = { restPicker = false; vm.setRest(it) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}
