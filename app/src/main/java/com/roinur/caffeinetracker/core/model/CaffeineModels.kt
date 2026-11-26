package com.roinur.caffeinetracker.core.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class Intake(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val caffeineMg: Double,
    val timestamp: Long,
    val durationMinutes: Int = 0,
    val category: String = "Kaffe",
    val note: String = "",
    val sourceKey: String? = null,
    val servings: Double = 1.0,
    val servingUnit: String = "",
    val caffeinePerServingMg: Double = caffeineMg,
    val absorptionMinutes90: Double = 45.0,
    val imageKey: String? = null
)
@Immutable
data class Drink(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val caffeineMg: Double,
    val portion: String,
    val category: String = "Kaffe",
    val favorite: Boolean = true,
    val imageKey: String? = null,
    val favoriteOrder: Int = 0,
    val volumeMl: Double? = null
)
@Immutable
data class TrackerSettings(
    val halfLifeHours: Double = 5.0,
    val bedtime: String = "23:00",
    val bedtimeThresholdMg: Double = 50.0,
    val dailyGoalMg: Double = 300.0,
    val themeMode: String = "SYSTEM",
    val accentMode: String = "AMBER",
    val extraDark: Boolean = true,
    val language: String = "SV"
)
@Immutable
data class TrackerSnapshot(val intakes: List<Intake>, val drinks: List<Drink>, val settings: TrackerSettings)

// Editable starting values, not a copied third-party drink database.
val starterDrinks = listOf(
    Drink("filter", "Filterkaffe", 95.0, "1 kopp", "Kaffe"),
    Drink("espresso", "Espresso", 63.0, "1 shot", "Kaffe", false),
    Drink("instant", "Snabbkaffe", 57.0, "1 tesked", "Kaffe", false),
    Drink("black-tea", "Svart te", 40.0, "1 kopp", "Te", false),
    Drink("green-tea", "Grönt te", 25.0, "1 kopp", "Te", false),
    Drink("energy", "Energidryck", 80.0, "250 ml · 32 mg/100 ml", "Energidryck"),
    Drink("energy-large", "Energidryck, stor", 160.0, "500 ml · 32 mg/100 ml", "Energidryck", false),
    Drink("monster", "Monster Energy", 160.0, "500 ml", "Energidryck", false, imageKey = "MONSTER", volumeMl = 500.0),
    Drink("monster-ultra", "Monster Energy Ultra", 150.0, "500 ml", "Energidryck", false, imageKey = "MONSTER_ULTRA", volumeMl = 500.0),
    Drink("red-bull", "Red Bull", 80.0, "250 ml", "Energidryck", false, imageKey = "RED_BULL", volumeMl = 250.0),
    Drink("nocco", "NOCCO", 180.0, "330 ml", "Energidryck", false, imageKey = "NOCCO", volumeMl = 330.0),
    Drink("celsius", "Celsius", 200.0, "355 ml", "Energidryck", false, imageKey = "CELSIUS", volumeMl = 355.0),
    Drink("powerking", "Powerking", 80.0, "250 ml", "Energidryck", false, imageKey = "POWERKING", volumeMl = 250.0)
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
