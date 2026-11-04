package com.example.auralocalai.data.briefing

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.auralocalai.AppLocale
import com.example.auralocalai.MainActivity
import com.example.auralocalai.R

/** Home-screen widget with the headlines of the latest briefing. Tap opens the news. */
class BriefingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        /** Call after a briefing was saved. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BriefingWidgetProvider::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val localized = AppLocale.localized(context)
            val latest = try { BriefingRepository(context.filesDir).loadHistory().firstOrNull() } catch (_: Exception) { null }
            val views = RemoteViews(context.packageName, R.layout.widget_briefing)
            views.setTextViewText(R.id.widget_title, latest?.profileName?.ifBlank { null } ?: localized.getString(R.string.nav_news))
            val titles = latest?.let { b ->
                b.articles.map { it.title.ifBlank { it.item.title } }.ifEmpty { b.sections.flatMap { s -> s.items.map { it.title } } }
            }.orEmpty()
            val lines = listOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3)
            lines.forEachIndexed { i, viewId ->
                views.setTextViewText(viewId, titles.getOrNull(i)?.let { "• $it" } ?: if (i == 0) localized.getString(R.string.widget_empty) else "")
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_BRIEFING, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 3001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            return views
        }
    }
}
