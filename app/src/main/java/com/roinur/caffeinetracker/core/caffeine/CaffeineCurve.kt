package com.roinur.caffeinetracker.core.caffeine

import com.roinur.caffeinetracker.core.model.Intake
import kotlin.math.sqrt

data class CaffeineCurve(val start: Long, val end: Long, val points: List<Pair<Long, Double>>)

data class CubicSegment(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val x3: Float,
    val y3: Float
)

/** Exact model samples (5-minute resolution). Called on Dispatchers.Default, outside lazy-item composition/drawing. */
fun sampleCaffeineCurve(intakes: List<Intake>, halfLifeHours: Double, start: Long): CaffeineCurve {
    val end = start + 30 * 3_600_000L
    val relevant = intakes.filter { it.timestamp <= end && it.timestamp >= start - CaffeineCalculator.activeWindowMs(it, halfLifeHours) }
    val steps = 360
    return CaffeineCurve(start, end, (0..steps).map { index ->
        val at = start + (end - start) * index / steps
        at to CaffeineCalculator.total(relevant, at, halfLifeHours)
    })
}

/**
 * Fritsch–Carlson monotone cubic Hermite interpolation converted to cubic Bezier segments.
 * Eliminates sharp corners at intake onsets and peaks while strictly preserving monotonicity
 * (never overshoots peaks or dips below the baseline).
 */
fun monotoneCubicSegments(xs: FloatArray, ys: FloatArray): List<CubicSegment> {
    val n = minOf(xs.size, ys.size)
    if (n < 2) return emptyList()
    val segCount = n - 1
    val dx = FloatArray(segCount)
    val slopes = FloatArray(segCount)
    for (i in 0 until segCount) {
        val step = xs[i + 1] - xs[i]
        dx[i] = step
        slopes[i] = if (step > 1e-6f) (ys[i + 1] - ys[i]) / step else 0f
    }
    val tangents = FloatArray(n)
    tangents[0] = slopes[0]
    tangents[n - 1] = slopes[segCount - 1]
    for (i in 1 until n - 1) {
        val prev = slopes[i - 1]
        val next = slopes[i]
        tangents[i] = if (prev == 0f || next == 0f || (prev > 0f) != (next > 0f)) {
            0f
        } else {
            (prev + next) * 0.5f
        }
    }
    for (i in 0 until segCount) {
        val d = slopes[i]
        if (d == 0f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
        } else {
            val a = tangents[i] / d
            val b = tangents[i + 1] / d
            val magSq = a * a + b * b
            if (magSq > 9f) {
                val scale = 3f / sqrt(magSq)
                tangents[i] = scale * a * d
                tangents[i + 1] = scale * b * d
            }
        }
    }
    return List(segCount) { i ->
        val step = dx[i]
        val y0 = ys[i]
        val y1 = ys[i + 1]
        val minY = minOf(y0, y1)
        val maxY = maxOf(y0, y1)
        val cp1x = xs[i] + step / 3f
        val cp1y = (y0 + tangents[i] * step / 3f).coerceIn(minY, maxY)
        val cp2x = xs[i + 1] - step / 3f
        val cp2y = (y1 - tangents[i + 1] * step / 3f).coerceIn(minY, maxY)
        CubicSegment(cp1x, cp1y, cp2x, cp2y, xs[i + 1], y1)
    }
}
