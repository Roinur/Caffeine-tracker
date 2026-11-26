package com.roinur.caffeinetracker.core.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.BuildConfig
import com.roinur.caffeinetracker.R
import com.roinur.caffeinetracker.core.model.DrinkImage
import com.roinur.caffeinetracker.core.model.drinkImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val coreImageResources = mapOf(
    DrinkImage.DRIP to R.drawable.drip_coffee,
    DrinkImage.INSTANT to R.drawable.instant_coffee,
    DrinkImage.LATTE to R.drawable.latte,
    DrinkImage.ICED_LATTE to R.drawable.iced_latte,
    DrinkImage.ENERGY to R.drawable.energy_drink,
    DrinkImage.ESPRESSO to R.drawable.espresso,
    DrinkImage.BLACK_TEA to R.drawable.black_tea,
    DrinkImage.GREEN_TEA to R.drawable.green_tea
)

// Optional local-only brand drawables bundled into signed builds when present locally,
// falling back cleanly to R.drawable.energy_drink in clean source checkouts.
private val optionalBrandDrawableNames = mapOf(
    DrinkImage.NOCCO to "nocco",
    DrinkImage.CELSIUS to "celsius",
    DrinkImage.RED_BULL to "red_bull",
    DrinkImage.LATITUDE to "latitude",
    DrinkImage.MONSTER to "monster",
    DrinkImage.MONSTER_ULTRA to "monster_ultra",
    DrinkImage.POWERKING to "powerking",
    DrinkImage.ROCKSTAR to "rockstar",
    DrinkImage.REIGN to "reign",
    DrinkImage.PRIME to "prime",
    DrinkImage.COCA_COLA to "coca_cola",
    DrinkImage.PEPSI to "pepsi"
)

private val resolvedIdCache = ConcurrentHashMap<DrinkImage, Int>(DrinkImage.entries.size * 2)

@SuppressLint("DiscouragedApi")
internal fun drinkImageResource(
    resources: Resources,
    kind: DrinkImage,
    packageName: String = BuildConfig.APPLICATION_ID
): Int? {
    coreImageResources[kind]?.let { return it }
    resolvedIdCache[kind]?.let { return it }
    val optionalName = optionalBrandDrawableNames[kind] ?: return null
    val resolved = resources.getIdentifier(optionalName, "drawable", packageName)
        .takeIf { it != 0 } ?: R.drawable.energy_drink
    return resolvedIdCache.putIfAbsent(kind, resolved) ?: resolved
}

internal fun drinkImageResource(context: Context, kind: DrinkImage): Int? =
    drinkImageResource(context.resources, kind, context.packageName)

// Lock-free cache of pre-decoded, GPU-prepared BitmapPainters so 120Hz scrolling never
// blocks on a synchronized lock, never decodes PNGs on the main thread, and never allocates Painters per row.
private val painterCache = ConcurrentHashMap<Int, BitmapPainter>(DrinkImage.entries.size * 2)
private val containerShape = RoundedCornerShape(14.dp)

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

internal fun warmDrinkThumbnails(resources: Resources, packageName: String = BuildConfig.APPLICATION_ID) {
    DrinkImage.entries.forEach { kind ->
        val id = drinkImageResource(resources, kind, packageName) ?: return@forEach
        if (!painterCache.containsKey(id)) {
            runCatching { decodePainter(resources, id) }
        }
    }
}

/** Bundled 256px images: lock-free cached BitmapPainters, zero main-thread PNG decode during scroll. */
@Composable
fun DrinkThumbnail(
    name: String,
    category: String,
    modifier: Modifier = Modifier,
    imageKey: String? = null,
    showContainer: Boolean = true
) {
    val kind = remember(name, category, imageKey) { drinkImage(name, category, imageKey) }
    DrinkThumbnail(kind = kind, modifier = modifier, showContainer = showContainer)
}

@Composable
fun DrinkThumbnail(
    kind: DrinkImage,
    modifier: Modifier = Modifier,
    showContainer: Boolean = true
) {
    val context = LocalContext.current
    val resource = remember(kind, context) { drinkImageResource(context, kind) }
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
                val resources = context.resources
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
