package com.roinur.caffeinetracker

import android.test.AndroidTestCase
import com.roinur.caffeinetracker.data.database.CaffeineDatabase
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.data.backup.*
import android.content.ContextWrapper
import java.io.File

/** Uses a separate test database and backup directory; never opens the user's tracker DB. */
class CaffeineStorageDeviceTest : AndroidTestCase() {
    private lateinit var database: CaffeineDatabase
    private val dbName = "qa_caffeine_storage.db"

    override fun setUp() {
        super.setUp()
        context.deleteDatabase(dbName)
        database = CaffeineDatabase(context, dbName)
    }
    override fun tearDown() {
        database.close()
        context.deleteDatabase(dbName)
        super.tearDown()
    }
    fun testReopenEditAndSourceDedupePreserveHistoricalDates() {
        val row = Intake("qa-history", "Kaffe", 114.0, 1761981300000L, sourceKey = "caffeine-clock:qa")
        assertEquals(1, database.mergeIntakes(listOf(row)))
        assertEquals(0, database.mergeIntakes(listOf(row)))
        database.close()
        database = CaffeineDatabase(context, dbName)
        assertEquals(row, database.snapshot().intakes.single())
        val edited = row.copy(caffeineMg = 120.0, note = "Ändrad")
        database.saveIntake(edited)
        assertEquals(edited, database.snapshot().intakes.single())
        assertEquals(0, database.mergeIntakes(listOf(row)))
        assertEquals(edited, database.snapshot().intakes.single())
    }
    fun testRestoreReplacesAtomicallyAndRejectsInvalidData() {
        val row = Intake(name = "Historik", caffeineMg = 80.0, timestamp = 1761981300000L)
        database.saveIntake(row)
        val before = database.snapshot()
        try {
            database.replaceSnapshot(before.copy(intakes = listOf(row.copy(caffeineMg = -5.0))))
            fail("Invalid restore accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(before, database.snapshot())
        val restored = before.copy(intakes = listOf(row.copy(caffeineMg = 200.0)))
        database.replaceSnapshot(restored)
        assertEquals(restored, database.snapshot())
    }
    fun testDrinkChangesNeverRewriteIntakeDose() {
        val drink = database.snapshot().drinks.first()
        val row = Intake(name = drink.name, caffeineMg = drink.caffeineMg, timestamp = 1761981300000L)
        database.saveIntake(row)
        database.saveDrink(drink.copy(caffeineMg = 999.0))
        database.deleteDrink(drink.id)
        assertEquals(row, database.snapshot().intakes.single())
    }
    fun testLocalRotationAndPreRestoreSnapshotAreReadable() {
        val folder = File(context.cacheDir, "qa-caffeine-backup").apply { mkdirs() }
        val wrapped = object : ContextWrapper(context) { override fun getFilesDir(): File = folder; override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(name + "_qa", mode) }
        val backup = CaffeineBackupStore(wrapped)
        // Test wrapper needs its own preferences, independent of the production backup location.
        val oldFolder = backup.folder
        backup.folder = null
        try {
            repeat(8) { index ->
                database.saveIntake(Intake("row-" + index, "Test", index.toDouble(), 1761981300000L + index))
                backup.checkpoint(database.snapshot())
            }
            assertEquals(5, backup.available().size)
            val current = backup.available().first { it.name == "caffeine_backup.json" }
            assertEquals(8, BackupCodec.decode(current.readText()).intakes.size)
            backup.preserveBeforeRestore(database.snapshot())
            val safety = backup.available().first { it.name == "before_restore.json" }
            assertEquals(database.snapshot(), BackupCodec.decode(safety.readText()))
        } finally {
            backup.folder = oldFolder
            folder.deleteRecursively()
        }
    }
    fun testSharedImagePersistsAcrossReopenAndRecipeDeletion() {
        val small = Drink("qa-small", "Liten favorit", 80.0, "250 ml", imageKey = "RED_BULL")
        val large = Drink("qa-large", "Stor favorit", 160.0, "500 ml", imageKey = "RED_BULL")
        database.saveDrink(small)
        database.saveDrink(large)
        val intake = Intake("qa-image-intake", large.name, large.caffeineMg, 1761981300000L,
            servingUnit = large.portion, imageKey = large.imageKey)
        database.saveIntake(intake)
        database.close()
        database = CaffeineDatabase(context, dbName)
        assertEquals(large, database.snapshot().drinks.single { it.id == large.id })
        database.saveDrink(small.copy(imageKey = "NOCCO", caffeineMg = 90.0))
        database.deleteDrink(large.id)
        assertEquals(intake, database.snapshot().intakes.single())
        assertEquals("RED_BULL", database.snapshot().intakes.single().imageKey)
    }

    fun testUndoAndFavoriteOrderPersistWithStructuredVolume() {
        val original = Intake("qa-undo", "Imported", 190.0, 1761981300000L, 35,
            sourceKey = "qa-source", servings = 2.0, servingUnit = "cup", caffeinePerServingMg = 95.0,
            absorptionMinutes90 = 73.0, imageKey = "DRIP")
        database.saveIntake(original)
        val edited = original.copy(note = "Changed")
        database.saveIntake(edited)
        val restored = undoTarget(IntakeChange(original, edited), database.snapshot().intakes.single())
        database.saveIntake(requireNotNull(restored))
        val a = Drink("qa-order-a", "A", 80.0, "can", favoriteOrder = 2, volumeMl = 250.0)
        val b = Drink("qa-order-b", "B", 160.0, "large can", favoriteOrder = 1, volumeMl = 500.0)
        database.saveDrinks(reorderedFavorites(listOf(a,b),listOf(b.id,a.id)))
        database.close(); database = CaffeineDatabase(context, dbName)
        assertEquals(original, database.snapshot().intakes.single())
        assertEquals(listOf("qa-order-b","qa-order-a"), database.snapshot().drinks.filter { it.id.startsWith("qa-order") }.map { it.id })
        assertEquals(500.0, database.snapshot().drinks.single { it.id == b.id }.volumeMl!!, 0.0)
        val snapshot = database.snapshot()
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }

}
