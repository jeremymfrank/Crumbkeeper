package com.crumbkeeper.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crumbkeeper.app.R
import com.crumbkeeper.app.BuildConfig
import com.crumbkeeper.app.data.*
import kotlin.math.roundToInt

internal const val COVER_ASPECT_RATIO = .8f

@Composable
internal fun RecipePhoto(recipe: Recipe, modifier: Modifier = Modifier,
    framing: CoverFraming = recipe.coverFraming, fit: Boolean = false,
    onClick: (() -> Unit)? = null) {
    val path = recipe.coverImage
    val bitmap by produceState<android.graphics.Bitmap?>(null, path, fit) {
        value = path?.let { RecipePhotos.load(it, if (fit) 2048 else 1024) }
    }
    val image = bitmap?.asImageBitmap()
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .then(if (onClick != null) Modifier.clickable(onClickLabel = "View full photo", onClick = onClick) else Modifier)
        .testTag("cover-${recipe.id}"), contentAlignment = Alignment.Center) {
        if (image == null) {
            Image(painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = if (path == null) "Illustrated recipe cover" else "Recipe photo unavailable",
                modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        } else if (fit) {
            Image(image, "Full photo: ${recipe.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Canvas(Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = "Recipe cover: ${recipe.title}" }) {
                val width = size.width.roundToInt().coerceAtLeast(1)
                val height = size.height.roundToInt().coerceAtLeast(1)
                val crop = framing.crop(image.width, image.height, width, height)
                drawImage(image, srcOffset = IntOffset(crop.left, crop.top),
                    srcSize = IntSize(crop.width, crop.height), dstSize = IntSize(width, height),
                    filterQuality = FilterQuality.High)
            }
        }
    }
}

@Composable
internal fun PhotoFramingDialog(recipe: Recipe, dismiss: () -> Unit, save: (CoverFraming) -> Unit) {
    var framing by remember(recipe.id, recipe.coverImage) { mutableStateOf(recipe.coverFraming.normalized()) }
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Frame the cover photo", style = MaterialTheme.typography.titleLarge)
                Text("Choose what appears on the recipe card. Your original photo stays complete.",
                    style = MaterialTheme.typography.bodySmall)
                RecipePhoto(recipe, Modifier.width(144.dp).aspectRatio(COVER_ASPECT_RATIO)
                    .align(Alignment.CenterHorizontally), framing = framing)
                Text("Horizontal position", style = MaterialTheme.typography.labelLarge)
                Slider(framing.x, { framing = framing.copy(x = it) }, modifier = Modifier.testTag("frame-horizontal"))
                Text("Vertical position", style = MaterialTheme.typography.labelLarge)
                Slider(framing.y, { framing = framing.copy(y = it) }, modifier = Modifier.testTag("frame-vertical"))
                Text("Zoom · ${"%.1f".format(framing.zoom)}×", style = MaterialTheme.typography.labelLarge)
                Slider(framing.zoom, { framing = framing.copy(zoom = it) }, valueRange = 1f..3f,
                    modifier = Modifier.testTag("frame-zoom"))
                TextButton(onClick = { framing = CoverFraming() }) { Text("Reset framing") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = dismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { save(framing.normalized()) }, modifier = Modifier.weight(1f)) { Text("Use Framing") }
                }
            }
        }
    }
}

@Composable
internal fun FullPhotoDialog(recipe: Recipe, dismiss: () -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 680.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(recipe.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "Close photo") }
                }
                RecipePhoto(recipe, Modifier.fillMaxWidth().heightIn(max = 480.dp).aspectRatio(1f), fit = true)
            }
        }
    }
}

@Composable
internal fun AboutDialog(dismiss: () -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.crumbkeeper_emblem), "Crumbkeeper grimoire emblem",
                    Modifier.fillMaxWidth().heightIn(max = 340.dp).aspectRatio(1f), contentScale = ContentScale.Fit)
                Text("Crumbkeeper ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                Text("Your offline recipe grimoire. Recipes, photos and timers stay with your cookbook.")
                TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}
