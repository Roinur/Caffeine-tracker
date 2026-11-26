package com.roinur.caffeinetracker.core.caffeine

import com.roinur.caffeinetracker.core.model.Intake
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.abs

/** One-compartment estimate with first-order absorption. Dose/time remain original facts. */
object CaffeineCalculator {
    const val MAX_ABSORPTION_MINUTES_90 = 1440.0
    const val MAX_DURATION_MINUTES = 1440

    fun activeWindowHours(intake: Intake, halfLifeHours: Double): Double =
        max(halfLifeHours * 20.0, (intake.absorptionMinutes90 / 60.0) * 6.0) + intake.durationMinutes / 60.0

    fun activeWindowMs(intake: Intake, halfLifeHours: Double): Long =
        (activeWindowHours(intake, halfLifeHours) * 3_600_000.0).toLong()

    /** Upper bound of activeWindowHours across any intake valid under BackupCodec.validIntake. */
    fun maxActiveWindowHours(halfLifeHours: Double): Double =
        max(halfLifeHours * 20.0, (MAX_ABSORPTION_MINUTES_90 / 60.0) * 6.0) + MAX_DURATION_MINUTES / 60.0

    fun maxActiveWindowMs(halfLifeHours: Double): Long =
        (maxActiveWindowHours(halfLifeHours) * 3_600_000.0).toLong()

    fun remaining(intake: Intake, at: Long, halfLifeHours: Double): Double {
        require(halfLifeHours.isFinite() && halfLifeHours in 1.0..24.0)
        val hours = (at - intake.timestamp) / 3_600_000.0
        val duration = intake.durationMinutes / 60.0
        if (hours <= 0 || intake.caffeineMg <= 0 || hours > activeWindowHours(intake, halfLifeHours)) return 0.0
        val ke = ln(2.0) / halfLifeHours
        val ka = ln(10.0) / (intake.absorptionMinutes90 / 60.0)
        val equalRates = abs(ka - ke) < 1e-9
        val fraction = if (duration <= 0) {
            if (equalRates) ka * hours * exp(-ka * hours)
            else ka / (ka - ke) * (exp(-ke * hours) - exp(-ka * hours))
        } else if (equalRates) {
            val a = minOf(hours, duration)
            ((ka * (hours - a) + 1) * exp(-ka * (hours - a)) -
                (ka * hours + 1) * exp(-ka * hours)) / ka / duration
        } else {
            val consumedHours = minOf(hours, duration)
            ka / (ka - ke) / duration * (
                (exp(-ke * (hours - consumedHours)) - exp(-ke * hours)) / ke -
                    (exp(-ka * (hours - consumedHours)) - exp(-ka * hours)) / ka
                )
        }
        return intake.caffeineMg * fraction.coerceIn(0.0, 1.0)
    }
    fun total(intakes: List<Intake>, at: Long, halfLifeHours: Double): Double =
        intakes.sumOf { remaining(it, at, halfLifeHours) }

    fun nextBedtime(now: Long, time: String, zone: ZoneId = ZoneId.systemDefault()): Long {
        val current = Instant.ofEpochMilli(now).atZone(zone)
        var bedtime = current.toLocalDate().atTime(LocalTime.parse(time)).atZone(zone)
        if (!bedtime.isAfter(current)) bedtime = current.toLocalDate().plusDays(1).atTime(LocalTime.parse(time)).atZone(zone)
        return bedtime.toInstant().toEpochMilli()
    }
    fun belowThresholdAfter(intakes: List<Intake>, now: Long, halfLife: Double, threshold: Double): Long? {
        val known = intakes.filter { it.timestamp <= now }
        val lastEnd = known.maxOfOrNull { it.timestamp + it.durationMinutes * 60_000L } ?: now
        val horizon = max(now, lastEnd) + 72 * 3_600_000L
        var lastAbove: Long? = null
        var t = now
        while (t <= horizon) {
            if (total(known, t, halfLife) > threshold) lastAbove = t
            t += 5 * 60_000
        }
        return if (lastAbove == null) now else (lastAbove + 5 * 60_000).takeIf { it <= horizon }
    }
}
