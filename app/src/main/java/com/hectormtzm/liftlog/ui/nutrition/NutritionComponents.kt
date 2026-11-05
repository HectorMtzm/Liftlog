package com.hectormtzm.liftlog.ui.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hectormtzm.liftlog.data.NutritionGoals
import com.hectormtzm.liftlog.data.WeightUnit
import com.hectormtzm.liftlog.data.nutrition.FoodEntity
import com.hectormtzm.liftlog.data.nutrition.Macros
import com.hectormtzm.liftlog.data.nutrition.Portion
import com.hectormtzm.liftlog.data.nutrition.displayName
import com.hectormtzm.liftlog.ui.components.NumberField
import java.io.File
import kotlin.math.roundToInt

fun kcalText(v: Double) = "${v.roundToInt()}"
fun gramsText(v: Double) = if (v < 10) WeightUnit.formatNumber(v) else "${v.roundToInt()}"
fun macroLine(m: Macros) = "${kcalText(m.kcal)} kcal  ·  P ${gramsText(m.protein)}  ·  C ${gramsText(m.carbs)}  ·  F ${gramsText(m.fat)}"
fun qtyText(q: Double) = WeightUnit.formatNumber(q)

/** "2" + "1 large" -> "2 large"; "150" + "g" -> "150 g"; "1" + "cup" -> "1 cup". */
fun amountText(quantity: Double, unit: String): String {
    val u = if (unit.startsWith("1 ") && unit.length > 2) unit.substring(2) else unit
    return "${qtyText(quantity)} $u"
}

/** Calories ring plus protein / carbs / fat bars against the daily goals. */
@Composable
fun DailySummary(total: Macros, goals: NutritionGoals, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CalorieRing(total.kcal, goals.kcal.toDouble(), Modifier.size(128.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroBar("Protein", total.protein, goals.protein.toDouble())
                MacroBar("Carbs", total.carbs, goals.carbs.toDouble())
                MacroBar("Fat", total.fat, goals.fat.toDouble())
            }
        }
    }
}

@Composable
private fun CalorieRing(kcal: Double, goal: Double, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val over = goal > 0 && kcal > goal
    val color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val fraction = if (goal > 0) (kcal / goal).toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val remaining = (goal - kcal).roundToInt()
            Text(
                if (remaining >= 0) "$remaining" else "${-remaining}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (remaining >= 0) "kcal left" else "kcal over",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${kcalText(kcal)} / ${goal.roundToInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Double, goal: Double) {
    val over = goal > 0 && value > goal * 1.05
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                "${value.roundToInt()} / ${goal.roundToInt()} g",
                style = MaterialTheme.typography.labelMedium,
                color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f },
            color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

/** Pick an amount of a catalog food. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PortionDialog(food: FoodEntity, onDismiss: () -> Unit, onAdd: (quantity: Double, portion: Portion) -> Unit) {
    val options = remember(food) { food.portionList + Portion("g", 1.0) }
    var portion by remember(food) { mutableStateOf(options.first()) }
    var qty by remember(food) { mutableDoubleStateOf(if (options.first().label == "g") 100.0 else 1.0) }
    val m = Macros.per100(qty * portion.grams, food.kcal100, food.protein100, food.carbs100, food.fat100)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.displayName(), maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(qty, { qty = it }, modifier = Modifier.width(96.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (portion.label == "g") "grams" else "x ${portion.label}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    options.forEach { p ->
                        FilterChip(
                            selected = p == portion,
                            onClick = {
                                if (p == portion) return@FilterChip
                                // Keep the same total weight when switching units.
                                val grams = qty * portion.grams
                                portion = p
                                qty = if (p.label == "g") grams.roundToInt().toDouble()
                                else ((grams / p.grams) * 4).roundToInt().coerceAtLeast(1) / 4.0
                            },
                            label = {
                                Text(if (p.label == "g") "g" else "${p.label} (${gramsText(p.grams)} g)", maxLines = 1)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                MacroGrid(m)
                Text(
                    "${gramsText(qty * portion.grams)} g",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(qty, portion) }, enabled = qty > 0) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MacroGrid(m: Macros, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        MacroCell("kcal", kcalText(m.kcal), Modifier.weight(1f))
        MacroCell("Protein", "${gramsText(m.protein)} g", Modifier.weight(1f))
        MacroCell("Carbs", "${gramsText(m.carbs)} g", Modifier.weight(1f))
        MacroCell("Fat", "${gramsText(m.fat)} g", Modifier.weight(1f))
    }
}

@Composable
private fun MacroCell(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Free-text meal description sent to Claude. */
@Composable
fun DescribeMealDialog(initial: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Describe what you ate") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("2 eggs scrambled in butter, 2 slices whole wheat toast, coffee with milk") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Claude estimates each item and its macros. You can edit everything afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text("Estimate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PhotoConfirmDialog(file: File, onDismiss: () -> Unit, onAnalyze: (note: String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Analyze photo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AsyncImage(
                    model = file,
                    contentDescription = "Meal photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. cooked in olive oil, half portion of rice") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAnalyze(note) }) { Text("Analyze") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Manual entry: a food with known macros per serving. */
@Composable
fun CustomFoodDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, serving: String, grams: Double, macros: Macros) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var serving by remember { mutableStateOf("1 serving") }
    var grams by remember { mutableDoubleStateOf(100.0) }
    var kcal by remember { mutableDoubleStateOf(0.0) }
    var p by remember { mutableDoubleStateOf(0.0) }
    var c by remember { mutableDoubleStateOf(0.0) }
    var f by remember { mutableDoubleStateOf(0.0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom food") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = serving, onValueChange = { serving = it }, label = { Text("Serving") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Labeled("Grams", Modifier.width(88.dp)) { NumberField(grams, { grams = it }) }
                }
                Text("Per serving", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Labeled("kcal", Modifier.weight(1f)) { NumberField(kcal, { kcal = it }) }
                    Labeled("Protein", Modifier.weight(1f)) { NumberField(p, { p = it }) }
                    Labeled("Carbs", Modifier.weight(1f)) { NumberField(c, { c = it }) }
                    Labeled("Fat", Modifier.weight(1f)) { NumberField(f, { f = it }) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, serving, grams, Macros(kcal, p, c, f)) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Labeled(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Blocking overlay while Claude works. */
@Composable
fun BusyOverlay(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

