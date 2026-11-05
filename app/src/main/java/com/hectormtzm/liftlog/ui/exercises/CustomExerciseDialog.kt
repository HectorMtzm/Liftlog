package com.hectormtzm.liftlog.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hectormtzm.liftlog.data.ExerciseEntity
import com.hectormtzm.liftlog.data.ExerciseRepository
import com.hectormtzm.liftlog.ui.components.OptionPickerDialog
import com.hectormtzm.liftlog.ui.titleCase

@Composable
fun CustomExerciseDialog(
    initial: ExerciseEntity?,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, muscle: String, equipment: String, category: String, instructions: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: initialName) }
    var muscle by remember { mutableStateOf(initial?.primaryMuscleList?.firstOrNull() ?: "chest") }
    var equipment by remember { mutableStateOf(initial?.equipment?.takeIf { it.isNotBlank() } ?: "barbell") }
    var category by remember { mutableStateOf(initial?.category?.takeIf { it.isNotBlank() } ?: "strength") }
    var instructions by remember { mutableStateOf(initial?.instructions ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New exercise" else "Edit exercise") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Picker("Primary muscle", muscle, ExerciseRepository.MUSCLES) { muscle = it }
                Picker("Equipment", equipment, ExerciseRepository.EQUIPMENT) { equipment = it }
                Picker("Category", category, ExerciseRepository.CATEGORIES) { category = it }
                OutlinedTextField(
                    value = instructions, onValueChange = { instructions = it },
                    label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, muscle, equipment, category, instructions) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Picker(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${value.titleCase()}", modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ArrowDropDown, null)
    }
    if (open) {
        OptionPickerDialog(
            title = label,
            options = options,
            selected = value,
            label = { it.titleCase() },
            onDismiss = { open = false },
            onPick = { open = false; if (it != null) onPick(it) },
        )
    }
}
