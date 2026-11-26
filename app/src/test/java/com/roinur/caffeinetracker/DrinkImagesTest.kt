package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.data.backup.BackupCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DrinkImagesTest {
    @Test fun brandImagesTakePrecedenceOverGenericEnergyCategory() {
        assertEquals(DrinkImage.NOCCO, drinkImage("NOCCO Tropical", "Energidryck"))
        assertEquals(DrinkImage.CELSIUS, drinkImage("Celcius Peach", "Energidryck"))
        assertEquals(DrinkImage.CELSIUS, drinkImage("Celsius", "Övrigt"))
        assertEquals(DrinkImage.RED_BULL, drinkImage("Stor Redbull", "Energidryck"))
        assertEquals(DrinkImage.RED_BULL, drinkImage("Red Bull Sugarfree", "Energidryck"))
        assertEquals(DrinkImage.LATITUDE, drinkImage("Latitud 65 Frost", "Energidryck"))
        assertEquals(DrinkImage.LATITUDE, drinkImage("Latitude 65", "Övrigt"))
        assertEquals(DrinkImage.LATITUDE, drinkImage("Latitude älven", "Egen"))
        assertEquals(DrinkImage.MONSTER, drinkImage("Monster Energy Zero Sugar", "Energidryck"))
        assertEquals(DrinkImage.MONSTER, drinkImage("Monster Energy Pipeline Punch", "Importerad"))
        assertEquals(DrinkImage.MONSTER_ULTRA, drinkImage("Monster Energy Ultra", "Importerad"))
        assertEquals(DrinkImage.MONSTER_ULTRA, drinkImage("Monster Energy Ultra Rosa", "Importerad"))
        assertEquals(DrinkImage.POWERKING, drinkImage("Powerking", "Energidryck"))
        assertEquals(DrinkImage.ROCKSTAR, drinkImage("Rockstar Original", "Energidryck"))
        assertEquals(DrinkImage.REIGN, drinkImage("Reign Total Body Fuel", "Energidryck"))
        assertEquals(DrinkImage.PRIME, drinkImage("Prime Energy", "Energidryck"))
        assertEquals(DrinkImage.COCA_COLA, drinkImage("Coca-Cola Zero", "Importerad"))
        assertEquals(DrinkImage.PEPSI, drinkImage("Pepsi Max", "Importerad"))
        assertEquals(DrinkImage.LATTE, drinkImage("Caffè Latte", "Importerad"))
        assertEquals(DrinkImage.LATTE, drinkImage("Nescafé Dolce Gusto - Latte Macchiato", "Importerad"))
        assertEquals(DrinkImage.ICED_LATTE, drinkImage("Dunkin' Iced Latte", "Importerad"))
        assertEquals(DrinkImage.DRIP, drinkImage("Dunkin' Americano", "Importerad"))
        assertEquals(DrinkImage.ENERGY, drinkImage("Generic 5kr", "Egen"))
    }
    @Test fun explicitSelectionSurvivesNameChangesAndCanDisableAutomaticBrandImage() {
        assertEquals(DrinkImage.RED_BULL, drinkImage("Min stora favorit", "Övrigt", "RED_BULL"))
        assertEquals(DrinkImage.NOCCO, drinkImage("Red Bull", "Energidryck", "NOCCO"))
        assertEquals(DrinkImage.NONE, drinkImage("Red Bull", "Energidryck", "NONE"))
        assertEquals(DrinkImage.RED_BULL, drinkImage("Red Bull", "Energidryck", null))
    }
    @Test fun fullBackupPreservesSharedAssetKeysAndHistoricalDose() {
        val small = Drink("small", "Min favorit", 80.0, "250 ml", imageKey = "RED_BULL")
        val large = Drink("large", "Stor favorit", 160.0, "500 ml", imageKey = "RED_BULL")
        val intake = Intake("history", large.name, large.caffeineMg, 1761981300000L, servingUnit = large.portion, imageKey = large.imageKey)
        val snapshot = TrackerSnapshot(listOf(intake), listOf(small, large), TrackerSettings())
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
        assertEquals(160.0, BackupCodec.decode(BackupCodec.encode(snapshot)).intakes.single().caffeineMg, 0.0)
    }
    @Test fun oldBackupsDefaultToAutomaticImagesAndUnknownKeysAreRejected() {
        val snapshot = TrackerSnapshot(listOf(Intake(name = "Red Bull", caffeineMg = 80.0, timestamp = 1761981300000L)),
            listOf(Drink(name = "Red Bull", caffeineMg = 80.0, portion = "250 ml")), TrackerSettings())
        val old = JSONObject(BackupCodec.encode(snapshot))
        old.getJSONArray("intakes").getJSONObject(0).remove("imageKey")
        old.getJSONArray("drinks").getJSONObject(0).remove("imageKey")
        val decoded = BackupCodec.decode(old.toString())
        assertEquals(snapshot, decoded)
        assertNull(decoded.drinks.single().imageKey)
        old.getJSONArray("drinks").getJSONObject(0).put("imageKey", "../unknown.png")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(old.toString()) }
    }
    @Test fun customImageKeysAreValidatedAndPreservedInBackups() {
        assertTrue(validDrinkImageKey("custom:0123456789abcdef.png"))
        assertFalse(validDrinkImageKey("custom:../evil.png"))
        assertFalse(validDrinkImageKey("custom:sub/dir.png"))
        assertFalse(validDrinkImageKey("custom:short.png"))
        val drink = Drink("c1", "Special Brew", 120.0, "330 ml", imageKey = "custom:0123456789abcdef.png")
        val snapshot = TrackerSnapshot(emptyList(), listOf(drink), TrackerSettings())
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }
}
