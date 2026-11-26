package com.roinur.caffeinetracker.core.ui.components

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
import com.roinur.caffeinetracker.R
import com.roinur.caffeinetracker.core.model.DrinkImage
import com.roinur.caffeinetracker.core.model.drinkImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val imageResources = mapOf(
    DrinkImage.DRIP to R.drawable.drip_coffee,
    DrinkImage.INSTANT to R.drawable.instant_coffee,
    DrinkImage.LATTE to R.drawable.latte,
    DrinkImage.ICED_LATTE to R.drawable.iced_latte,
    DrinkImage.ENERGY to R.drawable.energy_drink,
    DrinkImage.ESPRESSO to R.drawable.espresso,
    DrinkImage.BLACK_TEA to R.drawable.black_tea,
    DrinkImage.GREEN_TEA to R.drawable.green_tea,
    DrinkImage.NOCCO to R.drawable.energy_drink,
    DrinkImage.CELSIUS to R.drawable.energy_drink,
    DrinkImage.RED_BULL to R.drawable.energy_drink,
    DrinkImage.LATITUDE to R.drawable.energy_drink,
    DrinkImage.MONSTER to R.drawable.energy_drink,
    DrinkImage.MONSTER_ULTRA to R.drawable.energy_drink,
    DrinkImage.POWERKING to R.drawable.energy_drink,
    DrinkImage.ROCKSTAR to R.drawable.energy_drink,
    DrinkImage.REIGN to R.drawable.energy_drink,
    DrinkImage.PRIME to R.drawable.energy_drink,
    DrinkImage.COCA_COLA to R.drawable.energy_drink,
    DrinkImage.PEPSI to R.drawable.energy_drink
)

internal fun drinkImageResource(kind: DrinkImage): Int? = imageResources[kind]

// Lock-free cache of pre-decoded, GPU-prepared BitmapPainters so 120Hz scrolling never
// blocks on a synchronized lock, never decodes PNGs on the main thread, and never allocates Painters per row.
private val painterCache = ConcurrentHashMap<Int, BitmapPainter>(imageResources.size * 2)
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

internal fun warmDrinkThumbnails(resources: Resources) {
    imageResources.values.forEach { id ->
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
