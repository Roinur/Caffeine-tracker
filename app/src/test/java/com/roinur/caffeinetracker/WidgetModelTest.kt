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
}
