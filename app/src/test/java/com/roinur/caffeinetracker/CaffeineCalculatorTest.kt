package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.caffeine.CaffeineCalculator as C
import com.roinur.caffeinetracker.core.model.Intake
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CaffeineCalculatorTest {
    private val start = Instant.parse("2026-10-20T08:00:00Z").toEpochMilli()
    private fun intake(mg: Double = 100.0, time: Long = start, duration: Int = 0) =
        Intake(name = "Kaffe", caffeineMg = mg, timestamp = time, durationMinutes = duration)

    @Test fun futureAndUnconsumedDoseContributeNothing() {
        assertEquals(0.0, C.remaining(intake(), start - 1, 5.0), 0.0)
        assertEquals(0.0, C.remaining(intake(), start, 5.0), 0.0)
    }
    @Test fun absorptionProducesFinitePeakBelowOriginalDose() {
        val amounts = (0..1440).map { C.remaining(intake(), start + it * 60_000L, 5.0) }
        assertTrue(amounts.all { it.isFinite() && it in 0.0..100.0 })
        assertTrue(amounts.max() > 75)
        assertTrue(amounts.last() < 10)
    }
    @Test fun eliminationHalvesAfterFiveHoursOnceAbsorbed() {
        val a = C.remaining(intake(), start + 8 * 3600000L, 5.0)
        val b = C.remaining(intake(), start + 13 * 3600000L, 5.0)
        assertEquals(a / 2, b, 0.00001)
    }
    @Test fun independentDosesAddWithoutLosingOldDayCarryOver() {
        val rows = listOf(intake(95.0), intake(200.0, start - 12 * 3600000L))
        val at = start + 3600000
        assertEquals(rows.sumOf { C.remaining(it, at, 5.0) }, C.total(rows, at, 5.0), 0.000001)
        assertTrue(C.total(rows, at, 5.0) > C.remaining(rows[0], at, 5.0))
    }
    @Test fun sippingSpreadsDoseInsteadOfAddingWholeAmountImmediately() {
        val at = start + 15 * 60000
        assertTrue(C.remaining(intake(duration = 60), at, 5.0) < C.remaining(intake(), at, 5.0))
        assertTrue(C.remaining(intake(duration = 60), start + 3 * 3600000L, 5.0) > 0)
    }
    @Test fun longerHalfLifeRetainsMoreCaffeine() {
        val at = start + 10 * 3600000L
        assertTrue(C.remaining(intake(), at, 10.0) > C.remaining(intake(), at, 3.0))
    }
    @Test fun eveningTargetDoesNotTreatLowPreAbsorptionValueAsFinal() {
        val result = C.belowThresholdAfter(listOf(intake()), start, 5.0, 50.0)
        assertNotNull(result)
        assertTrue(result!! > start + 4 * 3600000L)
    }
    @Test fun bedtimeUsesNextLocalDateAcrossDaylightSavingTransition() {
        val now = Instant.parse("2026-10-24T23:30:00Z").toEpochMilli()
        val bedtime = C.nextBedtime(now, "23:00", ZoneId.of("Europe/Stockholm"))
        assertEquals(Instant.parse("2026-10-25T22:00:00Z").toEpochMilli(), bedtime)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidHalfLife() {
        C.remaining(intake(), start + 1000, Double.NaN)
    }

    @Test fun differentAbsorptionTimesChangeEarlyCurveWithoutChangingDose() {
        val quick = intake().copy(absorptionMinutes90 = 35.0)
        val slow = intake().copy(absorptionMinutes90 = 90.0)
        assertTrue(C.remaining(quick, start + 10 * 60000, 5.0) > C.remaining(slow, start + 10 * 60000, 5.0))
        assertEquals(100.0, slow.caffeineMg, 0.0)
    }
    @Test fun equalAbsorptionAndEliminationRatesRemainFinite() {
        val row = intake().copy(absorptionMinutes90 = kotlin.math.ln(10.0) / kotlin.math.ln(2.0) * 60.0)
        assertTrue(C.remaining(row, start + 3600000, 1.0).isFinite())
        assertTrue(C.remaining(row.copy(durationMinutes = 60), start + 3600000, 1.0).isFinite())
    }
    @Test fun slowAbsorptionIsNotCutOffEarlyByShortHalfLife() {
        val slow = intake(mg = 1000.0).copy(absorptionMinutes90 = 1440.0)
        assertTrue(C.remaining(slow, start + 21 * 3600000L, 1.0) > 20.0)
    }
}
