package com.roinur.caffeinetracker

import com.roinur.caffeinetracker.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DrinkCatalogTest {
    @Test fun searchFindsSwedishAndEnglishNamesWithoutLosingSavedRecipes() {
        val drinks = listOf(Drink("a", "Bryggkaffe", 100.0, "200 ml"), Drink("b", "Bryggkaffe", 150.0, "250 ml"),
            Drink("c", "Snabbkaffe", 57.0, "200 ml"), Drink("d", "Energidryck", 160.0, "500 ml", "Energidryck"))
        val choices = drinkChoices(drinks)
        assertEquals(3, choices.size)
        val choice = searchDrinks(choices, "drip coffee").single()
        assertEquals("Bryggkaffe", choice.name)
        assertEquals(2, choice.variants.size)
        assertEquals("Snabbkaffe", searchDrinks(choices, "instant").single().name)
        assertEquals("Energidryck", searchDrinks(choices, "energy drink").single().name)
    }
    @Test fun twoSuggestsBoth200And250MlWithTheStoredDoses() {
        val choice = drinkChoices(listOf(Drink("a", "Kaffe", 100.0, "200 ml"), Drink("b", "Kaffe", 150.0, "250 ml"))).single()
        val results = searchPortions(portionChoices(choice), "2")
        assertEquals(listOf("200 ml", "250 ml"), results.take(2).map { it.label })
        assertEquals(listOf(100.0, 150.0), results.take(2).map { it.caffeineMg })
    }
    @Test fun generatedVolumesScaleFromTheActualRecipe() {
        val choice = drinkChoices(listOf(Drink("energy", "Energy drink", 80.0, "250 ml · 32 mg/100 ml"))).single()
        val options = portionChoices(choice)
        assertEquals(64.0, options.single { it.label == "200 ml" }.caffeineMg, 1e-9)
        assertEquals(160.0, options.single { it.label == "500 ml" }.caffeineMg, 1e-9)
        assertTrue(searchPortions(options, "500 ml").any { it.caffeineMg == 160.0 })
        assertTrue(searchPortions(options, "160 mg").any { it.label == "500 ml" })
        assertTrue(searchPortions(options, "500ml").any { it.caffeineMg == 160.0 })
        assertTrue(searchPortions(options, "160mg").any { it.label == "500 ml" })
    }
    @Test fun unknownCupSizesNeverInventAMillilitreConcentration() {
        val options = portionChoices(drinkChoices(listOf(Drink("custom", "Mitt kaffe", 95.0, "min kopp"))).single())
        assertFalse(options.any { it.label.contains("ml") })
        assertEquals(190.0, options.single { it.label == "2 × min kopp" }.caffeineMg, 0.0)
    }
    @Test fun starterExampleVolumeDoesNotOverrideCustomisedRecipes() {
        val old = Drink("filter", "Filterkaffe", 95.0, "1 kopp")
        assertEquals(240.0, referenceVolumeMl(old)!!, 0.0)
        assertNull(referenceVolumeMl(old.copy(caffeineMg = 200.0)))
    }
    @Test fun caffeineClockCupAndMlUnitsKeepTheirSeparateRoundedValues() {
        val choice = drinkChoices(listOf(Drink("imported", "Drip coffee", 95.0, "cup"))).single()
        val options = portionChoices(choice)
        assertEquals(95.0, options.first().caffeineMg, 0.0)
        assertEquals("1 cup (240 ml)", options.first().label)
        assertEquals(80.0, options.single { it.label == "200 ml" }.caffeineMg, 0.0)
        assertEquals(100.0, options.single { it.label == "250 ml" }.caffeineMg, 0.0)
        assertEquals(120.0, options.single { it.label == "300 ml" }.caffeineMg, 0.0)
    }
    @Test fun instantCoffeeMlUsesClockStandardWithoutChangingTheTeaspoonDose() {
        val options = portionChoices(drinkChoices(listOf(Drink("imported", "Instant Coffee", 57.0, "teaspoon"))).single())
        assertEquals(57.0, options.first().caffeineMg, 0.0)
        assertEquals(56.0, options.single { it.label == "200 ml" }.caffeineMg, 1e-9)
        assertNull(clockPortionStandard(Drink("edited", "Instant Coffee", 57.0, "300 ml")))
    }
    @Test fun volumeParsingHandlesUnitsAndDecimalComma() {
        assertEquals(500.0, volumeMl("0,5 l")!!, 0.0)
        assertEquals(250.0, volumeMl("25 cl")!!, 0.0)
        assertEquals(200.0, volumeMl("2 dl")!!, 0.0)
        assertEquals(355.0, volumeMl("355 mL · 114 mg")!!, 0.0)
        assertNull(volumeMl("1 kapsel · 100 mg"))
        assertNull(volumeMl("0 ml"))
    }
    @Test fun generatedAmountsCannotExceedStorageLimitsOrLosePrecision() {
        val choice = drinkChoices(listOf(Drink("a", "Coffee", 95.0, "200 ml"))).single()
        assertEquals(118.75, portionChoices(choice).single { it.label == "250 ml" }.caffeineMg, 0.0)
        val concentrated = choice.copy(variants = listOf(choice.primary.copy(caffeineMg = 10000.0, portion = "1 ml")))
        assertTrue(portionChoices(concentrated).all { it.caffeineMg in 0.0..10000.0 })
    }
    @Test fun artworkDoesNotTurnEveryCoffeeOrTeaIntoDripCoffee() {
        assertEquals(DrinkImage.INSTANT, drinkImage("Coffee (instant)", "Kaffe"))
        assertEquals(DrinkImage.DRIP, drinkImage("Coffee (drip)", "Kaffe"))
        assertEquals(DrinkImage.ENERGY, drinkImage("Energy drink", "Övrigt"))
        assertEquals(DrinkImage.ESPRESSO, drinkImage("Espresso", "Kaffe"))
        assertEquals(DrinkImage.GREEN_TEA, drinkImage("Grönt te", "Te"))
    }
}
