package com.roinur.caffeinetracker.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import com.roinur.caffeinetracker.R
import com.roinur.caffeinetracker.data.backup.TrackerBackupQueue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors

class CaffeineWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) { schedule(context) }
    override fun onDisabled(context: Context) { alarm(context).cancel(alarmIntent(context)) }
    override fun onDeleted(context: Context,ids: IntArray) { ids.forEach { WidgetStorage.removeSample(context,it) } }
    override fun onReceive(context: Context,intent: Intent) {
        super.onReceive(context,intent)
        if(intent.action !in setOf(REFRESH,ADD,UNDO,AppWidgetManager.ACTION_APPWIDGET_UPDATE,AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val receivedAt = SystemClock.elapsedRealtime()
        val pending = goAsync()
        worker.execute {
            try {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
                if(intent.action == ADD || intent.action == UNDO) {
                    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,CaffeineWidget::class.java))
                    if(id !in ids) return@execute
                    runBlocking { TrackerDataEvents.mutations.withLock {
                        WidgetStorage.database(context,id).use { db ->
                            if(intent.action == ADD) {
                                val drinkId = intent.getStringExtra("drink_id") ?: return@use
                                val drink = db.findDrink(drinkId)?.takeIf { it.favorite } ?: return@use
                                val language = com.roinur.caffeinetracker.core.model.AppLanguage.fromCode(db.findSettings().language)
                                val prefs=WidgetStorage.preferences(context,id)
                                val key="tap:$drinkId";val time=System.currentTimeMillis()
                                if(time-prefs.getLong(key,0) in 0..1000) return@use
                                prefs.edit().putLong(key,time).apply()
                                val intake=widgetIntake(drink,WidgetStorage.now(context,id),language);db.saveIntake(intake)
                                WidgetStorage.remember(context,id,intake)
                            } else {
                                if(!WidgetStorage.undoAvailable(context,id)) return@use
                                val original=WidgetStorage.lastIntake(context,id) ?: return@use
                                val expectedId = intent.getStringExtra("intake_id")
                                if(expectedId.isNullOrBlank() || expectedId != original.id) return@use
                                val current=db.findIntake(original.id)
                                if(current != original) { WidgetStorage.clearUndo(context,id);return@use }
                                db.deleteIntake(original.id);WidgetStorage.clearUndo(context,id)
                            }
                        }
                        if(com.roinur.caffeinetracker.BuildConfig.DEBUG) android.util.Log.d("CaffeineLatency","widgetCommitMs="+(SystemClock.elapsedRealtime()-receivedAt))
                        TrackerDataEvents.changed()
                        if(!WidgetStorage.sample(context,id)) {
                            TrackerBackupQueue.request(context)
                            com.roinur.caffeinetracker.data.backup.drive.DriveBackupSync.enqueue(context)
                        }
                    } }
                }
                updateAll(context)
                schedule(context)
            } catch(e: Exception) {
                android.util.Log.e("CaffeineWidget","Widget action/update failed",e)
            } finally { pending.finish() }
        }
    }
    companion object {
        internal const val ADD="com.roinur.caffeinetracker.WIDGET_ADD"
        internal const val UNDO="com.roinur.caffeinetracker.WIDGET_UNDO"
        internal const val REFRESH="com.roinur.caffeinetracker.WIDGET_REFRESH"
        private val worker=Executors.newSingleThreadExecutor()
        fun refresh(context: Context,sampleOnly: Boolean? = null) {
            val app=context.applicationContext
            worker.execute { runCatching { updateAll(app,sampleOnly) }.onFailure { android.util.Log.e("CaffeineWidget","Refresh failed",it) } }
        }
        private fun updateAll(context: Context,sampleOnly: Boolean? = null) {
            val manager=AppWidgetManager.getInstance(context)
            val snapshots=mutableMapOf<Boolean,com.roinur.caffeinetracker.core.model.TrackerSnapshot>()
            manager.getAppWidgetIds(ComponentName(context,CaffeineWidget::class.java)).forEach { id ->
                val isSample=WidgetStorage.sample(context,id)
                if(sampleOnly==null || isSample==sampleOnly) {
                    val snapshot=snapshots.getOrPut(isSample) { WidgetStorage.database(context,id).use { it.snapshot() } }
                    manager.updateAppWidget(id,WidgetRenderer.build(context,id,snapshot,WidgetStorage.now(context,id),manager.getAppWidgetOptions(id)))
                    if(android.os.Build.VERSION.SDK_INT < 31) manager.notifyAppWidgetViewDataChanged(id,R.id.widget_favorites)
                }
            }
        }
        private fun alarm(context: Context)=context.getSystemService(AlarmManager::class.java)
        private fun alarmIntent(context: Context)=PendingIntent.getBroadcast(context,4921,Intent(context,CaffeineWidget::class.java).setAction(REFRESH),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        private fun schedule(context: Context) {
            val ids=AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,CaffeineWidget::class.java))
            if(ids.isNotEmpty()) alarm(context).setInexactRepeating(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+AlarmManager.INTERVAL_FIFTEEN_MINUTES,AlarmManager.INTERVAL_FIFTEEN_MINUTES,alarmIntent(context))
        }
        fun requestPin(context: Context, language: com.roinur.caffeinetracker.core.model.AppLanguage = com.roinur.caffeinetracker.core.model.AppLanguage.SV) {
            val manager=AppWidgetManager.getInstance(context)
            if(manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(context,CaffeineWidget::class.java),null,null)
            else Toast.makeText(context,com.roinur.caffeinetracker.core.ui.i18n.AppStrings.forLanguage(language).widgetPinUnsupported,Toast.LENGTH_LONG).show()
        }
    }
}
