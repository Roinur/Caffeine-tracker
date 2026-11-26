package com.roinur.caffeinetracker.core.ui.components

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.R
import com.roinur.caffeinetracker.core.model.CUSTOM_IMAGE_PREFIX
import com.roinur.caffeinetracker.core.model.DrinkImage
import com.roinur.caffeinetracker.core.model.customDrinkImageFileName
import com.roinur.caffeinetracker.core.model.drinkImage
import com.roinur.caffeinetracker.core.model.isCustomDrinkImageKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

private val imageResources = mapOf(
    DrinkImage.DRIP to R.drawable.drip_coffee,
    DrinkImage.INSTANT to R.drawable.instant_coffee,
    DrinkImage.LATTE to R.drawable.latte,
    DrinkImage.ICED_LATTE to R.drawable.iced_latte,
    DrinkImage.ENERGY to R.drawable.energy_drink,
    DrinkImage.ESPRESSO to R.drawable.espresso,
    DrinkImage.BLACK_TEA to R.drawable.black_tea,
    DrinkImage.GREEN_TEA to R.drawable.green_tea,
    DrinkImage.NOCCO to R.drawable.nocco,
    DrinkImage.CELSIUS to R.drawable.celsius,
    DrinkImage.RED_BULL to R.drawable.red_bull,
    DrinkImage.LATITUDE to R.drawable.latitude,
    DrinkImage.MONSTER to R.drawable.monster,
    DrinkImage.MONSTER_ULTRA to R.drawable.monster_ultra,
    DrinkImage.POWERKING to R.drawable.powerking,
    DrinkImage.ROCKSTAR to R.drawable.rockstar,
    DrinkImage.REIGN to R.drawable.reign,
    DrinkImage.PRIME to R.drawable.prime,
    DrinkImage.COCA_COLA to R.drawable.coca_cola,
    DrinkImage.PEPSI to R.drawable.pepsi
)

private const val CUSTOM_IMAGE_DIR = "custom_drink_images"
private const val CUSTOM_IMAGE_MAX_PX = 256

internal fun drinkImageResource(kind: DrinkImage): Int? = imageResources[kind]

internal fun drinkImageResource(@Suppress("UNUSED_PARAMETER") context: Context, kind: DrinkImage): Int? =
    imageResources[kind]

internal fun customDrinkImageDir(context: Context): File = File(context.filesDir, CUSTOM_IMAGE_DIR)

internal fun customDrinkImageFile(context: Context, imageKey: String?): File? {
    val fileName = customDrinkImageFileName(imageKey) ?: return null
    val dir = customDrinkImageDir(context)
    val file = File(dir, fileName)
    return file.takeIf { it.parentFile?.canonicalPath == dir.canonicalPath }
}

internal fun listCustomDrinkImageKeys(context: Context): List<String> {
    val dir = customDrinkImageDir(context)
    if (!dir.isDirectory) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
        ?.sortedByDescending { it.lastModified() }
        ?.mapNotNull { file ->
            val key = CUSTOM_IMAGE_PREFIX + file.name
            key.takeIf(::isCustomDrinkImageKey)
        }
        .orEmpty()
}

// Lock-free caches of pre-decoded, GPU-prepared BitmapPainters so 120Hz scrolling never
// blocks on a synchronized lock, never decodes PNGs on the main thread, and never allocates Painters per row.
private val painterCache = ConcurrentHashMap<Int, BitmapPainter>(imageResources.size * 2)
private val customPainterCache = ConcurrentHashMap<String, BitmapPainter>(32)
private val containerShape = RoundedCornerShape(14.dp)
private val customImageClipShape = RoundedCornerShape(10.dp)

private fun decodePainter(resources: Resources, id: Int): BitmapPainter {
    painterCache[id]?.let { return it }
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeResource(resources, id, options)
        ?: error("Could not decode drink drawable $id")
    decoded.prepareToDraw()
    val painter = BitmapPainter(decoded.asImageBitmap(), filterQuality = FilterQuality.Medium)
    return painterCache.putIfAbsent(id, painter) ?: painter
}

internal fun decodeCustomDrinkBitmap(context: Context, imageKey: String?): Bitmap? {
    val file = customDrinkImageFile(context, imageKey) ?: return null
    if (!file.isFile) return null
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
}

private fun decodeCustomPainter(context: Context, imageKey: String): BitmapPainter? {
    customPainterCache[imageKey]?.let { return it }
    val decoded = decodeCustomDrinkBitmap(context, imageKey) ?: return null
    decoded.prepareToDraw()
    val painter = BitmapPainter(decoded.asImageBitmap(), filterQuality = FilterQuality.Medium)
    return customPainterCache.putIfAbsent(imageKey, painter) ?: painter
}

