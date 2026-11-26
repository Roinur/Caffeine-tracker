package com.roinur.caffeinetracker.data.backup

import com.roinur.caffeinetracker.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringWriter
import java.time.LocalTime

object BackupCodec {
    const val FORMAT = "com.roinur.caffeinetracker.backup"
    const val MAX_BYTES = 20 * 1024 * 1024

    fun encode(snapshot: TrackerSnapshot): String {
        val root = JSONObject().put("format", FORMAT).put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("intakes", JSONArray().apply { snapshot.intakes.forEach { put(intakeJson(it)) } })
            .put("drinks", JSONArray().apply { snapshot.drinks.forEach { put(drinkJson(it)) } })
            .put("settings", settingsJson(snapshot.settings))
        return StringWriter().also { BackupJsonWriter.write(root, it, prettyPrint = true) }.toString()
    }

    fun decode(text: String): TrackerSnapshot {
        require(text.toByteArray().size <= MAX_BYTES) { "Filen är för stor (max 20 MB)." }
        val root = JSONObject(text.removePrefix("\uFEFF"))
        require(root.getString("format") == FORMAT) { "Det här är inte en Caffeine Tracker-backup." }
        require(root.getInt("version") == 1) { "Backupversionen stöds inte." }
        val intakes = root.getJSONArray("intakes").let { rows ->
            (0 until rows.length()).map { intakeFromJson(rows.getJSONObject(it)) }
        }
        val drinks = root.getJSONArray("drinks").let { rows ->
            (0 until rows.length()).map { drinkFromJson(rows.getJSONObject(it)) }
        }
        require(intakes.map { it.id }.distinct().size == intakes.size) { "Backupen har dubbla post-ID:n." }
        require(drinks.map { it.id }.distinct().size == drinks.size) { "Backupen har dubbla dryck-ID:n." }
        val sourceKeys = intakes.mapNotNull { it.sourceKey }
        require(sourceKeys.distinct().size == sourceKeys.size) { "Backupen har dubbla import-ID:n." }
        return TrackerSnapshot(intakes, drinks, settingsFromJson(root.getJSONObject("settings")))
    }

    fun validIntake(i: Intake) {
        require(i.id.isNotBlank() && i.name.isNotBlank() && i.name.length <= 500) { "Intaget saknar namn eller ID." }
        require(i.caffeineMg.isFinite() && i.caffeineMg in 0.0..10_000.0) { "Ogiltig koffeinmängd." }
        require(i.timestamp in 946684800000L..4102444800000L) { "Ogiltig tidpunkt." }
        require(i.durationMinutes in 0..1440) { "Ogiltig dricktid." }
        require(i.servings.isFinite() && i.servings > 0 && i.servings <= 10000) { "Ogiltigt portionsantal." }
        require(i.caffeinePerServingMg.isFinite() && i.caffeinePerServingMg in 0.0..10000.0) { "Ogiltig portionsmängd." }
        require(i.servingUnit.length <= 500) { "För lång portionsbeskrivning." }
        require(i.absorptionMinutes90.isFinite() && i.absorptionMinutes90 in 1.0..1440.0) { "Ogiltig upptagstid." }
        require(i.note.length <= 10000 && i.category.length <= 100 && i.id.length <= 500) { "För långt textfält." }
        require(i.sourceKey == null || (i.sourceKey.isNotBlank() && i.sourceKey.length <= 1000)) { "Ogiltigt import-ID." }
        require(validDrinkImageKey(i.imageKey)) { "Okänd dryckesbild." }
    }

    fun validDrink(d: Drink) {
        require(d.id.isNotBlank() && d.id.length <= 500 && d.name.isNotBlank() && d.name.length <= 500) { "Drycken saknar namn." }
        require(d.caffeineMg.isFinite() && d.caffeineMg in 0.0..10_000.0) { "Ogiltig koffeinmängd." }
        require(d.portion.length <= 500 && d.category.length <= 100) { "För långt textfält." }
        require(d.volumeMl == null || (d.volumeMl.isFinite() && d.volumeMl > 0 && d.volumeMl <= 100000)) { "Ogiltig volym." }
        require(validDrinkImageKey(d.imageKey)) { "Okänd dryckesbild." }
    }

