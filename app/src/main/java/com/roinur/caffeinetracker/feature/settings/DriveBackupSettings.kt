package com.roinur.caffeinetracker.feature.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.roinur.caffeinetracker.core.ui.i18n.LocalStrings
import com.roinur.caffeinetracker.data.backup.drive.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DriveBackupSettings(allowed: Boolean, onRestore: (String) -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val state = remember(context) { DriveBackupState(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        state.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { state.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val account = remember(revision) { state.account }
    val enabled = remember(revision) { state.enabled }
    val wifiOnly = remember(revision) { state.wifiOnly }
    val statusText = remember(revision) { state.status }
    val lastSuccess = remember(revision) { state.lastSuccess }
    val expectedLatestId = remember(revision) { state.expectedLatestId }
    val lineageEstablished = remember(revision) { state.lineageEstablished }
    val conflictPending = remember(revision) { state.conflictPending }
    val discoveredRemoteCount = remember(revision) { state.discoveredRemoteCount }
    val discoveredRemoteLatestAt = remember(revision) { state.discoveredRemoteLatestAt }

    var busy by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<DriveBackupFile>>(emptyList()) }
    var showBackups by rememberSaveable { mutableStateOf(false) }
    var confirmSimpleEnable by remember { mutableStateOf(false) }
    var pendingSimpleRemoteFiles by remember { mutableStateOf<List<DriveBackupFile>>(emptyList()) }
    var existingBackupPrompt by remember { mutableStateOf<DriveEnableDecision.RequireExistingBackupOptIn?>(null) }
    var existingOverrideChecked by remember { mutableStateOf(false) }
    var conflictPrompt by remember { mutableStateOf<DriveEnableDecision.ResolveConflictFirst?>(null) }
    var conflictEnableAfterResolve by remember { mutableStateOf(false) }
    var conflictOverrideChecked by remember { mutableStateOf(false) }
    var selectedAccount by rememberSaveable { mutableStateOf("") }
    val client = remember(context) { Identity.getAuthorizationClient(context) }

    fun error(error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        state.status(
            if (error is com.google.android.gms.common.api.ApiException) {
                strings.driveAuthFailed(error.statusCode)
            } else error.message ?: strings.driveActionFailed
        )
        busy = false
    }

    fun perform(action: (GoogleDriveTransport, String) -> Unit) {
        val target = state.account
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val token = DriveBackupSync.authorize(context, target)
                    check(state.account == target) { strings.driveAccountChanged }
                    action(GoogleDriveTransport(token, context.packageName), token)
                }
            } catch (exception: Exception) {
                error(exception)
            } finally {
                busy = false
            }
        }
    }

    fun openVerifiedBackups() {
        perform { transport, _ ->
            val discovered = transport.listVerified()
            scope.launch { files = discovered; showBackups = true }
        }
    }

    fun openConflictResolution(enableAfterResolve: Boolean) {
        perform { transport, _ ->
            val discovered = transport.listVerified()
            val competing = DriveBackupRetention.detectUnacknowledgedCompeting(
                verifiedFiles = discovered,
                expectedLatestId = discovered.firstOrNull()?.id.orEmpty(),
                acknowledgedCompetingIds = state.acknowledgedCompetingIds
            )
            scope.launch {
                files = discovered
                conflictEnableAfterResolve = enableAfterResolve
                conflictOverrideChecked = false
                conflictPrompt = DriveEnableDecision.ResolveConflictFirst(
                    expectedLatestId = state.expectedLatestId,
                    remoteFiles = discovered,
                    competingFiles = competing
                )
            }
        }
    }

    fun uploadNow(transport: GoogleDriveTransport, token: String, targetAccount: String) {
        try {
            val uploaded = DriveBackupSync.upload(context, token, targetAccount, force = true)
            if (uploaded?.hasCompetingVersions == true) {
                val discovered = runCatching { transport.listVerified() }.getOrDefault(emptyList())
                val competing = DriveBackupRetention.detectUnacknowledgedCompeting(
                    verifiedFiles = discovered,
                    expectedLatestId = discovered.firstOrNull()?.id.orEmpty(),
                    acknowledgedCompetingIds = state.acknowledgedCompetingIds
                )
                scope.launch {
                    files = discovered
                    conflictEnableAfterResolve = false
                    conflictOverrideChecked = false
                    conflictPrompt = DriveEnableDecision.ResolveConflictFirst(
                        expectedLatestId = uploaded.id,
                        remoteFiles = discovered,
                        competingFiles = competing
                    )
                }
            }
        } catch (conflict: DriveBackupConflictException) {
            scope.launch {
                files = conflict.remoteFiles
                conflictEnableAfterResolve = false
                conflictOverrideChecked = false
                conflictPrompt = DriveEnableDecision.ResolveConflictFirst(
                    expectedLatestId = conflict.expectedLatestId,
                    remoteFiles = conflict.remoteFiles,
                    competingFiles = conflict.competingFiles
                )
            }
        } catch (optIn: DriveExistingBackupOptInRequiredException) {
            scope.launch {
                files = optIn.remoteFiles
                existingOverrideChecked = false
                existingBackupPrompt = DriveEnableDecision.RequireExistingBackupOptIn(
                    remoteFiles = optIn.remoteFiles,
                    localSummary = optIn.localSummary
                )
            }
        }
    }

    fun accept(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank() || GoogleDriveTransport.SCOPE !in result.grantedScopes) {
            state.status(strings.drivePermissionDenied)
            busy = false
            return
        }
        scope.launch {
            try {
                val discovered = withContext(Dispatchers.IO) { GoogleDriveTransport(token, context.packageName).listVerified() }
                // Connecting never uploads or enables automatic backup, especially on a new phone.
                if (state.account != selectedAccount) {
                    state.resetAccountSyncState(selectedAccount)
                    DriveBackupSync.syncSchedule(context)
                } else {
                    state.preferences.edit().putString("account", selectedAccount).apply()
                }
                val remoteLatestAt = discovered.firstOrNull()?.createdAt.orEmpty()
                state.preferences.edit()
                    .putInt("discovered_remote_count", discovered.size)
                    .putString("discovered_remote_latest_at", remoteLatestAt)
                    .apply()
                files = discovered
                val drifted = state.lineageEstablished && (
                    (discovered.isEmpty() && state.expectedLatestId.isNotBlank()) ||
                        (discovered.isNotEmpty() && (
                            discovered.first().id != state.expectedLatestId ||
                                DriveBackupRetention.detectUnacknowledgedCompeting(
                                    verifiedFiles = discovered,
                                    expectedLatestId = discovered.first().id,
                                    acknowledgedCompetingIds = state.acknowledgedCompetingIds
                                ).isNotEmpty()
                            ))
                    )
                if (drifted) {
                    state.preferences.edit()
                        .putBoolean("conflict_pending", true)
                        .putString("conflict_message", strings.driveConflictStatus)
                        .putString("status", strings.driveConflictStatus)
                        .apply()
                } else {
                    state.status(
                        if (discovered.isEmpty()) strings.driveConnectedEmpty
                        else strings.driveConnectedFound(discovered.size)
                    )
                }
            } catch (exception: Exception) {
                error(exception)
            } finally {
                busy = false
            }
        }
    }

    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            state.status(strings.driveAuthCancelled)
            busy = false
        } else try {
            accept(client.getAuthorizationResultFromIntent(result.data))
        } catch (exception: Exception) {
            error(exception)
        }
    }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode != Activity.RESULT_OK || name.isNullOrBlank()) {
            busy = false
        } else {
            selectedAccount = name
            val request = AuthorizationRequest.builder()
                .setAccount(Account(name, "com.google"))
                .setRequestedScopes(listOf(Scope(GoogleDriveTransport.SCOPE)))
                .build()
            client.authorize(request).addOnSuccessListener { authorization ->
                try {
                    if (authorization.hasResolution()) {
                        authorization.pendingIntent?.let {
                            resolution.launch(IntentSenderRequest.Builder(it.intentSender).build())
                        } ?: run {
                            busy = false
                            state.status(strings.driveNoConsentDialog)
                        }
                    } else {
                        accept(authorization)
                    }
                } catch (exception: Exception) {
                    error(exception)
                }
            }.addOnFailureListener { error(it) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(strings.driveTitle, style = MaterialTheme.typography.titleMedium)
        Text(strings.driveSubtitle, style = MaterialTheme.typography.bodySmall)
        if (allowed && account.isNotBlank()) Text(account, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (allowed) statusText.ifBlank { strings.driveNotConnected } else strings.driveUnavailable,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (allowed) {
            Text(strings.driveLastVerified(lastSuccess.ifBlank { strings.driveNever }), style = MaterialTheme.typography.bodySmall)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))

        if (allowed && account.isNotBlank() && conflictPending) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        strings.driveConflictTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        strings.driveConflictBannerBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    SettingsActionButton(
                        primary = true,
                        onClick = { openVerifiedBackups() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.driveReviewAndRestore) }
                    SettingsActionButton(
                        onClick = { openConflictResolution(enableAfterResolve = !enabled) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.driveResolveConflict) }
                }
            }
        } else if (allowed && account.isNotBlank() && !lineageEstablished && discoveredRemoteCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        strings.driveExistingBannerTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        strings.driveExistingBannerBody(discoveredRemoteCount, discoveredRemoteLatestAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    SettingsActionButton(
                        primary = true,
                        onClick = { openVerifiedBackups() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.driveRestoreFirstRecommended) }
                }
            }
        }

        SettingsActionButton(
            onClick = {
                busy = true
                try {
                    chooser.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null))
                } catch (exception: Exception) {
                    error(exception)
                }
            },
            enabled = allowed && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (account.isBlank()) strings.driveConnect else strings.driveReconnect)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.driveAutoToggle, Modifier.weight(1f))
            Switch(
                checked = enabled,
                enabled = allowed && !busy && account.isNotBlank(),
                onCheckedChange = { on ->
                    if (on) {
                        perform { transport, _ ->
                            val discovered = transport.listVerified()
                            val localSummary = CloudBackupSnapshots.localSummary(context)
                            val decision = DriveBackupPolicy.evaluateEnable(localSummary, discovered, state.toLineage())
                            scope.launch {
                                files = discovered
                                when (decision) {
                                    is DriveEnableDecision.AllowFirstBackup -> {
                                        pendingSimpleRemoteFiles = emptyList()
                                        confirmSimpleEnable = true
                                    }
                                    is DriveEnableDecision.AllowEstablishedSync -> {
                                        pendingSimpleRemoteFiles = discovered
                                        confirmSimpleEnable = true
                                    }
                                    is DriveEnableDecision.RequireExistingBackupOptIn -> {
                                        existingOverrideChecked = false
                                        existingBackupPrompt = decision
                                    }
                                    is DriveEnableDecision.ResolveConflictFirst -> {
                                        conflictEnableAfterResolve = true
                                        conflictOverrideChecked = false
                                        conflictPrompt = decision
                                    }
                                }
                            }
                        }
                    } else {
                        state.preferences.edit().putBoolean("enabled", false).apply()
                        DriveBackupSync.syncSchedule(context)
                        state.status(strings.drivePaused)
                    }
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.driveWifiOnly, Modifier.weight(1f))
            Switch(
                checked = wifiOnly,
                enabled = allowed && !busy,
                onCheckedChange = {
                    state.preferences.edit().putBoolean("wifi_only", it).apply()
                    DriveBackupSync.syncSchedule(context)
                    if (state.enabled && !state.conflictPending) DriveBackupSync.enqueue(context, replace = true)
                }
            )
        }
        SettingsActionButton(
            primary = true,
            onClick = {
                if (conflictPending) {
                    openConflictResolution(enableAfterResolve = false)
                } else {
                    perform { transport, token -> uploadNow(transport, token, account) }
                }
            },
            enabled = allowed && !busy && enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text(strings.driveBackupNow) }
        SettingsActionButton(
            onClick = { openVerifiedBackups() },
            enabled = allowed && !busy && account.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(strings.driveRestoreFromDrive) }
        if (account.isNotBlank()) {
            SettingsActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    DriveBackupSync.clearPendingRestore()
                    state.resetAccountSyncState(newAccount = null)
                    DriveBackupSync.syncSchedule(context)
                    files = emptyList()
                },
                enabled = allowed && !busy
            ) { Text(strings.driveDisconnect) }
        }
        Text(
            strings.driveFooter,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmSimpleEnable && allowed) AlertDialog(
        onDismissRequest = { confirmSimpleEnable = false },
        title = { Text(strings.driveConfirmTitle) },
        text = { Text(strings.driveConfirmBody) },
        confirmButton = {
            TextButton(
                onClick = {
                    val snapshotFiles = pendingSimpleRemoteFiles
                    confirmSimpleEnable = false
                    perform { _, _ ->
                        DriveBackupSync.enableFromDecision(
                            context = context,
                            expectedAccount = account,
                            remoteFiles = snapshotFiles,
                            deliberateOverride = false
                        )
                    }
                },
                enabled = allowed && !busy
            ) { Text(strings.driveEnableBackup) }
        },
        dismissButton = { TextButton(onClick = { confirmSimpleEnable = false }) { Text(strings.cancel) } }
    )

    existingBackupPrompt?.takeIf { allowed }?.let { prompt ->
        val latestAt = prompt.remoteFiles.firstOrNull()?.createdAt.orEmpty()
        AlertDialog(
            onDismissRequest = { existingBackupPrompt = null },
            title = { Text(strings.driveExistingDialogTitle) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (prompt.localSummary.isEmptyOrFresh) {
                            strings.driveExistingEmptyWarning(prompt.remoteFiles.size, latestAt)
                        } else {
                            strings.driveExistingNonEmptyWarning(
                                prompt.remoteFiles.size,
                                latestAt,
                                prompt.localSummary.intakeCount
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SettingsActionButton(
                        primary = true,
                        onClick = {
                            existingBackupPrompt = null
                            files = prompt.remoteFiles
                            showBackups = true
                        },
                        enabled = allowed && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.driveRestoreFirstRecommended) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { existingOverrideChecked = !existingOverrideChecked }
                    ) {
                        Checkbox(
                            checked = existingOverrideChecked,
                            onCheckedChange = { existingOverrideChecked = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (prompt.localSummary.isEmptyOrFresh) strings.driveExistingEmptyOverrideAck
                            else strings.driveExistingOverrideAck,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    SettingsActionButton(
                        destructive = true,
                        onClick = {
                            existingBackupPrompt = null
                            perform { _, _ ->
                                DriveBackupSync.enableFromDecision(
                                    context = context,
                                    expectedAccount = account,
                                    remoteFiles = prompt.remoteFiles,
                                    deliberateOverride = true
                                )
                            }
                        },
                        enabled = allowed && !busy && existingOverrideChecked,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.driveOverrideExistingButton) }
                }
            },
            confirmButton = {
                TextButton(onClick = { existingBackupPrompt = null }) { Text(strings.cancel) }
            }
        )
    }

    conflictPrompt?.takeIf { allowed }?.let { prompt ->
        val latestAt = prompt.remoteFiles.firstOrNull()?.createdAt.orEmpty()
        AlertDialog(
            onDismissRequest = { conflictPrompt = null },
            title = { Text(strings.driveConflictTitle) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (prompt.remoteFiles.isEmpty()) strings.driveConflictRemoteMissingBody
                        else strings.driveConflictDialogBody(prompt.remoteFiles.size, latestAt),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (prompt.remoteFiles.isNotEmpty()) {
                        SettingsActionButton(
                            primary = true,
                            onClick = {
                                conflictPrompt = null
                                files = prompt.remoteFiles
                                showBackups = true
                            },
                            enabled = allowed && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(strings.driveReviewAndRestore) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { conflictOverrideChecked = !conflictOverrideChecked }
                    ) {
                        Checkbox(
                            checked = conflictOverrideChecked,
                            onCheckedChange = { conflictOverrideChecked = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.driveConflictOverrideAck, style = MaterialTheme.typography.bodySmall)
                    }
                    SettingsActionButton(
                        destructive = true,
                        onClick = {
                            val enableAfter = conflictEnableAfterResolve
                            conflictPrompt = null
                            perform { transport, token ->
                                DriveBackupSync.resolveConflictKeepLocal(
                                    context = context,
                                    expectedAccount = account,
                                    remoteFiles = prompt.remoteFiles
                                )
                                if (enableAfter) {
                                    DriveBackupSync.enableFromDecision(
                                        context = context,
                                        expectedAccount = account,
                                        remoteFiles = prompt.remoteFiles,
                                        deliberateOverride = true
                                    )
                                } else if (state.enabled) {
                                    uploadNow(transport, token, account)
                                }
                            }
                        },
                        enabled = allowed && !busy && conflictOverrideChecked,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.driveConflictKeepLocalButton) }
                }
            },
            confirmButton = {
                TextButton(onClick = { conflictPrompt = null }) { Text(strings.cancel) }
            }
        )
    }

    if (showBackups && allowed) {
        val classified = remember(files, expectedLatestId) {
            DriveBackupRetention.classify(files, expectedLatestId)
        }
        AlertDialog(
            onDismissRequest = { showBackups = false },
            title = { Text(strings.driveVerifiedTitle) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (classified.isEmpty()) Text(strings.driveNoVerifiedFound)
                    classified.forEach { entry ->
                        val roleLabel = when (entry.role) {
                            DriveVersionRole.LATEST -> strings.driveVersionLatest
                            DriveVersionRole.PREDECESSOR -> strings.driveVersionPredecessor
                            DriveVersionRole.COMPETING -> strings.driveVersionCompeting
                            DriveVersionRole.OLDER -> strings.driveVersionOlder
                        }
                        OutlinedButton(
                            enabled = allowed && !busy,
                            onClick = {
                                showBackups = false
                                perform { transport, _ ->
                                    val payload = DriveBackupSync.downloadForRestore(context, transport, account, entry.file.id)
                                    scope.launch { onRestore(payload) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(roleLabel, fontWeight = FontWeight.SemiBold)
                                Text(entry.file.createdAt, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBackups = false }) { Text(strings.close) } }
        )
    }
}
