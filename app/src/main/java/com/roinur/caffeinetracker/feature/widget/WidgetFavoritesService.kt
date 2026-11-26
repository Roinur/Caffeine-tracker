package com.roinur.caffeinetracker.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.roinur.caffeinetracker.R
import com.roinur.caffeinetracker.core.model.*
import com.roinur.caffeinetracker.core.ui.components.drinkImageResource
import com.roinur.caffeinetracker.core.ui.i18n.AppStrings
import com.roinur.caffeinetracker.core.ui.theme.resolveCaffeinePalette
import androidx.compose.ui.graphics.toArgb

class WidgetFavoritesService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = FavoritesFactory(applicationContext,intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1))
}
internal class FavoritesFactory(private val context: Context,private val widgetId: Int): RemoteViewsService.RemoteViewsFactory {
    private var drinks=emptyList<Drink>()
    private var settings=TrackerSettings()
    override fun onCreate() { onDataSetChanged() }
    override fun onDataSetChanged() {
        WidgetStorage.database(context,widgetId).use { db -> val snapshot=db.snapshot();drinks=snapshot.drinks.filter { it.favorite };settings=snapshot.settings }
    }
    override fun onDestroy() {}
    override fun getCount()=drinks.size
    override fun getViewAt(position: Int): RemoteViews? {
        val drink=drinks.getOrNull(position) ?: return null
        return widgetFavoriteRow(context,drink,settings)
    }
    override fun getLoadingView(): RemoteViews?=null
    override fun getViewTypeCount()=1
    override fun getItemId(position: Int)=drinks.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds()=true
}

internal fun widgetFavoriteRow(context: Context, drink: Drink, settings: TrackerSettings): RemoteViews {
        val language = AppLanguage.fromCode(settings.language)
        val strings = AppStrings.forLanguage(language)
        val shownName = displayDrinkName(drink.name, language)
        val palette=resolveCaffeinePalette(context,settings,WidgetRenderer.dark(context,settings))
        return RemoteViews(context.packageName,R.layout.widget_favorite_row).apply {
            setTextViewText(R.id.widget_drink_name,shownName)
            setTextViewText(R.id.widget_drink_dose,displayPortion(drink, language)+" · "+WidgetRenderer.mg(drink.caffeineMg))
            setTextColor(R.id.widget_drink_name,palette.onSurface.toArgb())
            setTextColor(R.id.widget_drink_dose,palette.onSurfaceVariant.toArgb())
            setImageViewResource(R.id.widget_drink_image,drinkImageResource(drinkImage(drink.name,drink.category,drink.imageKey)) ?: R.drawable.widget_coffee)
            setImageViewBitmap(R.id.widget_drink_add,WidgetRenderer.plus(palette.primary.toArgb(),palette.onPrimary.toArgb()))
            val fill=Intent().putExtra("drink_id",drink.id)
            setOnClickFillInIntent(R.id.widget_drink_row,fill)
            setContentDescription(R.id.widget_drink_row,strings.widgetAddDesc(shownName,WidgetRenderer.mg(drink.caffeineMg)))
        }
}
