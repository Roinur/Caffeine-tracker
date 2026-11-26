package com.roinur.caffeinetracker.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import com.roinur.caffeinetracker.core.model.TrackerSnapshot
import java.io.File
import java.io.IOException

/** Same validate-before-rotate policy and five-version retention as Sauce Tracker. */
class CaffeineBackupStore(private val context: Context) {
    private val directory = File(context.filesDir, "backups").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("backup_location", Context.MODE_PRIVATE)
    var folder: String?
        get() = prefs.getString("tree", null)
        set(value) { prefs.edit().putString("tree", value).apply() }

    fun checkpoint(snapshot: TrackerSnapshot) {
        val text = BackupCodec.encode(snapshot)
        val pending = File(directory, "pending.json")
        try {
            pending.outputStream().use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            val readBack = pending.readText()
            check(readBack == text && BackupCodec.decode(readBack) == snapshot) { "Backupens kontrolläsning misslyckades." }
            val files = directory.listFiles().orEmpty().associateBy { it.name }
            val rotated = LocalBackupRetention.rotate(
                find = { files[it] },
                delete = { check(it.delete()) { "Kunde inte rotera äldsta backupen." } },
                rename = { old, name ->
                    File(directory, name).also { check(old.renameTo(it)) { "Kunde inte rotera backupen." } }
                }
            )
            val current = File(directory, PROCEDURAL_BACKUP_CURRENT_FILENAME)
            if (!pending.renameTo(current)) {
                rotated?.renameTo(current)
                throw IOException("Kunde inte spara den kontrollerade backupen.")
            }
        } catch (e: Throwable) {
            pending.delete()
            throw e
        }
        folder?.let { uri ->
            writeRollingProceduralBackup(context, Uri.parse(uri), text) {
                runCatching { BackupCodec.decode(it) == snapshot }.getOrDefault(false)
            }
        }
    }

    fun preserveBeforeRestore(snapshot: TrackerSnapshot) {
        val text = BackupCodec.encode(snapshot)
        check(BackupCodec.decode(text) == snapshot) { "Kunde inte verifiera säkerhetskopian före återställning." }
        val atomic = AtomicFile(File(directory, "before_restore.json"))
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
        check(BackupCodec.decode(atomic.readFully().toString(Charsets.UTF_8)) == snapshot)
    }

    fun available(): List<File> = directory.listFiles().orEmpty()
        .filter { it.name == PROCEDURAL_BACKUP_CURRENT_FILENAME || it.name in LocalBackupRetention.previousNames || it.name == "before_restore.json" }
        .sortedByDescending { it.lastModified() }
}

internal fun resolveExistingBackupContainerUri(context: Context, treeUri: Uri): Uri? {
    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
    context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
        while (c.moveToNext()) if (c.getString(1) == "Caffeine Tracker")
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
    }
    return null
}

internal fun resolveOrCreateBackupContainerUri(context: Context, treeUri: Uri): Uri {
    resolveExistingBackupContainerUri(context, treeUri)?.let { return it }
    val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    return DocumentsContract.createDocument(context.contentResolver, root,
        DocumentsContract.Document.MIME_TYPE_DIR, "Caffeine Tracker")
        ?: throw IOException("Kunde inte skapa backupmappen.")
}
