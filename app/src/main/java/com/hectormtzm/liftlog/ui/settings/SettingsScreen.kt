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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.hectormtzm.liftlog.ui.components.NumberField
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
import com.hectormtzm.liftlog.data.NutritionGoals
import com.hectormtzm.liftlog.data.ClaudeModel
import com.hectormtzm.liftlog.data.InstructionLanguage
import com.hectormtzm.liftlog.ui.components.OptionPickerDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    fun setGoals(g: NutritionGoals) = viewModelScope.launch { c.settings.setGoals(g) }
    fun setModel(m: ClaudeModel) = viewModelScope.launch { c.settings.setClaudeModel(m) }
    fun setInstructionLanguage(l: InstructionLanguage?) = viewModelScope.launch { c.settings.setInstructionLanguage(l) }
    val apiKey: StateFlow<String> = c.apiKey.key
    fun setApiKey(k: String) = c.apiKey.set(k)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val vm = appViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    var restPicker by remember { mutableStateOf(false) }
    var goalsDialog by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf(false) }
    val apiKey by vm.apiKey.collectAsState()

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
                "Sync workouts to plan",
                "When you finish, sets you added or deleted and rest changes are saved to the plan, and weights go up when you lifted more",
                onClick = { vm.setUpdatePlan(!s.updatePlanWeights) },
            ) {
                Switch(checked = s.updatePlanWeights, onCheckedChange = vm::setUpdatePlan)
            }
            var langPicker by remember { mutableStateOf(false) }
            SettingRow(
                "Instruction language",
                if (s.instructionLanguage == null) "Automatic (phone language)" else "Exercise how-to steps",
                onClick = { langPicker = true },
            ) {
                Text(
                    s.effectiveInstructionLanguage.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (langPicker) {
                OptionPickerDialog(
                    title = "Instruction language",
                    options = InstructionLanguage.entries.map { it.code },
                    selected = s.instructionLanguage?.code,
                    label = { code -> InstructionLanguage.byCode(code)!!.label },
                    anyLabel = "Automatic (phone language)",
                    onDismiss = { langPicker = false },
                    onPick = { code -> langPicker = false; vm.setInstructionLanguage(InstructionLanguage.byCode(code)) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("Nutrition")
            SettingRow(
                "Daily goals",
                "${s.goals.kcal} kcal  ·  P ${s.goals.protein} g  ·  C ${s.goals.carbs} g  ·  F ${s.goals.fat} g",
                onClick = { goalsDialog = true },
            ) {}
            SettingRow(
                "Claude API key",
                if (apiKey.isBlank()) "Not set. Needed for photo and text estimates."
                else "Set (ends in ${apiKey.takeLast(4)}). Stored only on this phone.",
                onClick = { keyDialog = true },
            ) {}
            var modelMenu by remember { mutableStateOf(false) }
            SettingRow("Claude model", s.claudeModel.note, onClick = { modelMenu = true }) {
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.claudeModel.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        ClaudeModel.entries.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(m.label)
                                        Text(m.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { modelMenu = false; vm.setModel(m) },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("About")
            SettingRow("Liftlog", "Version ${BuildConfig.VERSION_NAME}") {}
            SettingRow(
                "Exercise library",
                "Exercise data from exercises-dataset (github.com/hasaneyldrm/exercises-dataset, MIT). " +
                    "Exercise animations \u00A9 Gym visual \u2014 https://gymvisual.com/, used under license. " +
                    "Food data from USDA FoodData Central and Open Food Facts.",
            ) {}
        }
    }

    if (goalsDialog) {
        GoalsDialog(s.goals, onDismiss = { goalsDialog = false }, onSave = { goalsDialog = false; vm.setGoals(it) })
    }
    if (keyDialog) {
        ApiKeyDialog(apiKey, onDismiss = { keyDialog = false }, onSave = { keyDialog = false; vm.setApiKey(it) })
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

@Composable
private fun GoalsDialog(initial: NutritionGoals, onDismiss: () -> Unit, onSave: (NutritionGoals) -> Unit) {
    var kcal by remember { mutableStateOf(initial.kcal.toDouble()) }
    var p by remember { mutableStateOf(initial.protein.toDouble()) }
    var c by remember { mutableStateOf(initial.carbs.toDouble()) }
    var f by remember { mutableStateOf(initial.fat.toDouble()) }
    val fromMacros = (p * 4 + c * 4 + f * 9).toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily goals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalField("Calories (kcal)", kcal) { kcal = it }
                GoalField("Protein (g)", p) { p = it }
                GoalField("Carbs (g)", c) { c = it }
                GoalField("Fat (g)", f) { f = it }
                Text(
                    "Your macros add up to $fromMacros kcal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { kcal = fromMacros.toDouble() }) { Text("Use $fromMacros kcal") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(NutritionGoals(kcal.toInt(), p.toInt(), c.toInt(), f.toInt())) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GoalField(label: String, value: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        NumberField(value, onChange, decimal = false, modifier = Modifier.width(96.dp))
    }
}

@Composable
private fun ApiKeyDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf(initial) }
    var show by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Claude API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("sk-ant-...") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Create a key at console.anthropic.com (API keys). It is stored only on this phone, " +
                        "excluded from backups, and sent only to Anthropic. A photo estimate costs about 1-2 cents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
