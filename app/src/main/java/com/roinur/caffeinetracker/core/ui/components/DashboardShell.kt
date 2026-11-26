@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.roinur.caffeinetracker.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.model.ThemeMode
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import com.roinur.caffeinetracker.core.ui.theme.AccentMode

/**
 * DashboardHost.kt's actual CenterAlignedTopAppBar: same background painting,
 * insets, title box and leading theme gesture control. Profile actions omitted.
 */
@Composable
internal fun DashboardHeader(title: String, onBack: (() -> Unit)? = null, onSettings: (() -> Unit)? = null,
    themeMode: ThemeMode, accentMode: AccentMode, onCycleTheme: () -> Unit, onAccent: (AccentMode) -> Unit) {
    val strings = LocalStrings.current
    CenterAlignedTopAppBar(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent),
        title = {
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
                label = "headerTitle"
            ) { text ->
                Text(text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if(onBack != null) IconButton(onClick = onBack) { TrackerGlyph(TrackerIcon.Back, description = strings.back) }
            else ThemeToggleWithAccentPicker(
                themeMode = themeMode, accentMode = accentMode,
                incognitoModeEnabled = false, cunnyModeActive = false,
                onCycleThemeMode = onCycleTheme, onAccentModeSelected = onAccent,
                modifier = Modifier.padding(start = 8.dp))
        },
        actions = {
            if(onSettings != null) IconButton(onClick = onSettings) { TrackerGlyph(TrackerIcon.Settings, description = strings.settings) }
        }
    )
}
