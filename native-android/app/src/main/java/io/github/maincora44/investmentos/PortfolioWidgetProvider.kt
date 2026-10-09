package io.github.maincora44.investmentos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class PortfolioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PortfolioWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.portfolio_widget)
            val snapshot = SnapshotStore.read(context)
            val show = SnapshotStore.valuesVisible(context)
            views.setTextViewText(R.id.total, when {
                snapshot == null -> "Import snapshot"
                !show -> "₩••••••••"
                else -> SnapshotStore.won(snapshot.observedTotal)
            })
            views.setTextViewText(R.id.detail, when {
                snapshot == null -> "Tap to import local JSON"
                !show -> "Values hidden · tap to open"
                else -> "5 accounts: " + SnapshotStore.won(snapshot.fiveAccountTotal)
            })
            views.setTextViewText(R.id.market, "US / KR regime: Not connected")
            views.setTextViewText(R.id.date, when {
                snapshot == null -> "No private data loaded"
                snapshot.provisional -> snapshot.asOf + " · mixed-date / provisional"
                else -> snapshot.asOf + " · snapshot, not live"
            })
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
