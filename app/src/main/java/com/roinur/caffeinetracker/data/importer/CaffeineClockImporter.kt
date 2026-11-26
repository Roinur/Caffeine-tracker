package com.roinur.caffeinetracker.data.importer

import com.roinur.caffeinetracker.core.model.Intake
import com.roinur.caffeinetracker.core.model.Drink
import com.roinur.caffeinetracker.core.model.TrackerSnapshot
import com.roinur.caffeinetracker.data.backup.BackupCodec
import com.roinur.caffeinetracker.data.backup.drive.CaffeineCloudBackup
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class ImportPreview(val entries: List<Intake>, val backup: TrackerSnapshot? = null, val source: String, val drinks: List<Drink> = emptyList()) {
    val isRestore get() = backup != null
}

object CaffeineClockImporter {
    fun preview(text: String): ImportPreview {
        require(text.toByteArray().size <= BackupCodec.MAX_BYTES) { "Filen är för stor (max 20 MB)." }
        val clean = text.removePrefix("\uFEFF").trim()
        require(clean.isNotEmpty()) { "Filen är tom." }
        if (clean.startsWith("{") || clean.startsWith("[")) {
            val root = JSONTokener(clean).nextValue()
            if (root is JSONObject && root.optString("format") == BackupCodec.FORMAT) {
                val snapshot = BackupCodec.decode(clean)
                return ImportPreview(snapshot.intakes, snapshot, "Caffeine Tracker-backup")
            }
            if (root is JSONObject && root.optString("format") == CaffeineCloudBackup.FORMAT) {
                val snapshot = CaffeineCloudBackup.decode(clean).snapshot
                return ImportPreview(snapshot.intakes, snapshot, "Caffeine Tracker Drive-backup")
            }
            val standaloneDrinks = if (root is JSONObject) {
                listOf("custom_items", "custom_drinks", "customDrinks", "customItems", "drinks")
                    .flatMap { key ->
                        val arr = root.optJSONArray(key) ?: return@flatMap emptyList()
                        (0 until arr.length()).mapNotNull { idx -> arr.optJSONObject(idx) }
                    }
                    .let(::parseStandaloneCustomDrinks)
            } else emptyList()
            val rows = when (root) {
                is JSONArray -> root
                is JSONObject -> listOf("entries","history","consumptions","intakes","logs","data")
                    .firstNotNullOfOrNull { root.optJSONArray(it) }
                    ?: if (standaloneDrinks.isNotEmpty()) JSONArray()
                    else error("Okänt JSON-format. Hittade fält: " + root.keys().asSequence().joinToString())
                else -> error("JSON-filen saknar intag.")
            }
            return parseRows((0 until rows.length()).map { rows.getJSONObject(it) }, standaloneDrinks)
        }
        val rows = parseCsv(clean)
        require(rows.size > 1) { "CSV-filen saknar intag." }
        val headers = rows.first().map { it.trim().lowercase().replace(Regex("[ _-]"), "") }
        return parseRows(rows.drop(1).filter { row -> row.any { it.isNotBlank() } }.map { row ->
            require(row.size == headers.size) { "CSV-radens antal kolumner stämmer inte." }
            JSONObject().apply { headers.forEachIndexed { i, key -> put(key, row[i]) } }
        })
    }

    private fun parseStandaloneCustomDrinks(rows: List<JSONObject>): List<Drink> = rows.mapIndexed { index, row ->
        try {
            fun value(vararg names: String): Any? = names.firstNotNullOfOrNull { key ->
                row.opt(key)?.takeUnless { it == JSONObject.NULL || it.toString().isBlank() }
            }
            val name = value("name", "item_name", "drinkName", "itemName", "title")?.toString()?.trim()
                ?: error("dryckens namn saknas")
            val mg = number(value("caffeine_per_unit_mg", "caffeineMg", "caffeine_mg", "caffeine", "mg")
                ?: error("koffeinmängd saknas"))
            val unit = value("unit", "portion", "servingUnit", "serving_unit")?.toString()?.trim().orEmpty()
            val volume = value("volumeMl", "volume_ml")?.let(::number)
            val identity = "caffeine-clock:drink:" + name + "|" + unit + "|" + mg
            Drink(
                id = UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString(),
                name = name,
                caffeineMg = mg,
                portion = unit.ifBlank { "1 portion" },
                category = "Egen",
                favorite = false,
                volumeMl = volume
            ).also(BackupCodec::validDrink)
        } catch (e: Exception) {
            error("Egen dryck " + (index + 1) + ": " + (e.message ?: "ogiltiga data") + ". Inget har importerats.")
        }
    }

