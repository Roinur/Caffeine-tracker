package com.roinur.caffeinetracker.data.backup

internal object LocalBackupRetention {
    const val VERSION_COUNT = 5
    val previousNames = listOf(
        PROCEDURAL_BACKUP_PREVIOUS_1_FILENAME,
        PROCEDURAL_BACKUP_PREVIOUS_2_FILENAME,
        PROCEDURAL_BACKUP_PREVIOUS_3_FILENAME,
        PROCEDURAL_BACKUP_PREVIOUS_4_FILENAME
    )

    /** Called only after the new pending backup has passed write/read-back validation. */
    fun <T : Any> rotate(find: (String) -> T?, delete: (T) -> Unit, rename: (T, String) -> T): T? {
        find(previousNames.last())?.let(delete)
        for (index in previousNames.lastIndex downTo 1) {
            find(previousNames[index - 1])?.let { rename(it, previousNames[index]) }
        }
        return find(PROCEDURAL_BACKUP_CURRENT_FILENAME)?.let { rename(it, previousNames.first()) }
    }
}
