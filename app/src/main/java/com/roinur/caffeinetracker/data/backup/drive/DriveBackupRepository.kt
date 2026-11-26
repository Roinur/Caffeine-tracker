package com.roinur.caffeinetracker.data.backup.drive

import java.io.File

internal data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdAt: String,
    val cleanupPending: Boolean = false,
    val parentId: String = "",
    val writerId: String = "",
    val competingIds: List<String> = emptyList()
) {
    val isLegacy: Boolean get() = parentId.isBlank() && writerId.isBlank()
    val hasCompetingVersions: Boolean get() = competingIds.isNotEmpty()
}

internal data class DriveDownloadChecksum(val bytes: Long, val sha256: String)

internal class DriveBackupConflictException(
    val remoteFiles: List<DriveBackupFile>,
    val expectedLatestId: String,
    val competingFiles: List<DriveBackupFile> = emptyList(),
    message: String
) : IllegalStateException(message)

internal class DriveExistingBackupOptInRequiredException(
    val remoteFiles: List<DriveBackupFile>,
    val localSummary: DriveLocalSummary,
    message: String
) : IllegalStateException(message)

internal interface DriveBackupTransport {
    fun listVerified(): List<DriveBackupFile>
    fun uploadPending(text: String, parentId: String = "", writerId: String = ""): DriveBackupFile
    fun uploadPending(file: File, parentId: String = "", writerId: String = ""): DriveBackupFile
    fun download(id: String): String
    fun downloadChecksum(id: String): DriveDownloadChecksum
    fun markVerified(id: String, parentId: String = "", writerId: String = "")
    fun delete(id: String)
}

/**
 * Never changes healthy cloud backups before read-back validation succeeds.
 * Preserves at least the direct successful predecessor and any competing verified versions.
 */
internal class DriveBackupRepository(private val transport: DriveBackupTransport) {
    /** Retry cleanup without uploading unchanged data; never touch latest, predecessor, or competing versions. */
    fun cleanupPreviousVersions(
        expectedLatestId: String,
        acknowledgedCompetingIds: Set<String> = emptySet()
    ): Boolean {
        val files = transport.listVerified()
        if (expectedLatestId.isBlank() || files.firstOrNull()?.id != expectedLatestId) {
            throw DriveBackupConflictException(
                remoteFiles = files,
                expectedLatestId = expectedLatestId,
                message = "Molnbackupen har ändrats. Granska Drive-backuperna innan äldre versioner tas bort."
            )
        }
        val plan = DriveBackupRetention.plan(files, expectedLatestId, acknowledgedCompetingIds)
        if (plan.unacknowledgedCompeting.isNotEmpty()) {
            throw DriveBackupConflictException(
                remoteFiles = files,
                expectedLatestId = expectedLatestId,
                competingFiles = plan.unacknowledgedCompeting,
                message = "Konkurrerande verifierad molnbackup hittades. Granska Drive-backuperna innan äldre versioner tas bort."
            )
        }
        return removePrevious(plan.prunable)
    }

    private fun removePrevious(files: List<DriveBackupFile>): Boolean {
        // Start at the old end of the chain. A failed removal must leave its link to the head intact.
        for (file in files) {
            if (runCatching { transport.delete(file.id) }.isFailure) return false
        }
        return true
    }

    fun publish(
        text: String,
        expectedLatestId: String,
        writerId: String = "",
        acknowledgedCompetingIds: Set<String> = emptySet()
    ): DriveBackupFile {
        CaffeineCloudBackup.decode(text)
        return publish(
            expectedLatestId = expectedLatestId,
            writerId = writerId,
            acknowledgedCompetingIds = acknowledgedCompetingIds,
            upload = { transport.uploadPending(text, parentId = expectedLatestId, writerId = writerId) }
        ) { pending ->
            val readBack = transport.download(pending.id)
            check(readBack == text) { "Kontrolläsningen från Drive matchade inte uppladdningen. Tidigare backup är oförändrad." }
            CaffeineCloudBackup.decode(readBack)
        }
    }

