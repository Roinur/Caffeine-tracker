package com.roinur.caffeinetracker

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.test.AndroidTestCase
import com.roinur.caffeinetracker.core.model.Intake
import com.roinur.caffeinetracker.data.database.CaffeineDatabase
import com.roinur.caffeinetracker.data.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses its own DB, preferences and files, including inside the process-owned backup worker. */
class BackupQueueDeviceTest : AndroidTestCase() {
    fun testSlowBackupCannotBlockSavingAndQueuedBackupReadsLatestState() = runBlocking {
        val base = context
        val folder = File(base.cacheDir,"qa-backup-queue").apply { mkdirs() }
        val databaseFile = base.getDatabasePath("qa_backup_queue.db")
        base.deleteDatabase(databaseFile.name)
        val isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = folder
            override fun getDatabasePath(name: String): File = databaseFile
            override fun getSharedPreferences(name: String,mode: Int) = base.getSharedPreferences("qa_queue_"+name,mode)
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = launch(Dispatchers.IO) {
            TrackerBackupQueue.exclusive { entered.countDown();check(release.await(8,TimeUnit.SECONDS)) }
        }
        try {
            check(entered.await(3,TimeUnit.SECONDS))
            val revision = TrackerBackupQueue.status.value.revision
            CaffeineDatabase(isolated).use { db ->
                db.saveIntake(Intake("qa-first","First",80.0,1761981300000))
                val start = SystemClock.elapsedRealtime()
                TrackerBackupQueue.request(isolated)
                db.saveIntake(Intake("qa-second","Second",95.0,1761982300000))
                TrackerBackupQueue.request(isolated)
                assertTrue("Save waited for the blocked backup",SystemClock.elapsedRealtime()-start < 1500)
                assertEquals(2,db.snapshot().intakes.size)
            }
            release.countDown();blocker.join()
            val result = withTimeout(5000) { TrackerBackupQueue.status.first { it.revision > revision } }
            assertNull(result.error)
            val snapshot = BackupCodec.decode(File(folder,"backups/caffeine_backup.json").readText())
            assertEquals(setOf("qa-first","qa-second"),snapshot.intakes.map { it.id }.toSet())
        } finally {
            release.countDown();blocker.join()
            // Flush any conflated follow-up request before removing the test-only directory.
            TrackerBackupQueue.exclusive { }
            base.deleteDatabase(databaseFile.name)
            folder.deleteRecursively()
            base.getSharedPreferences("qa_queue_backup_location",0).edit().clear().commit()
        }
    }
}
