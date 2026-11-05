package com.hectormtzm.liftlog.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.data.nutrition.MealFull
import com.hectormtzm.liftlog.data.nutrition.MealItemEntity
import com.hectormtzm.liftlog.data.nutrition.MealType
import com.hectormtzm.liftlog.data.nutrition.NutritionAiException
import com.hectormtzm.liftlog.data.nutrition.Portion
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import com.hectormtzm.liftlog.ui.components.ConfirmDialog
import com.hectormtzm.liftlog.ui.components.NumberField
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MealEditorViewModel(private val c: AppContainer, val mealId: Long) : ViewModel() {
    private val repo = c.nutrition
    val meal: StateFlow<MealFull?> = repo.observeMeal(mealId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val busy = MutableStateFlow<String?>(null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun rename(name: String) = viewModelScope.launch { meal.value?.meal?.let { repo.updateMeal(it.copy(name = name)) } }
    fun setType(t: MealType) = viewModelScope.launch { meal.value?.meal?.let { repo.updateMeal(it.copy(mealType = t.name)) } }
    fun updateItem(item: MealItemEntity) = viewModelScope.launch { repo.updateItem(item) }
    fun deleteItem(id: Long) = viewModelScope.launch { repo.deleteItem(id) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.deleteMeal(mealId); onDone() }
    suspend fun portionsFor(item: MealItemEntity): List<Portion> = repo.portionsFor(item)

    /** Sends the (corrected) item list back to Claude and replaces the macros. */
    fun reestimate() = viewModelScope.launch {
        val m = meal.value ?: return@launch
        val lines = m.sortedItems.map { "${amountText(it.quantity, it.unit)} ${it.name}" }
        if (lines.isEmpty()) return@launch
        busy.value = "Claude is recalculating..."
        try {
            val est = repo.claude.reestimate(lines, repo.photos.read(m.meal.photoPath))
            if (est.items.isEmpty()) _messages.tryEmit("Claude returned no items; nothing changed.")
            else repo.replaceItems(mealId, est)
        } catch (e: NutritionAiException) {
            _messages.tryEmit(e.message ?: "Claude request failed")
        } finally {
            busy.value = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorScreen(nav: NavController, mealId: Long) {
    val vm = appViewModel(key = "meal$mealId") { MealEditorViewModel(it, mealId) }
    val full by vm.meal.collectAsState()
    val busy by vm.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(full) {
        if (full != null) loaded = true
        else if (loaded) nav.popBackStack() // meal deleted
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(full?.let { MealType.parse(it.meal.mealType).label } ?: "") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete meal") }
                },
            )
        },
    ) { padding ->
        val m = full ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (m.meal.photoPath != null) item {
                AsyncImage(
                    model = File(m.meal.photoPath),
                    contentDescription = "Meal photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            item {
                var name by remember(m.meal.id) { mutableStateOf(m.meal.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; vm.rename(it) },
                    label = { Text("Meal name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MealType.entries) { t ->
                        FilterChip(
                            selected = MealType.parse(m.meal.mealType) == t,
                            onClick = { vm.setType(t) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(vertical = 14.dp)) {
                        MacroGrid(m.macros)
                    }
                }
            }
            if (m.meal.notes.isNotBlank()) item {
                Text(
                    m.meal.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(m.sortedItems, key = { it.id }) { item ->
                ItemCard(item, vm)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.addFood(m.meal.epochDay, MealType.parse(m.meal.mealType), m.meal.id)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add food")
                    }
                    FilledTonalButton(
                        onClick = { vm.reestimate() },
                        enabled = m.items.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Re-estimate")
                    }
                }
                Text(
                    "Changing an amount updates the macros right away. After renaming items (for example \"rice\" to " +
                        "\"brown rice\"), tap Re-estimate so Claude recalculates them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete meal?",
            message = "This meal and its items will be removed.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; vm.delete { } },
        )
    }
    busy?.let { BusyOverlay(it) }
}

@Composable
private fun ItemCard(item: MealItemEntity, vm: MealEditorViewModel) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var unitMenu by remember { mutableStateOf(false) }
    var portions by remember { mutableStateOf<List<Portion>>(emptyList()) }
    LaunchedEffect(unitMenu) { if (unitMenu) portions = vm.portionsFor(item) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = name,
                    onValueChange = { name = it; if (it.isNotBlank()) vm.updateItem(item.copy(name = it)) },
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.deleteItem(item.id) }) { Icon(Icons.Filled.Close, "Remove item", Modifier.size(18.dp)) }
            }
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField(
                    value = item.quantity,
                    onValueChange = { if (it > 0) vm.updateItem(item.copy(quantity = it)) },
                    modifier = Modifier.width(84.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    Surface(
                        onClick = { unitMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (item.unit == "g") "g" else "${item.unit} (${gramsText(item.gramsPerUnit)} g)",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(Icons.Filled.ArrowDropDown, "Change unit")
                        }
                    }
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        portions.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(if (p.label == "g") "grams" else "${p.label} (${gramsText(p.grams)} g)") },
                                onClick = {
                                    unitMenu = false
                                    if (p.label == item.unit) return@DropdownMenuItem
                                    // Keep the same weight when switching units.
                                    val grams = item.grams
                                    val q = if (p.label == "g") Math.round(grams).toDouble()
                                    else (Math.round(grams / p.grams * 4).coerceAtLeast(1)) / 4.0
                                    vm.updateItem(item.copy(unit = p.label, gramsPerUnit = p.grams, quantity = q))
                                },
                            )
                        }
                    }
                }
            }
            Text(
                macroLine(item.macros),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

