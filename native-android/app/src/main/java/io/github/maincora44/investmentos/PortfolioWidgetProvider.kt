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
        appWidgetIds.forEach { render(context, manager, it) }
        val pendingResult = goAsync()
        MarketDataStore.refreshAsync(context) {
            appWidgetIds.forEach { render(context, manager, it) }
            pendingResult.finish()
        }
    }

    companion object {
        fun refreshAll(context: Context, refreshMarket: Boolean = false, onDone: ((Boolean) -> Unit)? = null) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PortfolioWidgetProvider::class.java))
            ids.forEach { render(context, manager, it) }
            if (refreshMarket) {
                MarketDataStore.refreshAsync(context) { ok ->
                    ids.forEach { render(context, manager, it) }
                    onDone?.invoke(ok)
                }
            } else onDone?.invoke(true)
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.portfolio_widget)
            val snapshot = SnapshotStore.read(context)
            val market = MarketDataStore.read(context)
            val show = SnapshotStore.valuesVisible(context)

            views.setTextViewText(R.id.total, when {
                snapshot == null -> "Import snapshot"
                !show -> "₩••••"
                else -> SnapshotStore.shortWon(snapshot.observedTotal)
            })

            fun value(v: Long): String = if (show) SnapshotStore.shortWon(v) else "••••"
            views.setTextViewText(R.id.asset_left, when {
                snapshot == null -> "운용 —\n퇴직 —"
                else -> "운용 " + value(snapshot.activeTotal) + "\n퇴직 " + value(snapshot.retirementTotal)
            })
            views.setTextViewText(R.id.asset_right, when {
                snapshot == null -> "ISA —\n임직원 —"
                else -> "ISA " + value(snapshot.isaTotal) + "\n임직원 " + value(snapshot.employeeTotal)
            })

            val topPct = if (snapshot != null && snapshot.observedTotal > 0)
                snapshot.topExposureValue * 100.0 / snapshot.observedTotal else null
            views.setTextViewText(R.id.top_exposure, when {
                snapshot == null -> "Top exposure: —"
                !show -> "Top exposure: " + snapshot.topExposureName + " · ••%"
                topPct != null -> "Top exposure: " + snapshot.topExposureName + " · " + String.format("%.1f%%", topPct)
                else -> "Top exposure: —"
            })

            views.setTextViewText(R.id.us_market,
                market?.let { MarketDataStore.marketLabel("US", it.sp500) } ?: "US · awaiting data")
            views.setTextViewText(R.id.kr_market,
                market?.let { MarketDataStore.marketLabel("KR", it.kospi) } ?: "KR · awaiting data")
            views.setTextViewText(R.id.macro, MarketDataStore.macroLabel(market))

            val pDate = snapshot?.asOf?.replace(" KST", "") ?: "No portfolio"
            val mDate = market?.generatedAt?.replace("T", " ")?.take(16) ?: "market pending"
            val provisional = if (snapshot?.provisional == true) " · provisional" else ""
            views.setTextViewText(R.id.date, pDate + provisional + " · MKT " + mDate)

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
