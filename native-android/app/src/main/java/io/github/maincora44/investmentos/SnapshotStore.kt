package io.github.maincora44.investmentos

import android.content.Context
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

data class PortfolioSnapshot(
    val id: String,
    val asOf: String,
    val observedTotal: Long,
    val fiveAccountTotal: Long,
    val accountCount: Int,
    val activeTotal: Long,
    val retirementTotal: Long,
    val isaTotal: Long,
    val employeeTotal: Long,
    val provisional: Boolean,
    val topExposureName: String,
    val topExposureValue: Long
)

object SnapshotStore {
    private const val PREF = "private_portfolio"
    private const val DATA = "snapshot"
    private const val SHOW = "show_values"

    fun read(context: Context): PortfolioSnapshot? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(DATA, null) ?: return null
        return try { parse(raw) } catch (_: Exception) { null }
    }

    fun save(context: Context, json: String): PortfolioSnapshot {
        require(json.length <= 2_000_000) { "File exceeds 2 MB" }
        val snapshot = parse(json)
        check(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(DATA, json).commit()) {
            "Unable to save snapshot"
        }
        return snapshot
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(DATA).apply()
    }

    fun valuesVisible(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(SHOW, false)

    fun setValuesVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(SHOW, visible).apply()
    }

    private fun parse(json: String): PortfolioSnapshot {
        val root = JSONObject(json)
        require(root.optString("schema") == "portfolio-lab-private-snapshot-v1") { "Wrong JSON schema" }
        val accounts = root.getJSONArray("accounts")
        require(accounts.length() == 5) { "Expected five brokerage accounts" }

        var accountSum = 0L
        var active = 0L
        var retirement = 0L
        var isa = 0L
        val exposures = linkedMapOf<String, Long>()

        for (i in 0 until accounts.length()) {
            val account = accounts.getJSONObject(i)
            val accountName = account.optString("name")
            val accountTotal = account.getLong("total")
            require(accountTotal >= 0) { "Negative account total" }

            when (accountName) {
                "Comprehensive" -> active = accountTotal
                "DC", "IRP", "Pension" -> retirement = Math.addExact(retirement, accountTotal)
                "ISA" -> isa = accountTotal
            }

            val holdings = account.getJSONArray("holdings")
            var holdingSum = 0L
            for (j in 0 until holdings.length()) {
                val holding = holdings.getJSONObject(j)
                val value = holding.getLong("value")
                require(value >= 0) { "Negative holding value" }
                holdingSum = Math.addExact(holdingSum, value)
                val name = shortName(holding.optString("name", "Unknown"))
                exposures[name] = Math.addExact(exposures[name] ?: 0L, value)
            }
            require(abs(holdingSum - accountTotal) <= 1L) { "Holdings do not reconcile" }
            accountSum = Math.addExact(accountSum, accountTotal)
        }

        val five = root.getLong("five_account_total")
        require(abs(accountSum - five) <= 1L) { "Accounts do not reconcile" }

        val employee = root.optJSONObject("employee_shares")
        val employeeValue = employee?.optLong("value", 0L) ?: 0L
        require(employeeValue >= 0) { "Invalid employee share value" }
        if (employeeValue > 0) exposures["KT&G 임직원주식"] =
            Math.addExact(exposures["KT&G 임직원주식"] ?: 0L, employeeValue)

        val observed = root.getLong("observed_investments")
        require(observed >= 0) { "Invalid investment total" }
        require(abs(observed - Math.addExact(five, employeeValue)) <= 1L) {
            "Observed investments do not reconcile"
        }

        val top = exposures.maxByOrNull { it.value }

        return PortfolioSnapshot(
            id = root.optString("snapshot_id", "UNKNOWN"),
            asOf = root.optString("as_of", "UNKNOWN"),
            observedTotal = observed,
            fiveAccountTotal = five,
            accountCount = accounts.length(),
            activeTotal = active,
            retirementTotal = retirement,
            isaTotal = isa,
            employeeTotal = employeeValue,
            provisional = employee?.optString("status") == "PROVISIONAL",
            topExposureName = top?.key ?: "UNKNOWN",
            topExposureValue = top?.value ?: 0L
        )
    }

    private fun shortName(name: String): String = when {
        name.contains("Floating Rate Treasury", ignoreCase = true) || name.contains("USFR", ignoreCase = true) ->
            "USFR · 미 단기국채"
        name.contains("머니마켓", ignoreCase = true) || name.contains("Money Market", ignoreCase = true) ->
            "KODEX 미국머니마켓"
        name.contains("나스닥100", ignoreCase = true) || name.contains("Nasdaq", ignoreCase = true) ->
            "미국 Nasdaq 100"
        name.contains("KRX금현물", ignoreCase = true) -> "KRX 금현물"
        else -> if (name.length > 24) name.take(23) + "…" else name
    }

    fun won(value: Long): String = "₩" + NumberFormat.getIntegerInstance(Locale.KOREA).format(value)

    fun shortWon(value: Long): String {
        val eok = value / 100_000_000.0
        return if (value >= 100_000_000L) "₩" + String.format(Locale.KOREA, "%.2f억", eok)
        else if (value >= 10_000L) "₩" + String.format(Locale.KOREA, "%.0f만", value / 10_000.0)
        else won(value)
    }
}
