package com.crumbkeeper.app.ui

import android.app.Activity
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import java.io.BufferedInputStream
import androidx.core.content.FileProvider
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crumbkeeper.app.data.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

private val Ink = Color(0xFF100C15)
private val Surface = Color(0xFF1D1624)
private val Raised = Color(0xFF2B2232)
private val Cream = Color(0xFFF1E6D4)
private val Copper = Color(0xFFB88A53)
private val Violet = Color(0xFFB34BE4)

@Composable
fun CrumbkeeperApp(activity: Activity, incomingText: String?, onConsumed: () -> Unit) {
    val context: Context = activity
    val prefs = remember { context.getSharedPreferences("crumb-settings", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keep-awake", true)) }
    var timerSound by remember { mutableStateOf(prefs.getBoolean("timer-sound", true)) }
    val alertPlayer = remember(context) { TimerAlertPlayer(context) }
    val colors = if (dark) darkColorScheme(primary = Violet, secondary = Copper, background = Ink,
        surface = Surface, surfaceVariant = Raised, onBackground = Cream, onSurface = Cream,
        onPrimary = Color.White) else lightColorScheme(primary = Color(0xFF693C90), secondary = Color(0xFF80552B))

    val store = remember { RecipeStore(context) }
    val sessionStore = remember { CookingSessionStore(context) }
    val recipes = remember { mutableStateListOf<Recipe>().apply { addAll(store.all()) } }
    val recovered = remember { sessionStore.load() }
    var cooking by remember { mutableStateOf(recipes.firstOrNull { it.id == recovered?.recipeId }) }
    var snapshot by remember { mutableStateOf(recovered?.takeIf { s -> recipes.any { it.id == s.recipeId } }) }
    var screen by remember { mutableStateOf(if (cooking != null && snapshot != null) "cook" else "library") }
    var draft by remember { mutableStateOf(Recipe()) }
    var existing by remember { mutableStateOf(false) }
    var importWarnings by remember { mutableStateOf(emptyList<String>()) }
    var unplaced by remember { mutableStateOf(emptyList<String>()) }
    var confirmFinish by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var archivePending by remember { mutableStateOf<RecipeArchive.Prepared?>(null) }
    var transferMessage by remember { mutableStateOf<String?>(null) }

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RecipeArchive.MIME_TYPE)
    ) { uri ->
        if (uri != null) runCatching {
            val all = store.all()
            require(all.isNotEmpty()) { "No saved recipes to back up." }
            context.contentResolver.openOutputStream(uri)?.use { RecipeArchive.writeBook(all, it) }
                ?: error("Could not open destination for writing.")
            "Saved ${all.size} recipes to your chosen backup file."
        }.onSuccess { transferMessage = it }
         .onFailure { transferMessage = "Backup failed: ${it.message}" }
    }

    val importDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: error("Could not read file.")
            val buffered = BufferedInputStream(input)
            buffered.mark(4)
            val signature = ByteArray(4)
            val count = buffered.read(signature)
            buffered.reset()
            if (count >= 2 && signature[0] == 0x50.toByte() &&
                signature[1] == 0x4B.toByte()) {
                buffered.use { archivePending = RecipeArchive.prepare(it) }
            } else {
                val bytes = buffered.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(out.size() + n <= 1_048_576) { "Recipe text file is too large." }
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
                val parsed = RecipeParser.parse(String(bytes, Charsets.UTF_8))
                draft = parsed.recipe
                existing = false
                importWarnings = parsed.warnings
                unplaced = parsed.unplaced
                screen = "editor"
            }
        }.onFailure { transferMessage = "Import failed: ${it.message}" }
    }
    fun shareRecipe(recipe: Recipe) {
        runCatching {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            val destination = File(directory, "Crumbkeeper-${recipe.id}.crumbrecipe")
            destination.outputStream().use { RecipeArchive.writeRecipe(recipe, it) }
            val uri = FileProvider.getUriForFile(context,
                "${context.packageName}.fileprovider", destination)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = RecipeArchive.MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Crumbkeeper Recipe", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(send, "Share Crumbkeeper recipe"))
        }.onFailure { transferMessage = "Share failed: ${it.message}" }
    }

    fun newEditor(recipe: Recipe, alreadyExists: Boolean = false, warnings: List<String> = emptyList(), leftover: List<String> = emptyList()) {
        draft = recipe; existing = alreadyExists; importWarnings = warnings; unplaced = leftover; screen = "editor"
    }
    fun saveDraft() { prefs.edit().putString("editor-draft", encodeRecipe(draft).toString()).apply() }
    fun saveRecipe(copy: Boolean) {
        val saved = if (copy) draft.copy(id = newId()) else draft
        store.save(saved)
        recipes.removeAll { it.id == saved.id }
        recipes.add(0, saved)
        prefs.edit().remove("editor-draft").apply()
        screen = "library"
    }
    fun setSnapshot(new: SessionSnapshot) { snapshot = new; sessionStore.save(new) }
    fun updateTimer(id: String, action: String) {
        val s = snapshot ?: return
        val base = s.timers[id] ?: return
        val t = base.normalized(System.currentTimeMillis())
        val updated = when (action) {
            "start" -> if (t.state == "ready" || t.state == "done") t.copy(state = "running", deadlineMs = System.currentTimeMillis() + t.durationMs, remainingMs = t.durationMs) else t
            "pause" -> if (t.state == "running") t.copy(state = "paused", remainingMs = t.remaining(System.currentTimeMillis())) else t
            "resume" -> if (t.state == "paused") t.copy(state = "running", deadlineMs = System.currentTimeMillis() + t.remainingMs) else t
            "dismiss" -> t.copy(state = "ready", deadlineMs = 0L, remainingMs = t.durationMs)
            "cancel" -> t.copy(state = "ready", deadlineMs = 0L, remainingMs = t.durationMs)
            else -> t
        }
        setSnapshot(s.copy(timers = s.timers + (id to updated)))
    }
    fun launchCooking(recipe: Recipe) {
        val timers = recipe.steps.flatMap { it.timers }.filter { it.approved && it.durationSeconds > 0 }
            .associate { it.id to TimerRun(it.id, it.durationSeconds.toLong() * 1000) }
        val s = SessionSnapshot(recipe.id, timers = timers)
        cooking = recipe; setSnapshot(s); screen = "cook"
    }

    LaunchedEffect(incomingText, screen) {
        if (!incomingText.isNullOrBlank() && screen != "cook") {
            val parsed = RecipeParser.parse(incomingText)
            newEditor(parsed.recipe, warnings = parsed.warnings, leftover = parsed.unplaced)
            onConsumed()
        }
    }
    LaunchedEffect(screen) {
        if (screen == "cook") while (true) {
            delay(500)
            now = System.currentTimeMillis()
            val s = snapshot ?: continue
            val normalized = s.timers.mapValues { (_, timer) -> timer.normalized(now) }
            if (normalized != s.timers) setSnapshot(s.copy(timers = normalized))
        }
    }
    // A completed timer remains audible until its own Dismiss/Restart action is used.
    // This is a foreground cooking-mode alert; locked-screen background alarms are not yet implemented.
    val hasFinishedTimer = screen == "cook" && snapshot?.timers?.values?.any { it.state == "done" } == true
    LaunchedEffect(hasFinishedTimer, timerSound) {
        try {
            if (hasFinishedTimer && timerSound) {
                while (true) {
                    alertPlayer.pulse()
                    delay(1200L)
                }
            }
        } finally {
            alertPlayer.stop()
        }
    }
    DisposableEffect(alertPlayer) {
        onDispose { alertPlayer.release() }
    }
    DisposableEffect(screen, keepAwake) {
        if (screen == "cook" && keepAwake) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler(enabled = screen == "cook") { confirmFinish = true }
    BackHandler(enabled = screen == "editor") { saveDraft(); screen = "library" }
    BackHandler(enabled = screen == "settings") { screen = "library" }

    MaterialTheme(colorScheme = colors) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            when (screen) {
                "library" -> LibraryScreen(recipes, prefs.getString("editor-draft", null) != null,
                    newRecipe = { newEditor(Recipe()) },
                    importText = { text ->
                        val parsed = RecipeParser.parse(text)
                        newEditor(parsed.recipe, warnings = parsed.warnings, leftover = parsed.unplaced)
                    },
                    resumeDraft = {
                        prefs.getString("editor-draft", null)?.let { json ->
                            runCatching { newEditor(decodeRecipe(org.json.JSONObject(json))) }
                        }
                    },
                    edit = { newEditor(it, true) }, cook = { launchCooking(it) }, settings = { screen = "settings" },
                    importFile = { importDocument.launch(arrayOf("*/*")) },
                    backup = { saveBackup.launch("Crumbkeeper-Cookbook.crumbbook") },
                    share = ::shareRecipe)
                "editor" -> EditorScreen(context, draft,
                    update = { draft = it; saveDraft() }, warnings = importWarnings, unplaced = unplaced,
                    existing = existing, back = { saveDraft(); screen = "library" },
                    save = { saveRecipe(false) }, saveCopy = { saveRecipe(true) })
                "cook" -> cooking?.let { recipe ->
                    CookingScreen(recipe, snapshot, now,
                        onIngredient = { index -> snapshot?.let { s ->
                            setSnapshot(s.copy(checkedIngredients = if (index in s.checkedIngredients) s.checkedIngredients - index else s.checkedIngredients + index))
                        } },
                        onStep = { id -> snapshot?.let { s ->
                            setSnapshot(s.copy(checkedSteps = if (id in s.checkedSteps) s.checkedSteps - id else s.checkedSteps + id))
                        } },
                        onTimer = ::updateTimer, finish = { confirmFinish = true })
                }
                "settings" -> SettingsScreen(dark, keepAwake, timerSound,
                    toggleDark = { dark = it; prefs.edit().putBoolean("dark", it).apply() },
                    toggleAwake = { keepAwake = it; prefs.edit().putBoolean("keep-awake", it).apply() },
                    toggleTimerSound = { timerSound = it; prefs.edit().putBoolean("timer-sound", it).apply() },
                    back = { screen = "library" })
            }
        }
        archivePending?.let { pending ->
            val existingIds = recipes.map { it.id }.toSet()
            val duplicates = pending.duplicateCount(existingIds)
            AlertDialog(
                onDismissRequest = { archivePending = null },
                title = { Text(if (pending.isBook) "Restore cookbook?" else "Import recipe?") },
                text = { Text("Contains ${pending.recipes.size} recipes. ${duplicates} already exist on this device. Existing recipes will not be overwritten.") },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching {
                            val result = RecipeArchive.restore(context, store, pending)
                            recipes.clear()
                            recipes.addAll(store.all())
                            "Imported ${result.imported} recipes; skipped ${result.skipped} existing."
                        }.onSuccess { transferMessage = it }
                         .onFailure { transferMessage = "Restore failed: ${it.message}" }
                        archivePending = null
                    }) { Text("Import Missing") }
                },
                dismissButton = {
                    Row {
                        if (duplicates > 0) TextButton(onClick = {
                            runCatching {
                                val result = RecipeArchive.restore(context, store, pending, copies = true)
                                recipes.clear()
                                recipes.addAll(store.all())
                                "Imported ${result.imported} as independent copies."
                            }.onSuccess { transferMessage = it }
                             .onFailure { transferMessage = "Restore failed: ${it.message}" }
                            archivePending = null
                        }) { Text("Keep Both") }
                        TextButton(onClick = { archivePending = null }) { Text("Cancel") }
                    }
                }
            )
        }
        transferMessage?.let { message ->
            AlertDialog(onDismissRequest = { transferMessage = null },
                title = { Text("Crumbkeeper") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { transferMessage = null }) { Text("OK") } })
        }
        if (confirmFinish) AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish cooking?") },
            text = { Text("This will end the cooking session, including its timers. Your saved recipe remains unchanged.") },
            confirmButton = { TextButton(onClick = {
                confirmFinish = false; sessionStore.clear(); snapshot = null; cooking = null; screen = "library"
            }) { Text("Finish Cooking") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep Cooking") } }
        )
    }
}

