package com.roinur.caffeinetracker.data.backup.drive

import android.accounts.Account
import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.roinur.caffeinetracker.core.diagnostics.GitHubMediaSession
import com.roinur.caffeinetracker.core.model.TrackerSnapshot
import com.roinur.caffeinetracker.core.ui.i18n.AppStrings
import com.roinur.caffeinetracker.data.database.CaffeineDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class DriveBackupState(context: Context) {
    // Account/grants/sync state are installation-specific, never part of a portable backup.
    val preferences = context.applicationContext.getSharedPreferences("caffeine_drive_backup", Context.MODE_PRIVATE)
    val account: String get() = preferences.getString("account", "").orEmpty()
    val enabled: Boolean get() = preferences.getBoolean("enabled", false)
    val wifiOnly: Boolean get() = preferences.getBoolean("wifi_only", true)
    val status: String get() = preferences.getString("status", "").orEmpty()
    val lastSuccess: String get() = preferences.getString("last_success", "").orEmpty()
    val lastFingerprint: String get() = preferences.getString("fingerprint", "").orEmpty()
    val expectedLatestId: String get() = preferences.getString("latest_id", "").orEmpty()
    val cleanupPending: Boolean get() = preferences.getBoolean("cleanup_pending", false)
    val lineageEstablished: Boolean
        get() = preferences.getBoolean("lineage_established", false) ||
            expectedLatestId.isNotBlank() ||
            lastSuccess.isNotBlank()
    val acknowledgedCompetingIds: Set<String>
        get() = preferences.getStringSet("acknowledged_competing_ids", emptySet())?.toSet().orEmpty()
    val overrideApprovedRemoteId: String get() = preferences.getString("override_approved_remote_id", "").orEmpty()
    val conflictPending: Boolean get() = preferences.getBoolean("conflict_pending", false)
    val conflictMessage: String get() = preferences.getString("conflict_message", "").orEmpty()
    val discoveredRemoteCount: Int get() = preferences.getInt("discovered_remote_count", 0)
    val discoveredRemoteLatestAt: String get() = preferences.getString("discovered_remote_latest_at", "").orEmpty()
    val installationId: String
        get() {
            val existing = preferences.getString("installation_id", "").orEmpty()
            if (existing.isNotBlank()) return existing
            val generated = UUID.randomUUID().toString()
            preferences.edit().putString("installation_id", generated).apply()
            return generated
        }

    fun toLineage(): DriveInstallationLineage = DriveInstallationLineage(
        expectedLatestId = expectedLatestId,
        lineageEstablished = lineageEstablished,
        acknowledgedCompetingIds = acknowledgedCompetingIds,
        overrideApprovedRemoteId = overrideApprovedRemoteId,
        conflictPending = conflictPending
    )

    fun status(text: String) { preferences.edit().putString("status", text).apply() }

    fun resetAccountSyncState(newAccount: String? = null) {
        val currentInstallId = installationId
        val keepWifiOnly = wifiOnly
        preferences.edit().apply {
            clear()
            putString("installation_id", currentInstallId)
            putBoolean("wifi_only", keepWifiOnly)
            if (!newAccount.isNullOrBlank()) putString("account", newAccount)
            apply()
        }
    }
}

internal object CloudBackupSnapshots {
    data class PreparedSnapshot(
        val prepared: CaffeineCloudBackup.Prepared?,
        val localSummary: DriveLocalSummary
    )

    fun localSummary(context: Context): DriveLocalSummary {
        check(!GitHubMediaSession.active) { "Molnbackup är avstängd i GitHub-läge." }
        val snapshot = CaffeineDatabase(context.applicationContext, "caffeine_tracker.db").use { it.snapshot() }
        return DriveLocalSummary.fromSnapshot(snapshot)
    }

    fun prepare(context: Context, previousFingerprint: String, force: Boolean): PreparedSnapshot {
        check(!GitHubMediaSession.active) { "Molnbackup är avstängd i GitHub-läge." }
        val snapshot = CaffeineDatabase(context.applicationContext, "caffeine_tracker.db").use { it.snapshot() }
        val summary = DriveLocalSummary.fromSnapshot(snapshot)
        val fingerprint = CaffeineCloudBackup.fingerprint(snapshot)
        if (!force && fingerprint == previousFingerprint) return PreparedSnapshot(null, summary)
        return PreparedSnapshot(CaffeineCloudBackup.prepare(snapshot, context.cacheDir, fingerprint), summary)
    }
}

internal object DriveBackupSync {
    data class PendingDriveRestore(
        val account: String,
        val restoredFileId: String,
        val remoteHeadId: String,
        val remoteIds: Set<String>,
        val fingerprint: String,
        val localHeadId: String
    )

