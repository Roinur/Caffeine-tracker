package com.roinur.caffeinetracker.feature.widget

import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.caffeine.CaffeineCalculator
import java.time.Instant
import java.time.ZoneId

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
