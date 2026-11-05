package com.hectormtzm.liftlog.ui.nutrition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.hectormtzm.liftlog.AppContainer
import com.hectormtzm.liftlog.data.nutrition.FoodEntity
import com.hectormtzm.liftlog.data.nutrition.MealType
import com.hectormtzm.liftlog.data.nutrition.NutritionAiException
import com.hectormtzm.liftlog.data.nutrition.Portion
import com.hectormtzm.liftlog.data.nutrition.Source
import com.hectormtzm.liftlog.data.nutrition.displayName
import com.hectormtzm.liftlog.ui.Routes
import com.hectormtzm.liftlog.ui.appViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AddFoodViewModel(
    private val c: AppContainer,
    val epochDay: Long,
    initialType: MealType,
    val targetMealId: Long?,
) : ViewModel() {
    private val repo = c.nutrition
    val mealType = MutableStateFlow(initialType)
    val query = MutableStateFlow("")
    val local = MutableStateFlow<List<FoodEntity>>(emptyList())
    val online = MutableStateFlow<List<FoodEntity>?>(null)
    val onlineLoading = MutableStateFlow(false)
    val busy = MutableStateFlow<String?>(null)
    val pendingPhoto = MutableStateFlow<Pair<File, ByteArray>?>(null)
    val portionFor = MutableStateFlow<FoodEntity?>(null)
    val recent: StateFlow<List<FoodEntity>> =
        repo.observeRecentFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hasApiKey: StateFlow<String> = c.apiKey.key

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _openMeal = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openMeal = _openMeal.asSharedFlow()

    private var searchJob: Job? = null

    fun setQuery(q: String) {
        query.value = q
        online.value = null
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            local.value = if (q.isBlank()) emptyList() else repo.searchLocal(q)
        }
    }

    fun searchOnline() = viewModelScope.launch {
        val q = query.value.trim().ifEmpty { return@launch }
        onlineLoading.value = true
        try {
            online.value = repo.searchOnline(q)
        } catch (e: Exception) {
            _messages.tryEmit("Open Food Facts search failed. Check your connection.")
        } finally {
            onlineLoading.value = false
        }
    }

    fun onBarcode(code: String) = viewModelScope.launch {
        busy.value = "Looking up barcode..."
        try {
            val food = repo.lookupBarcode(code)
            if (food == null) _messages.tryEmit("Product $code not found. Try searching or ask Claude.")
            else portionFor.value = food
        } catch (e: Exception) {
            _messages.tryEmit("Barcode lookup failed. Check your connection.")
        } finally {
            busy.value = null
        }
    }

    fun addFood(food: FoodEntity, qty: Double, portion: Portion) = viewModelScope.launch {
        portionFor.value = null
        repo.addFood(epochDay, mealType.value, targetMealId, food, qty, portion.label, portion.grams)
        _messages.tryEmit("Added ${amountText(qty, portion.label)} ${food.displayName()}")
    }

    fun saveCustom(name: String, serving: String, grams: Double, m: com.hectormtzm.liftlog.data.nutrition.Macros) =
        viewModelScope.launch { portionFor.value = repo.createCustomFood(name, serving, grams, m) }

    fun onPhoto(uri: Uri) = viewModelScope.launch {
        busy.value = "Preparing photo..."
        try {
            pendingPhoto.value = repo.photos.importImage(uri)
        } catch (e: Exception) {
            _messages.tryEmit("Could not read that photo.")
        } finally {
            busy.value = null
        }
    }

    fun cancelPhoto() {
        pendingPhoto.value?.first?.let { repo.photos.delete(it.path) }
        pendingPhoto.value = null
    }

    fun analyzePhoto(note: String) = viewModelScope.launch {
        val (file, bytes) = pendingPhoto.value ?: return@launch
        pendingPhoto.value = null
        busy.value = "Claude is looking at your meal..."
        try {
            val est = repo.claude.estimateFromPhoto(bytes, note)
            if (est.items.isEmpty()) {
                repo.photos.delete(file.path)
                _messages.tryEmit(est.notes.ifBlank { "No food found in the photo." })
            } else {
                _openMeal.tryEmit(repo.saveEstimate(epochDay, mealType.value, targetMealId, est, Source.PHOTO, file.path))
            }
        } catch (e: NutritionAiException) {
            repo.photos.delete(file.path)
            _messages.tryEmit(e.message ?: "Claude request failed")
        } finally {
            busy.value = null
        }
    }

    fun describe(text: String) = viewModelScope.launch {
        busy.value = "Claude is estimating..."
        try {
            val est = repo.claude.estimateFromText(text)
            if (est.items.isEmpty()) _messages.tryEmit(est.notes.ifBlank { "Claude could not find any food in that." })
            else _openMeal.tryEmit(repo.saveEstimate(epochDay, mealType.value, targetMealId, est, Source.TEXT, null))
        } catch (e: NutritionAiException) {
            _messages.tryEmit(e.message ?: "Claude request failed")
        } finally {
            busy.value = null
        }
    }

    fun newCaptureUri(context: android.content.Context): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", repo.photos.newCaptureFile())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(nav: NavController, epochDay: Long, mealType: MealType, targetMealId: Long) {
    val vm = appViewModel(key = "add-$epochDay-$mealType-$targetMealId") {
        AddFoodViewModel(it, epochDay, mealType, targetMealId.takeIf { id -> id > 0 })
    }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val query by vm.query.collectAsState()
    val local by vm.local.collectAsState()
    val online by vm.online.collectAsState()
    val onlineLoading by vm.onlineLoading.collectAsState()
    val recent by vm.recent.collectAsState()
    val busy by vm.busy.collectAsState()
    val type by vm.mealType.collectAsState()
    val pendingPhoto by vm.pendingPhoto.collectAsState()
    val portionFor by vm.portionFor.collectAsState()
    val apiKey by vm.hasApiKey.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var describe by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf<String?>(null) }
    var typeMenu by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var needKey by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) {
        vm.openMeal.collect { id ->
            if (targetMealId > 0) nav.popBackStack() // came from the meal editor; go back to it
            else nav.navigate(Routes.meal(id)) { popUpTo(Routes.ADD_FOOD_PATTERN) { inclusive = true } }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUri
        if (ok && uri != null) vm.onPhoto(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPhoto(uri)
    }
    fun requireKey(action: () -> Unit) { if (apiKey.isBlank()) needKey = true else action() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (targetMealId > 0) Text("Add to meal")
                    else Box {
                        Row(
                            Modifier.clickable { typeMenu = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Add to ${type.label}")
                            Icon(Icons.Filled.ArrowDropDown, "Change meal")
                        }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            MealType.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(t.label) }, onClick = { typeMenu = false; vm.mealType.value = t })
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { TextButton(onClick = { nav.popBackStack() }) { Text("Done") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    QuickAction(Icons.Outlined.CameraAlt, "Photo") { requireKey { photoMenu = true } }
                    DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Take photo") },
                            leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                            onClick = {
                                photoMenu = false
                                val uri = vm.newCaptureUri(context)
                                captureUri = uri
                                takePicture.launch(uri)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Choose from gallery") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                            onClick = {
                                photoMenu = false
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                    }
                }
                QuickAction(Icons.Outlined.QrCodeScanner, "Barcode", Modifier.weight(1f)) {
                    GmsBarcodeScanning.getClient(context).startScan()
                        .addOnSuccessListener { b -> b.rawValue?.let(vm::onBarcode) }
                }
                QuickAction(Icons.Outlined.AutoAwesome, "Describe", Modifier.weight(1f)) {
                    requireKey { describe = query }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search foods") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (query.isBlank()) {
                    if (recent.isNotEmpty()) {
                        item { SectionLabel("Recent") }
                        items(recent, key = { "r-" + it.id }) { f -> FoodRow(f) { focus.clearFocus(); vm.portionFor.value = f } }
                    } else {
                        item {
                            Text(
                                "Search the food database, scan a barcode, snap a photo, or describe your meal and let Claude estimate it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                            )
                        }
                    }
                    item { ActionRow(Icons.Outlined.Edit, "Custom food with your own macros") { custom = "" } }
                } else {
                    // Claude fallback first when nothing matches locally.
                    if (local.isEmpty()) item { ClaudeRow(query) { requireKey { vm.describe(query) } } }
                    items(local, key = { "l-" + it.id }) { f -> FoodRow(f) { focus.clearFocus(); vm.portionFor.value = f } }
                    item { SectionLabel("Not what you need?") }
                    when {
                        onlineLoading -> item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        online == null -> item {
                            ActionRow(Icons.Outlined.Language, "Search packaged foods for \"$query\"") { focus.clearFocus(); vm.searchOnline() }
                        }
                        online!!.isEmpty() -> item {
                            Text(
                                "No packaged foods found online.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        else -> items(online!!, key = { "o-" + it.id }) { f -> FoodRow(f) { focus.clearFocus(); vm.portionFor.value = f } }
                    }
                    if (local.isNotEmpty()) item { ClaudeRow(query) { requireKey { vm.describe(query) } } }
                    item { ActionRow(Icons.Outlined.Edit, "Custom food \"$query\" with your own macros") { custom = query } }
                }
            }
        }
    }

    portionFor?.let { f ->
        PortionDialog(f, onDismiss = { vm.portionFor.value = null }, onAdd = { q, p -> vm.addFood(f, q, p) })
    }
    describe?.let { initial ->
        DescribeMealDialog(initial, onDismiss = { describe = null }, onSubmit = { describe = null; vm.describe(it) })
    }
    custom?.let { initial ->
        CustomFoodDialog(initial, onDismiss = { custom = null }, onSave = { n, s, g, m -> custom = null; vm.saveCustom(n, s, g, m) })
    }
    pendingPhoto?.let { (file, _) ->
        PhotoConfirmDialog(file, onDismiss = vm::cancelPhoto, onAnalyze = vm::analyzePhoto)
    }
    if (needKey) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { needKey = false },
            title = { Text("Claude API key needed") },
            text = { Text("Photo and text estimates use Claude. Add your Anthropic API key in Settings first.") },
            confirmButton = { TextButton(onClick = { needKey = false; nav.navigate(Routes.SETTINGS) }) { Text("Open Settings") } },
            dismissButton = { TextButton(onClick = { needKey = false }) { Text("Cancel") } },
        )
    }
    busy?.let { BusyOverlay(it) }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun FoodRow(food: FoodEntity, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(food.displayName(), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val serving = food.portionList.firstOrNull()
                val src = when (food.source) {
                    Source.OFF -> "Packaged"
                    Source.CUSTOM -> "Custom"
                    else -> null
                }
                Text(
                    listOfNotNull(
                        "${kcalText(food.kcal100)} kcal / 100 g",
                        serving?.let { "${it.label} = ${gramsText(it.grams)} g" },
                        src,
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ClaudeRow(query: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Estimate with Claude", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "\"$query\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
