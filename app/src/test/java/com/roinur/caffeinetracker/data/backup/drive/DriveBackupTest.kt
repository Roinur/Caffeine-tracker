package com.roinur.caffeinetracker.data.backup.drive

import com.roinur.caffeinetracker.core.model.Drink
import com.roinur.caffeinetracker.core.model.Intake
import com.roinur.caffeinetracker.core.model.TrackerSettings
import com.roinur.caffeinetracker.core.model.TrackerSnapshot
import com.roinur.caffeinetracker.core.model.starterDrinks
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DriveBackupTest {
    private fun snapshot() = TrackerSnapshot(
        intakes = listOf(
            Intake("i1", "Filterkaffe", 95.0, 1792483200000L, category = "Kaffe", servingUnit = "1 kopp"),
            Intake("i2", "NOCCO", 180.0, 1792490400000L, category = "Energidryck", servingUnit = "330 ml", imageKey = "NOCCO")
        ),
        drinks = listOf(
            Drink("d1", "Filterkaffe", 95.0, "1 kopp", "Kaffe", true, favoriteOrder = 0),
            Drink("d2", "NOCCO", 180.0, "330 ml", "Energidryck", true, imageKey = "NOCCO", favoriteOrder = 1, volumeMl = 330.0)
        ),
        settings = TrackerSettings(halfLifeHours = 5.0, bedtime = "23:00", bedtimeThresholdMg = 50.0, dailyGoalMg = 300.0)
    )

    private fun emptyFreshSnapshot() = TrackerSnapshot(
        intakes = emptyList(),
        drinks = starterDrinks,
        settings = TrackerSettings()
    )

    @Test fun cloudEncodingRoundTripsSnapshotAndRejectsTampering() {
        val original = snapshot()
        val encoded = CaffeineCloudBackup.encode(original)
        val decoded = CaffeineCloudBackup.decode(encoded)
        assertEquals(original, decoded.snapshot)
        assertEquals(CaffeineCloudBackup.fingerprint(original), decoded.fingerprint)

        val tampered = JSONObject(encoded).put("payload", JSONObject(encoded).getString("payload").replace("Filterkaffe", "Changed"))
        assertThrows(IllegalArgumentException::class.java) { CaffeineCloudBackup.decode(tampered.toString()) }
        assertThrows(Exception::class.java) { CaffeineCloudBackup.decode(encoded.take(encoded.length / 3)) }
    }

    @Test fun fingerprintIgnoresRootCreatedAtTimestampAndDetectsDataChanges() {
        val first = snapshot()
        val second = snapshot()
        assertEquals(CaffeineCloudBackup.fingerprint(first), CaffeineCloudBackup.fingerprint(second))
        val modified = first.copy(settings = first.settings.copy(bedtimeThresholdMg = 60.0))
        assertNotEquals(CaffeineCloudBackup.fingerprint(first), CaffeineCloudBackup.fingerprint(modified))
    }

    private class FakeTransport : DriveBackupTransport {
        val files = linkedMapOf<String, String>()
        val verified = mutableListOf<DriveBackupFile>()
        val deleted = mutableListOf<String>()
        var corruptDownload = false
        var failPromotion = false
        var failDeletion = false
        var failDeletionFor: String? = null
        var losePromotionResponse = false
        var failListAfterPromotion = false
        val events = mutableListOf<String>()
        var conflictDuringUpload = false
        var conflictRightAfterPromotion: DriveBackupFile? = null
        var uploaded = 0
        private var promotedCount = 0

        fun addVerified(id: String, parentId: String = "", writerId: String = "") {
            verified += DriveBackupFile(id = id, name = id, createdAt = id, parentId = parentId, writerId = writerId)
        }

        val verifiedIds: List<String> get() = verified.map { it.id }

        override fun listVerified(): List<DriveBackupFile> {
            if (failListAfterPromotion && promotedCount > 0) {
                throw IllegalStateException("Transient list failure after promotion")
            }
            return verified.toList()
        }

        override fun uploadPending(text: String, parentId: String, writerId: String): DriveBackupFile {
            val id = "new-${++uploaded}"
            events += "upload:$id"
            files[id] = text
            if (conflictDuringUpload) {
                verified.add(0, DriveBackupFile("another-device", "another-device", "another-device", parentId = parentId, writerId = "device-b"))
            }
            return DriveBackupFile(id, id, id, parentId = parentId, writerId = writerId)
        }

        override fun uploadPending(file: File, parentId: String, writerId: String) =
            uploadPending(file.readText(), parentId, writerId)

        override fun download(id: String): String {
            events += "read-back:$id"
            return if (corruptDownload) "truncated" else files.getValue(id)
        }

        override fun downloadChecksum(id: String): DriveDownloadChecksum {
            val text = download(id)
            return DriveDownloadChecksum(text.toByteArray(Charsets.UTF_8).size.toLong(), CaffeineCloudBackup.sha256(text))
        }

        override fun markVerified(id: String, parentId: String, writerId: String) {
            check(!failPromotion)
            events += "verified:$id"
            promotedCount++
            verified.add(0, DriveBackupFile(id, id, id, parentId = parentId, writerId = writerId))
            check(!losePromotionResponse) { "Drive applied the change, but the response was lost" }
            conflictRightAfterPromotion?.let { competing ->
                verified.add(0, competing)
                conflictRightAfterPromotion = null
            }
        }

        override fun delete(id: String) {
            check(!failDeletion && id != failDeletionFor)
            events += "delete:$id"
            deleted += id
            files.remove(id)
            verified.removeAll { it.id == id }
        }
    }

    @Test fun uploadFailureNeverRotatesHealthyVersions() {
        for (promotion in listOf(false, true)) {
            val transport = FakeTransport().apply {
                addVerified("good")
                addVerified("previous")
                corruptDownload = !promotion
                failPromotion = promotion
            }
            assertThrows(Exception::class.java) { DriveBackupRepository(transport).publish(CaffeineCloudBackup.encode(snapshot()), "good", writerId = "device-a") }
            assertEquals(listOf("good", "previous"), transport.verifiedIds)
            assertEquals(if (promotion) emptyList<String>() else listOf("new-1"), transport.deleted)
        }
    }

    @Test fun successfulPublicationPromotesBeforePruningAndPreservesDirectPredecessor() {
        val transport = FakeTransport().apply { (1..4).forEach { addVerified("old-$it") } }
        val file = DriveBackupRepository(transport).publish(CaffeineCloudBackup.encode(snapshot()), "old-1", writerId = "device-a")
        assertEquals(listOf(file.id, "old-1"), transport.verifiedIds)
        assertEquals(listOf("old-4", "old-3", "old-2"), transport.deleted)
        assertFalse(file.hasCompetingVersions)
        assertTrue(transport.events.indexOf("read-back:${file.id}") < transport.events.indexOf("verified:${file.id}"))
        assertTrue(transport.events.indexOf("verified:${file.id}") < transport.events.indexOf("delete:old-2"))
    }

    @Test fun sequentialLineagePublicationsAlwaysKeepLatestAndSuccessfulPredecessor() {
        val transport = FakeTransport()
        val repo = DriveBackupRepository(transport)
        val encoded = CaffeineCloudBackup.encode(snapshot())

        val v1 = repo.publish(encoded, expectedLatestId = "", writerId = "device-a")
        assertEquals(listOf(v1.id), transport.verifiedIds)

        val v2 = repo.publish(encoded, expectedLatestId = v1.id, writerId = "device-a")
        assertEquals(listOf(v2.id, v1.id), transport.verifiedIds)
        assertTrue(transport.deleted.isEmpty())

        val v3 = repo.publish(encoded, expectedLatestId = v2.id, writerId = "device-a")
        assertEquals(listOf(v3.id, v2.id), transport.verifiedIds)
        assertEquals(listOf(v1.id), transport.deleted)

        val v4 = repo.publish(encoded, expectedLatestId = v3.id, writerId = "device-a")
        assertEquals(listOf(v4.id, v3.id), transport.verifiedIds)
        assertEquals(listOf(v1.id, v2.id), transport.deleted)
    }

    @Test fun concurrentCloudUpdateLeavesPreviousBackupsUntouched() {
        val transport = FakeTransport().apply { addVerified("existing"); conflictDuringUpload = true }
        val error = assertThrows(DriveBackupConflictException::class.java) {
            DriveBackupRepository(transport).publish(CaffeineCloudBackup.encode(snapshot()), "existing", writerId = "device-a")
        }
        assertEquals("existing", error.expectedLatestId)
        assertEquals(listOf("another-device", "existing"), transport.verifiedIds)
        assertEquals(listOf("new-1"), transport.deleted)
    }

    @Test fun postPromotionRacePreservesCompetingVersionAndPredecessorAndFlagsConflict() {
        val transport = FakeTransport().apply {
            addVerified("v1", writerId = "device-a")
            conflictRightAfterPromotion = DriveBackupFile(
                id = "b2",
                name = "b2",
                createdAt = "b2",
                parentId = "v1",
                writerId = "device-b"
            )
        }
        val published = DriveBackupRepository(transport).publish(
            CaffeineCloudBackup.encode(snapshot()),
            expectedLatestId = "v1",
            writerId = "device-a"
        )
        assertTrue(published.hasCompetingVersions)
        assertEquals(listOf("b2"), published.competingIds)
        assertFalse(published.cleanupPending)
        assertTrue(transport.deleted.isEmpty())
        assertEquals(listOf("b2", published.id, "v1"), transport.verifiedIds)
    }

    @Test fun unacknowledgedCompetingVersionBlocksUploadEvenWhenOrderedOlderThanExpectedHead() {
        val transport = FakeTransport().apply {
            addVerified("a2", parentId = "v1", writerId = "device-a")
            addVerified("b2", parentId = "v1", writerId = "device-b")
            addVerified("v1", writerId = "device-a")
        }
        val error = assertThrows(DriveBackupConflictException::class.java) {
            DriveBackupRepository(transport).publish(
                CaffeineCloudBackup.encode(snapshot()),
                expectedLatestId = "a2",
                writerId = "device-a"
            )
        }
        assertEquals(listOf("b2"), error.competingFiles.map { it.id })
        assertEquals(0, transport.uploaded)
        assertEquals(listOf("a2", "b2", "v1"), transport.verifiedIds)
    }

    @Test fun acknowledgedCompetingVersionRemainsPreservedAcrossSubsequentUploads() {
        val transport = FakeTransport().apply {
            addVerified("a2", parentId = "v1", writerId = "device-a")
            addVerified("b2", parentId = "v1", writerId = "device-b")
            addVerified("v1", writerId = "device-a")
        }
        val repo = DriveBackupRepository(transport)
        val encoded = CaffeineCloudBackup.encode(snapshot())
        val acknowledged = setOf("a2", "b2", "v1")

        val a3 = repo.publish(encoded, expectedLatestId = "a2", writerId = "device-a", acknowledgedCompetingIds = acknowledged)
        assertFalse(a3.hasCompetingVersions)
        assertEquals(listOf(a3.id, "a2", "b2"), transport.verifiedIds)
        assertEquals(listOf("v1"), transport.deleted)

        val a4 = repo.publish(encoded, expectedLatestId = a3.id, writerId = "device-a", acknowledgedCompetingIds = acknowledged)
        assertFalse(a4.hasCompetingVersions)
        assertEquals(listOf(a4.id, a3.id, "b2"), transport.verifiedIds)
        assertEquals(listOf("v1", "a2"), transport.deleted)
    }

    @Test fun stagedUploadWorksAndPreservesPredecessorWhileCleaningTempPayload() {
        val folder = Files.createTempDirectory("caffeine-drive-test-").toFile()
        try {
            val prepared = CaffeineCloudBackup.prepare(snapshot(), folder)
            val transport = FakeTransport().apply { addVerified("old") }
            val published = DriveBackupRepository(transport).publish(prepared, "old", writerId = "device-a")
            assertEquals(listOf(published.id, "old"), transport.verifiedIds)
            assertEquals(snapshot(), CaffeineCloudBackup.decode(transport.files.getValue(published.id)).snapshot)
            assertEquals(1, folder.listFiles()!!.size)
        } finally {
            folder.listFiles().orEmpty().forEach { it.delete() }
            folder.delete()
        }
    }

    @Test fun failedCleanupRetriesWithoutReuploadingAndPreservesPredecessorAndCompeting() {
        val transport = FakeTransport().apply {
            addVerified("v2", parentId = "v1", writerId = "device-a")
            addVerified("b2", parentId = "v1", writerId = "device-b")
            addVerified("v1", writerId = "device-a")
            failDeletion = true
        }
        val acknowledged = setOf("v2", "b2", "v1")
        val repo = DriveBackupRepository(transport)
        val file = repo.publish(
            CaffeineCloudBackup.encode(snapshot()),
            expectedLatestId = "v2",
            writerId = "device-a",
            acknowledgedCompetingIds = acknowledged
        )
        assertTrue(file.cleanupPending)
        assertEquals(listOf(file.id, "v2", "b2", "v1"), transport.verifiedIds)

        // Cleanup must refuse if competing version is not acknowledged.
        assertThrows(DriveBackupConflictException::class.java) {
            repo.cleanupPreviousVersions(file.id, acknowledgedCompetingIds = emptySet())
        }

        transport.failDeletion = false
        assertTrue(repo.cleanupPreviousVersions(file.id, acknowledgedCompetingIds = acknowledged))
        assertEquals(listOf(file.id, "v2", "b2"), transport.verifiedIds)
        assertEquals(listOf("v1"), transport.deleted)
        assertEquals(1, transport.uploaded)
    }

    @Test fun cleanupFailureLeavesTheAncestorChainIntactForRetry() {
        val transport = FakeTransport().apply {
            addVerified("v4", parentId = "v3", writerId = "device-a")
            addVerified("v3", parentId = "v2", writerId = "device-a")
            addVerified("v2", parentId = "v1", writerId = "device-a")
            addVerified("v1", writerId = "device-a")
            failDeletionFor = "v1"
        }
        val repository = DriveBackupRepository(transport)
        assertFalse(repository.cleanupPreviousVersions("v4"))
        assertEquals(listOf("v4", "v3", "v2", "v1"), transport.verifiedIds)

        transport.failDeletionFor = null
        assertTrue(repository.cleanupPreviousVersions("v4"))
        assertEquals(listOf("v1", "v2"), transport.deleted)
        assertEquals(listOf("v4", "v3"), transport.verifiedIds)
    }

    @Test fun lostPromotionResponseDoesNotRemoveThePotentiallyVerifiedBackup() {
        val transport = FakeTransport().apply {
            addVerified("v1", writerId = "device-a")
            losePromotionResponse = true
        }
        assertThrows(IllegalStateException::class.java) {
            DriveBackupRepository(transport).publish(
                CaffeineCloudBackup.encode(snapshot()), "v1", writerId = "device-a"
            )
        }
        assertEquals(listOf("new-1", "v1"), transport.verifiedIds)
        assertTrue(transport.deleted.isEmpty())
    }

    @Test fun failedPostPromotionListingDefersCleanupWithoutDeletingPredecessor() {
        val transport = FakeTransport().apply {
            addVerified("v2", parentId = "v1", writerId = "device-a")
            addVerified("v1", writerId = "device-a")
            failListAfterPromotion = true
        }
        val file = DriveBackupRepository(transport).publish(
            CaffeineCloudBackup.encode(snapshot()),
            expectedLatestId = "v2",
            writerId = "device-a"
        )
        assertTrue(file.cleanupPending)
        assertTrue(transport.deleted.isEmpty())
        assertEquals(listOf(file.id, "v2", "v1"), transport.verifiedIds)
    }

    @Test fun newEmptyInstallationRequiresExistingBackupOptInBeforeEnablingOrUploading() {
        val emptySummary = DriveLocalSummary.fromSnapshot(emptyFreshSnapshot())
        assertTrue(emptySummary.isEmptyOrFresh)
        val remote = listOf(DriveBackupFile("remote-1", "remote-1", "2026-11-25T10:00:00Z"))
        val unlinkedLineage = DriveInstallationLineage(expectedLatestId = "", lineageEstablished = false)

        val enableDecision = DriveBackupPolicy.evaluateEnable(emptySummary, remote, unlinkedLineage)
        assertTrue(enableDecision is DriveEnableDecision.RequireExistingBackupOptIn)
        val optIn = enableDecision as DriveEnableDecision.RequireExistingBackupOptIn
        assertTrue(optIn.localSummary.isEmptyOrFresh)
        assertEquals(remote, optIn.remoteFiles)

        val uploadDecision = DriveBackupPolicy.evaluateUpload(emptySummary, remote, unlinkedLineage)
        assertTrue(uploadDecision is DriveUploadPreflight.BlockedExistingBackup)
        assertTrue((uploadDecision as DriveUploadPreflight.BlockedExistingBackup).localSummary.isEmptyOrFresh)
    }

    @Test fun newInstallationWithLocalDataStillRequiresExplicitOptInWhenRemoteBackupExists() {
        val localSummary = DriveLocalSummary.fromSnapshot(snapshot())
        assertFalse(localSummary.isEmptyOrFresh)
        assertEquals(2, localSummary.intakeCount)
        val remote = listOf(DriveBackupFile("remote-1", "remote-1", "2026-11-25T10:00:00Z"))
        val unlinkedLineage = DriveInstallationLineage(expectedLatestId = "", lineageEstablished = false)

        val enableDecision = DriveBackupPolicy.evaluateEnable(localSummary, remote, unlinkedLineage)
        assertTrue(enableDecision is DriveEnableDecision.RequireExistingBackupOptIn)
        assertFalse((enableDecision as DriveEnableDecision.RequireExistingBackupOptIn).localSummary.isEmptyOrFresh)

        val uploadDecision = DriveBackupPolicy.evaluateUpload(localSummary, remote, unlinkedLineage)
        assertTrue(uploadDecision is DriveUploadPreflight.BlockedExistingBackup)
    }

    @Test fun establishedInstallationAllowsSyncAndDetectsRemoteDriftOrUnexpectedFirstRemote() {
        val emptySummary = DriveLocalSummary.fromSnapshot(emptyFreshSnapshot())
        val populatedSummary = DriveLocalSummary.fromSnapshot(snapshot())

        // Fresh account with no existing remote backups allows first backup.
        assertEquals(
            DriveEnableDecision.AllowFirstBackup,
            DriveBackupPolicy.evaluateEnable(
                populatedSummary,
                emptyList(),
                DriveInstallationLineage(expectedLatestId = "", lineageEstablished = false)
            )
        )

        // If remote suddenly appears before an empty install's first upload, block as existing backup.
        val remote = listOf(DriveBackupFile("r1", "r1", "2026-11-25T10:00:00Z"))
        assertTrue(
            DriveBackupPolicy.evaluateUpload(
                emptySummary,
                remote,
                DriveInstallationLineage(expectedLatestId = "", lineageEstablished = true)
            ) is DriveUploadPreflight.BlockedExistingBackup
        )

        // After restoring r1, lineage matches remote head and allows enabling + uploading.
        val restoredLineage = DriveInstallationLineage(
            expectedLatestId = "r1",
            lineageEstablished = true,
            acknowledgedCompetingIds = setOf("r1")
        )
        assertEquals(
            DriveEnableDecision.AllowEstablishedSync("r1"),
            DriveBackupPolicy.evaluateEnable(populatedSummary, remote, restoredLineage)
        )
        assertTrue(
            DriveBackupPolicy.evaluateUpload(populatedSummary, remote, restoredLineage) is DriveUploadPreflight.Proceed
        )

        // When another device publishes r2, both enable and upload detect conflict.
        val driftedRemote = listOf(
            DriveBackupFile("r2", "r2", "2026-11-26T10:00:00Z", parentId = "r1", writerId = "device-b"),
            DriveBackupFile("r1", "r1", "2026-11-25T10:00:00Z", writerId = "device-a")
        )
        assertTrue(
            DriveBackupPolicy.evaluateEnable(populatedSummary, driftedRemote, restoredLineage)
                is DriveEnableDecision.ResolveConflictFirst
        )
        assertTrue(
            DriveBackupPolicy.evaluateUpload(populatedSummary, driftedRemote, restoredLineage)
                is DriveUploadPreflight.BlockedConflict
        )
    }

    @Test fun versionClassificationLabelsLatestPredecessorCompetingAndOlder() {
        val files = listOf(
            DriveBackupFile("a3", "a3", "t4", parentId = "a2", writerId = "device-a"),
            DriveBackupFile("a2", "a2", "t3", parentId = "v1", writerId = "device-a"),
            DriveBackupFile("b2", "b2", "t2", parentId = "v1", writerId = "device-b"),
            DriveBackupFile("v1", "v1", "t1", parentId = "", writerId = "device-a")
        )
        val classified = DriveBackupRetention.classify(files, expectedLatestId = "a3")
        assertEquals(
            listOf(
                "a3" to DriveVersionRole.LATEST,
                "a2" to DriveVersionRole.PREDECESSOR,
                "b2" to DriveVersionRole.COMPETING,
                "v1" to DriveVersionRole.OLDER
            ),
            classified.map { it.file.id to it.role }
        )
    }
}
