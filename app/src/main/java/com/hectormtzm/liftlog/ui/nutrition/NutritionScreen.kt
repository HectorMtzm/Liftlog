package com.hectormtzm.liftlog.ui.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.data.Settings
import com.hectormtzm.liftlog.data.nutrition.MealFull
import com.hectormtzm.liftlog.data.nutrition.MealType
import com.hectormtzm.liftlog.data.nutrition.Macros
import com.hectormtzm.liftlog.data.nutrition.Source
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(private val c: AppContainer) : ViewModel() {
    val day = MutableStateFlow(LocalDate.now().toEpochDay())
    val meals: StateFlow<List<MealFull>?> = day.flatMapLatest { c.nutrition.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<Settings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun shift(days: Int) { day.value += days }
}

fun dayTitle(epochDay: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (epochDay) {
        today -> "Today"
        today - 1 -> "Yesterday"
        today + 1 -> "Tomorrow"
        else -> LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(nav: NavController) {
    val vm = appViewModel { NutritionViewModel(it) }
    val day by vm.day.collectAsState()
    val meals by vm.meals.collectAsState()
    val settings by vm.settings.collectAsState()
    val list = meals.orEmpty()
    val total = list.fold(Macros.ZERO) { acc, m -> acc + m.macros }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                        Text(dayTitle(day), modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.addFood(day, MealType.forHour(LocalTime.now().hour), -1L)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Log food") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { DailySummary(total, settings.goals, onClick = { nav.navigate(Routes.SETTINGS) }) }
            MealType.entries.forEach { type ->
                val ofType = list.filter { MealType.parse(it.meal.mealType) == type }
                item(key = "h-${type.name}") {
                    MealTypeHeader(
                        type = type,
                        kcal = ofType.sumOf { it.macros.kcal },
                        onAdd = { nav.navigate(Routes.addFood(day, type, -1L)) },
                    )
                }
                items(ofType, key = { it.meal.id }) { m ->
                    MealCard(m) { nav.navigate(Routes.meal(m.meal.id)) }
                }
            }
        }
    }
}

@Composable
private fun MealTypeHeader(type: MealType, kcal: Double, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (kcal > 0) Text(
            "${kcalText(kcal)} kcal",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add to ${type.label}", tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun MealCard(m: MealFull, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            if (m.meal.photoPath != null) {
                AsyncImage(
                    model = File(m.meal.photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.meal.source == Source.PHOTO && m.meal.photoPath == null) {
                        Icon(Icons.Outlined.CameraAlt, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        m.meal.name.ifBlank {
                            m.sortedItems.map { it.name.substringBefore(',').substringBefore(" (") }
                                .distinct().joinToString(", ").ifBlank { "Meal" }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${kcalText(m.macros.kcal)} kcal", style = MaterialTheme.typography.titleSmall)
                }
                val items = m.sortedItems
                items.take(4).forEach { i ->
                    Text(
                        "${amountText(i.quantity, i.unit)}  ${i.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (items.size > 4) Text(
                    "+${items.size - 4} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "P ${gramsText(m.macros.protein)}  ·  C ${gramsText(m.macros.carbs)}  ·  F ${gramsText(m.macros.fat)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

