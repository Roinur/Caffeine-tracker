package com.roinur.caffeinetracker.data.backup.drive

import com.roinur.caffeinetracker.core.model.TrackerSettings
import com.roinur.caffeinetracker.core.model.TrackerSnapshot
import com.roinur.caffeinetracker.core.model.starterDrinks

internal data class DriveLocalSummary(
    val intakeCount: Int,
    val drinkCount: Int,
    val hasNonStarterDrinks: Boolean,
    val hasCustomSettings: Boolean
) {
    /** A fresh or empty installation has no logged caffeine history. */
    val isEmptyOrFresh: Boolean get() = intakeCount == 0

    companion object {
        fun fromSnapshot(snapshot: TrackerSnapshot): DriveLocalSummary {
            val defaultSettings = TrackerSettings()
            val starterById = starterDrinks.associateBy { it.id }
            val hasNonStarterDrinks = snapshot.drinks.size != starterDrinks.size ||
                snapshot.drinks.any { drink -> starterById[drink.id] != drink }
            val hasCustomSettings = snapshot.settings.halfLifeHours != defaultSettings.halfLifeHours ||
                snapshot.settings.bedtime != defaultSettings.bedtime ||
                snapshot.settings.bedtimeThresholdMg != defaultSettings.bedtimeThresholdMg ||
                snapshot.settings.dailyGoalMg != defaultSettings.dailyGoalMg
            return DriveLocalSummary(
                intakeCount = snapshot.intakes.size,
                drinkCount = snapshot.drinks.size,
                hasNonStarterDrinks = hasNonStarterDrinks,
                hasCustomSettings = hasCustomSettings
            )
        }
    }
}

internal data class DriveInstallationLineage(
    val expectedLatestId: String,
    val lineageEstablished: Boolean,
    val acknowledgedCompetingIds: Set<String> = emptySet(),
    val overrideApprovedRemoteId: String = "",
    val conflictPending: Boolean = false
)

internal enum class DriveVersionRole {
    LATEST,
    PREDECESSOR,
    COMPETING,
    OLDER
}

internal data class DriveClassifiedVersion(
    val file: DriveBackupFile,
    val role: DriveVersionRole
)

internal data class DriveRetentionPlan(
    val keep: List<DriveBackupFile>,
    val prunable: List<DriveBackupFile>,
    val competing: List<DriveBackupFile>,
    val unacknowledgedCompeting: List<DriveBackupFile>,
    val predecessor: DriveBackupFile?
)

internal sealed interface DriveEnableDecision {
    data object AllowFirstBackup : DriveEnableDecision
    data class AllowEstablishedSync(val remoteHeadId: String) : DriveEnableDecision
    data class RequireExistingBackupOptIn(
        val remoteFiles: List<DriveBackupFile>,
        val localSummary: DriveLocalSummary
    ) : DriveEnableDecision
    data class ResolveConflictFirst(
        val expectedLatestId: String,
        val remoteFiles: List<DriveBackupFile>,
        val competingFiles: List<DriveBackupFile>
    ) : DriveEnableDecision
}

internal sealed interface DriveUploadPreflight {
    data class Proceed(
        val expectedLatestId: String,
        val acknowledgedCompetingIds: Set<String>
    ) : DriveUploadPreflight
    data class BlockedExistingBackup(
        val remoteFiles: List<DriveBackupFile>,
        val localSummary: DriveLocalSummary
    ) : DriveUploadPreflight
    data class BlockedConflict(
        val expectedLatestId: String,
        val remoteFiles: List<DriveBackupFile>,
        val competingFiles: List<DriveBackupFile>
    ) : DriveUploadPreflight
}

/**
 * Lineage-aware cloud retention:
 * - Always preserves the latest verified version.
 * - Always preserves at least its direct successful predecessor.
 * - Always preserves competing verified versions (files not on the linear ancestor chain).
 * - Only prunes strictly superseded linear ancestors once no unacknowledged conflicts exist.
 */
