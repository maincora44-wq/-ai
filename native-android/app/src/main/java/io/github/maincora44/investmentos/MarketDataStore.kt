package io.github.maincora44.investmentos

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

data class MarketPoint(
    val value: Double?,
    val changePct: Double?,
    val distance200Pct: Double?,
    val regime: String
)

data class MarketData(
    val generatedAt: String,
    val sp500: MarketPoint,
    val kospi: MarketPoint,
    val usdkrw: MarketPoint,
    val us10y: MarketPoint
)

object MarketDataStore {
    private const val PREF = "public_market"
    private const val DATA = "market_json"
    private const val URL_PRIMARY = "https://maincora44-wq.github.io/-ai/market-data.json"

    fun read(context: Context): MarketData? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(DATA, null) ?: return null
        return try { parse(raw) } catch (_: Exception) { null }
    }

    fun refreshAsync(context: Context, callback: (Boolean) -> Unit = {}) {
        Executors.newSingleThreadExecutor().execute {
            val ok = try {
                val conn = (URL(URL_PRIMARY).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "InvestmentOS-Android/2.0")
                    useCaches = false
                }
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                require(conn.responseCode in 200..299) { "HTTP " + conn.responseCode }
                parse(raw)
                context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(DATA, raw).commit()
            } catch (_: Exception) { false }
            callback(ok)
        }
    }

    private fun parse(raw: String): MarketData {
        val root = JSONObject(raw)
        require(root.optString("schema") == "investment-os-market-v1") { "Wrong market schema" }
        fun p(key: String): MarketPoint {
            val o = root.getJSONObject(key)
            return MarketPoint(
                value = o.optDoubleOrNull("value"),
                changePct = o.optDoubleOrNull("change_pct"),
                distance200Pct = o.optDoubleOrNull("distance_200dma_pct"),
                regime = o.optString("regime", "UNKNOWN")
            )
        }
        return MarketData(
            generatedAt = root.optString("generated_at", "UNKNOWN"),
            sp500 = p("sp500"),
            kospi = p("kospi"),
            usdkrw = p("usdkrw"),
            us10y = p("us10y")
        )
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isFinite()) v else null
    }

    fun marketLabel(code: String, point: MarketPoint): String {
        val dist = point.distance200Pct?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—"
        return code + " · " + point.regime + " " + dist
    }

    fun macroLabel(market: MarketData?): String {
        if (market == null) return "USD/KRW — · US10Y —"
        val fx = market.usdkrw.value?.let { String.format(Locale.US, "%,.0f", it) } ?: "—"
        val y = market.us10y.value?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—"
        return "USD/KRW " + fx + " · US10Y " + y
    }
}
