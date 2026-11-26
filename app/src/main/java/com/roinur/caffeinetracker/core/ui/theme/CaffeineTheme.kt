package com.roinur.caffeinetracker.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.roinur.caffeinetracker.core.model.TrackerSettings

/** Sauce Tracker DashboardScreen's palette resolution, with a warm default accent. */
@Composable
fun CaffeineTheme(settings: TrackerSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (settings.themeMode) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    val palette = remember(context, settings, dark) { resolveCaffeinePalette(context, settings, dark) }
    val identity = remember(palette) { paletteIdentity(palette) }
    val view = LocalView.current
    val surfaceArgb = palette.surface.toArgb()
    val backgroundArgb = palette.background.toArgb()
    LaunchedEffect(dark, surfaceArgb, backgroundArgb) {
        val window = (context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = surfaceArgb
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(backgroundArgb))
        }
    }
    MaterialTheme(colorScheme = palette) {
        CompositionLocalProvider(LocalPaletteIdentity provides identity, content = content)
    }
}

internal fun resolveCaffeinePalette(context: android.content.Context, settings: TrackerSettings, dark: Boolean): ColorScheme {
    val fallback = if (dark) darkColorScheme(
        primary = Color(0xFF8BC1FF), onPrimary = Color(0xFF002B52), secondary = Color(0xFF8CC8A8),
        background = Color(0xFF1D2127), onBackground = Color(0xFFE9EDF2),
        surface = Color(0xFF292E36), onSurface = Color(0xFFE9EDF2),
        onSurfaceVariant = Color(0xFFB4BEC8), error = Color(0xFFFF8A8A)
    ) else lightColorScheme(
        primary = Color(0xFF1F63D8), onPrimary = Color.White, secondary = Color(0xFF0D8F4F),
        background = Color(0xFFF6F8FB), onBackground = Color(0xFF1F2935),
        surface = Color.White, onSurface = Color(0xFF1F2935),
        onSurfaceVariant = Color(0xFF5C6470), error = Color(0xFFB00020)
    )
    val base = if (Build.VERSION.SDK_INT >= 31 && settings.accentMode == "AUTO")
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) else fallback
    val accent = runCatching { AccentMode.valueOf(settings.accentMode) }.getOrDefault(AccentMode.AMBER)
    return applyAccentMode(applyExtraDarkMode(base, dark, settings.extraDark), accent, dark)
}
