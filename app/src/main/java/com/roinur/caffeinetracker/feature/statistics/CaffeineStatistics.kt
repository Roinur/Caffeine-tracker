package com.roinur.caffeinetracker.feature.statistics

import com.roinur.caffeinetracker.core.model.Intake
import com.roinur.caffeinetracker.core.model.canonicalDrinkKey
import com.roinur.caffeinetracker.core.ui.i18n.AppStrings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

internal enum class CaffeineStatsRange(val label: String) {
    WEEK("Vecka"), MONTH("Månad"), YEAR("År"), ALL_TIME("Allt");
    fun localizedLabel(strings: AppStrings): String = when (this) {
        WEEK -> strings.statsWeek
        MONTH -> strings.statsMonth
        YEAR -> strings.statsYear
        ALL_TIME -> strings.statsAll
    }
}
internal enum class CaffeineGraphMetric(val label: String) {
    CAFFEINE("Koffein"), INTAKES("Intag");
    fun localizedLabel(strings: AppStrings): String = when (this) {
        CAFFEINE -> strings.statsCaffeine
        INTAKES -> strings.statsIntakesMetric
    }
}
internal data class CaffeineDailyPoint(val date: LocalDate, val value: Double)
internal data class CaffeinePeriod(val start: LocalDate, val end: LocalDate) {
    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1
}
internal data class CaffeinePeriodSummary(val intakes: Int, val loggedDays: Int, val uniqueDrinks: Int)

internal fun caffeinePeriodSummary(rows: List<Intake>, zone: ZoneId = ZoneId.systemDefault()): CaffeinePeriodSummary =
    CaffeinePeriodSummary(
        intakes = rows.size,
        loggedDays = rows.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.distinct().size,
        uniqueDrinks = rows.map { canonicalDrinkKey(it.name) }.distinct().size
    )

internal fun caffeinePeriod(today: LocalDate, range: CaffeineStatsRange, offset: Int, earliest: LocalDate?): CaffeinePeriod {
    require(offset >= 0)
    val start = when(range) {
        CaffeineStatsRange.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(offset.toLong())
        CaffeineStatsRange.MONTH -> today.withDayOfMonth(1).minusMonths(offset.toLong())
        CaffeineStatsRange.YEAR -> today.withDayOfYear(1).minusYears(offset.toLong())
        CaffeineStatsRange.ALL_TIME -> earliest?.coerceAtMost(today) ?: today
    }
    val end = when(range) {
        CaffeineStatsRange.WEEK -> start.plusDays(6)
        CaffeineStatsRange.MONTH -> start.plusMonths(1).minusDays(1)
        CaffeineStatsRange.YEAR -> start.plusYears(1).minusDays(1)
        CaffeineStatsRange.ALL_TIME -> today
    }.coerceAtMost(today)
    return CaffeinePeriod(start, end)
}

internal fun caffeineDailyPoints(rows: List<Intake>, period: CaffeinePeriod, metric: CaffeineGraphMetric,
    zone: ZoneId = ZoneId.systemDefault()): List<CaffeineDailyPoint> {
    val grouped = rows.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    return (0 until period.days).map { index ->
        val day = period.start.plusDays(index)
        val entries = grouped[day].orEmpty()
        CaffeineDailyPoint(day, if(metric == CaffeineGraphMetric.CAFFEINE) entries.sumOf { it.caffeineMg } else entries.size.toDouble())
    }
}

internal fun caffeineChartPoints(points: List<CaffeineDailyPoint>, range: CaffeineStatsRange): List<CaffeineDailyPoint> {
    if (range != CaffeineStatsRange.YEAR && (range != CaffeineStatsRange.ALL_TIME || points.size <= 180)) return points
    return points.groupBy { it.date.withDayOfMonth(1) }.map { (month, days) -> CaffeineDailyPoint(month, days.sumOf { it.value }) }
}

/** The visible month may be partial at either end of an All-time/current-period chart. */
internal fun chartSelectionRange(date: LocalDate, monthly: Boolean, period: CaffeinePeriod): com.roinur.caffeinetracker.core.model.IntakeDateRange {
    val start = if(monthly) date.withDayOfMonth(1).coerceAtLeast(period.start) else date
    val end = if(monthly) date.withDayOfMonth(1).plusMonths(1).minusDays(1).coerceAtMost(period.end) else date
    return com.roinur.caffeinetracker.core.model.IntakeDateRange(start, end)
}
