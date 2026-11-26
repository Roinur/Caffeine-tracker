package com.roinur.caffeinetracker.core.caffeine

import com.roinur.caffeinetracker.core.model.Intake

data class CaffeineCurve(val start: Long, val end: Long, val points: List<Pair<Long, Double>>)

/** Exact model samples. Called on Dispatchers.Default, outside lazy-item composition/drawing. */
fun sampleCaffeineCurve(intakes: List<Intake>, halfLifeHours: Double, start: Long): CaffeineCurve {
    val end = start + 30 * 3_600_000L
    val relevant = intakes.filter { it.timestamp <= end && it.timestamp >= start - CaffeineCalculator.activeWindowMs(it, halfLifeHours) }
    return CaffeineCurve(start, end, (0..180).map { index ->
        val at = start + (end - start) * index / 180
        at to CaffeineCalculator.total(relevant, at, halfLifeHours)
    })
}