    val lock = ReentrantLock()
    private val changeRevision = java.util.concurrent.atomic.AtomicLong(0)
    @Volatile private var pendingRestore: PendingDriveRestore? = null
    private const val PERIODIC = "caffeine-drive-backup-periodic"
    private const val PENDING = "caffeine-drive-backup-pending"

    fun downloadForRestore(
        context: Context,
        transport: DriveBackupTransport,
        account: String,
        selectedId: String
    ): String = lock.withLock {
        val state = DriveBackupState(context)
        check(state.account == account) { strings(context).driveAccountChanged }
        val remoteFiles = transport.listVerified()
        check(remoteFiles.any { it.id == selectedId }) { strings(context).driveConflictStatus }
        val decoded = CaffeineCloudBackup.decode(transport.download(selectedId))
        pendingRestore = PendingDriveRestore(
            account = account,
            restoredFileId = selectedId,
            remoteHeadId = remoteFiles.first().id,
            remoteIds = remoteFiles.map { it.id }.toSet(),
            fingerprint = decoded.fingerprint,
            localHeadId = state.expectedLatestId
        )
        decoded.payload
    }

    fun clearPendingRestore() {
        pendingRestore = null
    }

    fun applyDriveRestore(context: Context, restoredSnapshot: TrackerSnapshot, replace: () -> Unit) = lock.withLock {
        val pending = pendingRestore
        val state = DriveBackupState(context)
        check(pending != null && state.account == pending.account &&
            CaffeineCloudBackup.fingerprint(restoredSnapshot) == pending.fingerprint
        ) { strings(context).driveAccountChanged }
        replace()
        pendingRestore = null
        if (state.expectedLatestId != pending.localHeadId) {
            // Our worker published while the preview was open. Preserve that newer local head
            // and schedule a snapshot of the restored data through the normal change path.
            state.preferences.edit().remove("fingerprint").apply()
            return@withLock
        }
        val s = AppStrings.forCode(restoredSnapshot.settings.language)
        state.preferences.edit().apply {
            putString("latest_id", pending.remoteHeadId)
            putBoolean("lineage_established", true)
            putStringSet("acknowledged_competing_ids", HashSet(pending.remoteIds))
            putBoolean("conflict_pending", false)
            remove("conflict_message")
            putInt("discovered_remote_count", 0)
            remove("discovered_remote_latest_at")
            remove("override_approved_remote_id")
            if (pending.restoredFileId == pending.remoteHeadId) {
                putString("fingerprint", pending.fingerprint)
            } else {
                remove("fingerprint")
            }
            putString("status", if (state.enabled) s.driveRestoredEnabledStatus else s.driveRestoredReadyStatus)
            apply()
        }
    }

