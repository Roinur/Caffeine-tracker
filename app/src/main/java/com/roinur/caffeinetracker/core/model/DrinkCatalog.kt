package com.roinur.caffeinetracker.core.model

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class DrinkImage(val label: String, val enLabel: String = label) {
    DRIP("Bryggkaffe", "Drip coffee"),
    INSTANT("Snabbkaffe", "Instant Coffee"),
    LATTE("Caffè Latte", "Caffè Latte"),
    ICED_LATTE("Iskaffe", "Iced Latte"),
    ENERGY("Energidryck", "Energy drink"),
    NONE("Kaffeikon", "Coffee icon"),
    NOCCO("NOCCO"),
    CELSIUS("Celsius"),
    RED_BULL("Red Bull"),
    LATITUDE("Latitude 65"),
    MONSTER("Monster Energy"),
    MONSTER_ULTRA("Monster Ultra"),
    POWERKING("Powerking"),
    ROCKSTAR("Rockstar"),
    REIGN("Reign"),
    PRIME("Prime Energy"),
    COCA_COLA("Coca-Cola"),
    PEPSI("Pepsi"),
    ESPRESSO("Espresso"),
    BLACK_TEA("Svart te", "Black tea"),
    GREEN_TEA("Grönt te", "Green tea");

    fun localizedLabel(language: AppLanguage): String = if (language == AppLanguage.EN) enLabel else label
}

const val CUSTOM_IMAGE_PREFIX = "custom:"
private val customImageKeyPattern = Regex("^custom:[a-zA-Z0-9_-]{8,64}\\.png$")

fun isCustomDrinkImageKey(key: String?): Boolean = key != null && customImageKeyPattern.matches(key)

fun customDrinkImageFileName(key: String?): String? =
    key?.takeIf(::isCustomDrinkImageKey)?.removePrefix(CUSTOM_IMAGE_PREFIX)

fun validDrinkImageKey(key: String?): Boolean =
    key == null || DrinkImage.entries.any { it.name == key } || isCustomDrinkImageKey(key)

private val combiningMarks = Regex("\\p{M}+")
private val whitespace = Regex("\\s+")
private val unitSpacing = Regex("(\\d)\\s+(ml|cl|dl|mg|l)\\b")
private val colaWordPattern = Regex("\\b(coke|cola)\\b")
private val drinkImageByKey = DrinkImage.entries.associateBy { it.name }
private val drinkImageCache = ConcurrentHashMap<String, DrinkImage>(128)

