package com.roinur.caffeinetracker.core.ui.theme

import com.roinur.caffeinetracker.core.model.ThemeMode

// Source expression from Sauce DashboardViewModel.cycleThemeMode; storage stays local.
internal fun nextThemeMode(themeMode: ThemeMode): ThemeMode =
// BEGIN SAUCE THEME CYCLE
when (themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
// END SAUCE THEME CYCLE
