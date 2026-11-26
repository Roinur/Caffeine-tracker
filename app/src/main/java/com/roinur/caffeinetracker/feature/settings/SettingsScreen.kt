package com.roinur.caffeinetracker.feature.settings

import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roinur.caffeinetracker.app.TrackerUiState
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import com.roinur.caffeinetracker.data.backup.BackupCodec
import java.io.File

/** Sauce's category/search/option-section navigation, with caffeine-domain controls. */
@Composable
fun SettingsScreen(state: TrackerUiState, onSettings: (TrackerSettings) -> Unit, onAppearance: (TrackerSettings) -> Unit, onImport: () -> Unit, onExport: () -> Unit,
    onFolder: () -> Unit, onDisableFolder: () -> Unit, onLocalBackup: (File) -> Unit, onDriveRestore: (String) -> Unit, onBack: () -> Unit, onBackHandler: ((() -> Unit)?) -> Unit = {}) {
    val language = LocalAppLanguage.current
    val strings = LocalStrings.current
    val settings = state.snapshot.settings
    var active by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var halfLife by remember(settings) { mutableStateOf(decimal(settings.halfLifeHours, language)) }
    var bedtime by remember(settings) { mutableStateOf(settings.bedtime) }
    var threshold by remember(settings) { mutableStateOf(decimal(settings.bedtimeThresholdMg, language)) }
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var localPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(active) { listState.scrollToItem(0) }
    fun back() { if (query.isNotBlank()) query = "" else if(active != null) active = null else onBack() }
    DisposableEffect(active, query) {
        onBackHandler { back() }; onDispose { onBackHandler(null) }
    }
    BackHandler(active != null || query.isNotBlank()) { back() }
    fun visible(category: SettingsCategory) = if(query.isNotBlank()) {
        val extra = when (category) {
            SettingsCategory.MODEL -> strings.sectionCalculation + " " + strings.calculationSearchTerms
            SettingsCategory.APPEARANCE -> strings.sectionThemeAndColor + " " + strings.themeSearchTerms + " " + strings.sectionLanguage + " " + strings.languageSearchTerms
            SettingsCategory.BACKUP -> strings.sectionGoogleDrive + " " + strings.driveSearchTerms + " " + strings.sectionImportExport + " " + strings.importExportSearchTerms + " " + strings.sectionAutoBackup + " " + strings.autoBackupSearchTerms
            SettingsCategory.ABOUT -> "version build versioncode versionname local storage lokal lagring widget"
        }
        settingsSearchMatches(query, category.title(strings) + " " + category.summary(strings) + " " + extra)
    } else active == category.name
    val fileActionsEnabled = !state.busy && !state.mediaMode
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("settings-search", contentType = "search") { SettingsSearchBar(query, { query = it }, { back() }) }
        if (active == null && query.isBlank()) {
            items(SettingsCategory.entries, key = { it.name }, contentType = { "category" }) { category ->
                SettingsCategoryRow(category) { active = category.name }
            }
        } else {
            if (query.isBlank()) item {
                Text(SettingsCategory.entries.firstOrNull { it.name == active }?.title(strings).orEmpty(),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(4.dp))
            }
            if (visible(SettingsCategory.MODEL)) item {
                SettingsOptionSection(SettingsCategory.MODEL, strings.sectionCalculation, strings.calculationSearchTerms,
                    active, query) {
                    SettingsActionButton(onClick = {
                        val time = java.time.LocalTime.parse(bedtime)
                        TimePickerDialog(context, { _, h, m -> bedtime = "%02d:%02d".format(h,m) }, time.hour, time.minute, true).show()
                    }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { TrackerGlyph(TrackerIcon.Clock); Spacer(Modifier.width(8.dp)); Text(strings.bedtimeLabel + " · " + bedtime) }
                    DetailSection(strings.advancedCalculation) {
                        PaletteOutlinedTextField(halfLife, { halfLife = it }, Modifier.fillMaxWidth(),
                            label = { Text(strings.halfLifeField) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        PaletteOutlinedTextField(threshold, { threshold = it }, Modifier.fillMaxWidth(),
                            label = { Text(strings.eveningThresholdField) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Text(strings.calculationDisclaimer, style = MaterialTheme.typography.bodySmall)
                    }
                    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    SettingsActionButton(onClick = {
                        runCatching {
                            val updated = settings.copy(halfLifeHours = halfLife.replace(",", ".").toDouble(),
                                bedtime = bedtime.trim(), bedtimeThresholdMg = threshold.replace(",", ".").toDouble(),
                                dailyGoalMg = settings.dailyGoalMg)
                            BackupCodec.settingsFromJson(BackupCodec.settingsJson(updated))
                        }.onSuccess { error = null; onSettings(it) }.onFailure { error = it.message ?: strings.checkDetailsError }
                    }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), primary = true) { Text(strings.saveCalculation) }
                }
            }
            if (visible(SettingsCategory.APPEARANCE)) {
                item {
                    SettingsOptionSection(SettingsCategory.APPEARANCE, strings.sectionLanguage, strings.languageSearchTerms,
                        active, query) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SV" to "Svenska", "EN" to "English").forEach { (code, label) ->
                                FilterChip(
                                    selected = settings.language == code,
                                    onClick = { if (settings.language != code) onAppearance(settings.copy(language = code)) },
                                    enabled = !state.busy,
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text(strings.languageHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    SettingsOptionSection(SettingsCategory.APPEARANCE, strings.sectionThemeAndColor, strings.themeSearchTerms,
                        active, query) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SYSTEM" to strings.themeSystem,"LIGHT" to strings.themeLight,"DARK" to strings.themeDark).forEach { (value,label) ->
                                FilterChip(settings.themeMode == value, { onAppearance(settings.copy(themeMode = value)) },
                                    enabled = !state.busy, label = { Text(label) })
                            }
                        }
                        Text(strings.accentColorLabel, style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "AUTO" to strings.accentAuto,
                                "BLUE" to strings.accentBlue,
                                "AMBER" to strings.accentAmber,
                                "TEAL" to strings.accentTeal,
                                "GREEN" to strings.accentGreen,
                                "PINK" to strings.accentPink,
                                "RED" to strings.accentRed,
                                "ORANGE" to strings.accentOrange,
                                "INDIGO" to strings.accentIndigo
                            ).forEach { (value,label) -> FilterChip(settings.accentMode == value,
                                { onAppearance(settings.copy(accentMode = value)) }, enabled = !state.busy, label = { Text(label) }) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(strings.extraDarkTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(strings.extraDarkSummary,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = settings.extraDark, onCheckedChange = { onAppearance(settings.copy(extraDark = it)) }, enabled = !state.busy)
                        }
                    }
                }
            }
            if (visible(SettingsCategory.BACKUP)) {
                item { SettingsOptionSection(SettingsCategory.BACKUP, strings.sectionGoogleDrive, strings.driveSearchTerms,
                    active, query) {
                    DriveBackupSettings(allowed = fileActionsEnabled, onRestore = onDriveRestore)
                } }
                item { SettingsOptionSection(SettingsCategory.BACKUP, strings.sectionImportExport, strings.importExportSearchTerms,
                    active, query) {
                    SettingsActionButton(onClick = onImport, enabled = fileActionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(strings.importHistoryJsonCsv) }
                    SettingsActionButton(onClick = onExport, enabled = fileActionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(strings.exportBackup) }
                    SettingsActionButton(onClick = onImport, enabled = fileActionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(strings.restoreBackup) }
                } }
                item { SettingsOptionSection(SettingsCategory.BACKUP, strings.sectionAutoBackup, strings.autoBackupSearchTerms,
                    active, query) {
                    val latest = state.localBackups.firstOrNull { it.name == "caffeine_backup.json" }
                    Text(if(latest == null) strings.noLocalBackupYet else strings.lastSavedPrefix + dateLabel(latest.lastModified(), language) + " " + clock(latest.lastModified()),
                        style = MaterialTheme.typography.bodyMedium)
                    DetailSection(strings.manageBackup, if(state.backupFolder == null) strings.savedOnPhone else strings.externalFolderConnected) {
                        SettingsActionButton(onClick = { localPicker = true }, enabled = fileActionsEnabled && state.localBackups.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(strings.previousVersions) }
                        SettingsActionButton(onClick = onFolder, enabled = fileActionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(if(state.backupFolder == null) strings.chooseExternalFolder else strings.changeBackupFolder) }
                        if(state.backupFolder != null) SettingsActionButton(onClick = onDisableFolder, enabled = fileActionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(strings.disconnectExternalFolder) }
                        Text(strings.localRetentionHint, style = MaterialTheme.typography.bodySmall)
                    }
                } }
            }
            if (visible(SettingsCategory.ABOUT)) item {
                SettingsOptionSection(SettingsCategory.ABOUT, strings.appName, "version build versioncode versionname lokal lagring local storage widget", active, query) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.aboutVersionLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(com.roinur.caffeinetracker.BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.aboutBuildLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(com.roinur.caffeinetracker.BuildConfig.VERSION_CODE.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(strings.localStorageAbout, style = MaterialTheme.typography.bodySmall)
                    SettingsActionButton(onClick = { com.roinur.caffeinetracker.feature.widget.CaffeineWidget.requestPin(context, language) },
                        enabled = !state.mediaMode, modifier = Modifier.fillMaxWidth()) {
                        TrackerGlyph(TrackerIcon.Home); Spacer(Modifier.width(8.dp)); Text(strings.addHomeWidget)
                    }
                    if (state.mediaMode) Text(strings.githubModeBadge, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (SettingsCategory.entries.none { visible(it) }) item { Text(strings.noMatchingSettings, Modifier.padding(16.dp)) }
        }
    }
    if (localPicker) AlertDialog(onDismissRequest = { localPicker = false }, title = { Text(strings.localBackupVersionsTitle) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.localBackups) { file ->
                    TextButton(onClick = { localPicker = false; onLocalBackup(file) }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(when(file.name) {
                                "caffeine_backup.json" -> strings.latestVersionLabel
                                "before_restore.json" -> strings.beforeRestoreLabel
                                else -> strings.previousVersionPrefix + file.name.filter { it.isDigit() }
                            })
                            Text(dateLabel(file.lastModified(), language) + " · " + clock(file.lastModified()), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { localPicker = false }) { Text(strings.close) } })
}
