package io.github.maincora44.investmentos

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    private val executor = Executors.newSingleThreadExecutor()
    private val kstFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Seoul"))

    fun read(context: Context): MarketData? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(DATA, null) ?: return null
        return try { parse(raw) } catch (_: Exception) { null }
    }

    fun refreshAsync(context: Context, callback: (Boolean) -> Unit = {}) {
        executor.execute {
            val ok = try {
                val conn = (URL(URL_PRIMARY).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "InvestmentOS-Android/2.1")
                    setRequestProperty("Cache-Control", "no-cache")
                    useCaches = false
                }
                try {
                    val code = conn.responseCode
                    require(code in 200..299) { "HTTP " + code }
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    parse(raw)
                    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(DATA, raw).commit()
                } finally {
                    conn.disconnect()
                }
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

    fun regimeShort(point: MarketPoint): String = when (point.regime) {
        "RISK-ON" -> "RISK-ON"
        "RISK-OFF" -> "RISK-OFF"
        "NEUTRAL" -> "NEUTRAL"
        else -> "—"
    }

    fun distanceLabel(point: MarketPoint): String =
        point.distance200Pct?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—"

    fun macroLabel(market: MarketData?): String {
        if (market == null) return "환율 —   ·   미10년 —"
        val fx = market.usdkrw.value?.let { String.format(Locale.US, "%,.0f", it) } ?: "—"
        val y = market.us10y.value?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—"
        return "환율 " + fx + "   ·   미10년 " + y
    }

    fun kstTime(iso: String): String {
        if (iso.isBlank() || iso == "UNKNOWN") return "—"
        return try { kstFormatter.format(Instant.parse(iso)) } catch (_: Exception) { iso.take(16) }
    }
}
