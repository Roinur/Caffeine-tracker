package com.roinur.caffeinetracker.core.model

import java.util.Locale

enum class AppLanguage(val code: String, val locale: Locale) {
    SV("SV", Locale.forLanguageTag("sv-SE")),
    EN("EN", Locale.ENGLISH);

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SV
    }
}

private data class CanonicalDrinkSpec(
    val key: String,
    val sv: String,
    val en: String,
    val aliases: Set<String>
)

// English names match the standard imported history names (e.g. "Instant Coffee", "Drip coffee", "Generic energy drink")
// so switching languages or logging from favorites always aligns with historical entries in statistics.
private val canonicalDrinks = listOf(
    CanonicalDrinkSpec(
        key = "canonical:instant",
        sv = "Snabbkaffe",
        en = "Instant Coffee",
        aliases = setOf("snabbkaffe", "instant coffee")
    ),
    CanonicalDrinkSpec(
        key = "canonical:drip",
        sv = "Filterkaffe",
        en = "Drip coffee",
        aliases = setOf("filterkaffe", "bryggkaffe", "drip coffee", "filter coffee", "coffee (drip)")
    ),
    CanonicalDrinkSpec(
        key = "canonical:espresso",
        sv = "Espresso",
        en = "Espresso",
        aliases = setOf("espresso")
    ),
    CanonicalDrinkSpec(
        key = "canonical:black_tea",
        sv = "Svart te",
        en = "Black tea",
        aliases = setOf("svart te", "black tea")
    ),
    CanonicalDrinkSpec(
        key = "canonical:green_tea",
        sv = "Grönt te",
        en = "Green tea",
        aliases = setOf("gront te", "green tea")
    ),
    CanonicalDrinkSpec(
        key = "canonical:energy",
        sv = "Energidryck",
        en = "Generic energy drink",
        aliases = setOf("energidryck", "generic energy drink", "energy drink")
    ),
    CanonicalDrinkSpec(
        key = "canonical:energy_large",
        sv = "Energidryck, stor",
        en = "Generic energy drink, large",
        aliases = setOf("energidryck, stor", "generic energy drink, large", "energy drink, large")
    )
)

private val canonicalByAlias: Map<String, CanonicalDrinkSpec> = buildMap {
    canonicalDrinks.forEach { spec ->
        spec.aliases.forEach { alias -> put(alias, spec) }
    }
}

fun canonicalDrinkKey(name: String): String {
    val normalized = searchKey(name)
    return canonicalByAlias[normalized]?.key ?: normalized
}

fun displayDrinkName(name: String, language: AppLanguage): String {
    val spec = canonicalByAlias[searchKey(name)] ?: return name
    return when (language) {
        AppLanguage.EN -> spec.en
        AppLanguage.SV -> name
    }
}

fun statsGroupLabel(names: List<String>, language: AppLanguage): String {
    if (names.isEmpty()) return ""
    val dominant = names.groupingBy { it.trim() }.eachCount()
        .entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.key
        ?: names.first().trim()
    val spec = canonicalByAlias[searchKey(dominant)]
    return when {
        spec != null && language == AppLanguage.EN -> spec.en
        else -> dominant
    }
}

private val svPluralUnits = Regex("^(\\d+(?:[.,]\\d+)?)\\s+(kopp|koppar|tesked|teskedar|portion|portioner)$", RegexOption.IGNORE_CASE)
private val enPluralUnits = Regex("^(\\d+(?:[.,]\\d+)?)\\s+(cup|cups|teaspoon|teaspoons|serving|servings)$", RegexOption.IGNORE_CASE)

fun displayPortionText(portion: String, language: AppLanguage): String {
    val trimmed = portion.trim()
    if (trimmed.isEmpty()) return trimmed
    return when (language) {
        AppLanguage.EN -> {
            val lower = trimmed.lowercase(Locale.ROOT)
            when (lower) {
                "kopp" -> "cup"
                "koppar" -> "cups"
                "tesked" -> "teaspoon"
                "teskedar" -> "teaspoons"
                "portion" -> "serving"
                "portioner" -> "servings"
                "egen mängd", "egen mangd" -> "custom amount"
                else -> {
                    val match = svPluralUnits.matchEntire(trimmed)
                    if (match != null) {
                        val qtyText = match.groupValues[1].replace(',', '.')
                        val qty = qtyText.toDoubleOrNull() ?: 1.0
                        val unit = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                            "kopp", "koppar" -> if (qty == 1.0) "cup" else "cups"
                            "tesked", "teskedar" -> if (qty == 1.0) "teaspoon" else "teaspoons"
                            else -> if (qty == 1.0) "serving" else "servings"
                        }
                        "$qtyText $unit"
                    } else {
                        trimmed
                            .replace("kopp (", "cup (", ignoreCase = true)
                            .replace("tesked (", "teaspoon (", ignoreCase = true)
                    }
                }
            }
        }
        AppLanguage.SV -> {
            val lower = trimmed.lowercase(Locale.ROOT)
            when (lower) {
                "serving" -> "portion"
                "servings" -> "portioner"
                "custom amount" -> "egen mängd"
                else -> {
                    val match = enPluralUnits.matchEntire(trimmed)
                    if (match != null && match.groupValues[2].lowercase(Locale.ROOT) in setOf("serving", "servings")) {
                        val qtyText = match.groupValues[1].replace('.', ',')
                        val qty = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 1.0
                        "$qtyText " + if (qty == 1.0) "portion" else "portioner"
                    } else trimmed
                }
            }
        }
    }
}

fun displayCategory(category: String, language: AppLanguage): String {
    val key = searchKey(category)
    return when (language) {
        AppLanguage.EN -> when (key) {
            "kaffe", "coffee" -> "Coffee"
            "te", "tea" -> "Tea"
            "energidryck", "energy drink" -> "Energy drink"
            "lask", "soda" -> "Soda"
            "egen", "custom" -> "Custom"
            "ovrigt", "other" -> "Other"
            "importerad", "imported" -> "Imported"
            else -> category
        }
        AppLanguage.SV -> when (key) {
            "kaffe", "coffee" -> "Kaffe"
            "te", "tea" -> "Te"
            "energidryck", "energy drink" -> "Energidryck"
            "lask", "soda" -> "Läsk"
            "egen", "custom" -> "Egen"
            "ovrigt", "other" -> "Övrigt"
            "importerad", "imported" -> "Importerad"
            else -> category
        }
    }
}

fun isCustomCategory(category: String): Boolean = searchKey(category) in setOf("egen", "custom")

fun defaultCategories(language: AppLanguage): List<String> = when (language) {
    AppLanguage.SV -> listOf("Kaffe", "Te", "Energidryck", "Läsk", "Egen", "Övrigt")
    AppLanguage.EN -> listOf("Coffee", "Tea", "Energy drink", "Soda", "Custom", "Other")
}
