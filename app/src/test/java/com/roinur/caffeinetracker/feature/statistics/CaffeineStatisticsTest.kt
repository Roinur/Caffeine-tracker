package com.roinur.caffeinetracker.feature.statistics

import com.roinur.caffeinetracker.core.model.Intake
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CaffeineStatisticsTest {
    private val today = LocalDate.of(2026, 10, 20)
    @Test fun currentPeriodDoesNotCountFutureDaysInDailyAverage() {
        val month = caffeinePeriod(today, CaffeineStatsRange.MONTH, 0, null)
        assertEquals(LocalDate.of(2026, 10, 1), month.start)
        assertEquals(today, month.end)
        assertEquals(20L, month.days)
        val week = caffeinePeriod(today, CaffeineStatsRange.WEEK, 0, null)
        assertEquals(LocalDate.of(2026, 10, 19), week.start)
        assertEquals(2L, week.days)
    }
    @Test fun navigationHandlesLeapMonthAndYearBoundary() {
        val february = caffeinePeriod(LocalDate.of(2024, 3, 5), CaffeineStatsRange.MONTH, 1, null)
        assertEquals(LocalDate.of(2024, 2, 29), february.end)
        assertEquals(29L, february.days)
        val previous = caffeinePeriod(LocalDate.of(2026, 1, 2), CaffeineStatsRange.WEEK, 1, null)
        assertEquals(LocalDate.of(2025, 12, 22), previous.start)
        assertEquals(LocalDate.of(2025, 12, 28), previous.end)
    }
    @Test fun zeroDaysAreIncludedAndTotalsSurviveMonthlyAggregation() {
        val zone = ZoneId.of("Europe/Stockholm")
        val period = CaffeinePeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28))
        val entries = listOf(
            Intake("a", "Kaffe", 95.5, period.start.atTime(23, 30).atZone(zone).toInstant().toEpochMilli()),
            Intake("b", "Te", 40.0, period.end.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()))
        val daily = caffeineDailyPoints(entries, period, CaffeineGraphMetric.CAFFEINE, zone)
        assertEquals(59, daily.size)
        assertEquals(0.0, daily[1].value, 0.0)
        assertEquals(95.5, daily.first().value, 0.0)
        val monthly = caffeineChartPoints(daily, CaffeineStatsRange.YEAR)
        assertEquals(2, monthly.size)
        assertEquals(135.5, monthly.sumOf { it.value }, 0.0)
        assertEquals(2.0, caffeineDailyPoints(entries, period, CaffeineGraphMetric.INTAKES, zone).sumOf { it.value }, 0.0)
    }
    @Test fun emptyHistoryStillHasOneValidDayAndNoDivisionByZero() {
        val period = caffeinePeriod(today, CaffeineStatsRange.ALL_TIME, 0, null)
        assertEquals(1L, period.days)
        assertEquals(listOf(CaffeineDailyPoint(today, 0.0)), caffeineDailyPoints(emptyList(), period, CaffeineGraphMetric.CAFFEINE))
    }
    @Test fun periodSummaryCountsIntakesLoggedDaysAndUniqueDrinksForPeriod() {
        val zone = ZoneId.of("Europe/Stockholm")
        val day1 = LocalDate.of(2026, 10, 19)
        val day2 = LocalDate.of(2026, 10, 20)
        val entries = listOf(
            Intake("a", "Filterkaffe", 95.0, day1.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()),
            Intake("b", "Filterkaffe", 95.0, day1.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()),
            Intake("c", "NOCCO", 180.0, day2.atTime(11, 0).atZone(zone).toInstant().toEpochMilli())
        )
        assertEquals(CaffeinePeriodSummary(intakes = 3, loggedDays = 2, uniqueDrinks = 2), caffeinePeriodSummary(entries, zone))
        assertEquals(CaffeinePeriodSummary(intakes = 0, loggedDays = 0, uniqueDrinks = 0), caffeinePeriodSummary(emptyList(), zone))
    }
}
