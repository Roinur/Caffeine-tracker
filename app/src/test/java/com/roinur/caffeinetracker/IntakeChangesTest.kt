package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.data.backup.BackupCodec
import org.junit.Assert.*
import org.junit.Test

class IntakeChangesTest {
    private val imported = Intake("imported", "Drip coffee", 190.0, 1761981300000L,
        durationMinutes = 37, sourceKey = "clock:original", servings = 2.0, servingUnit = "cup",
        caffeinePerServingMg = 95.0, absorptionMinutes90 = 71.0, imageKey = "DRIP")
    @Test fun simpleEditPreservesHiddenImportedFields() {
        val edited = reviseIntake(imported, imported.name, imported.caffeineMg, imported.timestamp,
            imported.durationMinutes, imported.absorptionMinutes90, "new note", imported.category,
            imported.servingUnit, imported.imageKey)
        assertEquals(imported.copy(note = "new note"), edited)
    }
    @Test fun doseEditKeepsServingCountAndReconcilesPerServingDose() {
        val edited = reviseIntake(imported, imported.name, 200.0, imported.timestamp, 37, 71.0,
            imported.note, imported.category, imported.servingUnit, imported.imageKey)
        assertEquals(2.0, edited.servings, 0.0)
        assertEquals(100.0, edited.caffeinePerServingMg, 0.0)
        assertEquals(imported.sourceKey, edited.sourceKey)
    }
    @Test fun undoRestoresExactRowForEditAndDeleteAndRemovesAddedRow() {
        val edited = imported.copy(caffeineMg = 200.0)
        assertEquals(imported, undoTarget(IntakeChange(imported, edited), edited))
        assertEquals(imported, undoTarget(IntakeChange(imported, null), null))
        assertNull(undoTarget(IntakeChange(null, imported), imported))
    }
    @Test(expected = IllegalArgumentException::class) fun undoRefusesToOverwriteLaterEdits() {
        undoTarget(IntakeChange(null, imported), imported.copy(note = "later change"))
    }
    @Test fun structuredVolumeAndFavoriteOrderSurviveBackup() {
        val drink = Drink("large", "Red Bull stor", 160.0, "stor burk", "Energidryck",
            imageKey = "RED_BULL", favoriteOrder = 3, volumeMl = 500.0)
        assertEquals(500.0, referenceVolumeMl(drink)!!, 0.0)
        val snapshot = TrackerSnapshot(listOf(imported), listOf(drink), TrackerSettings())
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }
    @Test fun olderDrinkPayloadKeepsAutomaticOrderingAndLegacyVolume() {
        val payload = BackupCodec.drinkJson(Drink(name = "Energi", caffeineMg = 80.0, portion = "250 ml"))
        payload.remove("favoriteOrder"); payload.remove("volumeMl")
        val old = BackupCodec.drinkFromJson(payload)
        assertEquals(0, old.favoriteOrder)
        assertNull(old.volumeMl)
        assertEquals(250.0, referenceVolumeMl(old)!!, 0.0)
    }
}
