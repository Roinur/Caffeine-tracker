package com.roinur.caffeinetracker.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.roinur.caffeinetracker.R
import com.roinur.caffeinetracker.app.MainActivity
import com.roinur.caffeinetracker.core.caffeine.CaffeineCalculator
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.i18n.AppStrings
import com.roinur.caffeinetracker.core.ui.theme.resolveCaffeinePalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal object WidgetRenderer {
    fun dark(c: Context,s: TrackerSettings)=when(s.themeMode) { "DARK"->true;"LIGHT"->false;else->c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES }
    fun mg(value: Double)=value.roundToInt().toString()+" mg"
    private fun clock(time: Long)=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    fun build(c: Context,id: Int,snapshot: TrackerSnapshot,now: Long,options: Bundle): RemoteViews {
        val palette=resolveCaffeinePalette(c,snapshot.settings,dark(c,snapshot.settings))
        val model=widgetSummary(snapshot,now)
        val density=c.resources.displayMetrics.density
        val width=(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,320)*density).toInt().coerceIn(320,1200)
        val height=(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,160)*density).toInt().coerceIn(160,600)
        val compact=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,200) < 190
        val language = AppLanguage.fromCode(snapshot.settings.language)
        val strings = AppStrings.forLanguage(language)
        val views=RemoteViews(c.packageName,if(compact) R.layout.caffeine_widget_compact else R.layout.caffeine_widget)
        views.setImageViewBitmap(R.id.widget_background,graph(snapshot,now,width,height,palette.surfaceContainerLow.toArgb(),palette.primary.toArgb()))
        views.setTextViewText(R.id.widget_label,strings.widgetNowLabel)
        views.setTextViewText(R.id.widget_now,mg(model.current))
        views.setTextViewText(R.id.widget_evening,strings.atTimePrefix+clock(model.bedtime)+" · "+mg(model.evening))
        views.setTextViewText(R.id.widget_today,strings.widgetTodayPrefix+mg(model.today))
        views.setTextViewText(R.id.widget_updated,clock(System.currentTimeMillis()))
        views.setTextViewText(R.id.widget_undo,strings.widgetUndo)
        views.setTextViewText(R.id.widget_header_undo,strings.widgetUndo)
        views.setTextViewText(R.id.widget_empty,strings.widgetNoFavorites)
        for(view in listOf(R.id.widget_title,R.id.widget_now)) views.setTextColor(view,palette.onSurface.toArgb())
        for(view in listOf(R.id.widget_label,R.id.widget_evening,R.id.widget_today,R.id.widget_updated,R.id.widget_empty)) views.setTextColor(view,palette.onSurfaceVariant.toArgb())
        views.setTextColor(R.id.widget_undo,palette.primary.toArgb())
        val refresh=PendingIntent.getBroadcast(c,id,Intent(c,CaffeineWidget::class.java).setAction(CaffeineWidget.REFRESH).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val launchIntent=Intent(c,MainActivity::class.java).apply {
            action=Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val open=if(WidgetStorage.sample(c,id)) refresh else PendingIntent.getActivity(c,id,launchIntent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_root,open)
        views.setOnClickPendingIntent(R.id.widget_metrics,open)
        views.setOnClickPendingIntent(R.id.widget_now,open)
        views.setOnClickPendingIntent(R.id.widget_title,open)
        views.setOnClickPendingIntent(R.id.widget_refresh,refresh)
        if(Build.VERSION.SDK_INT >= 31) {
            val items=RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1).setHasStableIds(false)
            snapshot.drinks.filter { it.favorite }.forEachIndexed { index,drink ->
                items.addItem(index.toLong(),widgetFavoriteRow(c,drink,snapshot.settings))
            }
            views.setRemoteAdapter(R.id.widget_favorites,items.build())
        } else {
            val service=Intent(c,WidgetFavoritesService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("caffeine-widget://favorites/$id"))
            views.setRemoteAdapter(R.id.widget_favorites,service)
        }
        views.setEmptyView(R.id.widget_favorites,R.id.widget_empty)
        val add=Intent(c,CaffeineWidget::class.java).setAction(CaffeineWidget.ADD).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
        views.setPendingIntentTemplate(R.id.widget_favorites,PendingIntent.getBroadcast(c,id,add,PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        val last=WidgetStorage.lastIntake(c,id)
        val canUndo=WidgetStorage.undoAvailable(c,id) && last in snapshot.intakes
        val undoIntent=Intent(c,CaffeineWidget::class.java).setAction(CaffeineWidget.UNDO)
            .setData(Uri.parse("caffeine-widget://undo/$id/${last?.id.orEmpty()}"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("intake_id",last?.id)
        val undo=PendingIntent.getBroadcast(c,id,undoIntent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_undo,undo)
        // Keep the body stable when the receipt appears. Short widgets use the header for Undo.
        views.setViewVisibility(R.id.widget_label,if(compact) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_today,if(compact) View.GONE else View.VISIBLE)
        views.setTextViewTextSize(R.id.widget_now,TypedValue.COMPLEX_UNIT_SP,if(compact) 21f else 28f)
        views.setViewVisibility(R.id.widget_footer,if(!compact && canUndo) View.VISIBLE else if(compact) View.GONE else View.INVISIBLE)
        views.setViewVisibility(R.id.widget_header_undo,if(compact && canUndo) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_refresh,if(compact && canUndo) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_header_undo,undo)
        views.setTextColor(R.id.widget_header_undo,palette.primary.toArgb())
        val lastName = last?.let { displayDrinkName(it.name, language) }
        views.setTextViewText(R.id.widget_title,if(compact && canUndo) strings.widgetAddedPrefix+lastName else c.getString(R.string.app_name))
        views.setTextViewText(R.id.widget_status,if(lastName!=null) strings.widgetAddedPrefix+lastName else "")
        views.setTextColor(R.id.widget_status,palette.onSurfaceVariant.toArgb())
        views.setContentDescription(R.id.widget_metrics,strings.widgetMetricsDesc(mg(model.current),mg(model.evening),mg(model.today)))
        return views
    }
    private var plusCache: Triple<Int,Int,Bitmap>? = null
    @Synchronized fun plus(color: Int,onColor: Int): Bitmap {
        plusCache?.takeIf { it.first==color && it.second==onColor }?.let { return it.third }
        val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color }
        canvas.drawCircle(40f,40f,34f,paint)
        paint.color=onColor;paint.strokeWidth=5f;paint.strokeCap=Paint.Cap.ROUND
        canvas.drawLine(27f,40f,53f,40f,paint);canvas.drawLine(40f,27f,40f,53f,paint)
        plusCache=Triple(color,onColor,bitmap)
        return bitmap
    }
    private fun graph(snapshot: TrackerSnapshot,now: Long,width: Int,height: Int,background: Int,accent: Int): Bitmap {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val radius=height*.14f;val rect=RectF(0f,0f,width.toFloat(),height.toFloat())
        val clip=Path().apply { addRoundRect(rect,radius,radius,Path.Direction.CW) };canvas.clipPath(clip)
        paint.color=background;canvas.drawRect(rect,paint)
        val start=now-6*3600000L;val duration=12*3600000L
        val known=snapshot.intakes.filter { it.timestamp<=now && it.timestamp>=start-CaffeineCalculator.activeWindowMs(it,snapshot.settings.halfLifeHours) }
        val values=(0..72).map { CaffeineCalculator.total(known,start+duration*it/72,snapshot.settings.halfLifeHours) }
        val max=(values.maxOrNull() ?: 0.0).coerceAtLeast(50.0)
        val path=Path()
        values.forEachIndexed { index,value ->
            val x=width*index/72f;val y=(height-height*.75f*value/max).toFloat()
            if(index==0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        val area=Path(path).apply { lineTo(width.toFloat(),height.toFloat());lineTo(0f,height.toFloat());close() }
        paint.color=(accent and 0x00ffffff) or (22 shl 24);canvas.drawPath(area,paint)
        paint.color=(accent and 0x00ffffff) or (55 shl 24);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
        canvas.drawPath(path,paint)
        paint.color=(accent and 0x00ffffff) or (30 shl 24)
        canvas.drawLine(width*.5f,0f,width*.5f,height.toFloat(),paint)
        return bitmap
    }
}
