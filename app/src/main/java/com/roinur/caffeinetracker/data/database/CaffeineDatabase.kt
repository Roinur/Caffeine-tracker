package com.roinur.caffeinetracker.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.data.backup.BackupCodec
import org.json.JSONObject

/**
 * Sauce Tracker's SQLiteOpenHelper/DAO transaction structure, reduced to the
 * caffeine domain. No reader, source, profile or legacy database is opened.
 */
class CaffeineDatabase(context: Context, name: String = "caffeine_tracker.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE intakes (id TEXT PRIMARY KEY NOT NULL, timestamp INTEGER NOT NULL, source_key TEXT UNIQUE, payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX intakes_time ON intakes(timestamp DESC)")
        db.execSQL("CREATE TABLE drinks (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE preferences (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)")
        starterDrinks.forEach { putDrink(db, it) }
        putSettings(db, TrackerSettings())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Ingen migration definierad från version " + oldVersion + " till " + newVersion)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            recategorizeImportedRows(db)
        }
    }

    private fun recategorizeImportedRows(db: SQLiteDatabase) {
        val where = "payload LIKE '%\"Importerad\"%' OR payload LIKE '%\"Imported\"%'"
        val hasImported = db.rawQuery(
            "SELECT 1 FROM drinks WHERE $where UNION ALL SELECT 1 FROM intakes WHERE $where LIMIT 1",
            null
        ).use { it.moveToFirst() }
        if (!hasImported) return

        db.beginTransaction()
        try {
            db.rawQuery("SELECT id, payload FROM drinks WHERE $where", null).use { c ->
                while (c.moveToNext()) {
                    val drink = BackupCodec.drinkFromJson(JSONObject(c.getString(1)))
                    if (searchKey(drink.category) in setOf("importerad", "imported")) {
                        putDrink(db, drink.copy(category = inferDrinkCategory(drink.name)))
                    }
                }
            }
            db.rawQuery("SELECT id, payload FROM intakes WHERE $where", null).use { c ->
                while (c.moveToNext()) {
                    val intake = BackupCodec.intakeFromJson(JSONObject(c.getString(1)))
                    if (searchKey(intake.category) in setOf("importerad", "imported")) {
                        val updated = intake.copy(category = inferDrinkCategory(intake.name))
                        db.update("intakes", intakeValues(updated), "id=?", arrayOf(updated.id))
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun snapshot(): TrackerSnapshot {
        val db = readableDatabase
        val intakePayloads: List<String>
        val drinkPayloads: List<String>
        val settingsPayload: String?
        db.beginTransactionNonExclusive()
        try {
            intakePayloads = db.rawQuery("SELECT payload FROM intakes ORDER BY timestamp DESC, id", null).use { c ->
                buildList(c.count.coerceAtLeast(0)) { while (c.moveToNext()) add(c.getString(0)) }
            }
            drinkPayloads = db.rawQuery("SELECT payload FROM drinks ORDER BY id", null).use { c ->
                buildList(c.count.coerceAtLeast(0)) { while (c.moveToNext()) add(c.getString(0)) }
            }
            settingsPayload = db.rawQuery("SELECT payload FROM preferences WHERE id=1", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        val intakes = intakePayloads.map { BackupCodec.intakeFromJson(JSONObject(it)) }
        val drinks = drinkPayloads.map { BackupCodec.drinkFromJson(JSONObject(it)) }
            .sortedWith(compareByDescending<Drink> { it.favorite }.thenBy { it.favoriteOrder }.thenBy { it.name.lowercase() }.thenBy { it.id })
        val settings = settingsPayload?.let { BackupCodec.settingsFromJson(JSONObject(it)) } ?: TrackerSettings()
        return TrackerSnapshot(intakes, drinks, settings)
    }

    fun findIntake(id: String): Intake? = readableDatabase
        .rawQuery("SELECT payload FROM intakes WHERE id=?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) BackupCodec.intakeFromJson(JSONObject(c.getString(0))) else null
        }

    fun findDrink(id: String): Drink? = readableDatabase
        .rawQuery("SELECT payload FROM drinks WHERE id=?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) BackupCodec.drinkFromJson(JSONObject(c.getString(0))) else null
        }

    fun findSettings(): TrackerSettings = readableDatabase
        .rawQuery("SELECT payload FROM preferences WHERE id=1", null).use { c ->
            if (c.moveToFirst()) BackupCodec.settingsFromJson(JSONObject(c.getString(0))) else TrackerSettings()
        }

    fun saveIntake(i: Intake) {
        BackupCodec.validIntake(i)
        val db = writableDatabase
        val values = intakeValues(i)
        if (db.update("intakes", values, "id=?", arrayOf(i.id)) == 0) db.insertOrThrow("intakes", null, values)
    }

    fun deleteIntake(id: String) { writableDatabase.delete("intakes", "id=?", arrayOf(id)) }
    fun saveDrink(d: Drink) { BackupCodec.validDrink(d); putDrink(writableDatabase, d) }
    fun saveDrinks(drinks: List<Drink>) {
        drinks.forEach(BackupCodec::validDrink)
        val db = writableDatabase
        db.beginTransaction()
        try { drinks.forEach { putDrink(db, it) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }

    fun deleteDrink(id: String) { writableDatabase.delete("drinks", "id=?", arrayOf(id)) }
    fun saveSettings(s: TrackerSettings) {
        BackupCodec.settingsFromJson(BackupCodec.settingsJson(s))
        putSettings(writableDatabase, s)
    }

    fun mergeIntakes(rows: List<Intake>, importedDrinks: List<Drink> = emptyList()): Int {
        importedDrinks.forEach(BackupCodec::validDrink)
        rows.forEach(BackupCodec::validIntake)
        val db = writableDatabase
        db.beginTransaction()
        return try {
            var added = 0
            rows.forEach { row ->
                // Only a known import identity can be ignored. Other constraint failures abort.
                val exists = db.rawQuery("SELECT 1 FROM intakes WHERE id=? OR (source_key IS NOT NULL AND source_key=?)",
                    arrayOf(row.id, row.sourceKey ?: "")).use { it.moveToFirst() }
                if (!exists) { db.insertOrThrow("intakes", null, intakeValues(row)); added++ }
            }
            importedDrinks.forEach { drink ->
                val existingPayload = db.rawQuery("SELECT payload FROM drinks WHERE id=?", arrayOf(drink.id)).use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                if (existingPayload == null) {
                    putDrink(db, drink)
                } else if (isCustomCategory(drink.category)) {
                    val existing = BackupCodec.drinkFromJson(JSONObject(existingPayload))
                    if (existing.category == "Importerad") {
                        putDrink(db, existing.copy(category = drink.category))
                    }
                }
            }
            db.setTransactionSuccessful()
            added
        } finally { db.endTransaction() }
    }

    fun replaceSnapshot(snapshot: TrackerSnapshot) {
        // Validate everything before entering the transaction, including empty but intentional backups.
        val checked = BackupCodec.decode(BackupCodec.encode(snapshot))
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("intakes", null, null)
            db.delete("drinks", null, null)
            checked.intakes.forEach { db.insertOrThrow("intakes", null, intakeValues(it)) }
            checked.drinks.forEach { putDrink(db, it) }
            putSettings(db, checked.settings)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun intakeValues(i: Intake) = ContentValues().apply {
        put("id", i.id); put("timestamp", i.timestamp); put("source_key", i.sourceKey)
        put("payload", BackupCodec.intakeJson(i).toString())
    }
    private fun putDrink(db: SQLiteDatabase, d: Drink) {
        val values = ContentValues().apply { put("id", d.id); put("payload", BackupCodec.drinkJson(d).toString()) }
        if (db.update("drinks", values, "id=?", arrayOf(d.id)) == 0) db.insertOrThrow("drinks", null, values)
    }
    private fun putSettings(db: SQLiteDatabase, s: TrackerSettings) {
        val values = ContentValues().apply { put("id", 1); put("payload", BackupCodec.settingsJson(s).toString()) }
        if (db.update("preferences", values, "id=1", null) == 0) db.insertOrThrow("preferences", null, values)
    }
}