@Composable
private fun LibraryScreen(recipes: List<Recipe>, hasDraft: Boolean, newRecipe: () -> Unit,
    importText: (String) -> Unit, resumeDraft: () -> Unit, edit: (Recipe) -> Unit,
    cook: (Recipe) -> Unit, settings: () -> Unit,
    importFile: () -> Unit, backup: () -> Unit, share: (Recipe) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Crumbkeeper", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
            TextButton(onClick = settings) { Text("Settings") }
        }
        Text("Your offline grimoire of recipes", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = newRecipe) { Text("+ New Recipe") }
            OutlinedButton(onClick = { clipboard.getText()?.text?.toString()?.let(importText) }) { Text("Paste Recipe") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = importFile) { Text("Import File") }
            OutlinedButton(onClick = backup, enabled = recipes.isNotEmpty()) { Text("Back Up Cookbook") }
        }
        if (hasDraft) OutlinedButton(onClick = resumeDraft) { Text("Resume unsaved draft") }
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search saved recipes") }, singleLine = true)
        val filtered = recipes.filter { it.title.contains(query, true) || it.ingredients.any { line -> line.contains(query, true) } }
        if (filtered.isEmpty()) Text(if (recipes.isEmpty()) "No recipes yet. Create one or paste a recipe from ChatGPT." else "No matching recipes.",
            modifier = Modifier.padding(top = 18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { recipe ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(recipe.title.ifBlank { "Untitled Recipe" }, style = MaterialTheme.typography.titleLarge)
                        Text("${recipe.ingredients.size} ingredients  •  ${recipe.steps.size} steps" +
                            if (recipe.servings.isNotBlank()) "  •  ${recipe.servings}" else "", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { cook(recipe) }) { Text("Start Cooking") }
                            OutlinedButton(onClick = { edit(recipe) }) { Text("Edit") }
                            OutlinedButton(onClick = { share(recipe) }) { Text("Share") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(context: Context, draft: Recipe, update: (Recipe) -> Unit,
    warnings: List<String>, unplaced: List<String>, existing: Boolean,
    back: () -> Unit, save: () -> Unit, saveCopy: () -> Unit) {
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val dir = File(context.filesDir, "recipe-images").apply { mkdirs() }
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            update(draft.copy(coverImage = target.absolutePath))
        }
    }
    var originalOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = back) { Text("‹ Library") }
            Text("Recipe Builder", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = save, enabled = draft.title.isNotBlank()) { Text("Save") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (warnings.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Review imported recipe", fontWeight = FontWeight.Bold)
                    warnings.forEach { Text("• $it") }
                    if (unplaced.isNotEmpty()) Text("Unplaced text: ${unplaced.joinToString(" | ")}")
                }
            }
            OutlinedTextField(draft.title, { update(draft.copy(title = it)) }, label = { Text("Recipe title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.servings, { update(draft.copy(servings = it)) }, label = { Text("Servings / yield") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { pickImage.launch("image/*") }) { Text(if (draft.coverImage == null) "Add Cover Photo" else "Replace Cover Photo") }
            draft.coverImage?.let { CoverImage(it, modifier = Modifier.fillMaxWidth().height(180.dp)) }
            BoxWithConstraints {
                val wide = maxWidth > 650.dp
                if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IngredientEditor(draft, update, Modifier.weight(.8f))
                    StepEditor(draft, update, Modifier.weight(1.2f))
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IngredientEditor(draft, update, Modifier.fillMaxWidth())
                    StepEditor(draft, update, Modifier.fillMaxWidth())
                }
            }
            OutlinedTextField(draft.notes, { update(draft.copy(notes = it)) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (draft.originalText.isNotBlank()) {
                OutlinedButton(onClick = { originalOpen = !originalOpen }) { Text(if (originalOpen) "Hide Original Text" else "Show Original Text") }
                if (originalOpen) Text(draft.originalText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = save, enabled = draft.title.isNotBlank()) { Text(if (existing) "Save Changes" else "Save Recipe") }
                if (existing) OutlinedButton(onClick = saveCopy) { Text("Save as New") }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun IngredientEditor(recipe: Recipe, update: (Recipe) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ingredients", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        Text("One ingredient per line", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(recipe.ingredients.joinToString("\n"), { text -> update(recipe.copy(ingredients = text.split("\n"))) },
            modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("Ingredients") })
    }
}

@Composable
private fun StepEditor(recipe: Recipe, update: (Recipe) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Instructions & Timers", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        recipe.steps.forEachIndexed { index, step ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Step ${index+1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { update(recipe.copy(steps = recipe.steps.filterNot { it.id == step.id })) }) { Text("Remove") }
                    }
                    OutlinedTextField(step.instruction, { text ->
                        update(recipe.copy(steps = recipe.steps.map { if (it.id == step.id) it.copy(instruction = text) else it }))
                    }, label = { Text("Instruction") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    step.timers.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Checkbox(checked = t.approved, onCheckedChange = { approved ->
                                update(recipe.replaceTimer(step.id, t.copy(approved = approved)))
                            })
                            OutlinedTextField(t.label, { label -> update(recipe.replaceTimer(step.id, t.copy(label = label))) },
                                label = { Text("Timer") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField((t.durationSeconds / 60).toString(), { v ->
                                val minutes = v.toIntOrNull()?.coerceIn(0, 1440) ?: 0
                                update(recipe.replaceTimer(step.id, t.copy(durationSeconds = minutes * 60)))
                            }, label = { Text("min") }, modifier = Modifier.width(78.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            TextButton(onClick = { update(recipe.copy(steps = recipe.steps.map { s ->
                                if (s.id == step.id) s.copy(timers = s.timers.filterNot { it.id == t.id }) else s
                            })) }) { Text("×") }
                        }
                        if (!t.approved) Text("Approve to include this timer in Cooking Mode", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { update(recipe.copy(steps = recipe.steps.map { s ->
                        if (s.id == step.id) s.copy(timers = s.timers + TimerPreset(label = "Step ${index+1} Timer")) else s
                    })) }) { Text("+ Timer") }
                }
            }
        }
        OutlinedButton(onClick = { update(recipe.copy(steps = recipe.steps + RecipeStep())) }) { Text("+ Add Step") }
    }
}

private fun Recipe.replaceTimer(stepId: String, next: TimerPreset): Recipe = copy(steps = steps.map { step ->
    if (step.id == stepId) step.copy(timers = step.timers.map { if (it.id == next.id) next else it }) else step
})

@Composable
private fun CoverImage(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap = bitmap, contentDescription = "Recipe photograph", modifier = modifier,
        contentScale = ContentScale.Crop)
}

@Composable
private fun CookingScreen(recipe: Recipe, snapshot: SessionSnapshot?, now: Long,
    onIngredient: (Int) -> Unit, onStep: (String) -> Unit, onTimer: (String, String) -> Unit,
    finish: () -> Unit) {
    val active = snapshot?.timers.orEmpty().values.filter { it.state in listOf("running", "paused", "done") }
    var cancelTimer by remember { mutableStateOf<String?>(null) }
    if (cancelTimer != null) AlertDialog(
        onDismissRequest = { cancelTimer = null },
        title = { Text("Cancel this timer?") },
        text = { Text("The timer will reset to its original duration. The recipe will not change.") },
        confirmButton = { TextButton(onClick = { cancelTimer?.let { onTimer(it, "cancel") }; cancelTimer = null }) { Text("Cancel Timer") } },
        dismissButton = { TextButton(onClick = { cancelTimer = null }) { Text("Keep Timer") } }
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(recipe.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = finish) { Text("Finish Cooking") }
        }
        if (active.isNotEmpty()) {
            Text("ACTIVE TIMERS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                active.forEach { t ->
                    val preset = recipe.steps.flatMap { it.timers }.firstOrNull { it.id == t.id }
                    TimerControls(t, preset?.label ?: "Timer", now, onTimer, compact = true, onCancel = { cancelTimer = it })
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recipe.coverImage != null) item { CoverImage(recipe.coverImage, Modifier.fillMaxWidth().height(170.dp)) }
            item { Text("INGREDIENTS", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
            item {
                // One list item for the entire ingredient group avoids the 12dp LazyColumn
                // spacing and extra Material checkbox padding between every ingredient.
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    recipe.ingredients.forEachIndexed { index, ingredient ->
                        if (ingredient.isNotBlank()) {
                            val checked = snapshot?.checkedIngredients?.contains(index) == true
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clickable(role = Role.Checkbox) { onIngredient(index) }
                                    .semantics { stateDescription = if (checked) "Checked" else "Not checked" }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    Modifier.size(20.dp)
                                        .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            RoundedCornerShape(4.dp))
                                        .border(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else
                                            MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (checked) Text("✓", color = Color.White,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                                Text(ingredient, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item { Text("INSTRUCTIONS", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
            items(recipe.steps, key = { it.id }) { step ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = snapshot?.checkedSteps?.contains(step.id) == true, onCheckedChange = { onStep(step.id) })
                            Text("Step ${recipe.steps.indexOf(step) + 1}", fontWeight = FontWeight.Bold)
                        }
                        Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
                        step.timers.filter { it.approved && it.durationSeconds > 0 }.forEach { timer ->
                            val runtime = snapshot?.timers?.get(timer.id) ?: TimerRun(timer.id, timer.durationSeconds.toLong() * 1000)
                            if (runtime.state == "ready") TimerControls(runtime, timer.label, now, onTimer, compact = false, onCancel = { cancelTimer = it })
                        }
                    }
                }
            }
            if (recipe.notes.isNotBlank()) item {
                Text("NOTES", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text(recipe.notes)
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun TimerControls(timer: TimerRun, label: String, now: Long,
    action: (String, String) -> Unit, compact: Boolean, onCancel: (String) -> Unit) {
    val secs = (timer.remaining(now) + 999L) / 1000L
    val formatted = "%02d:%02d".format(secs / 60, secs % 60)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(if (timer.state == "done") "Finished!" else formatted,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                color = if (timer.state == "done") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
        }
        val labelAction = when(timer.state) {
            "ready" -> "Start"
            "running" -> "Pause"
            "paused" -> "Resume"
            else -> "Restart"
        }
        val kind = when(timer.state) {
            "ready" -> "start"; "running" -> "pause"; "paused" -> "resume"; else -> "start"
        }
        Button(onClick = { action(timer.id, kind) }) { Text(labelAction) }
        if (timer.state == "done") TextButton(onClick = { action(timer.id, "dismiss") }) { Text("Dismiss") }
        if (timer.state == "running" || timer.state == "paused") TextButton(onClick = { onCancel(timer.id) }) { Text("Cancel") }
    }
}

@Composable
private fun SettingsScreen(dark: Boolean, keepAwake: Boolean, timerSound: Boolean,
    toggleDark: (Boolean) -> Unit, toggleAwake: (Boolean) -> Unit,
    toggleTimerSound: (Boolean) -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        TextButton(onClick = back) { Text("‹ Library") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Dark theme"); Text("Default Crumbkeeper appearance", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = dark, onCheckedChange = toggleDark)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Keep screen awake"); Text("When Cooking Mode is open; Android device restrictions still apply", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = keepAwake, onCheckedChange = toggleAwake)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Audible timer alarm")
                Text("On by default. Uses Android's alarm audio volume; silent/DND settings may affect it.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = timerSound, onCheckedChange = toggleTimerSound)
        }
        Text("Crumbkeeper 0.1.2-dev — Offline cookbook backup test", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
