package com.crumbkeeper.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crumbkeeper.app.R
import com.crumbkeeper.app.data.Recipe

@Composable
internal fun LibraryScreen(recipes: List<Recipe>, hasDraft: Boolean, newRecipe: () -> Unit,
    importText: (String) -> Unit, resumeDraft: () -> Unit,
    requestEdit: (Recipe) -> Unit, editCopy: (Recipe) -> Unit, requestTrash: (Recipe) -> Unit,
    cook: (Recipe) -> Unit, settings: () -> Unit, importFile: () -> Unit,
    backup: () -> Unit, share: (Recipe) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Recipe?>(null) }
    val filtered = recipes.filter { it.title.contains(query, true) || it.ingredients.any { line -> line.contains(query, true) } }
    LazyVerticalGrid(columns = GridCells.Adaptive(320.dp), modifier = Modifier.fillMaxSize().testTag("recipe-library"),
        contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.crumbkeeper_emblem), "Crumbkeeper emblem",
                    Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Text("Crumbkeeper", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Your offline recipe grimoire", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { toolsOpen = true }) { Icon(Icons.Default.Settings, "Cookbook tools") }
                    DropdownMenu(toolsOpen, { toolsOpen = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { toolsOpen = false; settings() })
                        DropdownMenuItem(text = { Text("Back Up Cookbook") }, enabled = recipes.isNotEmpty(),
                            onClick = { toolsOpen = false; backup() })
                        DropdownMenuItem(text = { Text("About Crumbkeeper") }, onClick = { toolsOpen = false; aboutOpen = true })
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("My recipes", style = MaterialTheme.typography.headlineSmall)
                    Text("${recipes.size} saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    Button(onClick = { addOpen = true }, shape = MaterialTheme.shapes.small) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add Recipe")
                    }
                    DropdownMenu(addOpen, { addOpen = false }) {
                        DropdownMenuItem(text = { Text("New Recipe") }, onClick = { addOpen = false; newRecipe() })
                        DropdownMenuItem(text = { Text("Paste Recipe") }, onClick = {
                            addOpen = false; pasted = clipboard.getText()?.text.orEmpty(); pasteOpen = true
                        })
                        DropdownMenuItem(text = { Text("Import File") }, onClick = { addOpen = false; importFile() })
                    }
                }
            }
        }
        if (hasDraft) item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(onClick = resumeDraft) { Text("Resume unsaved draft") }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Find a recipe…") }, label = { Text("Search recipes") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = MaterialTheme.shapes.small)
        }
        if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(if (recipes.isEmpty()) "Your cookbook starts here. Add a recipe, paste one, or import a cookbook." else "No matching recipes.",
                Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(filtered, key = { it.id }) { recipe ->
            var optionsOpen by remember(recipe.id) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().testTag("recipe-card-${recipe.id}"),
                shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        RecipePhoto(recipe, Modifier.width(80.dp).aspectRatio(COVER_ASPECT_RATIO),
                            onClick = if (recipe.coverImage != null) ({ photo = recipe }) else null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(recipe.title.ifBlank { "Untitled Recipe" }, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f))
                                Box {
                                    IconButton(onClick = { optionsOpen = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.MoreVert, "More options for ${recipe.title}")
                                    }
                                    DropdownMenu(optionsOpen, { optionsOpen = false }) {
                                        DropdownMenuItem(text = { Text("Edit a Copy") }, onClick = { optionsOpen = false; editCopy(recipe) })
                                        DropdownMenuItem(text = { Text("Edit Original…") }, onClick = { optionsOpen = false; requestEdit(recipe) })
                                        HorizontalDivider()
                                        DropdownMenuItem(text = { Text("Move to Trash…") }, onClick = { optionsOpen = false; requestTrash(recipe) })
                                    }
                                }
                            }
                            Text("${recipe.ingredients.size} ingredients · ${recipe.steps.size} steps",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (recipe.servings.isNotBlank()) Text(recipe.servings, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { cook(recipe) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.small) { Text("Cook") }
                        TextButton(onClick = { share(recipe) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Share") }
                    }
                }
            }
        }
    }
    photo?.let { FullPhotoDialog(it) { photo = null } }
    if (aboutOpen) AboutDialog { aboutOpen = false }
    if (pasteOpen) AlertDialog(onDismissRequest = { pasteOpen = false }, title = { Text("Paste Recipe") },
        text = { OutlinedTextField(pasted, { pasted = it }, Modifier.fillMaxWidth().heightIn(max = 280.dp),
            label = { Text("Recipe text") }, minLines = 5) },
        confirmButton = { TextButton(onClick = { pasteOpen = false; importText(pasted) }, enabled = pasted.isNotBlank()) { Text("Review Recipe") } },
        dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("Cancel") } })
}
