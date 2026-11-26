package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.caffeine.*
import com.roinur.caffeinetracker.core.model.Intake
import org.junit.Assert.*
import org.junit.Test

class CaffeineCurveTest {
    @Test fun cachedCurveUsesTheUnchangedExactModelIncludingSippingAndSlowAbsorption() {
        val start = 1761951600000L
        val entries = listOf(Intake("a", "Kaffe", 95.0, start + 8 * 3600000L, durationMinutes = 40),
            Intake("b", "Te", 45.0, start - 12 * 3600000L, absorptionMinutes90 = 300.0))
        val curve = sampleCaffeineCurve(entries, 5.0, start)
        assertEquals(181, curve.points.size)
        assertEquals(start, curve.points.first().first)
        assertEquals(start + 30 * 3600000L, curve.points.last().first)
        curve.points.forEach { (at, amount) -> assertEquals(CaffeineCalculator.total(entries, at, 5.0), amount, 1e-9) }
    }
    @Test fun emptyCurveRemainsZeroAcrossTheWholeWindow() {
        assertTrue(sampleCaffeineCurve(emptyList(), 5.0, 1761951600000L).points.all { it.second == 0.0 })
    }
}