internal object DriveBackupRetention {
    fun plan(
        verifiedFiles: List<DriveBackupFile>,
        headId: String,
        acknowledgedCompetingIds: Set<String> = emptySet()
    ): DriveRetentionPlan {
        if (verifiedFiles.isEmpty() || headId.isBlank()) {
            return DriveRetentionPlan(
                keep = verifiedFiles,
                prunable = emptyList(),
                competing = emptyList(),
                unacknowledgedCompeting = emptyList(),
                predecessor = null
            )
        }

        val indexById = verifiedFiles.withIndex().associate { it.value.id to it.index }
        val byId = verifiedFiles.associateBy { it.id }
        val head = byId[headId] ?: return DriveRetentionPlan(
            keep = verifiedFiles,
            prunable = emptyList(),
            competing = verifiedFiles,
            unacknowledgedCompeting = verifiedFiles.filter { it.id !in acknowledgedCompetingIds },
            predecessor = null
        )

        val ancestors = mutableListOf(head)
        val ancestorIds = linkedSetOf(head.id)
        var cursor = head
        while (cursor.parentId.isNotBlank()) {
            val parent = byId[cursor.parentId] ?: break
            if (!ancestorIds.add(parent.id)) break
            ancestors += parent
            cursor = parent
        }

        // Upgrade compatibility: if a legacy head has no parent metadata, treat the next older
        // legacy file as its predecessor so at least two generations are preserved.
        if (ancestors.size == 1 && head.isLegacy) {
            val headIndex = indexById.getValue(head.id)
            val legacyPredecessor = verifiedFiles
                .drop(headIndex + 1)
                .firstOrNull { it.isLegacy }
            if (legacyPredecessor != null && ancestorIds.add(legacyPredecessor.id)) {
                ancestors += legacyPredecessor
            }
        }

        val predecessor = ancestors.getOrNull(1)
        val chainTail = ancestors.last()
        val chainTailIndex = indexById.getValue(chainTail.id)
        val canPruneOlderLegacy = ancestors.size >= 2 && chainTail.isLegacy

        val legacySuperseded = mutableListOf<DriveBackupFile>()
        val competing = mutableListOf<DriveBackupFile>()
        verifiedFiles.forEachIndexed { index, file ->
            if (file.id in ancestorIds) return@forEachIndexed
            if (canPruneOlderLegacy && file.isLegacy && index > chainTailIndex) {
                legacySuperseded += file
            } else {
                competing += file
            }
        }

        val unacknowledgedCompeting = competing.filter { it.id !in acknowledgedCompetingIds }
        val supersededAncestors = ancestors.drop(2)
        val prunable = if (unacknowledgedCompeting.isNotEmpty()) {
            emptyList()
        } else {
            (supersededAncestors + legacySuperseded).distinctBy { it.id }.asReversed()
        }
        val prunableIds = prunable.map { it.id }.toSet()
        val keep = verifiedFiles.filter { it.id !in prunableIds }

        return DriveRetentionPlan(
            keep = keep,
            prunable = prunable,
            competing = competing,
            unacknowledgedCompeting = unacknowledgedCompeting,
            predecessor = predecessor
        )
    }

    fun detectUnacknowledgedCompeting(
        verifiedFiles: List<DriveBackupFile>,
        expectedLatestId: String,
        acknowledgedCompetingIds: Set<String> = emptySet()
    ): List<DriveBackupFile> {
        if (verifiedFiles.isEmpty() || expectedLatestId.isBlank()) return emptyList()
        return plan(verifiedFiles, expectedLatestId, acknowledgedCompetingIds).unacknowledgedCompeting
    }

    fun classify(
        verifiedFiles: List<DriveBackupFile>,
        expectedLatestId: String = ""
    ): List<DriveClassifiedVersion> {
        if (verifiedFiles.isEmpty()) return emptyList()
        val head = verifiedFiles.first()
        val plan = plan(verifiedFiles, head.id)
        val competingIds = plan.competing.map { it.id }.toSet()
        val predecessorId = plan.predecessor?.id
        return verifiedFiles.map { file ->
            val role = when {
                file.id == head.id -> DriveVersionRole.LATEST
                file.id in competingIds -> DriveVersionRole.COMPETING
                file.id == predecessorId -> DriveVersionRole.PREDECESSOR
                expectedLatestId.isNotBlank() && file.id == expectedLatestId -> DriveVersionRole.COMPETING
                else -> DriveVersionRole.OLDER
            }
            DriveClassifiedVersion(file, role)
        }
    }
}

