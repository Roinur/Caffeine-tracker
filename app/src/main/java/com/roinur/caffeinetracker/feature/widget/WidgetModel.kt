package com.roinur.caffeinetracker.feature.widget

import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.caffeine.CaffeineCalculator
import java.time.Instant
import java.time.ZoneId

internal const val WIDGET_GRAPH_LOOKBACK_MS = 6 * 3_600_000L

/**
 * Earliest intake timestamp the widget needs from SQLite: covers both the full local calendar
 * day for "Today" and any older dose that can still contribute to the model at graph start,
 * now, or bedtime (even with maximum valid sipping duration and absorption time).
 */
internal fun widgetIntakeWindowStart(
    now: Long,
    halfLifeHours: Double,
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val modelStart = now - WIDGET_GRAPH_LOOKBACK_MS - CaffeineCalculator.maxActiveWindowMs(halfLifeHours)
    return minOf(dayStart, modelStart)
}

internal data class WidgetSummary(val current: Double, val evening: Double, val today: Double, val bedtime: Long)
internal fun widgetSummary(snapshot: TrackerSnapshot, now: Long, zone: ZoneId = ZoneId.systemDefault()): WidgetSummary {
    val known = snapshot.intakes.filter { it.timestamp <= now }
    val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val bedtime = CaffeineCalculator.nextBedtime(now, snapshot.settings.bedtime, zone)
    return WidgetSummary(CaffeineCalculator.total(known,now,snapshot.settings.halfLifeHours),
        CaffeineCalculator.total(known,bedtime,snapshot.settings.halfLifeHours),
        known.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == day }.sumOf { it.caffeineMg }, bedtime)
}
internal fun widgetIntake(drink: Drink, now: Long, language: AppLanguage = AppLanguage.SV): Intake = Intake(
    name = displayDrinkName(drink.name, language),
    caffeineMg = drink.caffeineMg,
    timestamp = now,
    category = displayCategory(drink.category, language),
    servingUnit = displayPortion(drink, language),
    imageKey = drink.imageKey
)
