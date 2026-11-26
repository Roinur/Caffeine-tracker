@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.roinur.caffeinetracker.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.*
import com.roinur.caffeinetracker.core.ui.i18n.AppStrings
import com.roinur.caffeinetracker.core.ui.i18n.LocalAppLanguage
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import com.roinur.caffeinetracker.core.ui.theme.CaffeineTheme
import com.roinur.caffeinetracker.feature.dashboard.home.DashboardScreen
import com.roinur.caffeinetracker.core.ui.theme.AccentMode
import com.roinur.caffeinetracker.core.diagnostics.GitHubMediaSession
import com.roinur.caffeinetracker.feature.history.*
import com.roinur.caffeinetracker.feature.drinks.*
import com.roinur.caffeinetracker.feature.statistics.*
import com.roinur.caffeinetracker.feature.settings.*
import com.roinur.caffeinetracker.feature.editor.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

private enum class AppScene { LOADING, DRINK_EDITOR, INTAKE_EDITOR, HOME, HISTORY, DRINKS, STATISTICS, SETTINGS, WIDGET_PREVIEW }

@Composable
fun CaffeineTrackerApp(vm: TrackerViewModel) {
    val resources = LocalContext.current.resources
    LaunchedEffect(resources) { withContext(Dispatchers.Default) { warmDrinkThumbnails(resources) } }
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(GitHubMediaSession.initialSurface) }
    val statisticsState = rememberSaveableStateHolder()
    var historyStart by rememberSaveable { mutableStateOf<String?>(null) }
    var historyEnd by rememberSaveable { mutableStateOf<String?>(null) }
    var historyFromStatistics by rememberSaveable { mutableStateOf(false) }
    val historyRange = historyStart?.let { IntakeDateRange(java.time.LocalDate.parse(it), java.time.LocalDate.parse(historyEnd ?: it)) }
    fun setHistoryRange(range: IntakeDateRange?) { historyStart = range?.start?.toString(); historyEnd = range?.end?.toString() }
    var editingIntakeId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingIntake = state.snapshot.intakes.firstOrNull { it.id == editingIntakeId }
    var lastEditingIntake by remember { mutableStateOf<Intake?>(null) }
    if (editingIntake != null) lastEditingIntake = editingIntake
    var chosenDrink by remember { mutableStateOf<Drink?>(null) }
    var drinkEditor by rememberSaveable { mutableStateOf(false) }
    var editingDrinkJson by rememberSaveable { mutableStateOf<String?>(null) }
    val editingDrink = remember(editingDrinkJson) { editingDrinkJson?.let {
        com.roinur.caffeinetracker.data.backup.BackupCodec.drinkFromJson(org.json.JSONObject(it))
    } }
    fun editDrink(drink: Drink?) {
        editingDrinkJson = drink?.let { com.roinur.caffeinetracker.data.backup.BackupCodec.drinkJson(it).toString() }
    }
    var newDrink by rememberSaveable { mutableStateOf(true) }
    var pendingDrink by remember { mutableStateOf<Drink?>(null) }
    var deletingDrinkId by remember { mutableStateOf<String?>(null) }
    var tickerNow by remember { mutableLongStateOf(GitHubMediaSession.clockTime()) }
    val now = remember(tickerNow, state.snapshot.intakes) { GitHubMediaSession.clockTime() }
    val currentEntries = remember(state.snapshot.intakes, now) { state.snapshot.intakes.filter { it.timestamp <= now } }
    val zone = java.time.ZoneId.systemDefault()
    val curveStart = remember(dayOf(now), zone) { java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() }
    val curve by produceState<com.roinur.caffeinetracker.core.caffeine.CaffeineCurve?>(
        null, currentEntries, state.snapshot.settings.halfLifeHours, curveStart
    ) {
        value = withContext(Dispatchers.Default) {
            android.os.Trace.beginSection("CaffeineCurve.sample")
            try { com.roinur.caffeinetracker.core.caffeine.sampleCaffeineCurve(currentEntries, state.snapshot.settings.halfLifeHours, curveStart) }
            finally { android.os.Trace.endSection() }
        }
    }
    LaunchedEffect(state.receipt?.token) {
        state.receipt?.after?.let { row ->
            if(com.roinur.caffeinetracker.BuildConfig.DEBUG && state.lastSaveStartedAt > 0 && row.timestamp <= now) {
                android.os.Trace.beginSection("Caffeine.intakeEligible")
                try { android.util.Log.d("CaffeineLatency", "intakeEligibleMs=" + (android.os.SystemClock.elapsedRealtime() - state.lastSaveStartedAt)) }
                finally { android.os.Trace.endSection() }
            }
        }
    }
    val settingsBack = remember { arrayOfNulls<() -> Unit>(1) }
    LaunchedEffect(Unit) { while(true) { tickerNow = GitHubMediaSession.clockTime(); delay(30_000) } }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readImport) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::chooseFolder) }
    val snack = remember { SnackbarHostState() }
    val appLanguage = AppLanguage.fromCode(state.snapshot.settings.language)
    val strings = AppStrings.forLanguage(appLanguage)
    var shownReceipt by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.receipt?.token, state.message) {
        val change = state.receipt?.takeIf { it.token != shownReceipt }
        val message = state.message
        if(change != null) {
            shownReceipt = change.token
            snack.currentSnackbarData?.dismiss()
            val verb = if(change.after == null) strings.intakeDeleted else if(change.before == null) strings.intakeAdded else strings.intakeUpdated
            val label = displayDrinkName(change.row.name, appLanguage) + " · " + mg(change.row.caffeineMg) + " · " + verb
            val result = snack.showSnackbar(label + (message?.let { "\n" + it } ?: ""), strings.undo, withDismissAction = true, duration = SnackbarDuration.Long)
            if(result == SnackbarResult.ActionPerformed) vm.undo(change)
            if(message != null) vm.clearMessage()
        } else if(message != null) {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(message)
            vm.clearMessage()
        }
    }
    LaunchedEffect(state.snapshot.drinks, state.busy) {
        if(!state.busy && pendingDrink?.let { it in state.snapshot.drinks } == true) {
            pendingDrink = null; drinkEditor = false
        }
        if(!state.busy && deletingDrinkId != null && state.snapshot.drinks.none { it.id == deletingDrinkId }) {
            deletingDrinkId = null; drinkEditor = false
        }
    }
    LaunchedEffect(state.snapshot.intakes, state.loading) {
        if(!state.loading && editingIntakeId != null && editingIntake == null) editingIntakeId = null
    }
    fun back() {
        when {
            drinkEditor -> { drinkEditor = false; pendingDrink = null; deletingDrinkId = null }
            editingIntakeId != null -> editingIntakeId = null
            tab == 1 && historyFromStatistics -> { historyFromStatistics = false; tab = 3 }
            tab == 4 -> settingsBack[0]?.invoke() ?: run { tab = 0 }
            else -> tab = 0
        }
    }
    val openImport = { importPicker.launch(arrayOf("*/*")) }
    BackHandler((tab != 0 || editingIntakeId != null || drinkEditor) && state.preview == null) { if(!state.busy) back() }
    CompositionLocalProvider(LocalAppLanguage provides appLanguage, LocalStrings provides strings) {
    CaffeineTheme(state.snapshot.settings) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Box(Modifier.fillMaxWidth()) {
                    DashboardHeader(
                        title = when {
                            drinkEditor -> if(newDrink) strings.newDrink else strings.editDrink
                            editingIntake != null -> strings.editIntake
                            else -> when(tab) { 1 -> strings.history; 2 -> strings.drinks; 3 -> strings.statistics; 4 -> strings.settings; else -> strings.appName }
                        },
                        themeMode = runCatching { ThemeMode.valueOf(state.snapshot.settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
                        accentMode = runCatching { AccentMode.valueOf(state.snapshot.settings.accentMode) }.getOrDefault(AccentMode.AMBER),
                        onCycleTheme = {
                            if(!state.busy) {
                                val mode = runCatching { ThemeMode.valueOf(state.snapshot.settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
                                vm.appearance(state.snapshot.settings.copy(themeMode = com.roinur.caffeinetracker.core.ui.theme.nextThemeMode(mode).name))
                            }
                        },
                        onAccent = { if(!state.busy && state.snapshot.settings.accentMode != it.name) vm.appearance(state.snapshot.settings.copy(accentMode = it.name)) },
                        onBack = if(tab != 0 || editingIntakeId != null || drinkEditor) ({ if(!state.busy) back() }) else null,
                        onSettings = if(tab != 4 && editingIntakeId == null && !drinkEditor) ({ tab = 4 }) else null)
                    if(state.showProgress) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                }
            }, snackbarHost = { SnackbarHost(snack) }) { padding ->
            val currentScene = when {
                state.loading -> AppScene.LOADING
                drinkEditor -> AppScene.DRINK_EDITOR
                editingIntake != null -> AppScene.INTAKE_EDITOR
                else -> when (tab) {
                    1 -> AppScene.HISTORY
                    2 -> AppScene.DRINKS
                    3 -> AppScene.STATISTICS
                    4 -> AppScene.SETTINGS
                    5 -> AppScene.WIDGET_PREVIEW
                    else -> AppScene.HOME
                }
            }
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = currentScene,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(180, easing = FastOutSlowInEasing)),
                            initialContentExit = ExitTransition.KeepUntilTransitionsFinished,
                            targetContentZIndex = 1f
                        )
                    },
                    label = "appSceneTransition"
                ) { scene ->
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (scene) {
                        AppScene.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if(state.busy) CircularProgressIndicator() else Text(strings.dbLoadError)
                        }
                        AppScene.DRINK_EDITOR -> key(editingDrink?.id) {
                            DrinkEditor(editingDrink, newDrink, !state.busy,
                                { pendingDrink = it; vm.saveDrink(it) }, { deletingDrinkId = it.id; vm.deleteDrink(it) }, state.operationVersion)
                        }
                        AppScene.INTAKE_EDITOR -> (editingIntake ?: lastEditingIntake)?.let { target ->
                            key(target.id) {
                                IntakeEditor(target, state.snapshot.drinks, now, !state.busy, state.receipt,
                                    { editingIntakeId = null }, vm::saveIntake, vm::deleteIntake, state.operationVersion)
                            }
                        }
                        AppScene.HOME -> DashboardScreen(state.snapshot, now, !state.busy, vm::saveIntake,
                            { editingIntakeId = it.id }, {
                                if(it == 1) { setHistoryRange(null); historyFromStatistics = false }
                                tab = it
                            },
                            { editDrink(it); newDrink = false; drinkEditor = true },
                            state.receipt, chosenDrink, { chosenDrink = null }, vm::saveDrink, state.operationVersion,
                            active = currentScene == AppScene.HOME, cachedCurve = curve)
                        AppScene.HISTORY -> HistoryScreen(state.snapshot.intakes, { editingIntakeId = it.id }, openImport, historyRange, ::setHistoryRange, now)
                        AppScene.DRINKS -> DrinksScreen(state.snapshot.drinks, !state.busy, state.operationVersion,
                            { chosenDrink = it; tab = 0 },
                            { editDrink(it); newDrink = false; drinkEditor = true },
                            { editDrink(null); newDrink = true; drinkEditor = true }, vm::saveDrink,
                            { editDrink(it.copy(id = java.util.UUID.randomUUID().toString(), name = displayDrinkName(it.name, appLanguage) + strings.newSizeSuffix)); newDrink = true; drinkEditor = true },
                            vm::reorderFavorites)
                        AppScene.STATISTICS -> statisticsState.SaveableStateProvider("statistics") {
                            StatisticsScreen(state.snapshot.intakes, now, state.snapshot.drinks) { range ->
                                setHistoryRange(range); historyFromStatistics = true; tab = 1
                            }
                        }
                        AppScene.WIDGET_PREVIEW -> com.roinur.caffeinetracker.feature.widget.WidgetPreviewScreen { tab = 0 }
                        AppScene.SETTINGS -> SettingsScreen(state, vm::settings, vm::appearance, openImport,
                            { exportPicker.launch("Caffeine-Tracker-" + java.time.LocalDate.now() + ".json") },
                            { folderPicker.launch(null) }, vm::disableFolder, vm::localPreview, vm::drivePreview, { tab = 0 }, { settingsBack[0] = it })
                    }
                    }
                }
            }
        }
        state.preview?.let { preview ->
            val first = preview.entries.minOfOrNull { it.timestamp }
            val last = preview.entries.maxOfOrNull { it.timestamp }
            val existingKeys = state.snapshot.intakes.mapNotNull { it.sourceKey }.toSet()
            val duplicateCount = preview.entries.count { it.sourceKey != null && it.sourceKey in existingKeys }
            AlertDialog(onDismissRequest = { if (!state.busy) vm.dismissPreview() },
                title = { Text(if (preview.isRestore) strings.restoreBackupTitle else strings.importHistoryTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(preview.entries.size.toString() + " " + strings.statsIntakesUnit + " · " + mg(preview.entries.sumOf { it.caffeineMg }))
                        if (first != null && last != null) Text(dateLabel(first, appLanguage) + " – " + dateLabel(last, appLanguage))
                        if (preview.isRestore) {
                            Text(strings.restoreReplacesHint)
                            Text(strings.restoreDrinksCount(preview.backup!!.drinks.size))
                        } else {
                            Text(strings.importNewAndDuplicate(preview.entries.size - duplicateCount, duplicateCount))
                            Text(strings.importPreservesHint)
                            Text(strings.importDrinksCount(preview.drinks.size))
                        }
                    }
                }, confirmButton = {
                    TextButton(onClick = vm::applyImport, enabled = !state.busy) { Text(if (preview.isRestore) strings.restoreButton else strings.importButton) }
                }, dismissButton = { TextButton(onClick = vm::dismissPreview, enabled = !state.busy) { Text(strings.cancel) } })
        }
    }
    }
}
