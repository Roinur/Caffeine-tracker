package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.data.backup.BackupCodec
import com.roinur.caffeinetracker.data.importer.CaffeineClockImporter as Importer
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.Instant

class BackupAndImportTest {
    private val timestamp = Instant.parse("2025-11-01T07:15:00Z").toEpochMilli()
    private val intake = Intake("saved-id", "Kaffe ☕ \"morgon\"", 114.5, timestamp, 20, "Kaffe", "Anteckning\nrad två", "caffeine-clock:123")
    private val snapshot = TrackerSnapshot(listOf(intake), starterDrinks, TrackerSettings(extraDark = true))

    @Test fun fullBackupRoundTripPreservesEveryFieldAndOriginalDates() {
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }
    @Test fun supportsIntentionalEmptyBackup() {
        val empty = TrackerSnapshot(emptyList(), emptyList(), TrackerSettings())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsFutureBackupVersion() {
        val json = JSONObject(BackupCodec.encode(snapshot)).put("version", 2)
        BackupCodec.decode(json.toString())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsRepeatedBackupIds() {
        BackupCodec.decode(BackupCodec.encode(snapshot.copy(intakes = listOf(intake, intake))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonfiniteDose() {
        BackupCodec.validIntake(intake.copy(caffeineMg = Double.NaN))
    }
    @Test fun nativeBackupIsPreviewedAsReplaceWhileClockIsMerge() {
        assertTrue(Importer.preview(BackupCodec.encode(snapshot)).isRestore)
        assertFalse(Importer.preview("""[{"id":"a","name":"Kaffe","caffeineMg":95,"timestamp":1761981300000}]""").isRestore)
    }
    @Test fun repeatedClockImportHasSameStableIdentity() {
        val text = """{"entries":[{"id":"external-a","name":"Café","caffeineMg":95,"timestamp":"2025-11-01T08:15:00+01:00"}]}"""
        val a = Importer.preview(text).entries.single()
        val b = Importer.preview(text).entries.single()
        assertEquals(a, b)
        assertEquals(timestamp, a.timestamp)
        assertEquals(95.0, a.caffeineMg, 0.0)
    }
    @Test fun preservesTwoLegitimateIdenticalIntakesWithoutSourceIds() {
        val row = """{"name":"Kaffe","caffeineMg":95,"timestamp":1761981300000}"""
        val parsed = Importer.preview("[" + row + "," + row + "]").entries
        assertEquals(2, parsed.size)
        assertNotEquals(parsed[0].id, parsed[1].id)
        assertEquals(parsed, Importer.preview("[" + row + "," + row + "]").entries)
    }
    @Test fun csvHandlesQuotedNamesNewlinesAndDecimalComma() {
        val csv = "name;caffeineMg;timestamp;notes\r\n\"Kaffe; stor\";95,5;2025-11-01T07:15:00Z;\"rad ett\nrad två\""
        val row = Importer.preview(csv).entries.single()
        assertEquals("Kaffe; stor", row.name)
        assertEquals(95.5, row.caffeineMg, 0.0)
        assertEquals("rad ett\nrad två", row.note)
        assertEquals(timestamp, row.timestamp)
    }
    @Test(expected = IllegalStateException::class) fun malformedRowStopsWholeImport() {
        Importer.preview("""[{"name":"Kaffe","caffeineMg":95,"timestamp":1761981300000},{"name":"Saknar tid","caffeineMg":95}]""")
    }
    @Test(expected = IllegalArgumentException::class) fun unclosedCsvQuoteIsRejected() {
        Importer.preview("name,caffeineMg,timestamp\n\"Kaffe,95,2025-11-01T07:15:00Z")
    }

    @Test fun actualClock293SchemaPreservesDosePortionsAndAbsorption() {
        val json = """[{"id":"fixture-id","consumed_at":"2025-11-01T14:00:00.000Z","source_type":"catalog","item_id":"instant-coffee","item_name":"Instant Coffee","unit":"teaspoon","quantity":2,"caffeine_per_unit_mg":57,"total_caffeine_mg":114,"drank_over_time_minutes":60,"absorption_time_90_percent_minutes":45,"notes":null}]"""
        val preview = Importer.preview(json)
        val row = preview.entries.single()
        assertEquals(114.0, row.caffeineMg, 0.0)
        assertEquals(2.0, row.servings, 0.0)
        assertEquals(57.0, row.caffeinePerServingMg, 0.0)
        assertEquals("teaspoon", row.servingUnit)
        assertEquals(60, row.durationMinutes)
        assertEquals(45.0, row.absorptionMinutes90, 0.0)
        assertEquals(Instant.parse("2025-11-01T14:00:00Z").toEpochMilli(), row.timestamp)
        assertEquals(1, preview.drinks.size)
        assertEquals(57.0, preview.drinks.single().caffeineMg, 0.0)
        val restored = BackupCodec.decode(BackupCodec.encode(TrackerSnapshot(preview.entries, preview.drinks, TrackerSettings())))
        assertEquals(row, restored.intakes.single())
    }
    @Test fun actualClockCsvUsesTotalDoseAndKeepsServingDoseSeparate() {
        val csv = "id,consumed_at,item_name,unit,quantity,caffeine_per_unit_mg,total_caffeine_mg,drank_over_time_minutes,absorption_time_90_percent_minutes\nfixture-id,2025-11-01T14:00:00Z,Instant Coffee,teaspoon,2,57,114,60,45"
        val row = Importer.preview(csv).entries.single()
        assertEquals(114.0, row.caffeineMg, 0.0)
        assertEquals(57.0, row.caffeinePerServingMg, 0.0)
        assertEquals(60, row.durationMinutes)
    }
    @Test fun customDrinksInImportJsonAreTaggedCustomAndStandaloneCustomDrinksAreImported() {
        val json = """{
            "entries": [
                {"id":"c1","consumed_at":"2025-11-01T14:00:00.000Z","source_type":"custom","item_name":"Latitude älven","unit":"can","quantity":1,"caffeine_per_unit_mg":180,"total_caffeine_mg":180},
                {"id":"cat1","consumed_at":"2025-11-01T15:00:00.000Z","source_type":"catalog","item_name":"Monster Energy Ultra","unit":"can","quantity":1,"caffeine_per_unit_mg":150,"total_caffeine_mg":150}
            ],
            "custom_drinks": [
                {"name":"My Pre-Workout","unit":"scoop","caffeine_per_unit_mg":220}
            ]
        }"""
        val preview = Importer.preview(json)
        assertEquals(2, preview.entries.size)
        val customEntry = preview.entries.single { it.name == "Latitude älven" }
        assertEquals("Egen", customEntry.category)
        val catalogEntry = preview.entries.single { it.name == "Monster Energy Ultra" }
        assertEquals("Importerad", catalogEntry.category)
        assertEquals(3, preview.drinks.size)
        assertEquals("Egen", preview.drinks.single { it.name == "Latitude älven" }.category)
        assertEquals("Egen", preview.drinks.single { it.name == "My Pre-Workout" }.category)
        assertEquals(220.0, preview.drinks.single { it.name == "My Pre-Workout" }.caffeineMg, 0.0)
    }
    @Test fun standaloneRecipeKeepsVolumeWhenAlsoInHistory() {
        val json = """{
            "custom_drinks":[{"name":"Test drink","unit":"bottle","caffeine_per_unit_mg":80,"volumeMl":250}],
            "entries":[{"item_name":"Test drink","unit":"bottle","total_caffeine_mg":80,"timestamp":1761981300000}]
        }"""
        val preview = Importer.preview(json)
        assertEquals(1, preview.entries.size)
        assertEquals(250.0, requireNotNull(preview.drinks.single().volumeMl), 0.0)
    }
    @Test fun downloadedDriveBackupCanBeRestoredAsAFile() {
        val original = TrackerSnapshot(
            listOf(Intake(name = "Coffee", caffeineMg = 80.0, timestamp = 1761981300000)),
            emptyList(), TrackerSettings()
        )
        val text = com.roinur.caffeinetracker.data.backup.drive.CaffeineCloudBackup.encode(original)
        assertEquals(original, Importer.preview(text).backup)
    }
}
