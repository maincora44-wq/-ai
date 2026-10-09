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
                snapshot == null -> "JSON 불러오기"
                !show -> "₩••••"
                else -> SnapshotStore.shortWon(snapshot.observedTotal)
            })

            fun value(v: Long): String = if (show) SnapshotStore.shortWon(v) else "••••"
            views.setTextViewText(R.id.active_asset,
                if (snapshot == null) "운용\n—" else "운용\n" + value(snapshot.activeTotal))
            views.setTextViewText(R.id.retirement_asset,
                if (snapshot == null) "연금\n—" else "연금\n" + value(snapshot.retirementTotal))
            views.setTextViewText(R.id.isa_asset,
                if (snapshot == null) "ISA\n—" else "ISA\n" + value(snapshot.isaTotal))
            views.setTextViewText(R.id.employee_asset,
                if (snapshot == null) "임직원\n—" else "임직원\n" + value(snapshot.employeeTotal))

            val topPct = if (snapshot != null && snapshot.observedTotal > 0)
                snapshot.topExposureValue * 100.0 / snapshot.observedTotal else null
            views.setTextViewText(R.id.top_exposure, when {
                snapshot == null -> "최대 익스포저 —"
                !show -> "최대 익스포저 " + snapshot.topExposureName
                topPct != null -> "최대 익스포저 " + snapshot.topExposureName + " · " + String.format("%.1f%%", topPct)
                else -> "최대 익스포저 —"
            })

            views.setTextViewText(R.id.us_market, when {
                market == null -> "미국\n—"
                else -> "미국\n" + MarketDataStore.regimeShort(market.sp500) + " " +
                    MarketDataStore.distanceLabel(market.sp500)
            })
            views.setTextViewText(R.id.kr_market, when {
                market == null -> "한국\n—"
                else -> "한국\n" + MarketDataStore.regimeShort(market.kospi) + " " +
                    MarketDataStore.distanceLabel(market.kospi)
            })
            views.setTextViewText(R.id.macro, MarketDataStore.macroLabel(market))
            views.setTextViewText(
                R.id.market_time,
                "MKT " + (market?.let { MarketDataStore.kstTime(it.generatedAt) } ?: "—")
            )

            val pDate = snapshot?.asOf?.replace(" KST", "") ?: "No portfolio"
            val provisional = if (snapshot?.provisional == true) " · 임직원 잠정" else ""
            views.setTextViewText(R.id.date, "Portfolio " + pDate + provisional)

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