    private fun parseRows(rows: List<JSONObject>, standaloneDrinks: List<Drink> = emptyList()): ImportPreview {
        require(rows.isNotEmpty() || standaloneDrinks.isNotEmpty()) { "Exporten innehåller inga intag." }
        val occurrences = mutableMapOf<String, Int>()
        val entries = rows.mapIndexed { index, row ->
            try {
                fun value(vararg names: String): Any? = names.firstNotNullOfOrNull { key ->
                    row.opt(key)?.takeUnless { it == JSONObject.NULL || it.toString().isBlank() }
                }
                val nested = row.optJSONObject("drink") ?: row.optJSONObject("item")
                val name = value("item_name","name","drinkName","itemName","drinkname","itemname","drink","title")?.takeUnless { it is JSONObject }?.toString()
                    ?: nested?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: error("dryckens namn saknas")
                val mg = number(value("total_caffeine_mg","totalcaffeinemg","caffeineMg","caffeineAmount","totalCaffeine","caffeine","caffeinemg","caffeineamount","totalcaffeine","mg")
                    ?: error("koffeinmängd saknas"))
                val timestamp = time(value("consumed_at","timestamp","consumedAt","dateTime","datetime","date","time","consumedat","createdAt","createdat")
                    ?: error("tidpunkt saknas"))
                val duration = value("drank_over_time_minutes","drankovertimeminutes","durationMinutes","durationminutes","sippingDuration","sippingduration","duration")?.let(::number)?.toInt() ?: 0
                val originalId = value("id","uuid","entryId","entryid")?.toString()
                val identity = originalId ?: (name.trim() + "|" + timestamp + "|" + mg + "|" + duration)
                val occurrence = occurrences.getOrDefault(identity, 0)
                occurrences[identity] = occurrence + 1
                require(originalId == null || occurrence == 0) { "dubbelt ID i exporten" }
                val sourceKey = "caffeine-clock:" + identity + if (originalId == null) ":" + occurrence else ""
                val sourceType = value("source_type", "sourceType", "sourcetype")?.toString()?.trim()?.lowercase()
                val isCustom = sourceType == "custom"
                val servings = value("quantity","servings")?.let(::number) ?: 1.0
                val perServing = value("caffeine_per_unit_mg","caffeineperunitmg")?.let(::number)
                    ?: if (servings > 0.0) mg / servings else mg
                Intake(
                    id = UUID.nameUUIDFromBytes(sourceKey.toByteArray(Charsets.UTF_8)).toString(),
                    name = name, caffeineMg = mg, timestamp = timestamp, durationMinutes = duration,
                    category = value("category")?.toString() ?: if (isCustom) "Egen" else "Importerad",
                    note = value("note","notes")?.toString() ?: "", sourceKey = sourceKey,
                    servings = servings,
                    servingUnit = value("unit","servingunit")?.toString() ?: "",
                    caffeinePerServingMg = perServing,
                    absorptionMinutes90 = value("absorption_time_90_percent_minutes","absorptiontime90percentminutes")?.let(::number) ?: 45.0
                ).also(BackupCodec::validIntake)
            } catch (e: Exception) {
                error("Rad " + (index + 1) + ": " + (e.message ?: "ogiltiga data") + ". Inget har importerats.")
            }
        }
        val entryDrinks = entries.distinctBy { Triple(it.name, it.servingUnit, it.caffeinePerServingMg) }.map {
            val identity = "caffeine-clock:drink:" + it.name + "|" + it.servingUnit + "|" + it.caffeinePerServingMg
            Drink(UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString(),
                it.name, it.caffeinePerServingMg, it.servingUnit.ifBlank { "1 portion" }, it.category, false)
        }
        val drinks = (standaloneDrinks + entryDrinks).distinctBy { it.id }
        return ImportPreview(entries.sortedByDescending { it.timestamp }, source = "JSON/CSV", drinks = drinks)
    }

    private fun number(value: Any): Double = when (value) {
        is Number -> value.toDouble()
        else -> value.toString().trim().replace(",", ".").toDouble()
    }.also { require(it.isFinite()) { "ogiltigt tal" } }

    private fun time(value: Any): Long {
        if (value is Number || value.toString().matches(Regex("\\d+(\\.\\d+)?"))) {
            val number = number(value)
            return (if (number < 100_000_000_000) number * 1000 else number).toLong()
        }
        val text = value.toString().trim()
        return runCatching { Instant.parse(text).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(text.replace(" ", "T")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .getOrElse { error("tidpunkten saknar ett känt ISO-format") }
    }

    internal fun parseCsv(text: String): List<List<String>> {
        val firstLine = text.lineSequence().first()
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> { row.add(field.toString()); field.clear() }
                (c == '\n' || c == '\r') && !quoted -> {
                    row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "CSV-filen har ett oavslutat citattecken." }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