    fun publish(
        prepared: CaffeineCloudBackup.Prepared,
        expectedLatestId: String,
        writerId: String = "",
        acknowledgedCompetingIds: Set<String> = emptySet(),
        stillAllowed: () -> Boolean = { true }
    ): DriveBackupFile {
        check(prepared.file.isFile && prepared.file.length() in 1L..CaffeineCloudBackup.MAX_BYTES.toLong()) {
            "Saknad eller för stor temporär molnbackup."
        }
        check(CaffeineCloudBackup.fileChecksum(prepared.file) == prepared.checksum) {
            "Temporär molnbackup ändrades före uppladdning. Inget laddades upp."
        }
        return publish(
            expectedLatestId = expectedLatestId,
            writerId = writerId,
            acknowledgedCompetingIds = acknowledgedCompetingIds,
            upload = { transport.uploadPending(prepared.file, parentId = expectedLatestId, writerId = writerId) },
            stillAllowed = stillAllowed
        ) { pending ->
            val readBack = transport.downloadChecksum(pending.id)
            check(readBack.bytes == prepared.file.length() && readBack.sha256 == prepared.checksum) {
                "Kontrolläsningen från Drive matchade inte uppladdningen. Tidigare backup är oförändrad."
            }
        }
    }

    private fun publish(
        expectedLatestId: String,
        writerId: String,
        acknowledgedCompetingIds: Set<String>,
        upload: () -> DriveBackupFile,
        stillAllowed: () -> Boolean = { true },
        verify: (DriveBackupFile) -> Unit
    ): DriveBackupFile {
        val before = transport.listVerified()
        if (before.firstOrNull()?.id.orEmpty() != expectedLatestId) {
            throw DriveBackupConflictException(
                remoteFiles = before,
                expectedLatestId = expectedLatestId,
                message = "En annan enhet har ändrat molnbackupen. Granska Drive-backuperna innan uppladdning från den här mobilen."
            )
        }
        val preCompeting = DriveBackupRetention.detectUnacknowledgedCompeting(
            verifiedFiles = before,
            expectedLatestId = expectedLatestId,
            acknowledgedCompetingIds = acknowledgedCompetingIds
        )
        if (preCompeting.isNotEmpty()) {
            throw DriveBackupConflictException(
                remoteFiles = before,
                expectedLatestId = expectedLatestId,
                competingFiles = preCompeting,
                message = "En konkurrerande verifierad molnbackup hittades. Granska Drive-backuperna innan uppladdning från den här mobilen."
            )
        }

        val beforeIds = before.map { it.id }.toSet()
        val pending = upload()
        try {
            verify(pending)
            check(stillAllowed()) { "Drive-backup pausades eller kontot ändrades under uppladdning." }
            // Best-effort pre-promotion conflict detection before marking our pending file verified.
            val prePromote = transport.listVerified()
            val concurrentBeforePromote = prePromote.filter { it.id !in beforeIds }
            if (prePromote.firstOrNull()?.id.orEmpty() != expectedLatestId || concurrentBeforePromote.isNotEmpty()) {
                throw DriveBackupConflictException(
                    remoteFiles = prePromote,
                    expectedLatestId = expectedLatestId,
                    competingFiles = concurrentBeforePromote,
                    message = "Molnbackupen ändrades under uppladdning. Tidigare backup är oförändrad."
                )
            }
        } catch (error: Exception) {
            runCatching { transport.delete(pending.id) } // Only our own unpublished attempt.
            throw error
        }

        // A network failure here can mean Drive already applied the update. Leave the file for
        // the next listing to reconcile instead of deleting a potentially verified backup.
        transport.markVerified(pending.id, parentId = expectedLatestId, writerId = writerId)
        val promoted = pending.copy(parentId = expectedLatestId, writerId = writerId)
        // Best-effort post-promotion check: detect concurrent promotions and compute lineage-aware retention.
        val after = runCatching { transport.listVerified() }.getOrNull()
            ?: return promoted.copy(cleanupPending = before.size > 1)

        val concurrentAfterPromote = after.filter { it.id != pending.id && it.id !in beforeIds }
        val plan = DriveBackupRetention.plan(after, pending.id, acknowledgedCompetingIds)
        val detectedCompeting = (concurrentAfterPromote + plan.unacknowledgedCompeting).distinctBy { it.id }
        if (detectedCompeting.isNotEmpty() || after.firstOrNull()?.id != pending.id) {
            val competingIds = detectedCompeting.map { it.id }
                .ifEmpty { listOfNotNull(after.firstOrNull()?.id?.takeIf { it != pending.id }) }
            // Preserve promoted file, predecessor, and all competing verified versions without deleting anything.
            return promoted.copy(cleanupPending = false, competingIds = competingIds)
        }

        // Preserve latest, direct successful predecessor, and all competing verified versions.
        return promoted.copy(cleanupPending = !removePrevious(plan.prunable))
    }
}
