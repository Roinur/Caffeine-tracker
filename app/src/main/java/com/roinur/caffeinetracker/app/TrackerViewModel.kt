package com.roinur.caffeinetracker.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.roinur.caffeinetracker.core.diagnostics.GitHubMediaSession
import android.net.Uri
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.storage.DocumentTreeStorage
import com.roinur.caffeinetracker.core.ui.i18n.AppStrings
import com.roinur.caffeinetracker.data.backup.*
import com.roinur.caffeinetracker.data.database.CaffeineDatabase
import com.roinur.caffeinetracker.data.importer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class TrackerUiState(
    val snapshot: TrackerSnapshot = TrackerSnapshot(emptyList(), emptyList(), TrackerSettings()),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val showProgress: Boolean = false,
    val message: String? = null,
    val preview: ImportPreview? = null,
    val backupFolder: String? = null,
    val localBackups: List<File> = emptyList(),
    val receipt: IntakeChange? = null,
    val operationVersion: Long = 0,
    val lastSaveStartedAt: Long = 0,
    val mediaMode: Boolean = false
)

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val mediaMode = GitHubMediaSession.active
    private val database = CaffeineDatabase(app, GitHubMediaSession.databaseName())
    private val storageContext = if (mediaMode) object : ContextWrapper(app) {
        override fun getFilesDir(): File = File(app.filesDir, "github-media").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(name + "_github_media", mode)
    } else app
    private val backups = CaffeineBackupStore(storageContext)
    private val mutex = com.roinur.caffeinetracker.feature.widget.TrackerDataEvents.mutations
    private val folderMutex = Mutex()
    private val activeOps = java.util.concurrent.atomic.AtomicInteger(0)
    private val activeProgress = java.util.concurrent.atomic.AtomicInteger(0)
    private val mutableState = MutableStateFlow(TrackerUiState())
    val state = mutableState.asStateFlow()
    private fun strings(): AppStrings = AppStrings.forCode(mutableState.value.snapshot.settings.language)

    init {
        operation {
            if (mediaMode) database.replaceSnapshot(GitHubMediaSession.sampleSnapshot())
            else com.roinur.caffeinetracker.data.backup.drive.DriveBackupSync.syncSchedule(app)
            reload()
        }
        viewModelScope.launch { com.roinur.caffeinetracker.feature.widget.TrackerDataEvents.changes.drop(1).collect {
            operation(showProgress = false) { reload() }
        } }
        if(!mediaMode) viewModelScope.launch {
            TrackerBackupQueue.status.collect { result ->
                if(result.revision == 0L) return@collect
                val files = withContext(Dispatchers.IO) { backups.available() }
                mutableState.update { it.copy(localBackups = files,
                    message = result.error?.let { error -> strings().backupFailedPrefix + error } ?: it.message) }
            }
        }
    }

    private fun beginBusy(showProgress: Boolean) {
        activeOps.incrementAndGet()
        if (showProgress) activeProgress.incrementAndGet()
        mutableState.update { it.copy(busy = true, showProgress = activeProgress.get() > 0) }
    }

    private fun endBusy(showProgress: Boolean) {
        val remainingOps = activeOps.decrementAndGet().coerceAtLeast(0)
        val remainingProgress = if (showProgress) activeProgress.decrementAndGet().coerceAtLeast(0) else activeProgress.get()
        mutableState.update {
            it.copy(
                busy = remainingOps > 0,
                showProgress = remainingProgress > 0,
                operationVersion = it.operationVersion + 1
            )
        }
    }

    private fun operation(showProgress: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                beginBusy(showProgress)
                try { withContext(Dispatchers.IO) { block() } }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mutableState.update { it.copy(message = e.message ?: strings().actionFailed) }
                } finally { endBusy(showProgress) }
            }
        }
    }

    private fun sortedIntakes(intakes: List<Intake>) = intakes.sortedWith(compareByDescending<Intake> { it.timestamp }.thenBy { it.id })
    private fun sortedDrinks(drinks: List<Drink>) = drinks.sortedWith(compareByDescending<Drink> { it.favorite }.thenBy { it.favoriteOrder }.thenBy { it.name.lowercase() }.thenBy { it.id })

    private fun reload() {
        val snapshot = database.snapshot()
        mutableState.update { it.copy(snapshot = snapshot, loading = false,
            backupFolder = backups.folder, localBackups = backups.available(), mediaMode = mediaMode) }
    }

    private fun changed(message: String?, updatedSnapshot: TrackerSnapshot? = null) {
        if (updatedSnapshot != null) {
            mutableState.update { it.copy(snapshot = updatedSnapshot, loading = false, message = message ?: it.message) }
        } else {
            reload()
            if (message != null) mutableState.update { it.copy(message = message) }
        }
        if(!mediaMode) {
            TrackerBackupQueue.request(app)
            com.roinur.caffeinetracker.data.backup.drive.DriveBackupSync.enqueue(app)
        }
        com.roinur.caffeinetracker.feature.widget.CaffeineWidget.refresh(app, mediaMode)
    }

    fun saveIntake(intake: Intake) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        operation(showProgress = false) {
            android.os.Trace.beginSection("Caffeine.saveIntake")
            try {
                val before = database.findIntake(intake.id)
                database.saveIntake(intake)
                val snap = mutableState.value.snapshot
                val nextIntakes = sortedIntakes(snap.intakes.filterNot { it.id == intake.id } + intake)
                changed(null, snap.copy(intakes = nextIntakes))
                mutableState.update { it.copy(receipt = IntakeChange(before, intake), lastSaveStartedAt = startedAt) }
            } finally { android.os.Trace.endSection() }
        }
    }
    fun deleteIntake(intake: Intake) = operation(showProgress = false) {
        val before = database.findIntake(intake.id) ?: error(strings().intakeMissing)
        database.deleteIntake(intake.id)
        val snap = mutableState.value.snapshot
        changed(null, snap.copy(intakes = snap.intakes.filterNot { it.id == intake.id }))
        mutableState.update { it.copy(receipt = IntakeChange(before, null)) }
    }
    fun undo(change: IntakeChange) = operation(showProgress = false) {
        val current = database.findIntake(change.row.id)
        val target = undoTarget(change, current)
        val snap = mutableState.value.snapshot
        val nextIntakes = if(target == null) {
            database.deleteIntake(change.row.id)
            snap.intakes.filterNot { it.id == change.row.id }
        } else {
            database.saveIntake(target)
            sortedIntakes(snap.intakes.filterNot { it.id == target.id } + target)
        }
        changed(strings().changeUndone, snap.copy(intakes = nextIntakes))
    }
    fun reorderFavorites(ids: List<String>) = operation(showProgress = false) {
        val snap = mutableState.value.snapshot
        val reordered = reorderedFavorites(snap.drinks, ids)
        database.saveDrinks(reordered)
        val byId = reordered.associateBy { it.id }
        val nextDrinks = sortedDrinks(snap.drinks.map { byId[it.id] ?: it })
        changed(null, snap.copy(drinks = nextDrinks))
    }
    fun saveDrink(drink: Drink) = operation(showProgress = false) {
        database.saveDrink(drink)
        val snap = mutableState.value.snapshot
        val nextDrinks = sortedDrinks(snap.drinks.filterNot { it.id == drink.id } + drink)
        changed(strings().drinkSaved, snap.copy(drinks = nextDrinks))
    }
    fun deleteDrink(drink: Drink) = operation(showProgress = false) {
        database.deleteDrink(drink.id)
        val snap = mutableState.value.snapshot
        changed(strings().drinkDeleted, snap.copy(drinks = snap.drinks.filterNot { it.id == drink.id }))
    }
    fun settings(settings: TrackerSettings) = operation(showProgress = false) {
        database.saveSettings(settings)
        changed(AppStrings.forCode(settings.language).settingsSaved, mutableState.value.snapshot.copy(settings = settings))
    }
    fun appearance(settings: TrackerSettings) = operation(showProgress = false) {
        database.saveSettings(settings)
        changed(null, mutableState.value.snapshot.copy(settings = settings))
    }
    fun clearMessage() { mutableState.update { it.copy(message = null) } }

    private fun ioAction(showProgress: Boolean = true, block: suspend () -> Unit) {
        viewModelScope.launch {
            beginBusy(showProgress)
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(message = e.message ?: strings().actionFailed) }
            } finally { endBusy(showProgress) }
        }
    }

    fun readImport(uri: Uri) = ioAction {
        val s = strings()
        require(!mediaMode) { s.githubModeReadOnly }
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            input.readTrackerBytes()
        } ?: error(s.fileOpenFailed)
        require(bytes.size <= BackupCodec.MAX_BYTES) { s.fileTooLarge }
        val text = bytes.toString(Charsets.UTF_8)
        val preview = CaffeineClockImporter.preview(text)
        mutableState.update { it.copy(preview = preview) }
    }

    fun localPreview(file: File) = ioAction {
        val allowed = backups.available().any { it.canonicalFile == file.canonicalFile }
        require(allowed) { strings().unknownBackupFile }
        val preview = CaffeineClockImporter.preview(file.readText())
        mutableState.update { it.copy(preview = preview) }
    }

    fun drivePreview(payload: String) = ioAction {
        require(!mediaMode) { strings().githubModeReadOnly }
        val snapshot = BackupCodec.decode(payload)
        mutableState.update { it.copy(preview = ImportPreview(snapshot.intakes, snapshot, "Google Drive")) }
    }

    fun dismissPreview() {
        com.roinur.caffeinetracker.data.backup.drive.DriveBackupSync.clearPendingRestore()
        mutableState.update { it.copy(preview = null) }
    }

    fun applyImport() = operation {
        val preview = mutableState.value.preview ?: return@operation
        if(mediaMode) backups.preserveBeforeRestore(database.snapshot())
        else TrackerBackupQueue.exclusive { backups.preserveBeforeRestore(database.snapshot()) }
        if (preview.backup != null) {
            if (!mediaMode && preview.source == "Google Drive") {
                com.roinur.caffeinetracker.data.backup.drive.DriveBackupSync.applyDriveRestore(app, preview.backup) {
                    database.replaceSnapshot(preview.backup)
                }
            } else {
                database.replaceSnapshot(preview.backup)
            }
            changed(AppStrings.forCode(preview.backup.settings.language).backupRestored)
        } else {
            val added = database.mergeIntakes(preview.entries, preview.drinks)
            changed(strings().importedSummary(added, preview.entries.size - added))
        }
        dismissPreview()
    }

    fun export(uri: Uri) = ioAction {
        val s = strings()
        require(!mediaMode) { s.githubModeReadOnly }
        val snapshot = mutex.withLock { database.snapshot() }
        val text = BackupCodec.encode(snapshot)
        app.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8)); out.flush()
        } ?: error(s.exportWriteFailed)
        val readBack = app.contentResolver.openInputStream(uri)?.use {
            it.readTrackerBytes().toString(Charsets.UTF_8)
        } ?: error(s.exportReadBackFailed)
        check(BackupCodec.decode(readBack) == snapshot) { s.exportVerifyFailed }
        mutableState.update { it.copy(message = s.backupExported) }
    }

    fun chooseFolder(uri: Uri) = ioAction {
        val s = strings()
        folderMutex.withLock {
            require(!mediaMode) { s.githubModeReadOnly }
            val previousFolder = backups.folder
            val newStorage = DocumentTreeStorage(app, uri)
            newStorage.persistReadWritePermission()
            try {
                val snapshot = mutex.withLock { database.snapshot() }
                // Verify writing before storing the new location.
                TrackerBackupQueue.exclusive {
                    writeRollingProceduralBackup(app, uri, BackupCodec.encode(snapshot)) {
                        runCatching { BackupCodec.decode(it) }.isSuccess
                    }
                    backups.folder = uri.toString()
                }
                if (previousFolder != null && previousFolder != uri.toString()) {
                    DocumentTreeStorage(app, Uri.parse(previousFolder)).releaseReadWritePermission()
                }
            } catch (e: Exception) {
                if (previousFolder != uri.toString()) newStorage.releaseReadWritePermission()
                throw e
            }
            mutableState.update { it.copy(backupFolder = backups.folder, message = s.externalFolderEnabled) }
        }
    }

    fun disableFolder() = ioAction(showProgress = false) {
        folderMutex.withLock {
            val previousFolder = backups.folder
            backups.folder = null
            if (previousFolder != null) DocumentTreeStorage(app, Uri.parse(previousFolder)).releaseReadWritePermission()
            mutableState.update { it.copy(backupFolder = null) }
        }
    }
    override fun onCleared() { database.close(); super.onCleared() }
}