/** Imports a user-picked photo/PNG into app-private storage, scaled cleanly to max 256px. */
internal suspend fun importCustomDrinkImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return@runCatching null

        var sample = 1
        while (max(srcW / sample, srcH / sample) > CUSTOM_IMAGE_MAX_PX * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@runCatching null

        val maxSide = max(decoded.width, decoded.height)
        val scaled = if (maxSide > CUSTOM_IMAGE_MAX_PX) {
            val ratio = CUSTOM_IMAGE_MAX_PX.toFloat() / maxSide.toFloat()
            val targetW = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val dir = customDrinkImageDir(context).apply { mkdirs() }
        val fileName = "${UUID.randomUUID().toString().replace("-", "")}.png"
        val tmp = File(dir, "$fileName.tmp")
        val target = File(dir, fileName)
        FileOutputStream(tmp).use { out ->
            check(scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed" }
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }

        val key = CUSTOM_IMAGE_PREFIX + fileName
        scaled.prepareToDraw()
        customPainterCache[key] = BitmapPainter(scaled.asImageBitmap(), filterQuality = FilterQuality.Medium)
        key
    }.getOrNull()
}

internal fun warmDrinkThumbnails(resources: Resources) {
    imageResources.values.forEach { id ->
        if (!painterCache.containsKey(id)) {
            runCatching { decodePainter(resources, id) }
        }
    }
}

internal fun warmDrinkThumbnails(context: Context) {
    warmDrinkThumbnails(context.resources)
    listCustomDrinkImageKeys(context).forEach { key ->
        if (!customPainterCache.containsKey(key)) {
            runCatching { decodeCustomPainter(context, key) }
        }
    }
}

/** Bundled 256px & custom images: lock-free cached BitmapPainters, zero main-thread PNG decode during scroll. */
@Composable
fun DrinkThumbnail(
    name: String,
    category: String,
    modifier: Modifier = Modifier,
    imageKey: String? = null,
    showContainer: Boolean = true
) {
    if (isCustomDrinkImageKey(imageKey)) {
        CustomDrinkThumbnail(
            imageKey = imageKey!!,
            fallbackKind = remember(name, category) { drinkImage(name, category, null) },
            modifier = modifier,
            showContainer = showContainer
        )
    } else {
        val kind = remember(name, category, imageKey) { drinkImage(name, category, imageKey) }
        DrinkThumbnail(kind = kind, modifier = modifier, showContainer = showContainer)
    }
}

@Composable
internal fun CustomDrinkThumbnail(
    imageKey: String,
    fallbackKind: DrinkImage = DrinkImage.NONE,
    modifier: Modifier = Modifier,
    showContainer: Boolean = true
) {
    val context = LocalContext.current
    val cached = customPainterCache[imageKey]
    val loaded by produceState(cached, imageKey, context) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                runCatching { decodeCustomPainter(context, imageKey) }.getOrNull()
            }
        }
    }
    val painter = cached ?: loaded
    if (painter != null) {
        val boxModifier = if (showContainer) {
            modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .08f), containerShape)
        } else modifier
        Box(boxModifier, contentAlignment = Alignment.Center) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .matchParentSize()
                    .padding(if (showContainer) 3.dp else 1.dp)
                    .clip(customImageClipShape)
            )
        }
    } else {
        DrinkThumbnail(kind = fallbackKind, modifier = modifier, showContainer = showContainer)
    }
}

@Composable
fun DrinkThumbnail(
    kind: DrinkImage,
    modifier: Modifier = Modifier,
    showContainer: Boolean = true
) {
    val resource = imageResources[kind]
    val boxModifier = if (showContainer) {
        modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .08f), containerShape)
    } else modifier
    Box(boxModifier, contentAlignment = Alignment.Center) {
        if (resource != null) {
            val cached = painterCache[resource]
            if (cached != null) {
                Image(
                    painter = cached,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize().padding(if (showContainer) 3.dp else 1.dp)
                )
            } else {
                val resources = LocalContext.current.resources
                val loaded by produceState<BitmapPainter?>(null, resource, resources) {
                    value = withContext(Dispatchers.Default) {
                        runCatching { decodePainter(resources, resource) }.getOrNull()
                    }
                }
                if (loaded != null) {
                    Image(
                        painter = loaded!!,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize().padding(if (showContainer) 3.dp else 1.dp)
                    )
                }
            }
        } else {
            TrackerGlyph(TrackerIcon.Coffee, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