    fun syncSchedule(context: Context) {
        if (GitHubMediaSession.active) return
        val state = DriveBackupState(context)
        val manager = WorkManager.getInstance(context)
        if (!state.enabled || state.account.isBlank()) {
            manager.cancelUniqueWork(PERIODIC)
            manager.cancelUniqueWork(PENDING)
            return
        }
        manager.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DriveFullBackupWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints(state))
                .build()
        )
    }

    private fun strings(context: Context): AppStrings = runCatching {
        CaffeineDatabase(context.applicationContext, "caffeine_tracker.db").use {
            AppStrings.forCode(it.findSettings().language)
        }
    }.getOrDefault(AppStrings.SV)

    fun enqueue(context: Context, replace: Boolean = false) {
        if (GitHubMediaSession.active) return
        changeRevision.incrementAndGet()
        val state = DriveBackupState(context)
        if (!state.enabled || state.account.isBlank() || state.conflictPending) return
        state.status(strings(context).driveQueuedStatus)
        WorkManager.getInstance(context).enqueueUniqueWork(
            PENDING,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DriveFullBackupWorker>()
                .setConstraints(constraints(state))
                .setInitialDelay(15, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }

    fun enableFromDecision(
        context: Context,
        expectedAccount: String,
        remoteFiles: List<DriveBackupFile>,
        deliberateOverride: Boolean
    ) = lock.withLock {
        val state = DriveBackupState(context)
        val s = strings(context)
        check(state.account == expectedAccount) { s.driveAccountChanged }
        val remoteHeadId = remoteFiles.firstOrNull()?.id.orEmpty()
        if (remoteFiles.isNotEmpty() && !deliberateOverride) {
            check(state.lineageEstablished && state.expectedLatestId == remoteHeadId && !state.conflictPending) {
                s.driveExistingBackupBlockedStatus
            }
        }
        state.preferences.edit().apply {
            putString("latest_id", remoteHeadId)
            putBoolean("lineage_established", true)
            if (deliberateOverride) {
                putStringSet("acknowledged_competing_ids", HashSet(remoteFiles.map { it.id }))
                putString("override_approved_remote_id", remoteHeadId)
                remove("fingerprint")
            }
            putBoolean("conflict_pending", false)
            remove("conflict_message")
            putInt("discovered_remote_count", 0)
            remove("discovered_remote_latest_at")
            putBoolean("enabled", true)
            commit()
        }
        syncSchedule(context)
        enqueue(context, replace = true)
    }

    fun resolveConflictKeepLocal(
        context: Context,
        expectedAccount: String,
        remoteFiles: List<DriveBackupFile>
    ) = lock.withLock {
        val state = DriveBackupState(context)
        val s = strings(context)
        check(state.account == expectedAccount) { s.driveAccountChanged }
        val remoteHeadId = remoteFiles.firstOrNull()?.id.orEmpty()
        state.preferences.edit().apply {
            putString("latest_id", remoteHeadId)
            putBoolean("lineage_established", true)
            putStringSet("acknowledged_competing_ids", HashSet(remoteFiles.map { it.id }))
            putString("override_approved_remote_id", remoteHeadId)
            putBoolean("conflict_pending", false)
            remove("conflict_message")
            putInt("discovered_remote_count", 0)
            remove("discovered_remote_latest_at")
            remove("fingerprint")
            commit()
        }
    }

    private fun recordConflict(
        state: DriveBackupState,
        remoteFiles: List<DriveBackupFile>,
        statusText: String
    ) {
        state.preferences.edit().apply {
            putBoolean("conflict_pending", true)
            putString("conflict_message", statusText)
            putString("status", statusText)
            putInt("discovered_remote_count", remoteFiles.size)
            putString("discovered_remote_latest_at", remoteFiles.firstOrNull()?.createdAt.orEmpty())
            commit()
        }
    }

    private fun recordExistingBackupBlock(
        context: Context,
        state: DriveBackupState,
        remoteFiles: List<DriveBackupFile>,
        statusText: String
    ) {
        state.preferences.edit().apply {
            putBoolean("enabled", false)
            putInt("discovered_remote_count", remoteFiles.size)
            putString("discovered_remote_latest_at", remoteFiles.firstOrNull()?.createdAt.orEmpty())
            putString("status", statusText)
            commit()
        }
        syncSchedule(context)
    }

    private fun constraints(state: DriveBackupState) = Constraints.Builder()
        .setRequiredNetworkType(if (state.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    /** Runs on IO only. Tokens are short-lived and never persisted or logged. */
    fun authorize(context: Context, account: String): String {
        val request = AuthorizationRequest.builder()
            .setAccount(Account(account, "com.google"))
            .setRequestedScopes(listOf(Scope(GoogleDriveTransport.SCOPE)))
            .build()
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request), 40, TimeUnit.SECONDS)
        check(!result.hasResolution()) { "Öppna Inställningar → Backup och import och anslut Google Drive igen för att förnya behörigheten." }
        return requireNotNull(result.accessToken?.takeIf(String::isNotBlank)) { "Google beviljade inte Drive-åtkomst." }
    }

    fun upload(context: Context, token: String, expectedAccount: String, force: Boolean = false): DriveBackupFile? = lock.withLock {
        val s = strings(context)
        val state = DriveBackupState(context)
        check(state.enabled && state.account == expectedAccount) { s.driveAccountChanged }
        val transport = GoogleDriveTransport(token, context.packageName)
        val repository = DriveBackupRepository(transport)

        if (state.conflictPending) {
            val remoteFiles = runCatching { transport.listVerified() }.getOrDefault(emptyList())
            val message = state.conflictMessage.ifBlank { s.driveConflictStatus }
            recordConflict(state, remoteFiles, message)
            throw DriveBackupConflictException(
                remoteFiles = remoteFiles,
                expectedLatestId = state.expectedLatestId,
                message = message
            )
        }

        val capturedRevision = changeRevision.get()
        state.status(s.drivePreparingStatus)
        val preparedSnapshot = CloudBackupSnapshots.prepare(context, state.lastFingerprint, force)
        val prepared = preparedSnapshot.prepared
        val uploaded = try {
            val remoteFiles = transport.listVerified()
            when (val decision = DriveBackupPolicy.evaluateUpload(preparedSnapshot.localSummary, remoteFiles, state.toLineage())) {
                is DriveUploadPreflight.BlockedExistingBackup -> {
                    recordExistingBackupBlock(context, state, decision.remoteFiles, s.driveExistingBackupBlockedStatus)
                    throw DriveExistingBackupOptInRequiredException(
                        remoteFiles = decision.remoteFiles,
                        localSummary = decision.localSummary,
                        message = s.driveExistingBackupBlockedStatus
                    )
                }
                is DriveUploadPreflight.BlockedConflict -> {
                    recordConflict(state, decision.remoteFiles, s.driveConflictStatus)
                    throw DriveBackupConflictException(
                        remoteFiles = decision.remoteFiles,
                        expectedLatestId = decision.expectedLatestId,
                        competingFiles = decision.competingFiles,
                        message = s.driveConflictStatus
                    )
                }
                is DriveUploadPreflight.Proceed -> {
                    if (prepared == null) {
                        if (state.cleanupPending && state.expectedLatestId.isNotBlank()) {
                            val cleaned = try {
                                repository.cleanupPreviousVersions(state.expectedLatestId, state.acknowledgedCompetingIds)
                            } catch (conflict: DriveBackupConflictException) {
                                recordConflict(state, conflict.remoteFiles, s.driveConflictStatus)
                                throw conflict
                            }
                            state.preferences.edit().putBoolean("cleanup_pending", !cleaned).apply()
                            state.status(
                                if (cleaned) s.driveUpToDateStatus
                                else s.driveUpToDateCleanupPending
                            )
                        } else {
                            state.status(s.driveUpToDateStatus)
                        }
                        return null
                    }
                    state.status(s.driveUploadingStatus)
                    try {
                        repository.publish(
                            prepared = prepared,
                            expectedLatestId = decision.expectedLatestId,
                            writerId = state.installationId,
                            acknowledgedCompetingIds = decision.acknowledgedCompetingIds,
                            stillAllowed = { state.enabled && state.account == expectedAccount }
                        )
                    } catch (conflict: DriveBackupConflictException) {
                        recordConflict(state, conflict.remoteFiles, s.driveConflictStatus)
                        throw conflict
                    }
                }
            }
        } finally {
            prepared?.file?.delete()
        }

        check(state.enabled && state.account == expectedAccount) { s.driveAccountChanged }
        val statusText = when {
            uploaded.hasCompetingVersions -> s.driveCompetingDetectedStatus
            uploaded.cleanupPending -> s.driveBackedUpCleanupPending
            else -> s.driveBackedUpStatus
        }
        check(
            state.preferences.edit().apply {
                putString("latest_id", uploaded.id)
                putString("fingerprint", requireNotNull(prepared).fingerprint)
                putBoolean("lineage_established", true)
                putBoolean("cleanup_pending", uploaded.cleanupPending)
                putString("last_success", Instant.now().toString())
                remove("override_approved_remote_id")
                if (uploaded.hasCompetingVersions) {
                    putBoolean("conflict_pending", true)
                    putString("conflict_message", statusText)
                } else {
                    putBoolean("conflict_pending", false)
                    remove("conflict_message")
                    putInt("discovered_remote_count", 0)
                    remove("discovered_remote_latest_at")
                }
                putString("status", statusText)
            }.commit()
        ) {
            s.driveActionFailed
        }
        if (!uploaded.hasCompetingVersions && changeRevision.get() != capturedRevision) {
            enqueue(context, replace = true)
        }
        uploaded
    }
}

class DriveFullBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (GitHubMediaSession.active) return@withContext Result.success()
        val state = DriveBackupState(applicationContext)
        if (!state.enabled || state.account.isBlank() || state.conflictPending) return@withContext Result.success()
        try {
            val account = state.account
            DriveBackupSync.upload(applicationContext, DriveBackupSync.authorize(applicationContext, account), account)
            Result.success()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (error !is DriveBackupConflictException && error !is DriveExistingBackupOptInRequiredException) {
                val message = error.message ?: "Google Drive-backup misslyckades; tidigare backuper är oförändrade."
                state.status(message)
            }
            val authError = (error as? java.util.concurrent.ExecutionException)?.cause as? com.google.android.gms.common.api.ApiException
            when {
                error is DriveBackupConflictException || error is DriveExistingBackupOptInRequiredException -> Result.failure()
                error is java.util.concurrent.TimeoutException || authError?.statusCode in listOf(7, 8, 15) -> Result.retry()
                error is DriveHttpException && error.code in listOf(401, 403) -> Result.failure()
                error is DriveHttpException && (error.code == 429 || error.code >= 500) -> Result.retry()
                error is java.io.IOException -> Result.retry()
                else -> Result.failure()
            }
        }
    }
}
