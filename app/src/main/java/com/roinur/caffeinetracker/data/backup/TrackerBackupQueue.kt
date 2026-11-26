package com.roinur.caffeinetracker.data.backup

import android.content.Context
import com.roinur.caffeinetracker.data.database.CaffeineDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BackupCompletion(val revision: Long = 0, val error: String? = null)

/** Process scope survives the activity. Slow SAF writes cannot block the database/UI mutation queue. */
internal object TrackerBackupQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Context>(Channel.CONFLATED)
    private val lock = Mutex()
    private val completion = MutableStateFlow(BackupCompletion())
    val status = completion.asStateFlow()
    init {
        scope.launch {
            for(context in requests) {
                var error: String? = null
                try { lock.withLock {
                    val snapshot = CaffeineDatabase(context, "caffeine_tracker.db").use { it.snapshot() }
                    CaffeineBackupStore(context).checkpoint(snapshot)
                } } catch(e: Exception) {
                    if(e is CancellationException) throw e
                    error = e.message ?: "Automatisk backup misslyckades"
                }
                completion.value = BackupCompletion(completion.value.revision + 1, error)
            }
        }
    }
    fun request(context: Context) { requests.trySend(context.applicationContext) }
    suspend fun exclusive(block: () -> Unit) = lock.withLock { block() }
}
