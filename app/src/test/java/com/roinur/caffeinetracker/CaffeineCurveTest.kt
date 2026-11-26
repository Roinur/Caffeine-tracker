package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.caffeine.*
import com.roinur.caffeinetracker.core.model.Intake
import org.junit.Assert.*
import org.junit.Test

class CaffeineCurveTest {
    @Test fun cachedCurveUsesTheUnchangedExactModelIncludingSippingAndSlowAbsorption() {
        val start = 1761951600000L
        val entries = listOf(
            Intake("a", "Kaffe", 95.0, start + 8 * 3600000L, durationMinutes = 40),
            Intake("b", "Te", 45.0, start - 12 * 3600000L, absorptionMinutes90 = 300.0)
        )
        val curve = sampleCaffeineCurve(entries, 5.0, start)
        assertEquals(361, curve.points.size)
        assertEquals(start, curve.points.first().first)
        assertEquals(start + 30 * 3600000L, curve.points.last().first)
        curve.points.forEach { (at, amount) -> assertEquals(CaffeineCalculator.total(entries, at, 5.0), amount, 1e-9) }
    }

    @Test fun emptyCurveRemainsZeroAcrossTheWholeWindow() {
        assertTrue(sampleCaffeineCurve(emptyList(), 5.0, 1761951600000L).points.all { it.second == 0.0 })
    }

    @Test fun monotoneCubicSegmentsStayWithinBoundsAndFlattenAtPeaksAndOnsets() {
        // Screen coordinates: lower y = higher caffeine peak
        val xs = floatArrayOf(0f, 10f, 20f, 30f, 40f, 50f)
        val ys = floatArrayOf(100f, 100f, 40f, 20f, 55f, 80f)
        val segments = monotoneCubicSegments(xs, ys)
        assertEquals(5, segments.size)
        // Flat baseline segment stays flat
        assertEquals(100f, segments[0].y1, 1e-5f)
        assertEquals(100f, segments[0].y2, 1e-5f)
        // Onset at index 1 leaves baseline with horizontal tangent (smooth S-bend, no sharp corner)
        assertEquals(100f, segments[1].y1, 1e-5f)
        // Peak at index 3 has horizontal tangent on both sides (smooth rounded crest)
        assertEquals(20f, segments[2].y2, 1e-5f)
        assertEquals(20f, segments[3].y1, 1e-5f)
        // Every control point stays strictly within its segment's [minY, maxY] (no overshoot/undershoot)
        for (i in segments.indices) {
            val minY = minOf(ys[i], ys[i + 1])
            val maxY = maxOf(ys[i], ys[i + 1])
            assertTrue(segments[i].y1 in minY..maxY)
            assertTrue(segments[i].y2 in minY..maxY)
        }
    }
}
