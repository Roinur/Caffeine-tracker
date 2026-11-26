package com.roinur.caffeinetracker.feature.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import com.roinur.caffeinetracker.data.backup.BackupCodec
import com.roinur.caffeinetracker.feature.dashboard.home.IntakeComposer

/** Editing is a normal page, with the same form used by the Home screen. */
@Composable
fun IntakeEditor(initial: Intake, drinks: List<Drink>, now: Long, enabled: Boolean, receipt: IntakeChange?,
    onSaved: () -> Unit, onSave: (Intake) -> Unit, onDelete: (Intake) -> Unit, operationVersion: Long) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    var delete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IntakeComposer(drinks, now, enabled, null, {}, onSave, receipt, initial, onSaved, operationVersion = operationVersion)
        TextButton(enabled = enabled, onClick = { delete = true }) {
            TrackerGlyph(TrackerIcon.Delete, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp)); Text(strings.deleteIntake, color = MaterialTheme.colorScheme.error)
        }
    }
    if(delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(strings.deleteIntakeTitle) },
        text = { Text(displayDrinkName(initial.name, language) + " · " + mg(initial.caffeineMg)) },
        confirmButton = { TextButton(enabled = enabled, onClick = { delete = false; onDelete(initial) }) { Text(strings.delete) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(strings.cancel) } })
}

@Composable
fun DrinkEditor(initial: Drink?, isNew: Boolean, enabled: Boolean, onSave: (Drink) -> Unit, onDelete: (Drink) -> Unit, operationVersion: Long) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    val original = remember { initial ?: Drink(name = "", caffeineMg = 100.0, portion = strings.defaultPortion, category = displayCategory("Kaffe", language)) }
    val originalVolume = remember { original.volumeMl ?: volumeMl(original.portion) }
    val initialUnit = if(originalVolume != null) "ml" else "portion"
    val initialName = displayDrinkName(original.name, language)
    val initialCategory = displayCategory(original.category, language)
    val initialPortion = originalVolume?.let { decimal(it, language) } ?: displayPortionText(original.portion, language)
    var name by rememberSaveable { mutableStateOf(initialName) }
    var amount by rememberSaveable { mutableStateOf(if(initial == null) "" else decimal(original.caffeineMg, language)) }
    var portion by rememberSaveable { mutableStateOf(initialPortion) }
    var unit by rememberSaveable { mutableStateOf(initialUnit) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var imageKey by rememberSaveable { mutableStateOf(original.imageKey) }
    var favorite by rememberSaveable { mutableStateOf(original.favorite) }
    var error by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, operationVersion) { if(enabled) submitting = false }
    val dose = amount.replace(',','.').toDoubleOrNull()
    val valid = name.isNotBlank() && dose?.let { it.isFinite() && it in 0.0..10000.0 } == true &&
        (if(unit == "ml") portion.replace(',','.').toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true else portion.isNotBlank())
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TrackerCard {
            PaletteOutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(strings.nameField) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(unit == "ml", {
                    if(unit != "ml") { unit = "ml"; portion = "" }
                }, label = { Text(strings.volumeInMl) })
                FilterChip(unit == "portion", {
                    if(unit != "portion") { unit = "portion"; portion = strings.defaultPortion }
                }, label = { Text(strings.otherPortion) })
            }
            PaletteOutlinedTextField(portion, { portion = it }, Modifier.fillMaxWidth(),
                label = { Text(if(unit == "ml") strings.volumeMlField else strings.portionExampleField) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if(unit == "ml") KeyboardType.Decimal else KeyboardType.Text))
            PaletteOutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text(strings.caffeineInPortionField) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            DrinkImagePicker(name, category, imageKey) { imageKey = it }
            Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).toggleable(favorite, role = Role.Checkbox, onValueChange = { favorite = it }).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TrackerGlyph(TrackerIcon.Favorite, tint = if(favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                Text(strings.showInFavorites, Modifier.weight(1f).padding(horizontal = 12.dp))
                Checkbox(favorite, onCheckedChange = null)
            }
            DetailSection(strings.moreSettings) {
                Text(strings.categoryLabel, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (defaultCategories(language) + category).distinct().forEach {
                        FilterChip(category == it, { category = it }, label = { Text(it) })
                    }
                }
                Text(strings.drinkEditFutureHint, style = MaterialTheme.typography.bodySmall)
            }
            if(error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            Button(enabled = enabled && !submitting && valid, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                runCatching {
                    val portionChanged = unit != initialUnit || portion != initialPortion
                    val volume = if(unit == "ml") portion.replace(',','.').toDouble() else null
                    val savedName = if (!isNew && name == initialName) original.name else name.trim()
                    val savedCategory = if (!isNew && category == initialCategory) original.category else category
                    original.copy(name = savedName, caffeineMg = requireNotNull(dose),
                        portion = if(!portionChanged) original.portion else if(volume != null) portionNumber(volume, language) + " ml" else portion.trim(),
                        volumeMl = if(!portionChanged) original.volumeMl else volume,
                        category = savedCategory, favorite = favorite, imageKey = imageKey).also(BackupCodec::validDrink)
                }.onSuccess { submitting = true; onSave(it) }.onFailure { error = it.message ?: strings.checkAmountError }
            }) { Text(if(submitting) strings.saving else strings.saveDrinkButton) }
        }
        if(!isNew) TextButton(enabled = enabled, onClick = { delete = true }) {
            TrackerGlyph(TrackerIcon.Delete, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp)); Text(strings.deleteDrink, color = MaterialTheme.colorScheme.error)
        }
    }
    if(delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(strings.deleteDrinkTitle) },
        text = { Text(strings.previousIntakesKept) },
        confirmButton = { TextButton(enabled = enabled, onClick = { delete = false; onDelete(original) }) { Text(strings.delete) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(strings.cancel) } })
}
