package com.roinur.caffeinetracker.core.diagnostics

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.roinur.caffeinetracker.core.model.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Adapted from Sauce Tracker's GitHubMediaSession: explicit launcher/config,
 * isolated database/preferences and mandatory privacy mode. This app uses only
 * synthetic fixtures, and never copies the user's production history.
 */
internal object GitHubMediaSession {
    const val EXTRA_CONFIG_BASE64 = "github_media_config_b64"
    const val DATABASE_NAME = "caffeine_tracker_github_media.db"
    const val LAUNCHER_COMPONENT_CLASS = "com.roinur.caffeinetracker.app.GitHubMediaLauncher"
    private const val MAX_ENCODED_CONFIG = 64 * 1024
    @Volatile var active = false
        private set
    var themeOverride = "DARK"
        private set
    var initialSurface = 0
        private set
    var widgetHeightDp = 200
        private set
    var widgetWidthDp = 360
        private set
    var realtimeClock = false
        private set
    fun clockTime() = if(active && !realtimeClock) now else System.currentTimeMillis()
    var now = System.currentTimeMillis()
        private set

    fun activateIfRequested(context: Context, intent: Intent?): Boolean {
        if (intent?.component?.className != LAUNCHER_COMPONENT_CLASS) return false
        val encoded = requireNotNull(intent.getStringExtra(EXTRA_CONFIG_BASE64)) { "GitHub-läget kräver en explicit konfiguration." }
        require(intent.component?.className == LAUNCHER_COMPONENT_CLASS) { "GitHub-läget kräver den separata startpunkten." }
        require(encoded.length <= MAX_ENCODED_CONFIG) { "GitHub media config is too large." }
        val json = JSONObject(String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8))
        require(json.optBoolean("privacyMask", true)) { "GitHub media mode requires privacyMask=true." }
        themeOverride = when(json.optString("theme", "dark").lowercase()) {
            "light" -> "LIGHT"; "system" -> "SYSTEM"; else -> "DARK"
        }
        initialSurface = when(json.optString("surface", "dashboard").lowercase()) {
            "history" -> 1; "drinks" -> 2; "statistics" -> 3; "settings" -> 4; "widget" -> 5; else -> 0
        }
        now = LocalDate.now().atTime(16,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        widgetHeightDp = json.optInt("widgetHeightDp",200).coerceIn(120,300)
        widgetWidthDp = json.optInt("widgetWidthDp",360).coerceIn(300,440)
        realtimeClock = json.optBoolean("realtimeClock", false)
        if(realtimeClock) now = System.currentTimeMillis()
        active = true
        context.deleteDatabase(DATABASE_NAME)
        return true
    }
    fun databaseName() = if (active) DATABASE_NAME else "caffeine_tracker.db"
    fun deactivate() { active = false;initialSurface = 0 }

    fun sampleSnapshot(): TrackerSnapshot {
        check(active)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val entries = (0..29).flatMap { offset ->
            val day = today.minusDays(offset.toLong())
            listOf(
                Intake("github-am-" + offset, "Filterkaffe", 95.0,
                    day.atTime(8,15).atZone(zone).toInstant().toEpochMilli(), 20,
                    servings = 1.0, servingUnit = "kopp"),
                Intake("github-pm-" + offset, if (offset % 3 == 0) "Energidryck" else "Espresso",
                    if (offset % 3 == 0) 160.0 else 63.0,
                    day.atTime(13,30).atZone(zone).toInstant().toEpochMilli(), 30,
                    category = if (offset % 3 == 0) "Energidryck" else "Kaffe",
                    servingUnit = if (offset % 3 == 0) "burk" else "shot")
            )
        }
        return TrackerSnapshot(entries.filter { it.timestamp <= now }, starterDrinks, TrackerSettings(themeMode = themeOverride, accentMode = "AMBER", extraDark = true))
    }
}
