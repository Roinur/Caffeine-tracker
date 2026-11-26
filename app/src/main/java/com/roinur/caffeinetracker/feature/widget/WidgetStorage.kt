package com.roinur.caffeinetracker.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.roinur.caffeinetracker.BuildConfig
import com.roinur.caffeinetracker.core.diagnostics.GitHubMediaSession
import com.roinur.caffeinetracker.core.model.Intake
import com.roinur.caffeinetracker.data.backup.BackupCodec
import com.roinur.caffeinetracker.data.database.CaffeineDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

internal object TrackerDataEvents {
    val changes = MutableStateFlow(0L)
    val mutations = Mutex()
    fun changed() { changes.update { it + 1 } }
}
internal object WidgetStorage {
    private fun sources(c: Context) = c.getSharedPreferences("widget_sample_ids",Context.MODE_PRIVATE)
    fun sample(c: Context,id: Int) = BuildConfig.DEBUG && sources(c).getBoolean("sample:$id",false) && !sources(c).getBoolean("deleted:$id",false)
    fun registerSample(c: Context,id: Int) { check(BuildConfig.DEBUG && GitHubMediaSession.active)
        sources(c).edit().putBoolean("sample:$id",true).putBoolean("deleted:$id",false).putLong("time:$id",GitHubMediaSession.now)
            .putBoolean("realtime:$id",GitHubMediaSession.realtimeClock).commit() }
    fun removeSample(c: Context,id: Int) { if(sources(c).getBoolean("sample:$id",false)) sources(c).edit()
        .putBoolean("deleted:$id",true).remove("sample:$id").remove("time:$id").remove("realtime:$id").apply() }
    fun database(c: Context,id: Int) = CaffeineDatabase(c,if(sample(c,id)) GitHubMediaSession.DATABASE_NAME else "caffeine_tracker.db")
    fun now(c: Context,id: Int) = if(sample(c,id) && !sources(c).getBoolean("realtime:$id",false)) sources(c).getLong("time:$id",0) else System.currentTimeMillis()
    fun preferences(c: Context,id: Int) = c.getSharedPreferences(if(sample(c,id)) "widget_actions_sample" else "widget_actions",Context.MODE_PRIVATE)
    fun lastIntake(c: Context,id: Int): Intake? = preferences(c,id).getString("last_intake",null)?.let {
        runCatching { BackupCodec.intakeFromJson(JSONObject(it)) }.getOrNull()
    }
    fun remember(c: Context,id: Int,intake: Intake) { preferences(c,id).edit().putString("last_intake",BackupCodec.intakeJson(intake).toString())
        .putLong("last_added",System.currentTimeMillis()).apply() }
    fun undoAvailable(c: Context,id: Int) = System.currentTimeMillis()-preferences(c,id).getLong("last_added",0) in 0..600000 && lastIntake(c,id)!=null
    fun clearUndo(c: Context,id: Int) { preferences(c,id).edit().remove("last_intake").remove("last_added").apply() }
}
