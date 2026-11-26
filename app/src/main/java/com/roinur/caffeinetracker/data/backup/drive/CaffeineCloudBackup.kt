package com.roinur.caffeinetracker.data.backup.drive

import com.roinur.caffeinetracker.core.model.TrackerSnapshot
import com.roinur.caffeinetracker.data.backup.BackupCodec
import com.roinur.caffeinetracker.data.backup.BackupJsonWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.Writer
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant

/** Cloud restore is a whole-installation restore with SHA-256 envelope integrity. */
internal object CaffeineCloudBackup {
    const val FORMAT = "CAFFEINE_TRACKER_CLOUD_BACKUP_V1"
    const val MAX_BYTES = BackupCodec.MAX_BYTES

    data class Decoded(val snapshot: TrackerSnapshot, val payload: String, val fingerprint: String)
    data class Prepared(val file: File, val checksum: String, val fingerprint: String)

    fun snapshotJson(snapshot: TrackerSnapshot): JSONObject = JSONObject()
        .put("format", BackupCodec.FORMAT)
        .put("version", 1)
        .put("createdAt", System.currentTimeMillis())
        .put("intakes", JSONArray().apply { snapshot.intakes.forEach { put(BackupCodec.intakeJson(it)) } })
        .put("drinks", JSONArray().apply { snapshot.drinks.forEach { put(BackupCodec.drinkJson(it)) } })
        .put("settings", BackupCodec.settingsJson(snapshot.settings))

    fun prepare(snapshot: TrackerSnapshot, directory: File, fingerprintValue: String = fingerprint(snapshot)): Prepared {
        val root = snapshotJson(snapshot)
        // Validate every row/setting before writing to disk.
        snapshot.intakes.forEach(BackupCodec::validIntake)
        snapshot.drinks.forEach(BackupCodec::validDrink)
        BackupCodec.settingsFromJson(BackupCodec.settingsJson(snapshot.settings))

        val payload = File.createTempFile("drive-payload-", ".tmp", directory)
        var envelope: File? = null
        try {
            boundedWriter(payload).use { BackupJsonWriter.write(root, it, prettyPrint = false) }
            val payloadHash = fileChecksum(payload)
            envelope = File.createTempFile("drive-upload-", ".json", directory)
            val metadata = JSONObject()
                .put("format", FORMAT)
                .put("restore_scope", "full_backup")
                .put("created_at", Instant.now().toString())
                .put("sha256", payloadHash)
            boundedWriter(envelope).use { writer ->
                writer.write(metadata.toString().dropLast(1))
                writer.write(",\"payload\":")
                payload.reader(Charsets.UTF_8).use { BackupJsonWriter.quoted(it, writer) }
                writer.write("}")
            }
            return Prepared(envelope, fileChecksum(envelope), fingerprintValue)
        } catch (error: Throwable) {
            envelope?.delete()
            throw error
        } finally {
            payload.delete()
        }
    }

    private fun boundedWriter(file: File): Writer = object : FilterOutputStream(file.outputStream()) {
        private var count = 0L
        private fun count(length: Int) {
            count += length
            require(count <= MAX_BYTES) { "Molnbackupen överstiger gränsen på 20 MB. Tidigare backuper är oförändrade." }
        }
        override fun write(value: Int) { count(1); out.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { count(length); out.write(bytes, offset, length) }
    }.bufferedWriter(Charsets.UTF_8)

    fun fileChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    fun encode(snapshot: TrackerSnapshot): String {
        val payload = BackupCodec.encode(snapshot)
        return JSONObject()
            .put("format", FORMAT)
            .put("restore_scope", "full_backup")
            .put("created_at", Instant.now().toString())
            .put("sha256", sha256(payload))
            .put("payload", payload)
            .toString()
            .also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Molnbackupen överstiger gränsen på 20 MB." }
            }
    }

    fun decode(text: String): Decoded {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Molnbackupen är för stor." }
        val envelope = JSONObject(text)
        require(envelope.getString("format") == FORMAT && envelope.getString("restore_scope") == "full_backup") {
            "Det här är inte en fullständig Caffeine Tracker-molnbackup."
        }
        val payload = envelope.getString("payload")
        require(sha256(payload) == envelope.getString("sha256")) {
            "Kontrollsumman för molnbackupen stämde inte. Inget återställdes."
        }
        val snapshot = BackupCodec.decode(payload)
        return Decoded(snapshot, payload, fingerprint(snapshot))
    }

    fun sha256(text: String): String = digest { it.write(text) }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun digest(write: (Writer) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val discard = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }
        DigestOutputStream(discard, digest).bufferedWriter(Charsets.UTF_8).use(write)
        return hex(digest.digest())
    }

    fun fingerprint(snapshot: TrackerSnapshot): String {
        val root = snapshotJson(snapshot)
        return digest { BackupJsonWriter.write(root, it, sortKeys = true, excludedRootKeys = setOf("createdAt")) }
    }
}
