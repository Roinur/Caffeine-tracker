package com.roinur.caffeinetracker.core.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Resolve the dashboard's key once per palette update, not per row/animation frame.
internal val LocalPaletteIdentity = compositionLocalOf<List<Color>?> { null }

@Composable
internal fun currentPaletteIdentity(): List<Color> =
    LocalPaletteIdentity.current ?: paletteIdentity(MaterialTheme.colorScheme)

// MaterialTheme updates its remembered ColorScheme in place. Its object identity is
// therefore not a palette revision: capture the actual immutable color values.
internal fun paletteIdentity(scheme: ColorScheme): List<Color> = with(scheme) {
    listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
        secondary, onSecondary, secondaryContainer, onSecondaryContainer,
        tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
        background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
        surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer,
        onErrorContainer, outline, outlineVariant, scrim, surfaceBright, surfaceDim,
        surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
        surfaceContainerLow, surfaceContainerLowest
    )
}

/** Animate interactions within a palette; apply a new palette on its first frame.
 * Only the color animator is restarted. The owning screen, focus, scroll, gesture
 * and popup state remain alive, and no bitmap/screen copy or reload is needed.
 */
@Composable
internal fun animatePaletteColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color> = spring(),
    label: String = "paletteInteractionColor"
): State<Color> = key(currentPaletteIdentity()) {
    animateColorAsState(targetValue = targetValue, animationSpec = animationSpec, label = label)
}
