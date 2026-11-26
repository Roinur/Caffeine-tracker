package com.roinur.caffeinetracker.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class IntakeDateRange(val start: LocalDate, val end: LocalDate = start) {
    init { require(!end.isBefore(start)) }
    fun contains(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate() in start..end
}

/** Validate against current favorites, then modify only order fields in one database transaction. */
fun reorderedFavorites(drinks: List<Drink>, ids: List<String>): List<Drink> {
    val favorites = drinks.filter { it.favorite }.associateBy { it.id }
    require(ids.size == ids.distinct().size && ids.toSet() == favorites.keys) { "Favoriterna har ändrats. Öppna ordningen igen." }
    return ids.mapIndexed { index, id -> favorites.getValue(id).copy(favoriteOrder = index) }
}
