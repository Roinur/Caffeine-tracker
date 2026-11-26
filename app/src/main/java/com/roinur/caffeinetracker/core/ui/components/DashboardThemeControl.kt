package com.roinur.caffeinetracker.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.R
import com.roinur.caffeinetracker.core.model.ThemeMode
import com.roinur.caffeinetracker.core.ui.theme.AccentMode
import com.roinur.caffeinetracker.core.ui.components.compat.SauceThemeText as Text

// Verbatim upstream component and its helpers. Only package/import wiring is local.
// Verify against the original checkout with tools/verify-theme-source.py.
// BEGIN SAUCE THEME SOURCE
@Composable
internal fun ThemeToggleWithAccentPicker(
    themeMode: ThemeMode,
    accentMode: AccentMode,
    incognitoModeEnabled: Boolean,
    cunnyModeActive: Boolean,
    onCycleThemeMode: () -> Unit,
    onAccentModeSelected: (AccentMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = ACCENT_PICKER_OPTIONS
    var pickerVisible by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableStateOf<Int?>(null) }

    val iconSize = 40.dp
    val chipSize = 22.dp
    val chipSpacing = 8.dp
    val pillPadding = 10.dp
    val pillGap = 8.dp
    val pillHeight = 34.dp
    val pillWidth =
        (pillPadding * 2) + (chipSize * options.size) + (chipSpacing * (options.size - 1))
    val expandedWidth = iconSize + pillGap + pillWidth

    val density = androidx.compose.ui.platform.LocalDensity.current
    fun indexForX(x: Float): Int? {
        val iconPx = with(density) { iconSize.toPx() }
        val gapPx = with(density) { pillGap.toPx() }
        val paddingPx = with(density) { pillPadding.toPx() }
        val chipPx = with(density) { chipSize.toPx() }
        val slotPx = with(density) { (chipSize + chipSpacing).toPx() }

        val start = iconPx + gapPx + paddingPx
        val end = start + ((options.size - 1) * slotPx) + chipPx
        if (x < start || x > end) return null
        val idx = ((x - start) / slotPx).toInt()
        return idx.coerceIn(0, options.lastIndex)
    }

    Box(
        modifier = modifier
            .width(if (pickerVisible && !incognitoModeEnabled) expandedWidth else iconSize)
            .height(iconSize)
            .pointerInput(incognitoModeEnabled, accentMode, cunnyModeActive) {
                awaitEachGesture {
                    if (cunnyModeActive) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (incognitoModeEnabled) {
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }

                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress == null) {
                        onCycleThemeMode()
                        return@awaitEachGesture
                    }

                    pickerVisible = true
                    highlightedIndex = indexForX(longPress.position.x)
                        ?: options.indexOfFirst { it.mode == accentMode }.takeIf { it >= 0 }

                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null) {
                            released = true
                        } else if (!change.pressed) {
                            released = true
                        } else {
                            highlightedIndex = indexForX(change.position.x) ?: highlightedIndex
                        }
                    }

                    val picked = highlightedIndex
                    if (picked != null && picked in options.indices) {
                        onAccentModeSelected(options[picked].mode)
                    }
                    pickerVisible = false
                    highlightedIndex = null
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        if (cunnyModeActive) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\uD83E\uDD80",
                    style = MaterialTheme.typography.titleLarge,
                    cunnyExempt = true
                )
            }
            return@Box
        }
        if (pickerVisible && !incognitoModeEnabled) {
            Box(
                modifier = Modifier
                    .offset(x = iconSize + pillGap)
                    .height(pillHeight)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = pillPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(chipSpacing)
                ) {
                    options.forEachIndexed { index, option ->
                        val selected = accentMode == option.mode
                        val hovered = highlightedIndex == index
                        val ringColor = when {
                            hovered -> MaterialTheme.colorScheme.primary
                            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                        if (option.color == null) {
                            val autoCircleColor = if (hovered || selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            }
                            Canvas(
                                modifier = Modifier
                                    .size(chipSize)
                                    .padding(1.dp)
                            ) {
                                val stroke = if (hovered || selected) 3f else 2f
                                val radius = (size.minDimension / 2f) - stroke
                                drawCircle(
                                    color = autoCircleColor,
                                    radius = radius,
                                    style = Stroke(width = stroke)
                                )
                                drawLine(
                                    color = autoCircleColor,
                                    start = Offset(size.width * 0.72f, size.height * 0.18f),
                                    end = Offset(size.width * 0.28f, size.height * 0.82f),
                                    strokeWidth = stroke
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(chipSize)
                                    .clip(CircleShape)
                                    .background(option.color)
                                    .border(
                                        width = if (hovered || selected) 2.2.dp else 1.2.dp,
                                        color = ringColor,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (incognitoModeEnabled) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_incognito_24),
                    contentDescription = "Incognito mode enabled",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = themeModeSymbol(themeMode),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

internal fun themeModeSymbol(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.LIGHT -> "☀"
        ThemeMode.DARK -> "☾"
        ThemeMode.SYSTEM -> "◐"
    }
}

internal data class AccentPickerOption(
    val mode: AccentMode,
    val color: Color?
)

private val ACCENT_PICKER_OPTIONS = listOf(
    AccentPickerOption(AccentMode.AUTO, null),
    AccentPickerOption(AccentMode.RED, Color(0xFFE53935)),
    AccentPickerOption(AccentMode.ORANGE, Color(0xFFFB8C00)),
    AccentPickerOption(AccentMode.AMBER, Color(0xFFF9A825)),
    AccentPickerOption(AccentMode.GREEN, Color(0xFF43A047)),
    AccentPickerOption(AccentMode.TEAL, Color(0xFF00897B)),
    AccentPickerOption(AccentMode.BLUE, Color(0xFF1E88E5)),
    AccentPickerOption(AccentMode.INDIGO, Color(0xFF5E35B1)),
    AccentPickerOption(AccentMode.PINK, Color(0xFFD81B60))
)
// END SAUCE THEME SOURCE