internal object DriveBackupPolicy {
    fun evaluateEnable(
        localSummary: DriveLocalSummary,
        remoteFiles: List<DriveBackupFile>,
        lineage: DriveInstallationLineage
    ): DriveEnableDecision {
        if (remoteFiles.isEmpty()) {
            return if (lineage.lineageEstablished && lineage.expectedLatestId.isNotBlank()) {
                DriveEnableDecision.ResolveConflictFirst(
                    expectedLatestId = lineage.expectedLatestId,
                    remoteFiles = emptyList(),
                    competingFiles = emptyList()
                )
            } else {
                DriveEnableDecision.AllowFirstBackup
            }
        }

        if (!lineage.lineageEstablished) {
            return DriveEnableDecision.RequireExistingBackupOptIn(
                remoteFiles = remoteFiles,
                localSummary = localSummary
            )
        }

        val remoteHead = remoteFiles.first()
        val unacknowledgedCompeting = DriveBackupRetention.detectUnacknowledgedCompeting(
            verifiedFiles = remoteFiles,
            expectedLatestId = remoteHead.id,
            acknowledgedCompetingIds = lineage.acknowledgedCompetingIds
        )
        if (lineage.conflictPending || remoteHead.id != lineage.expectedLatestId || unacknowledgedCompeting.isNotEmpty()) {
            return DriveEnableDecision.ResolveConflictFirst(
                expectedLatestId = lineage.expectedLatestId,
                remoteFiles = remoteFiles,
                competingFiles = unacknowledgedCompeting.ifEmpty {
                    remoteFiles.filter { it.id != lineage.expectedLatestId }
                }
            )
        }

        return DriveEnableDecision.AllowEstablishedSync(remoteHead.id)
    }

    fun evaluateUpload(
        localSummary: DriveLocalSummary,
        remoteFiles: List<DriveBackupFile>,
        lineage: DriveInstallationLineage
    ): DriveUploadPreflight {
        if (lineage.conflictPending) {
            return DriveUploadPreflight.BlockedConflict(
                expectedLatestId = lineage.expectedLatestId,
                remoteFiles = remoteFiles,
                competingFiles = DriveBackupRetention.detectUnacknowledgedCompeting(
                    verifiedFiles = remoteFiles,
                    expectedLatestId = remoteFiles.firstOrNull()?.id.orEmpty(),
                    acknowledgedCompetingIds = lineage.acknowledgedCompetingIds
                )
            )
        }

        if (remoteFiles.isEmpty()) {
            return if (lineage.expectedLatestId.isNotBlank()) {
                DriveUploadPreflight.BlockedConflict(
                    expectedLatestId = lineage.expectedLatestId,
                    remoteFiles = emptyList(),
                    competingFiles = emptyList()
                )
            } else {
                DriveUploadPreflight.Proceed(
                    expectedLatestId = "",
                    acknowledgedCompetingIds = lineage.acknowledgedCompetingIds
                )
            }
        }

        val remoteHead = remoteFiles.first()
        if (!lineage.lineageEstablished) {
            return DriveUploadPreflight.BlockedExistingBackup(
                remoteFiles = remoteFiles,
                localSummary = localSummary
            )
        }

        if (localSummary.isEmptyOrFresh &&
            lineage.expectedLatestId.isBlank() &&
            lineage.overrideApprovedRemoteId != remoteHead.id
        ) {
            return DriveUploadPreflight.BlockedExistingBackup(
                remoteFiles = remoteFiles,
                localSummary = localSummary
            )
        }

        if (remoteHead.id != lineage.expectedLatestId) {
            return DriveUploadPreflight.BlockedConflict(
                expectedLatestId = lineage.expectedLatestId,
                remoteFiles = remoteFiles,
                competingFiles = remoteFiles.filter { it.id != lineage.expectedLatestId }
            )
        }

        val unacknowledgedCompeting = DriveBackupRetention.detectUnacknowledgedCompeting(
            verifiedFiles = remoteFiles,
            expectedLatestId = lineage.expectedLatestId,
            acknowledgedCompetingIds = lineage.acknowledgedCompetingIds
        )
        if (unacknowledgedCompeting.isNotEmpty()) {
            return DriveUploadPreflight.BlockedConflict(
                expectedLatestId = lineage.expectedLatestId,
                remoteFiles = remoteFiles,
                competingFiles = unacknowledgedCompeting
            )
        }

        return DriveUploadPreflight.Proceed(
            expectedLatestId = lineage.expectedLatestId,
            acknowledgedCompetingIds = lineage.acknowledgedCompetingIds
        )
    }
}
