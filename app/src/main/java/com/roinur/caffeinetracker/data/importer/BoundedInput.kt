package com.roinur.caffeinetracker.data.importer

import java.io.ByteArrayOutputStream
import java.io.InputStream
import com.roinur.caffeinetracker.data.backup.BackupCodec

internal fun InputStream.readTrackerBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val length = read(buffer)
        if (length < 0) break
        require(output.size() + length <= BackupCodec.MAX_BYTES) { "Filen är för stor (max 20 MB)." }
        output.write(buffer, 0, length)
    }
    return output.toByteArray()
}
