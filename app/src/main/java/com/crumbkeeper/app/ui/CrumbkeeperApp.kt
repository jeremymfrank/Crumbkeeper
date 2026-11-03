package com.crumbkeeper.app.ui

import android.app.Activity
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import java.io.BufferedInputStream
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crumbkeeper.app.data.*
import com.crumbkeeper.app.BuildConfig
import com.crumbkeeper.app.IncomingRecipe
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CrumbkeeperApp(activity: Activity, incoming: IncomingRecipe?, onConsumed: (String) -> Unit) {
    val context: Context = activity
    val prefs = remember { context.getSharedPreferences("crumb-settings", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keep-awake", true)) }
    var timerSound by remember { mutableStateOf(prefs.getBoolean("timer-sound", true)) }
    val alertPlayer = remember(context) { TimerAlertPlayer(context) }
    val store = remember { RecipeStore(context) }
    val sessionStore = remember { CookingSessionStore(context) }
    val recipes = remember { mutableStateListOf<Recipe>().apply { addAll(store.all()) } }
    val recovered = remember { sessionStore.load()?.let { previous ->
        recipes.firstOrNull { it.id == previous.recipeId }?.let { previous.withRecipeTimers(it) }
    } }
    var cooking by remember { mutableStateOf(recipes.firstOrNull { it.id == recovered?.recipeId }) }
    var snapshot by remember { mutableStateOf(recovered?.takeIf { s -> recipes.any { it.id == s.recipeId } }) }
    var screen by remember { mutableStateOf(if (cooking != null && snapshot != null) "cook" else "library") }
    var draft by remember { mutableStateOf(Recipe()) }
    var existing by remember { mutableStateOf(false) }
    var editRequest by remember { mutableStateOf<Recipe?>(null) }
    var confirmSaveChanges by remember { mutableStateOf(false) }
    var trashRequest by remember { mutableStateOf<Recipe?>(null) }
    var permanentDeleteRequest by remember { mutableStateOf<Recipe?>(null) }
    val deletedRecipes = remember { mutableStateListOf<Recipe>().apply { addAll(store.trashed()) } }
    fun reloadRecipes() {
        recipes.clear()
        recipes.addAll(store.all())
        deletedRecipes.clear()
        deletedRecipes.addAll(store.trashed())
    }
    var importWarnings by remember { mutableStateOf(emptyList<String>()) }
    var unplaced by remember { mutableStateOf(emptyList<String>()) }
    var confirmFinish by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var archivePending by remember { mutableStateOf<RecipeArchive.Prepared?>(null) }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    var extractingPdf by remember { mutableStateOf(false) }
    val importScope = rememberCoroutineScope()

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RecipeArchive.BOOK_MIME_TYPE)
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

    fun importUri(uri: Uri) {
        if (!extractingPdf) runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: error("Could not read file.")
            val buffered = BufferedInputStream(input)
            buffered.mark(16)
            val signature = ByteArray(8)
            val size = buffered.read(signature)
            buffered.reset()
            val kind = try {
                RecipeFileSniffer.kind(signature.copyOf(size.coerceAtLeast(0)),
                    context.contentResolver.getType(uri))
            } catch (error: Exception) {
                buffered.close()
                throw error
            }
            when (kind) {
                RecipeFileSniffer.Kind.PDF -> {
                    buffered.close()
                    extractingPdf = true
                    importScope.launch {
                        try {
                            val result = PdfRecipeImporter.import(context, uri)
                            draft = result.parsed.recipe
                            existing = false
                            importWarnings = result.parsed.warnings
                            unplaced = result.parsed.unplaced
                            screen = "editor"
                        } catch (error: Exception) {
                            transferMessage = "PDF import failed: " +
                                (error.message ?: "Could not read document.")
                        } finally { extractingPdf = false }
                    }
                }
                RecipeFileSniffer.Kind.ARCHIVE -> {
                    buffered.use { archivePending = RecipeArchive.prepare(it) }
                }
                RecipeFileSniffer.Kind.TEXT -> {
                    val bytes = buffered.use { source ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            require(out.size() + n <= 1_048_576) { "Recipe text file is too large." }
                            out.write(buffer, 0, n)
                        }
                        out.toByteArray()
                    }
                    val parsed = RecipeParser.parse(RecipeFileSniffer.decodeText(bytes))
                    draft = parsed.recipe
                    existing = false
                    importWarnings = parsed.warnings
                    unplaced = parsed.unplaced
                    screen = "editor"
                }
            }
        }.onFailure { transferMessage = "Import failed: " + it.message }
    }
    val importDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importUri(uri) }
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
        draft = recipe
        existing = alreadyExists
        importWarnings = warnings
        unplaced = leftover
        screen = "editor"
        // Drafts are separate from the saved recipe and must not overwrite it.
        prefs.edit().putString("editor-draft", encodeRecipe(recipe).toString()).apply()
    }
    fun saveDraft() { prefs.edit().putString("editor-draft", encodeRecipe(draft).toString()).apply() }
    fun saveRecipe(copy: Boolean, confirmedOriginal: Boolean = false) {
        runCatching {
            val saved = if (copy) RecipeEditSafety.editableCopy(draft)
                else RecipeEditSafety.requireSafeSave(
                    draft, existing, recipes.map { it.id }.toSet(), confirmedOriginal
                )
            store.save(saved)
            recipes.removeAll { it.id == saved.id }
            recipes.add(0, saved)
            prefs.edit().remove("editor-draft").apply()
            existing = false
            confirmSaveChanges = false
            screen = "library"
        }.onFailure { transferMessage = "Recipe not saved: ${it.message}" }
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
        val timers = recipe.steps.flatMap { it.timers }.filter { it.durationSeconds > 0 }
            .associate { it.id to TimerRun(it.id, it.durationSeconds.toLong() * 1000) }
        val s = SessionSnapshot(recipe.id, timers = timers)
        cooking = recipe; setSnapshot(s); screen = "cook"
    }

    LaunchedEffect(incoming?.id, screen, extractingPdf) {
        // Keep cooking and unsaved editing in place; open the queued attachment afterward.
        if (incoming != null && screen != "cook" && screen != "editor" && !extractingPdf) {
            incoming.uri?.let(::importUri)
            incoming.text?.let { text ->
                val parsed = RecipeParser.parse(text)
                newEditor(parsed.recipe, warnings = parsed.warnings, leftover = parsed.unplaced)
            }
            onConsumed(incoming.id)
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
    BackHandler(enabled = screen == "trash") { screen = "library" }

    GildedTheme(dark) {
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
                            runCatching {
                                val recoveredDraft = decodeRecipe(org.json.JSONObject(json))
                                newEditor(recoveredDraft,
                                    alreadyExists = recipes.any { it.id == recoveredDraft.id })
                            }.onFailure { transferMessage = "Could not reopen draft: ${it.message}" }
                        }
                    },
                    requestEdit = { editRequest = it },
                    editCopy = { newEditor(RecipeEditSafety.editableCopy(it)) },
                    requestTrash = { trashRequest = it },
                    cook = { launchCooking(it) }, settings = { screen = "settings" },
                    importFile = { importDocument.launch(arrayOf("*/*")) },
                    backup = { saveBackup.launch("Crumbkeeper-Cookbook.crumbbook") },
                    share = ::shareRecipe)
                "trash" -> TrashScreen(
                    deletedRecipes,
                    restore = { recipe ->
                        runCatching {
                            check(store.restoreFromTrash(recipe.id)) { "Recipe was not in Trash." }
                            reloadRecipes()
                            "Restored “${recipe.title}” to the library."
                        }.onSuccess { transferMessage = it }
                            .onFailure { transferMessage = "Restore failed: ${it.message}" }
                    },
                    requestPermanentDelete = { permanentDeleteRequest = it },
                    back = { screen = "library" }
                )
                "editor" -> EditorScreen(context, draft,
                    update = { draft = it; saveDraft() }, warnings = importWarnings, unplaced = unplaced,
                    existing = existing, back = { saveDraft(); screen = "library" },
                    save = { if (existing) confirmSaveChanges = true else saveRecipe(false) },
                    saveCopy = { saveRecipe(true) })
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
                    trashCount = deletedRecipes.size,
                    showTrash = { screen = "trash" },
                    toggleDark = { dark = it; prefs.edit().putBoolean("dark", it).apply() },
                    toggleAwake = { keepAwake = it; prefs.edit().putBoolean("keep-awake", it).apply() },
                    toggleTimerSound = { timerSound = it; prefs.edit().putBoolean("timer-sound", it).apply() },
                    back = { screen = "library" })
            }
        }
        trashRequest?.let { recipe ->
            AlertDialog(
                onDismissRequest = { trashRequest = null },
                title = { Text("Move recipe to Trash?") },
                text = { Text("Move “${recipe.title}” out of your cookbook? It will no longer appear in cooking, sharing or cookbook backups. You can restore it from Settings → Trash.") },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching {
                            check(store.moveToTrash(recipe.id)) { "Recipe was not found in the library." }
                            reloadRecipes()
                            "Moved “${recipe.title}” to Trash. Restore it in Settings → Trash."
                        }.onSuccess { transferMessage = it }
                            .onFailure { transferMessage = "Could not move recipe: ${it.message}" }
                        trashRequest = null
                    }) { Text("Move to Trash") }
                },
                dismissButton = {
                    TextButton(onClick = { trashRequest = null }) { Text("Keep Recipe") }
                }
            )
        }
        permanentDeleteRequest?.let { recipe ->
            AlertDialog(
                onDismissRequest = { permanentDeleteRequest = null },
                title = { Text("Delete permanently?") },
                text = { Text("“${recipe.title}” will be permanently removed. This cannot be undone unless you previously saved a backup. This action is only available from Trash.") },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching {
                            check(store.deletePermanently(recipe.id, confirmed = true)) {
                                "Recipe is not in Trash."
                            }
                            reloadRecipes()
                            "Permanently deleted “${recipe.title}”."
                        }.onSuccess { transferMessage = it }
                            .onFailure { transferMessage = "Delete failed: ${it.message}" }
                        permanentDeleteRequest = null
                    }) { Text("Delete Permanently") }
                },
                dismissButton = {
                    TextButton(onClick = { permanentDeleteRequest = null }) { Text("Cancel") }
                }
            )
        }
        editRequest?.let { recipe ->
            AlertDialog(
                onDismissRequest = { editRequest = null },
                title = { Text("Edit this saved recipe?") },
                text = { Text("“${recipe.title}” is already saved. Cooking and sharing never change it. Editing the original requires a second confirmation when you save. Use an editable copy to protect the original.") },
                confirmButton = {
                    TextButton(onClick = {
                        editRequest = null
                        newEditor(recipe, alreadyExists = true)
                    }) { Text("Edit Original") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            editRequest = null
                            newEditor(RecipeEditSafety.editableCopy(recipe))
                        }) { Text("Edit a Copy") }
                        TextButton(onClick = { editRequest = null }) { Text("Cancel") }
                    }
                }
            )
        }
        if (confirmSaveChanges) {
            AlertDialog(
                onDismissRequest = { confirmSaveChanges = false },
                title = { Text("Replace saved recipe?") },
                text = { Text("This will save your edits over “${draft.title}”. The original will change. To preserve it, choose Save as New instead.") },
                confirmButton = {
                    TextButton(onClick = { saveRecipe(copy = false, confirmedOriginal = true) }) {
                        Text("Replace Original")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            confirmSaveChanges = false
                            saveRecipe(copy = true)
                        }) { Text("Save as New") }
                        TextButton(onClick = { confirmSaveChanges = false }) { Text("Keep Editing") }
                    }
                }
            )
        }
        if (extractingPdf) AlertDialog(
            onDismissRequest = { },
            title = { Text("Reading PDF offline") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Recognizing recipe text from each page. Review the result before saving.")
            } },
            confirmButton = { }
        )
        archivePending?.let { pending ->
            val existingIds = store.existingIds()
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
private fun EditorScreen(context: Context, draft: Recipe, update: (Recipe) -> Unit,
    warnings: List<String>, unplaced: List<String>, existing: Boolean,
    back: () -> Unit, save: () -> Unit, saveCopy: () -> Unit) {
    val photoScope = rememberCoroutineScope()
    val currentDraft by rememberUpdatedState(draft)
    var importingPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var framingOpen by remember { mutableStateOf(false) }
    var fullPhotoOpen by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photoScope.launch {
            importingPhoto = true
            try {
                val path = RecipePhotos.importPhoto(context, uri)
                update(currentDraft.copy(coverImage = path, coverFraming = CoverFraming()))
                framingOpen = true
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                photoError = error.message ?: "Could not open this photo."
            } finally { importingPhoto = false }
        }
    }
    if (framingOpen && draft.coverImage != null) PhotoFramingDialog(draft,
        dismiss = { framingOpen = false }, save = { update(currentDraft.copy(coverFraming = it)); framingOpen = false })
    if (fullPhotoOpen) FullPhotoDialog(draft) { fullPhotoOpen = false }
    photoError?.let { message -> AlertDialog(onDismissRequest = { photoError = null },
        title = { Text("Photo could not be added") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { photoError = null }) { Text("OK") } }) }
    var originalOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = back) { Text("‹ Library") }
            Text("Recipe Builder", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!existing) Button(onClick = save, enabled = draft.title.isNotBlank() && !importingPhoto) { Text("Save Recipe") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (warnings.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Review imported recipe", fontWeight = FontWeight.Bold)
                    warnings.forEach { Text("• $it") }
                    if (unplaced.isNotEmpty()) Text("Unplaced lines: " + unplaced.size +
                        ". Open Original Text below for source details.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(draft.title, { update(draft.copy(title = it)) }, label = { Text("Recipe title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.servings, { update(draft.copy(servings = it)) }, label = { Text("Servings / yield") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { pickImage.launch("image/*") }, enabled = !importingPhoto) {
                Text(if (importingPhoto) "Adding photo…" else if (draft.coverImage == null) "Add Cover Photo" else "Replace Cover Photo")
            }
            if (draft.coverImage != null) {
                RecipePhoto(draft, Modifier.width(112.dp).aspectRatio(COVER_ASPECT_RATIO), onClick = { fullPhotoOpen = true })
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { framingOpen = true }, modifier = Modifier.weight(1f)) { Text("Adjust framing") }
                    TextButton(onClick = { fullPhotoOpen = true }, modifier = Modifier.weight(1f)) { Text("View full photo") }
                }
            }
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
                Button(onClick = save, enabled = draft.title.isNotBlank() && !importingPhoto) {
                    Text(if (existing) "Review Changes…" else "Save Recipe")
                }
                if (existing) OutlinedButton(onClick = saveCopy, enabled = !importingPhoto) { Text("Save as New") }
            }
            if (existing) Text("Changes stay in a draft until you confirm replacing the saved recipe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        if (t.durationSeconds <= 0) Text("Enter a duration above zero.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
internal fun CookingScreen(recipe: Recipe, snapshot: SessionSnapshot?, now: Long,
    onIngredient: (Int) -> Unit, onStep: (String) -> Unit, onTimer: (String, String) -> Unit,
    finish: () -> Unit) {
    var cancelTimer by remember { mutableStateOf<String?>(null) }
    var fullPhotoOpen by remember { mutableStateOf(false) }
    if (fullPhotoOpen) FullPhotoDialog(recipe) { fullPhotoOpen = false }
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
        LazyColumn(Modifier.fillMaxSize().testTag("cooking-recipe"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recipe.coverImage != null) item {
                RecipePhoto(recipe, Modifier.fillMaxWidth().height(170.dp), fit = true, onClick = { fullPhotoOpen = true })
            }
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
                                        .background(if (checked) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                                            RoundedCornerShape(4.dp))
                                        .border(1.5.dp, if (checked) MaterialTheme.colorScheme.tertiary else
                                            MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (checked) Text("✓", color = MaterialTheme.colorScheme.onTertiary,
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
                Card(modifier = Modifier.fillMaxWidth().testTag("step-${step.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = snapshot?.checkedSteps?.contains(step.id) == true, onCheckedChange = { onStep(step.id) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.tertiary,
                                    checkmarkColor = MaterialTheme.colorScheme.onTertiary))
                            Text("Step ${recipe.steps.indexOf(step) + 1}", fontWeight = FontWeight.Bold)
                        }
                        Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
                        step.timers.filter { it.durationSeconds > 0 }.forEach { timer ->
                            val runtime = snapshot?.timers?.get(timer.id) ?: TimerRun(timer.id, timer.durationSeconds.toLong() * 1000)
                            // A timer belongs to its step for every state. Never remove or pin it.
                            key(timer.id) {
                                TimerControls(runtime, timer.label, now, onTimer, onCancel = { cancelTimer = it })
                            }
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
    action: (String, String) -> Unit, onCancel: (String) -> Unit) {
    val secs = (timer.remaining(now) + 999L) / 1000L
    val formatted = "%02d:%02d".format(secs / 60, secs % 60)
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("timer-${timer.id}")
        .semantics { stateDescription = timer.state }
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatted, maxLines = 1,
                style = MaterialTheme.typography.headlineSmall,
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
        Button(onClick = { action(timer.id, kind) }, modifier = Modifier.width(100.dp)) {
            Text(labelAction, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Reserve the secondary action's space even before starting. Button changes
        // cannot resize the step card or shift the recipe under the cook's finger.
        Box(Modifier.width(80.dp).height(48.dp), contentAlignment = Alignment.Center) {
            if (timer.state == "done") TextButton(onClick = { action(timer.id, "dismiss") }) { Text("Dismiss") }
            if (timer.state == "running" || timer.state == "paused") TextButton(onClick = { onCancel(timer.id) }) { Text("Cancel") }
        }
    }
}


@Composable
private fun TrashScreen(
    recipes: List<Recipe>, restore: (Recipe) -> Unit,
    requestPermanentDelete: (Recipe) -> Unit, back: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("‹ Library") }
            Text("Trash", style = MaterialTheme.typography.headlineMedium)
        }
        Text("Recipes here are hidden from cooking, sharing and regular cookbook backups. You can restore them at any time.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (recipes.isEmpty()) {
            Text("Trash is empty.", style = MaterialTheme.typography.titleMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recipes, key = { it.id }) { recipe ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                            Text("${recipe.ingredients.size} ingredients • ${recipe.steps.size} steps",
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { restore(recipe) }) { Text("Restore") }
                                TextButton(onClick = { requestPermanentDelete(recipe) }) {
                                    Text("Delete Forever")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(dark: Boolean, keepAwake: Boolean, timerSound: Boolean,
    trashCount: Int, showTrash: () -> Unit,
    toggleDark: (Boolean) -> Unit, toggleAwake: (Boolean) -> Unit,
    toggleTimerSound: (Boolean) -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        TextButton(onClick = back) { Text("‹ Library") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Dark theme"); Text("Gilded Grimoire", style = MaterialTheme.typography.bodySmall) }
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
        OutlinedButton(onClick = showTrash) {
            Text("Trash ($trashCount)")
        }
        Text("Recipes in Trash can be restored. Permanent deletion requires separate confirmation.",
            style = MaterialTheme.typography.bodySmall)
        Text("Crumbkeeper ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