fun searchKey(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(combiningMarks, "").replace(whitespace, " ").trim()

fun drinkImage(name: String, category: String, imageKey: String? = null): DrinkImage {
    imageKey?.let { key -> drinkImageByKey[key]?.let { return it } }
    val cacheKey = "$name\u0000$category"
    drinkImageCache[cacheKey]?.let { return it }
    val key = searchKey(name)
    val compact = key.replace(" ", "").replace("-", "")
    val catKey = searchKey(category)
    val resolved = when {
        compact.contains("nocco") -> DrinkImage.NOCCO
        compact.contains("celsius") || compact.contains("celcius") -> DrinkImage.CELSIUS
        compact.contains("redbull") -> DrinkImage.RED_BULL
        compact.contains("latitud") -> DrinkImage.LATITUDE
        compact.contains("monster") && key.contains("ultra") -> DrinkImage.MONSTER_ULTRA
        compact.contains("monster") -> DrinkImage.MONSTER
        compact.contains("powerking") -> DrinkImage.POWERKING
        compact.contains("rockstar") -> DrinkImage.ROCKSTAR
        compact.contains("reign") -> DrinkImage.REIGN
        compact == "prime" || (key.contains("prime") && (key.contains("energy") || key.contains("energi") || catKey.contains("energi") || catKey.contains("energy"))) -> DrinkImage.PRIME
        compact.contains("pepsi") -> DrinkImage.PEPSI
        compact.contains("cocacola") || colaWordPattern.containsMatchIn(key) -> DrinkImage.COCA_COLA
        listOf("iced latte", "iced coffee", "iskaffe", "cold brew", "frappuccino").any(key::contains) -> DrinkImage.ICED_LATTE
        listOf("latte", "cappuccino", "macchiato", "flat white", "cortado", "mocha").any(key::contains) -> DrinkImage.LATTE
        key.contains("espresso") -> DrinkImage.ESPRESSO
        listOf("green tea", "gront te", "matcha").any(key::contains) -> DrinkImage.GREEN_TEA
        listOf("black tea", "svart te", "earl grey").any(key::contains) || catKey in setOf("te", "tea") -> DrinkImage.BLACK_TEA
        listOf("instant", "snabbkaffe", "nescafe", "soluble").any(key::contains) -> DrinkImage.INSTANT
        listOf("energy", "energi", "generic 5kr").any(key::contains) || catKey.let { it.contains("energi") || it.contains("energy") } -> DrinkImage.ENERGY
        listOf("drip", "filter", "brygg", "americano").any(key::contains) || key in setOf("kaffe", "coffee") -> DrinkImage.DRIP
        else -> DrinkImage.NONE
    }
    if (drinkImageCache.size > 512) drinkImageCache.clear()
    drinkImageCache[cacheKey] = resolved
    return resolved
}

fun inferDrinkCategory(name: String, fallback: String = "Övrigt"): String =
    when (drinkImage(name, "")) {
        DrinkImage.DRIP, DrinkImage.INSTANT, DrinkImage.LATTE, DrinkImage.ICED_LATTE, DrinkImage.ESPRESSO -> "Kaffe"
        DrinkImage.BLACK_TEA, DrinkImage.GREEN_TEA -> "Te"
        DrinkImage.NOCCO, DrinkImage.CELSIUS, DrinkImage.RED_BULL, DrinkImage.LATITUDE,
        DrinkImage.MONSTER, DrinkImage.MONSTER_ULTRA, DrinkImage.POWERKING,
        DrinkImage.ROCKSTAR, DrinkImage.REIGN, DrinkImage.PRIME, DrinkImage.ENERGY -> "Energidryck"
        DrinkImage.COCA_COLA, DrinkImage.PEPSI -> "Läsk"
        DrinkImage.NONE -> {
            val key = searchKey(name)
            when {
                listOf("kaffe", "coffee", "cappuccino", "mocha", "cortado", "americano", "espresso", "nescafe").any(key::contains) -> "Kaffe"
                listOf("te ", " tea", "chai", "matcha", "oolong", "yerba", "mate").any(key::contains) || key == "te" || key == "tea" -> "Te"
                listOf("energi", "energy", "pwo", "pre-workout", "preworkout", "guarana", "burn", "bang", "ghost", "alani").any(key::contains) -> "Energidryck"
                listOf("cola", "pepsi", "soda", "dr pepper", "mountain dew", "mtn dew", "fanta", "sprite", "lask").any(key::contains) -> "Läsk"
                else -> fallback
            }
        }
    }

@Immutable
data class DrinkChoice(val key: String, val name: String, val variants: List<Drink>) {
    val primary: Drink get() = variants.first()
    val searchText: String = drinkSearchText(name, primary.category)
}

/** One name in drink search; recipes/volumes stay separate in portion search. */
fun drinkChoices(drinks: List<Drink>, language: AppLanguage = AppLanguage.SV): List<DrinkChoice> = drinks
    .groupBy { canonicalDrinkKey(it.name) }
    .map { (key, variants) ->
        val ordered = variants.sortedWith(compareByDescending<Drink> { it.favorite }.thenBy { it.caffeineMg })
        DrinkChoice(key, displayDrinkName(ordered.first().name, language), ordered)
    }

/** Shared aliases and normalization for Home, library and History; artwork overrides do not affect search. */
fun drinkSearchText(name: String, category: String): String {
    val aliases = when(drinkImage(name, category)) {
        DrinkImage.DRIP -> "bryggkaffe filterkaffe drip coffee filter coffee americano"
        DrinkImage.INSTANT -> "snabbkaffe instant coffee pulverkaffe"
        DrinkImage.LATTE -> "latte caffe latte cappuccino macchiato kaffe coffee"
        DrinkImage.ICED_LATTE -> "iskaffe iced latte iced coffee cold brew kaffe coffee"
        DrinkImage.ESPRESSO -> "espresso kaffe coffee"
        DrinkImage.BLACK_TEA -> "svart te black tea"
        DrinkImage.GREEN_TEA -> "grönt te green tea"
        DrinkImage.NOCCO -> "nocco energidryck energy drink"
        DrinkImage.CELSIUS -> "celsius celcius energidryck energy drink"
        DrinkImage.RED_BULL -> "redbull red bull energidryck energy drink"
        DrinkImage.LATITUDE -> "latitude latitud energidryck energy drink"
        DrinkImage.MONSTER -> "monster energidryck energy drink"
        DrinkImage.MONSTER_ULTRA -> "monster ultra energidryck energy drink"
        DrinkImage.POWERKING -> "powerking energidryck energy drink"
        DrinkImage.ROCKSTAR -> "rockstar energidryck energy drink"
        DrinkImage.REIGN -> "reign energidryck energy drink"
        DrinkImage.PRIME -> "prime energidryck energy drink"
        DrinkImage.COCA_COLA -> "coca cola coke läsk soda"
        DrinkImage.PEPSI -> "pepsi max läsk soda"
        DrinkImage.ENERGY -> "energidryck energy drink generic energy drink"
        DrinkImage.NONE -> ""
    }
    val englishName = displayDrinkName(name, AppLanguage.EN)
    return searchKey("$name $englishName $category $aliases")
}
fun matchesDrinkSearch(name: String, category: String, query: String, note: String = ""): Boolean {
    if (query.isBlank()) return true
    val haystack = drinkSearchText(name, category) + " " + searchKey(note)
    return searchKey(query).split(' ').filter(String::isNotBlank).all(haystack::contains)
}
private fun drinkSearchRank(name: String, query: String): Int {
    val key = searchKey(name); val q = searchKey(query)
    return when { q.isBlank() -> 3; key == q -> 0; key.startsWith(q) -> 1; key.contains(q) -> 2; else -> 3 }
}
fun searchDrinks(choices: List<DrinkChoice>, query: String, recentNames: List<String> = emptyList()): List<DrinkChoice> {
    val recent = recentNames.map(::canonicalDrinkKey).distinct().withIndex().associate { it.value to it.index }
    return choices.filter { matchesDrinkSearch(it.name, it.primary.category, query) }
        .sortedWith(compareBy<DrinkChoice> { drinkSearchRank(it.name, query) }
            .thenBy { if(query.isBlank()) recent[it.key] ?: Int.MAX_VALUE else Int.MAX_VALUE }
            .thenByDescending { it.variants.any(Drink::favorite) }.thenBy { searchKey(it.name) })
}
fun searchDrinkRows(drinks: List<Drink>, query: String, language: AppLanguage = AppLanguage.SV): List<Drink> = drinks
    .filter { matchesDrinkSearch(it.name, it.category, query) }
    .sortedWith(compareBy<Drink> { minOf(drinkSearchRank(it.name, query), drinkSearchRank(displayDrinkName(it.name, language), query)) }
        .thenByDescending { it.favorite }
        .thenBy { if(query.isBlank()) it.favoriteOrder else 0 }
        .thenBy { searchKey(displayDrinkName(it.name, language)) })

@Immutable
data class PortionChoice(val key: String, val label: String, val caffeineMg: Double, val calculated: Boolean = false)

private val volumePattern = Regex("(?<![\\d.])(\\d+(?:[.,]\\d+)?)\\s*(ml|cl|dl|l)\\b", RegexOption.IGNORE_CASE)

fun volumeMl(portion: String): Double? = volumePattern.find(portion)?.let {
    val quantity = it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let null
    val factor = when(it.groupValues[2].lowercase(Locale.ROOT)) { "l" -> 1000; "dl" -> 100; "cl" -> 10; else -> 1 }
    (quantity * factor).takeIf { amount -> amount > 0 && amount.isFinite() }
}

data class ClockPortionStandard(val mgPerMl: Double, val cupMl: Double? = null)

/** Numeric unit facts verified in installed Caffeine Clock 2.9.3, not inferred from a cup. */
fun clockPortionStandard(drink: Drink): ClockPortionStandard? {
    if(drink.volumeMl != null || volumeMl(drink.portion) != null) return null // An explicit user recipe always wins.
    val name = searchKey(drink.name)
    val unit = searchKey(drink.portion).removePrefix("1 ")
    return when {
        name in setOf("drip coffee", "filterkaffe", "bryggkaffe", "coffee (drip)") &&
            unit in setOf("cup", "kopp") && drink.caffeineMg == 95.0 -> ClockPortionStandard(.4, 240.0)
        name in setOf("instant coffee", "snabbkaffe") &&
            unit in setOf("teaspoon", "tesked") && drink.caffeineMg == 57.0 -> ClockPortionStandard(.28)
        else -> null
    }
}

/** Explicit app example recipes for the original starter cups; custom recipes are never guessed. */
fun referenceVolumeMl(drink: Drink): Double? = drink.volumeMl ?: volumeMl(drink.portion) ?: when {
    clockPortionStandard(drink)?.cupMl != null -> clockPortionStandard(drink)?.cupMl
    drink.id == "espresso" && drink.portion == "1 shot" && drink.caffeineMg == 63.0 -> 30.0
    drink.id == "black-tea" && drink.portion in setOf("1 kopp", "1 cup") && drink.caffeineMg == 40.0 -> 200.0
    drink.id == "green-tea" && drink.portion in setOf("1 kopp", "1 cup") && drink.caffeineMg == 25.0 -> 200.0
    else -> null
}

/** Only presentation is shortened; the original recipe text stays in storage. */
fun displayPortion(drink: Drink, language: AppLanguage = AppLanguage.SV): String = (drink.volumeMl ?: volumeMl(drink.portion))
    ?.let { portionNumber(it, language) + " ml" } ?: displayPortionText(drink.portion, language)

fun portionNumber(value: Double, language: AppLanguage = AppLanguage.SV): String = if(value % 1.0 == 0.0) value.roundToInt().toString()
    else String.format(language.locale, "%.1f", value)

fun portionChoices(choice: DrinkChoice, reference: Drink = choice.primary, language: AppLanguage = AppLanguage.SV): List<PortionChoice> {
    val orderedVariants = if (reference in choice.variants) listOf(reference) + choice.variants.filterNot { it.id == reference.id } else choice.variants
    val exact = orderedVariants.map {
        val volume = referenceVolumeMl(it)
        val clock = clockPortionStandard(it)
        val localizedPortion = displayPortionText(it.portion, language)
        val label = if(clock?.cupMl != null) {
            val unit = if(localizedPortion.firstOrNull()?.isDigit() == true) localizedPortion else "1 " + localizedPortion
            unit + " (" + portionNumber(clock.cupMl, language) + " ml)"
        } else volume?.let { ml -> portionNumber(ml, language) + " ml" } ?: localizedPortion
        PortionChoice("saved:" + it.id, label, it.caffeineMg)
    }
    val base = reference
    val volume = referenceVolumeMl(base)
    val rate = clockPortionStandard(base)?.mgPerMl ?: volume?.let { base.caffeineMg / it }
    val calculated = if(rate != null) {
        listOf(50, 100, 150, 200, 250, 300, 330, 355, 400, 450, 500, 750, 1000).map { ml ->
            PortionChoice("ml:" + ml, "$ml ml", rate * ml, true)
        }
    } else listOf(.5, 1.0, 1.5, 2.0, 3.0).map { factor ->
        PortionChoice("times:" + factor, portionNumber(factor, language) + " × " + displayPortionText(base.portion, language), base.caffeineMg * factor, true)
    }
    // Saved recipes win when the same volume has a manually specified dose.
    val labels = exact.map { searchKey(it.label) }.toSet()
    return (exact + calculated.filter { searchKey(it.label) !in labels })
        .filter { it.caffeineMg in 0.0..10000.0 }
        .distinctBy { searchKey(it.label) to it.caffeineMg }
}

fun searchPortions(options: List<PortionChoice>, query: String): List<PortionChoice> {
    fun key(text: String) = searchKey(text).replace(',', '.').replace(unitSpacing, "$1$2")
    val normalized = key(query)
    val words = normalized.split(' ').filter { it.isNotBlank() }
    return options.filter { option ->
        val haystack = key(option.label + " " + portionNumber(option.caffeineMg) + " mg")
        words.all(haystack::contains)
    }.sortedByDescending { option -> normalized.isNotBlank() && key(option.label).startsWith(normalized) }
}

/** A numeric query without a unit means ml only when the drink has a known volume/concentration. */
fun requestedVolumeMl(query: String): Double? {
    val match = Regex("^(\\d+(?:[.,]\\d+)?)\\s*(ml|cl|dl|l)?$", RegexOption.IGNORE_CASE).matchEntire(query.trim()) ?: return null
    val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val factor = when(match.groupValues[2].lowercase(Locale.ROOT)) { "l" -> 1000; "dl" -> 100; "cl" -> 10; else -> 1 }
    return (number * factor).takeIf { it.isFinite() && it > 0 && it <= 20000 }
}
fun customVolumePortion(choice: DrinkChoice, query: String, reference: Drink = choice.primary, language: AppLanguage = AppLanguage.SV): PortionChoice? {
    val ml = requestedVolumeMl(query) ?: return null
    // A saved volume always wins, even if another variant has a different concentration.
    choice.variants.firstOrNull { referenceVolumeMl(it) == ml }?.let { saved ->
        val volume = referenceVolumeMl(saved)
        val clock = clockPortionStandard(saved)
        val localizedPortion = displayPortionText(saved.portion, language)
        val label = if(clock?.cupMl != null) {
            val unit = if(localizedPortion.firstOrNull()?.isDigit() == true) localizedPortion else "1 " + localizedPortion
            unit + " (" + portionNumber(clock.cupMl, language) + " ml)"
        } else volume?.let { v -> portionNumber(v, language) + " ml" } ?: localizedPortion
        return PortionChoice("saved:" + saved.id, label, saved.caffeineMg)
    }
    val base = reference
    val rate = clockPortionStandard(base)?.mgPerMl ?: referenceVolumeMl(base)?.let { base.caffeineMg / it } ?: return null
    val dose = rate * ml
    if(!dose.isFinite() || dose !in 0.0..10000.0) return null
    val plain = java.math.BigDecimal.valueOf(ml).stripTrailingZeros().toPlainString()
    val number = if (language == AppLanguage.SV) plain.replace('.', ',') else plain
    return PortionChoice("custom-ml:$ml", "$number ml", dose, calculated = true)
}
