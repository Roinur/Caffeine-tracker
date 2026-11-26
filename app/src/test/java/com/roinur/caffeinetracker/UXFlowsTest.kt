package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.feature.statistics.*
import com.roinur.caffeinetracker.data.backup.BackupCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UXFlowsTest {
    private fun choice(vararg drinks: Drink) = drinkChoices(drinks.toList()).single()
    @Test fun arbitraryVolumesUseTheStoredConcentrationAndPreserveDecimals() {
        val c = choice(Drink("a", "Energidryck", 80.0, "250 ml"))
        assertEquals(88.0, customVolumePortion(c, "275")!!.caffeineMg, 1e-9)
        assertEquals("275 ml", customVolumePortion(c, "27,5 cl")!!.label)
        assertEquals(88.08, customVolumePortion(c, "275,25 ml")!!.caffeineMg, 1e-9)
        assertEquals("275,25 ml", customVolumePortion(c, "275,25 ml")!!.label)
        assertEquals(160.0, customVolumePortion(c, "0.5 l")!!.caffeineMg, 0.0)
    }
    @Test fun savedRecipeAndClockUnitsRemainAuthoritative() {
        val c = choice(Drink("a", "Kaffe", 100.0, "200 ml"), Drink("b", "Kaffe", 180.0, "275 ml"))
        assertEquals("saved:b", customVolumePortion(c, "275")!!.key)
        assertEquals(180.0, customVolumePortion(c, "275")!!.caffeineMg, 0.0)
        val clock = choice(Drink("a", "Drip coffee", 95.0, "cup"))
        assertEquals(95.0, customVolumePortion(clock, "240")!!.caffeineMg, 0.0)
        assertEquals(110.0, customVolumePortion(clock, "275")!!.caffeineMg, 1e-9)
    }
    @Test fun theSelectedRecipeControlsNewVolumesWhenVariantsHaveDifferentStrengths() {
        val weak=Drink("weak","Kaffe",100.0,"250 ml")
        val strong=Drink("strong","Kaffe",180.0,"300 ml")
        val c=choice(weak,strong)
        assertEquals(165.0,customVolumePortion(c,"275",strong)!!.caffeineMg,1e-9)
        assertEquals(110.0,customVolumePortion(c,"275",weak)!!.caffeineMg,1e-9)
        assertEquals(120.0,portionChoices(c,strong).single { it.label=="200 ml" }.caffeineMg,1e-9)
    }
    @Test fun unknownVolumesAndInvalidAmountsNeverInventAConcentration() {
        val unknown = choice(Drink("a", "Eget kaffe", 90.0, "min kopp"))
        assertNull(customVolumePortion(unknown, "275"))
        val c = choice(Drink("a", "Koncentrat", 10000.0, "1 ml"))
        for(q in listOf("0", "-1", "NaN", "Infinity", "20001", "275 mg", "2 koppar", "3mlgarbage"))
            assertNull(customVolumePortion(c, q))
        assertNull(customVolumePortion(c, "2"))
    }
    @Test fun aliasAndAccentSearchMatchesInHomeLibraryAndHistory() {
        val d = Drink("a", "Bryggkaffe", 95.0, "kopp")
        for(q in listOf("drip coffee", "BRYGGKAFFE", "filter coffee")) {
            assertEquals(d.name, searchDrinks(drinkChoices(listOf(d)),q).single().name)
            assertEquals(d, searchDrinkRows(listOf(d),q).single())
            assertTrue(matchesDrinkSearch(d.name,d.category,q))
        }
        assertTrue(matchesDrinkSearch("Eget te", "Te", "mork", "Mörk kopp"))
        assertTrue(matchesDrinkSearch("Grönt te", "Te", "green tea"))
        assertFalse(matchesDrinkSearch("Bryggkaffe", "Kaffe", "energy"))
        assertTrue(matchesDrinkSearch("Red Bull Sugarfree","Energidryck","redbull"))
        assertTrue(matchesDrinkSearch("Celsius Peach","Energidryck","celcius"))
    }
    @Test fun exactNamesRankBeforeFavoritesAndEmptySearchCanUseRecentNames() {
        val a=Drink("a","Coffee",95.0,"kopp",favorite=false)
        val b=Drink("b","Coffee dark",120.0,"kopp",favorite=true)
        assertEquals(listOf("a","b"),searchDrinkRows(listOf(b,a),"coffee").map { it.id })
        assertEquals("Coffee",searchDrinks(drinkChoices(listOf(b,a)),"",listOf("Coffee")).first().name)
        assertEquals("Coffee dark",searchDrinks(drinkChoices(listOf(b,a)),"").first().name)
    }
    @Test fun reorderingChangesOnlyOrderAndRejectsMissingDuplicateOrForeignIds() {
        val a=Drink("a","NOCCO",180.0,"330 ml",imageKey="NOCCO",volumeMl=330.0)
        val b=Drink("b","Espresso",63.0,"1 shot",imageKey="ESPRESSO")
        val n=Drink("n","Not favorite",1.0,"1 ml",favorite=false)
        val result=reorderedFavorites(listOf(a,n,b),listOf("b","a"))
        assertEquals(listOf(b.copy(favoriteOrder=0),a.copy(favoriteOrder=1)),result)
        for(ids in listOf(listOf("a"),listOf("a","a"),listOf("a","n")))
            assertThrows(IllegalArgumentException::class.java) { reorderedFavorites(listOf(a,n,b),ids) }
        assertEquals(emptyList<Drink>(),reorderedFavorites(listOf(n),emptyList()))
        val snapshot=TrackerSnapshot(emptyList(),result,TrackerSettings())
        assertEquals(snapshot,BackupCodec.decode(BackupCodec.encode(snapshot)))
    }
    @Test fun historyDateWindowsAreInclusiveAcrossDaylightSavingChanges() {
        val zone=ZoneId.of("Europe/Stockholm")
        val day=LocalDate.of(2026,10,25)
        val range=IntakeDateRange(day)
        assertTrue(range.contains(day.atStartOfDay(zone).toInstant().toEpochMilli(),zone))
        assertTrue(range.contains(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1,zone))
        assertFalse(range.contains(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),zone))
        assertThrows(IllegalArgumentException::class.java) { IntakeDateRange(day,day.minusDays(1)) }
    }
    @Test fun monthlyDrillDownClampsToTheDisplayedPartialPeriod() {
        val period=CaffeinePeriod(LocalDate.of(2026,9,12),LocalDate.of(2026,10,21))
        assertEquals(IntakeDateRange(period.start,LocalDate.of(2026,9,30)),chartSelectionRange(LocalDate.of(2026,9,1),true,period))
        assertEquals(IntakeDateRange(LocalDate.of(2026,10,1),period.end),chartSelectionRange(LocalDate.of(2026,10,1),true,period))
        assertEquals(IntakeDateRange(period.end),chartSelectionRange(period.end,false,period))
    }
    @Test fun newImagesAreReusableAndSurviveBackupRoundTrip() {
        val images=mapOf("Espresso" to DrinkImage.ESPRESSO,"Black tea" to DrinkImage.BLACK_TEA,"Grönt te" to DrinkImage.GREEN_TEA)
        images.forEach { (name,image) ->
            assertEquals(image,drinkImage(name,"Övrigt"))
            assertEquals(image,drinkImage("Egen favorit","Övrigt",image.name))
            val d=Drink(name=name,caffeineMg=40.0,portion="kopp",imageKey=image.name)
            val s=TrackerSnapshot(emptyList(),listOf(d),TrackerSettings())
            assertEquals(s,BackupCodec.decode(BackupCodec.encode(s)))
        }
    }
    @Test fun closeSavedVolumesDoNotCrashCustomVolumeSearch() {
        val a = Drink("a", "Kaffe", 100.0, "250,01 ml", volumeMl = 250.01)
        val b = Drink("b", "Kaffe", 100.0, "250,02 ml", volumeMl = 250.02)
        val c = choice(a, b)
        val matched = customVolumePortion(c, "250,02")
        assertNotNull(matched)
        assertEquals("saved:b", matched!!.key)
        assertEquals(100.0, matched.caffeineMg, 0.0)
    }
    @Test fun csvImportComputesPerServingCaffeineWhenOnlyTotalIsProvided() {
        val csv = "item_name,total_caffeine_mg,consumed_at,quantity,unit\nKaffe,120,2026-10-20T08:00:00Z,2,kopp"
        val preview = com.roinur.caffeinetracker.data.importer.CaffeineClockImporter.preview(csv)
        assertEquals(120.0, preview.entries.single().caffeineMg, 0.0)
        assertEquals(60.0, preview.entries.single().caffeinePerServingMg, 0.0)
        assertEquals(60.0, preview.drinks.single().caffeineMg, 0.0)
        assertFalse(preview.source.contains("Caffeine Clock", ignoreCase = true))
    }
    @Test fun englishDrinkNamesMatchImportedHistoryAndMergeInChoicesAndStats() {
        assertEquals("Instant Coffee", displayDrinkName("Snabbkaffe", AppLanguage.EN))
        assertEquals("Drip coffee", displayDrinkName("Filterkaffe", AppLanguage.EN))
        assertEquals("Generic energy drink", displayDrinkName("Energidryck", AppLanguage.EN))
        assertEquals("Black tea", displayDrinkName("Svart te", AppLanguage.EN))
        assertEquals("Green tea", displayDrinkName("Grönt te", AppLanguage.EN))
        assertEquals("Celsius Sparkling Orange", displayDrinkName("Celsius Sparkling Orange", AppLanguage.EN))
        assertEquals("2 teaspoons", displayPortionText("2 tesked", AppLanguage.EN))
        assertEquals("1 cup", displayPortionText("1 kopp", AppLanguage.EN))

        val drinks = listOf(
            Drink("instant", "Snabbkaffe", 114.0, "2 tesked", "Kaffe", favorite = true),
            Drink("imp-instant", "Instant Coffee", 57.0, "teaspoon", "Importerad", favorite = false),
            Drink("filter", "Filterkaffe", 95.0, "1 kopp", "Kaffe", favorite = true),
            Drink("imp-drip", "Drip coffee", 95.0, "cup", "Importerad", favorite = false)
        )
        val enChoices = drinkChoices(drinks, AppLanguage.EN)
        assertEquals(2, enChoices.size)
        assertEquals(setOf("Instant Coffee", "Drip coffee"), enChoices.map { it.name }.toSet())
        assertEquals(2, enChoices.single { it.name == "Instant Coffee" }.variants.size)
        assertEquals(2, enChoices.single { it.name == "Drip coffee" }.variants.size)
        // Both Filterkaffe/1 kopp and Drip coffee/cup collapse to "1 cup (240 ml)" at 95 mg in EN;
        // selecting either variant as reference must keep its own saved:<id> entry.
        val dripChoice = enChoices.single { it.name == "Drip coffee" }
        assertTrue(portionChoices(dripChoice, drinks[2], AppLanguage.EN).any { it.key == "saved:filter" })
        assertTrue(portionChoices(dripChoice, drinks[3], AppLanguage.EN).any { it.key == "saved:imp-drip" })
        // Searching for the visible English name "Generic energy drink" must find the Swedish starter too.
        val energy = Drink("energy", "Energidryck", 80.0, "250 ml · 32 mg/100 ml", "Energidryck")
        assertEquals(listOf(energy), searchDrinkRows(listOf(energy), "Generic energy drink", AppLanguage.EN))

        val zone = ZoneId.of("Europe/Stockholm")
        val t = LocalDate.of(2026, 10, 20).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val mixedIntakes = listOf(
            Intake("1", "Instant Coffee", 57.0, t),
            Intake("2", "Snabbkaffe", 114.0, t + 60_000),
            Intake("3", "Drip coffee", 95.0, t + 120_000),
            Intake("4", "Filterkaffe", 95.0, t + 180_000),
            Intake("5", "Generic energy drink", 96.0, t + 240_000),
            Intake("6", "Energidryck", 80.0, t + 300_000)
        )
        val summary = caffeinePeriodSummary(mixedIntakes, zone)
        assertEquals(6, summary.intakes)
        assertEquals(3, summary.uniqueDrinks)
        assertEquals("Instant Coffee", statsGroupLabel(listOf("Snabbkaffe", "Instant Coffee"), AppLanguage.EN))
        assertEquals("Drip coffee", statsGroupLabel(listOf("Filterkaffe"), AppLanguage.EN))
    }
    @Test fun settingsLanguageDefaultsToSwedishForLegacyBackupsAndRoundTripsEnglish() {
        val enSnapshot = TrackerSnapshot(emptyList(), emptyList(), TrackerSettings(language = "EN"))
        val decodedEn = BackupCodec.decode(BackupCodec.encode(enSnapshot))
        assertEquals("EN", decodedEn.settings.language)

        val legacyJson = org.json.JSONObject(BackupCodec.encode(enSnapshot)).apply {
            getJSONObject("settings").remove("language")
        }.toString()
        assertEquals("SV", BackupCodec.decode(legacyJson).settings.language)
    }
}
