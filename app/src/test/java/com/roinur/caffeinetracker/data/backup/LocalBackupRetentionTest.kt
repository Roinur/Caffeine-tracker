package com.roinur.caffeinetracker.data.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LocalBackupRetentionTest {
    private data class Document(val name: String, val content: String)

    private fun rotate(files: MutableMap<String, Document>, failDelete: Boolean = false,
                       failRename: String? = null): Document? = LocalBackupRetention.rotate(
        find = { files[it] },
        delete = { check(!failDelete) { "Delete failed" }; files.remove(it.name) },
        rename = { old, name ->
            if (old.name == failRename) throw IOException("Rename failed")
            check(name !in files) { "Destination still occupied" }
            files.remove(old.name)
            old.copy(name = name).also { files[name] = it }
        }
    )

    @Test fun expandsOldThreeVersionLayoutWithoutLosingExistingBackups() {
        val files = linkedMapOf(
            PROCEDURAL_BACKUP_CURRENT_FILENAME to Document(PROCEDURAL_BACKUP_CURRENT_FILENAME, "current"),
            PROCEDURAL_BACKUP_PREVIOUS_1_FILENAME to Document(PROCEDURAL_BACKUP_PREVIOUS_1_FILENAME, "older-1"),
            PROCEDURAL_BACKUP_PREVIOUS_2_FILENAME to Document(PROCEDURAL_BACKUP_PREVIOUS_2_FILENAME, "older-2")
        )
        rotate(files)
        files[PROCEDURAL_BACKUP_CURRENT_FILENAME] = Document(PROCEDURAL_BACKUP_CURRENT_FILENAME, "new-1")
        assertEquals(setOf("new-1", "current", "older-1", "older-2"), files.values.map { it.content }.toSet())
        rotate(files)
        files[PROCEDURAL_BACKUP_CURRENT_FILENAME] = Document(PROCEDURAL_BACKUP_CURRENT_FILENAME, "new-2")
        assertEquals(5, files.size)
        assertEquals("older-2", files.getValue(PROCEDURAL_BACKUP_PREVIOUS_4_FILENAME).content)
    }

    @Test fun repeatedRotationKeepsFiveLatestAndNeverTouchesManualExport() {
        val manual = Document("my-manual-export.txt", "never change")
        val files = linkedMapOf(manual.name to manual)
        repeat(20) { index ->
            rotate(files)
            files[PROCEDURAL_BACKUP_CURRENT_FILENAME] = Document(PROCEDURAL_BACKUP_CURRENT_FILENAME, index.toString())
        }
        assertEquals(LocalBackupRetention.VERSION_COUNT + 1, files.size)
        assertEquals(manual, files[manual.name])
        assertEquals("19", files.getValue(PROCEDURAL_BACKUP_CURRENT_FILENAME).content)
        LocalBackupRetention.previousNames.forEachIndexed { index, name ->
            assertEquals((18 - index).toString(), files.getValue(name).content)
        }
    }

    @Test fun rotationFailureLeavesCurrentRecoverable() {
        val current = Document(PROCEDURAL_BACKUP_CURRENT_FILENAME, "healthy")
        val older = LocalBackupRetention.previousNames.associateWith { Document(it, "old:$it") }
        for (delete in listOf(true, false)) {
            val files = older.toMutableMap().apply { put(current.name, current) }
            assertThrows(Exception::class.java) { rotate(files, failDelete = delete,
                failRename = if (delete) null else PROCEDURAL_BACKUP_PREVIOUS_3_FILENAME) }
            assertEquals(current, files[current.name])
        }
    }

    @Test fun promotionFailureCanRestoreThePreviousCurrent() {
        val current = Document(PROCEDURAL_BACKUP_CURRENT_FILENAME, "healthy")
        val files = mutableMapOf(current.name to current)
        val moved = requireNotNull(rotate(files))
        // The SAF adapter uses the returned URI to roll Current back if promotion fails.
        files.remove(moved.name)
        files[current.name] = moved.copy(name = current.name)
        assertEquals(current, files[current.name])
    }
}