    fun intakeJson(i: Intake) = JSONObject().put("id", i.id).put("name", i.name)
        .put("caffeineMg", i.caffeineMg).put("timestamp", i.timestamp)
        .put("durationMinutes", i.durationMinutes).put("category", i.category)
        .put("note", i.note).put("sourceKey", i.sourceKey ?: JSONObject.NULL)
        .put("servings", i.servings).put("servingUnit", i.servingUnit)
        .put("caffeinePerServingMg", i.caffeinePerServingMg).put("absorptionMinutes90", i.absorptionMinutes90)
        .put("imageKey", i.imageKey ?: JSONObject.NULL)

    fun intakeFromJson(o: JSONObject) = Intake(
        id = o.getString("id"), name = o.getString("name"), caffeineMg = o.getDouble("caffeineMg"),
        timestamp = o.getLong("timestamp"), durationMinutes = o.getInt("durationMinutes"),
        category = o.getString("category"), note = o.optString("note", ""),
        sourceKey = if (o.isNull("sourceKey")) null else o.getString("sourceKey"),
        servings = o.optDouble("servings", 1.0), servingUnit = o.optString("servingUnit", ""),
        caffeinePerServingMg = o.optDouble("caffeinePerServingMg", o.getDouble("caffeineMg")),
        absorptionMinutes90 = o.optDouble("absorptionMinutes90", 45.0),
        imageKey = if(o.isNull("imageKey")) null else o.getString("imageKey")
    ).also(::validIntake)

    fun drinkJson(d: Drink) = JSONObject().put("id", d.id).put("name", d.name)
        .put("caffeineMg", d.caffeineMg).put("portion", d.portion)
        .put("category", d.category).put("favorite", d.favorite)
        .put("favoriteOrder", d.favoriteOrder).put("volumeMl", d.volumeMl ?: JSONObject.NULL)
        .put("imageKey", d.imageKey ?: JSONObject.NULL)

    fun drinkFromJson(o: JSONObject) = Drink(o.getString("id"), o.getString("name"),
        o.getDouble("caffeineMg"), o.getString("portion"), o.getString("category"),
        o.getBoolean("favorite"), imageKey = if(o.isNull("imageKey")) null else o.getString("imageKey"),
        favoriteOrder = o.optInt("favoriteOrder", 0), volumeMl = if(o.isNull("volumeMl")) null else o.getDouble("volumeMl")).also(::validDrink)

    fun settingsJson(s: TrackerSettings) = JSONObject().put("halfLifeHours", s.halfLifeHours)
        .put("bedtime", s.bedtime).put("bedtimeThresholdMg", s.bedtimeThresholdMg)
        .put("dailyGoalMg", s.dailyGoalMg).put("themeMode", s.themeMode)
        .put("accentMode", s.accentMode).put("extraDark", s.extraDark)
        .put("language", s.language)

    fun settingsFromJson(o: JSONObject): TrackerSettings {
        val s = TrackerSettings(o.getDouble("halfLifeHours"), o.getString("bedtime"),
            o.getDouble("bedtimeThresholdMg"), o.getDouble("dailyGoalMg"), o.getString("themeMode"),
            o.getString("accentMode"), o.getBoolean("extraDark"),
            o.optString("language", "SV").ifBlank { "SV" })
        require(s.halfLifeHours.isFinite() && s.halfLifeHours in 1.0..24.0) { "Halveringstid måste vara 1–24 timmar." }
        LocalTime.parse(s.bedtime)
        require(s.bedtimeThresholdMg.isFinite() && s.bedtimeThresholdMg in 1.0..1000.0) { "Ogiltig tröskel." }
        require(s.dailyGoalMg.isFinite() && s.dailyGoalMg in 1.0..10_000.0) { "Ogiltigt dagsmål." }
        require(s.themeMode in listOf("SYSTEM", "DARK", "LIGHT")) { "Ogiltigt tema." }
        require(s.accentMode in listOf("AUTO","RED","ORANGE","AMBER","GREEN","TEAL","BLUE","INDIGO","PINK")) { "Ogiltig accent." }
        require(s.language in listOf("SV", "EN")) { "Ogiltigt språk." }
        return s
    }
}
