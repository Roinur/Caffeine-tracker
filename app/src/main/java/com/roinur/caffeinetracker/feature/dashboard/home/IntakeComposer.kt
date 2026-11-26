package com.roinur.caffeinetracker.feature.dashboard.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import com.roinur.caffeinetracker.core.diagnostics.GitHubMediaSession
import com.roinur.caffeinetracker.data.backup.BackupCodec
import java.time.*

/** The same form owns new intakes on Home and existing intakes in History. */
@Composable
internal fun IntakeComposer(drinks: List<Drink>, now: Long, enabled: Boolean, preset: Drink?,
    onPresetConsumed: () -> Unit, onSave: (Intake) -> Unit, receipt: IntakeChange?,
    initial: Intake? = null, onSaved: () -> Unit = {}, onFavorite: ((Drink) -> Unit)? = null,
    operationVersion: Long = 0, recentNames: List<String> = emptyList(),
    closeSignal: Int = 0, onOpenChange: (Boolean) -> Unit = {}, active: Boolean = true) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    var generation by rememberSaveable { mutableIntStateOf(0) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSaved by remember { mutableStateOf<Intake?>(null) }
    LaunchedEffect(receipt?.token) {
        if(receipt?.after != null && receipt.after.id == pendingId) {
            pendingId = null
            lastSaved = receipt.after
            generation++
            onSaved()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        key(initial?.id, generation) {
            IntakeFields(drinks, now, enabled, preset, onPresetConsumed, initial, operationVersion, recentNames, closeSignal, onOpenChange, active) {
                pendingId = it.id
                onSave(it)
            }
        }
        lastSaved?.takeIf { initial == null && drinks.none { d ->
            canonicalDrinkKey(d.name) == canonicalDrinkKey(it.name) &&
                d.caffeineMg == it.caffeineMg &&
                (searchKey(d.portion) == searchKey(it.servingUnit) ||
                    searchKey(displayPortion(d, language)) == searchKey(displayPortionText(it.servingUnit, language)) ||
                    searchKey(displayPortionText(d.portion, language)) == searchKey(displayPortionText(it.servingUnit, language)))
        } }?.let { saved ->
            if(onFavorite != null) TextButton(enabled = enabled, onClick = {
                onFavorite(Drink(name = saved.name, caffeineMg = saved.caffeineMg, portion = saved.servingUnit,
                    category = saved.category, imageKey = saved.imageKey, volumeMl = volumeMl(saved.servingUnit)))
                lastSaved = null
            }) { TrackerGlyph(TrackerIcon.Favorite); Spacer(Modifier.width(8.dp)); Text(strings.saveAsFavorite) }
        }
    }
}

@Composable
private fun IntakeFields(drinks: List<Drink>, now: Long, enabled: Boolean, preset: Drink?,
    onPresetConsumed: () -> Unit, initial: Intake?, operationVersion: Long, recentNames: List<String>,
    closeSignal: Int, onOpenChange: (Boolean) -> Unit, active: Boolean, onSave: (Intake) -> Unit) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    val defaultOtherCategory = displayCategory("Övrigt", language)
    val choices = remember(drinks, language) { drinkChoices(drinks.filterNot { it.id == "other" && it.name == "Eget intag" }, language) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVariantId by rememberSaveable { mutableStateOf<String?>(null) }
    var customName by rememberSaveable { mutableStateOf(initial != null) }
    var drinkQuery by rememberSaveable { mutableStateOf(initial?.let { displayDrinkName(it.name, language) }.orEmpty()) }
    var drinkFocused by remember { mutableStateOf(false) }
    var drinkMenuOpen by remember { mutableStateOf(false) }
    var portionQuery by rememberSaveable { mutableStateOf("") }
    var portionLabel by rememberSaveable { mutableStateOf(initial?.servingUnit.orEmpty()) }
    var portionMg by rememberSaveable { mutableStateOf<Double?>(null) }
    var portionFocused by remember { mutableStateOf(false) }
    var portionMenuOpen by remember { mutableStateOf(false) }
    var manualDose by rememberSaveable { mutableStateOf(initial != null) }
    var manualMg by rememberSaveable { mutableStateOf(initial?.caffeineMg?.let { decimal(it, language) }.orEmpty()) }
    var timestamp by rememberSaveable { mutableStateOf(initial?.timestamp) }
    var duration by rememberSaveable { mutableStateOf((initial?.durationMinutes ?: 0).toString()) }
    var absorption by rememberSaveable { mutableStateOf(decimal(initial?.absorptionMinutes90 ?: 45.0, language)) }
    var servings by rememberSaveable { mutableStateOf(decimal(initial?.servings ?: 1.0, language)) }
    var category by rememberSaveable { mutableStateOf(initial?.let { displayCategory(it.category, language) } ?: defaultOtherCategory) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var drinkLimit by remember { mutableIntStateOf(4) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val selected = choices.firstOrNull { it.key == selectedKey }
    val selectedDrink = selected?.variants?.firstOrNull { it.id == selectedVariantId } ?: selected?.primary
    val options = remember(selected, selectedDrink, language) { selected?.let { portionChoices(it, selectedDrink ?: it.primary, language) }.orEmpty() }
    val foundDrinks = remember(choices, drinkQuery, recentNames) { searchDrinks(choices, drinkQuery, recentNames) }
    val foundPortions = remember(options, portionQuery) { searchPortions(options, portionQuery) }
    val customPortion = remember(selected, selectedDrink, portionQuery, language) { selected?.let { customVolumePortion(it, portionQuery, selectedDrink ?: it.primary, language) } }
    val initialDisplayedName = initial?.let { displayDrinkName(it.name, language) }
    val name = if(initial != null && drinkQuery == initialDisplayedName) initial.name else if(customName) drinkQuery.trim() else selected?.name.orEmpty()
    val amount = if(manualDose) manualMg.replace(',', '.').toDoubleOrNull() else portionMg
    val valid = name.isNotBlank() && amount?.let { it.isFinite() && it in 0.0..10000.0 } == true
    val composerOpen = initial == null && (drinkMenuOpen || portionMenuOpen || selected != null || customName)
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun closeComposer() {
        keyboard?.hide()
        focus.clearFocus()
        drinkFocused = false
        drinkMenuOpen = false
        portionFocused = false
        portionMenuOpen = false
        selectedKey = null
        selectedVariantId = null
        customName = false
        drinkQuery = ""
        portionQuery = ""
        portionLabel = ""
        portionMg = null
        manualDose = false
        manualMg = ""
        timestamp = null
        duration = "0"
        absorption = "45"
        servings = "1"
        category = defaultOtherCategory
        note = ""
        error = null
    }
    LaunchedEffect(composerOpen) { onOpenChange(composerOpen) }
    var handledCloseSignal by remember { mutableIntStateOf(closeSignal) }
    LaunchedEffect(closeSignal) {
        if (closeSignal != handledCloseSignal) {
            handledCloseSignal = closeSignal
            if (initial == null && composerOpen) {
                if (imeVisible) keyboard?.hide()
                else closeComposer()
            }
        }
    }
    BackHandler(enabled = initial == null && active && composerOpen) {
        if (imeVisible) {
            keyboard?.hide()
        } else {
            closeComposer()
        }
    }
    LaunchedEffect(enabled, operationVersion) { if(enabled) submitting = false }
    fun changeServings(value: String) {
        val previous = servings.replace(',','.').toDoubleOrNull()
        val next = value.replace(',','.').toDoubleOrNull()
        if(previous != null && previous > 0 && next != null && next > 0 && amount != null) {
            manualMg = decimal(amount / previous * next, language); manualDose = true
        }
        servings = value
    }
    fun choosePortion(portion: PortionChoice) {
        if(portion.key.startsWith("saved:")) selectedVariantId = portion.key.removePrefix("saved:")
        portionLabel = portion.label; portionMg = portion.caffeineMg
        portionQuery = ""; portionMenuOpen = false; manualDose = false; error = null
    }
    fun chooseDrink(choice: DrinkChoice, variant: Drink = choice.primary) {
        selectedKey = choice.key; selectedVariantId = variant.id; customName = false; drinkQuery = choice.name
        drinkMenuOpen = false; portionMenuOpen = false
        val savedChoice = portionChoices(choice, variant, language).firstOrNull { it.key == "saved:" + variant.id }
            ?: PortionChoice("saved:" + variant.id, displayPortion(variant, language), variant.caffeineMg)
        choosePortion(savedChoice)
        category = displayCategory(variant.category, language)
        keyboard?.hide()
        focus.clearFocus()
    }
    LaunchedEffect(preset, choices) {
        preset?.let { drink -> choices.firstOrNull { drink in it.variants }?.let { chooseDrink(it, drink) }; onPresetConsumed() }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaletteOutlinedTextField(
            value = if(drinkMenuOpen || drinkFocused || selected == null) drinkQuery else selected.name,
            onValueChange = {
                drinkQuery = it; error = null; drinkLimit = 4
                if(initial == null) { drinkMenuOpen = true; selectedKey = null; customName = false; portionMg = null }
            },
            enabled = enabled && !submitting, label = { Text(if(initial == null) strings.drinkField else strings.drinkNameField) },
            placeholder = { Text(strings.searchDrinkPlaceholder) },
            leadingIcon = {
                if(selected != null && !drinkMenuOpen) DrinkThumbnail(selected.name, selected.primary.category, Modifier.size(36.dp), selectedDrink?.imageKey, showContainer = false)
                else if(initial != null) DrinkThumbnail(initial.name, initial.category, Modifier.size(36.dp), initial.imageKey, showContainer = false)
                else TrackerGlyph(TrackerIcon.Search)
            },
            trailingIcon = { if(initial == null && drinkQuery.isNotBlank()) IconButton(onClick = {
                drinkQuery = ""; selectedKey = null; customName = false; portionMg = null; manualDose = false; drinkMenuOpen = true
            }) { TrackerGlyph(TrackerIcon.Close, description = strings.clearDrinkSearch) } },
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if(initial == null && foundDrinks.size == 1) chooseDrink(foundDrinks.single()) else keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                drinkFocused = it.isFocused
                if (it.isFocused && initial == null) { drinkMenuOpen = true; portionMenuOpen = false }
            },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors)
        AnimatedVisibility(
            visible = drinkMenuOpen && initial == null,
            enter = fadeIn(tween(130)) + expandVertically(tween(150)),
            exit = fadeOut(tween(90)) + shrinkVertically(tween(120))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    foundDrinks.take(drinkLimit).forEach { choice ->
                        SuggestionRow(choice.name, displayPortion(choice.primary, language) + " · " + mg(choice.primary.caffeineMg),
                            onClick = { chooseDrink(choice) }, leading = {
                                DrinkThumbnail(choice.name, choice.primary.category, Modifier.size(38.dp), choice.primary.imageKey, showContainer = false)
                            })
                    }
                    if(foundDrinks.size > drinkLimit) TextButton(onClick = { drinkLimit += 6 }) { Text(strings.showMoreDrinks) }
                    if(drinkQuery.isNotBlank() && choices.none { it.key == canonicalDrinkKey(drinkQuery) }) {
                        TextButton(onClick = {
                            customName = true; selectedKey = null; drinkMenuOpen = false; manualDose = true; manualMg = ""; portionLabel = ""
                            keyboard?.hide(); focus.clearFocus()
                        }) { Text(strings.createDrinkPrompt(drinkQuery.trim())) }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = selected != null || customName,
            enter = fadeIn(tween(130)) + expandVertically(tween(150)),
            exit = fadeOut(tween(90)) + shrinkVertically(tween(120))
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if(initial != null && initial.servings != 1.0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaletteOutlinedTextField(servings, ::changeServings, Modifier.weight(1f),
                        label = { Text(strings.amountField) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors)
                    PaletteOutlinedTextField(portionLabel, { portionLabel = it }, Modifier.weight(1f),
                        label = { Text(strings.unitField) }, singleLine = true, colors = fieldColors)
                }
            } else PaletteOutlinedTextField(
                value = if(manualDose) portionLabel else if(portionMenuOpen || portionFocused) portionQuery
                    else portionMg?.let { portionLabel + " · " + portionNumber(it, language) + " mg" } ?: portionQuery,
                onValueChange = { if(manualDose) portionLabel = it else { portionQuery = it; portionMg = null; portionMenuOpen = true }; error = null },
                enabled = enabled && !submitting, label = { Text(strings.portionField) }, placeholder = { Text(strings.portionPlaceholder) },
                leadingIcon = { TrackerGlyph(if(manualDose) TrackerIcon.Drinks else TrackerIcon.Search) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    if(it.isFocused && !portionFocused) { portionQuery = ""; portionMenuOpen = true }
                    portionFocused = it.isFocused
                }, keyboardOptions = KeyboardOptions(imeAction = if(manualDose) ImeAction.Next else ImeAction.Search),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }, onSearch = {
                    if(!manualDose) {
                        val choice = foundPortions.singleOrNull() ?: customPortion?.takeIf { foundPortions.isEmpty() }
                        choice?.let { choosePortion(it); keyboard?.hide(); focus.clearFocus() }
                    }
                }), shape = RoundedCornerShape(16.dp), colors = fieldColors)
            AnimatedVisibility(
                visible = portionMenuOpen && !manualDose && selected != null,
                enter = fadeIn(tween(120)) + expandVertically(tween(140)),
                exit = fadeOut(tween(90)) + shrinkVertically(tween(110))
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        foundPortions.take(5).forEach { portion ->
                            SuggestionRow(portion.label, if(portion.calculated) strings.estimatedAmount else strings.savedPortion,
                                onClick = { choosePortion(portion); keyboard?.hide(); focus.clearFocus() }, trailing = mg(portion.caffeineMg))
                        }
                        if(customPortion != null && foundPortions.none { it.label == customPortion.label && it.caffeineMg == customPortion.caffeineMg }) {
                            SuggestionRow(strings.usePortion(customPortion.label), strings.calculatedFromRecipe,
                                onClick = { choosePortion(customPortion); keyboard?.hide(); focus.clearFocus() }, trailing = mg(customPortion.caffeineMg))
                        }
                        if(foundPortions.isEmpty() && customPortion == null) Text(strings.noMatchingPortion, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = enabled, onClick = {
                            manualDose = true; portionMenuOpen = false
                            if(portionQuery.isNotBlank()) portionLabel = customPortion?.label ?: portionQuery.trim()
                            manualMg = portionMg?.let { decimal(it, language) }.orEmpty()
                            error = null; keyboard?.hide(); focus.clearFocus()
                        }) { Text(strings.enterCaffeineManually) }
                    }
                }
            }
            if(manualDose) PaletteOutlinedTextField(manualMg, { manualMg = it; error = null }, Modifier.fillMaxWidth(),
                label = { Text(strings.totalCaffeineField) }, singleLine = true, enabled = enabled && !submitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }), colors = fieldColors)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if(valid) mg(requireNotNull(amount)) else strings.enterAmount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(enabled = enabled, onClick = {
                    keyboard?.hide(); focus.clearFocus()
                    val date = Instant.ofEpochMilli(timestamp ?: now).atZone(ZoneId.systemDefault())
                    TimePickerDialog(context, { _, h, m ->
                        timestamp = date.toLocalDate().atTime(h,m).atZone(date.zone).toInstant().toEpochMilli()
                    }, date.hour, date.minute, true).show()
                }) {
                    TrackerGlyph(TrackerIcon.Clock); Spacer(Modifier.width(6.dp))
                    Text(if(timestamp == null) strings.nowLabel else if(dayOf(timestamp!!) == dayOf(now)) clock(timestamp!!) else dateLabel(timestamp!!, language) + " " + clock(timestamp!!))
                }
            }
            if((servings.replace(',','.').toDoubleOrNull() ?: 1.0) != 1.0 && (initial == null || initial.servings == 1.0))
                Text(servings + " × " + portionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DetailSection(strings.moreDetails, if(initial != null && (initial.absorptionMinutes90 != 45.0 || initial.durationMinutes > 0 || initial.servings != 1.0))
                strings.savedTimingPreserved else null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val date = Instant.ofEpochMilli(timestamp ?: now).atZone(ZoneId.systemDefault())
                        DatePickerDialog(context, { _, y, m, d ->
                            timestamp = LocalDate.of(y,m+1,d).atTime(date.toLocalTime()).atZone(date.zone).toInstant().toEpochMilli()
                        }, date.year, date.monthValue-1, date.dayOfMonth).show()
                    }) { TrackerGlyph(TrackerIcon.Calendar); Spacer(Modifier.width(6.dp)); Text(dateLabel(timestamp ?: now, language)) }
                    if(timestamp != null) TextButton(onClick = { timestamp = null }) { Text(strings.useCurrentTime) }
                }
                if(selected != null) TextButton(onClick = {
                    manualDose = !manualDose; portionMenuOpen = false; keyboard?.hide(); focus.clearFocus()
                    if(manualDose) manualMg = portionMg?.let { decimal(it, language) }.orEmpty() else choosePortion(options.first())
                }) { Text(if(manualDose) strings.showPortionSuggestions else strings.enterCustomCaffeine) }
                PaletteOutlinedTextField(duration, { duration = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(strings.drinkingDurationField) }, supportingText = { Text(strings.durationZeroHint) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors)
                PaletteOutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(strings.noteField) }, maxLines = 3, colors = fieldColors)
                DetailSection(strings.advancedCalculation) {
                    if(initial == null || initial.servings == 1.0) PaletteOutlinedTextField(servings, ::changeServings, Modifier.fillMaxWidth(),
                        label = { Text(strings.servingsCountField) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors)
                    PaletteOutlinedTextField(absorption, { absorption = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(strings.absorptionTimeField) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors)
                    PaletteOutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text(strings.categoryField) }, singleLine = true, colors = fieldColors)
                    Text(strings.appliesOnlyToIntake, style = MaterialTheme.typography.bodySmall)
                }
                if(selected != null) DetailSection(strings.aboutPortionSuggestions) {
                    Text(strings.portionSuggestionsHelp, style = MaterialTheme.typography.bodySmall)
                    selected.primary.let { reference ->
                        val standard = clockPortionStandard(reference)
                        if(standard != null) Text(strings.standardConcentration(decimal(standard.mgPerMl, language)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(enabled = enabled && !submitting && valid, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                if(!submitting) runCatching {
                    val at = timestamp ?: GitHubMediaSession.clockTime()
                    require(at <= (if(GitHubMediaSession.active) maxOf(now,System.currentTimeMillis()) else System.currentTimeMillis()) + 60_000) { strings.futureIntakeError }
                    val base = initial ?: Intake(name = name, caffeineMg = requireNotNull(amount), timestamp = at)
                    reviseIntake(base, name, requireNotNull(amount), at,
                        duration.toIntOrNull() ?: error(strings.durationWholeMinutesError),
                        requireNotNull(absorption.replace(',','.').toDoubleOrNull()) { strings.absorptionMinutesError }, if(note == initial?.note) note else note.trim(), category,
                        if(portionLabel == initial?.servingUnit) portionLabel else portionLabel.trim(),
                        initial?.imageKey ?: selectedDrink?.imageKey,
                        requireNotNull(servings.replace(',','.').toDoubleOrNull()) { strings.invalidServingsError }).also(BackupCodec::validIntake)
                }.onSuccess { submitting = true; error = null; keyboard?.hide(); focus.clearFocus(); onSave(it) }.onFailure { error = it.message ?: strings.checkAmountError }
            }) {
                TrackerGlyph(if(initial == null) TrackerIcon.Plus else TrackerIcon.Check)
                Spacer(Modifier.width(8.dp)); Text(if(submitting) strings.saving else if(initial == null) strings.addButton else strings.saveChangesButton)
            }
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, subtitle: String, onClick: () -> Unit,
    trailing: String? = null, leading: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
    }
}
