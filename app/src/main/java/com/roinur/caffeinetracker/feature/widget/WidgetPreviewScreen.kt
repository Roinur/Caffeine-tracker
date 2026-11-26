package com.roinur.caffeinetracker.feature.widget

import android.appwidget.*
import android.content.ComponentName
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.roinur.caffeinetracker.BuildConfig
import com.roinur.caffeinetracker.core.diagnostics.GitHubMediaSession

/** Real AppWidgetHost/RemoteViews collection, bound only for explicit GitHub QA sessions. */
@Composable
internal fun WidgetPreviewScreen(onDashboard: () -> Unit = {}) {
    check(BuildConfig.DEBUG && GitHubMediaSession.active)
    val context=LocalContext.current
    val manager=remember { AppWidgetManager.getInstance(context) }
    val host=remember { AppWidgetHost(context,7250).also { it.deleteHost() } }
    val id=remember {
        host.allocateAppWidgetId().also { WidgetStorage.registerSample(context,it) }
    }
    val component=ComponentName(context,CaffeineWidget::class.java)
    val bound=remember {
        manager.bindAppWidgetIdIfAllowed(id,component,Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,GitHubMediaSession.widgetWidthDp);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,GitHubMediaSession.widgetWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,GitHubMediaSession.widgetHeightDp);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,GitHubMediaSession.widgetHeightDp)
        })
    }
    DisposableEffect(host,id) {
        onDispose { host.stopListening();host.deleteAppWidgetId(id);WidgetStorage.removeSample(context,id) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Hemskärmswidget",style=MaterialTheme.typography.headlineSmall)
        Text("GitHub-läge · riktig widget med separata exempeldata",style=MaterialTheme.typography.bodySmall)
        if(bound) AndroidView(factory={ host.createView(it,id,manager.getAppWidgetInfo(id)).also {
            // Register the actual listener before startListening sends its widget-ID list to Android.
            host.startListening();CaffeineWidget.refresh(context,true)
        } },modifier=Modifier.width(GitHubMediaSession.widgetWidthDp.dp).height(GitHubMediaSession.widgetHeightDp.dp))
        else Text("Widgetbindning saknas för granskning.")
        Text("Tryck på en favorit för att registrera. Ångra tar bort just det intaget.",style=MaterialTheme.typography.bodyMedium)
        Button(onClick=onDashboard) { Text("Visa dagens intag") }
    }
}
