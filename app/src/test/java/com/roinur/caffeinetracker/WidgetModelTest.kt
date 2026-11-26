package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.caffeine.CaffeineCalculator
import com.roinur.caffeinetracker.feature.widget.*
import com.roinur.caffeinetracker.data.backup.BackupCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId

class WidgetModelTest {
    private val zone = ZoneId.of("Europe/Stockholm")
    private val now = ZonedDateTime.of(2026,10,25,0,10,0,0,zone).toInstant().toEpochMilli()
    @Test fun todayUsesLocalDayAndExcludesFutureIntakes() {
        val old = Intake(name="Yesterday", caffeineMg=90.0, timestamp=now-20*60000)
        val recent = Intake(name="Now", caffeineMg=80.0, timestamp=now)
        val future = Intake(name="Future", caffeineMg=200.0, timestamp=now+1)
        val settings = TrackerSettings()
        val summary = widgetSummary(TrackerSnapshot(listOf(old,recent,future),emptyList(),settings),now,zone)
        assertEquals(80.0,summary.today,0.0)
        assertEquals(CaffeineCalculator.total(listOf(old,recent),now,settings.halfLifeHours),summary.current,0.00001)
        assertEquals(CaffeineCalculator.total(listOf(old,recent),summary.bedtime,settings.halfLifeHours),summary.evening,0.00001)
        assertEquals(23,ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(summary.bedtime),zone).hour)
    }
    @Test fun intakeAtFreshClockAppearsWithoutWaitingForPeriodicTick() {
        val intake = Intake(name="Fresh", caffeineMg=120.0,timestamp=now+20000)
        val snapshot = TrackerSnapshot(listOf(intake),emptyList(),TrackerSettings())
        assertEquals(0.0,widgetSummary(snapshot,now,zone).today,0.0)
        assertEquals(120.0,widgetSummary(snapshot,intake.timestamp,zone).today,0.0)
    }
    @Test fun favoriteQuickAddPreservesExactDosePortionAndSharedImage() {
        val drink = Drink("redbull-large","Stor Red Bull",160.0,"burk","Energidryck",imageKey="RED_BULL",volumeMl=500.0)
        val intake = widgetIntake(drink,now)
        assertEquals("500 ml",intake.servingUnit)
        assertEquals(drink.caffeineMg,intake.caffeineMg,0.0)
        assertEquals(drink.caffeineMg,intake.caffeinePerServingMg,0.0)
        assertEquals(drink.imageKey,intake.imageKey)
        assertEquals(drink.category,intake.category)
        assertEquals(now,intake.timestamp)
        val snapshot = TrackerSnapshot(listOf(intake),listOf(drink),TrackerSettings())
        assertEquals(snapshot,BackupCodec.decode(BackupCodec.encode(snapshot)))
        assertNotEquals(intake.id,widgetIntake(drink,now).id)
    }
    @Test fun widgetIntakeWindowCoversTodayAndMaxModelHorizonWhileExcludingOldHistory() {
        val settings = TrackerSettings(halfLifeHours = 5.0)
        val windowStart = widgetIntakeWindowStart(now, settings.halfLifeHours, zone)
        val ancient = Intake(name = "Last month", caffeineMg = 200.0, timestamp = now - 30L * 24 * 3_600_000L)
        val slowActiveAtGraphStart = Intake(
            name = "Slow sip",
            caffeineMg = 180.0,
            timestamp = now - WIDGET_GRAPH_LOOKBACK_MS - CaffeineCalculator.maxActiveWindowMs(settings.halfLifeHours) + 60_000L,
            durationMinutes = CaffeineCalculator.MAX_DURATION_MINUTES,
            absorptionMinutes90 = CaffeineCalculator.MAX_ABSORPTION_MINUTES_90
        )
        val yesterday = Intake(name = "Yesterday", caffeineMg = 95.0, timestamp = now - 8 * 3_600_000L)
        val today = Intake(name = "Today", caffeineMg = 114.0, timestamp = now - 60_000L)
        val future = Intake(name = "Future", caffeineMg = 160.0, timestamp = now + 60_000L)
        val all = listOf(ancient, slowActiveAtGraphStart, yesterday, today, future)

        assertTrue(ancient.timestamp < windowStart)
        assertTrue(slowActiveAtGraphStart.timestamp >= windowStart)
        assertTrue(yesterday.timestamp >= windowStart)
        assertTrue(today.timestamp >= windowStart)

        val bounded = all.filter { it.timestamp in windowStart..now }
        assertEquals(listOf(slowActiveAtGraphStart, yesterday, today), bounded)

        val fullSummary = widgetSummary(TrackerSnapshot(all, emptyList(), settings), now, zone)
        val boundedSummary = widgetSummary(TrackerSnapshot(bounded, emptyList(), settings), now, zone)
        assertEquals(fullSummary.current, boundedSummary.current, 1e-9)
        assertEquals(fullSummary.evening, boundedSummary.evening, 1e-9)
        assertEquals(fullSummary.today, boundedSummary.today, 1e-9)
        assertEquals(fullSummary.bedtime, boundedSummary.bedtime)
    }
}
