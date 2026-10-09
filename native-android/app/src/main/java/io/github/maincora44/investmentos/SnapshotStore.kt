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
    val provisional: Boolean
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
        for (i in 0 until accounts.length()) {
            val account = accounts.getJSONObject(i)
            val accountTotal = account.getLong("total")
            require(accountTotal >= 0) { "Negative account total" }
            val holdings = account.getJSONArray("holdings")
            var holdingSum = 0L
            for (j in 0 until holdings.length()) {
                val value = holdings.getJSONObject(j).getLong("value")
                require(value >= 0) { "Negative holding value" }
                holdingSum = Math.addExact(holdingSum, value)
            }
            require(abs(holdingSum - accountTotal) <= 1L) { "Holdings do not reconcile" }
            accountSum = Math.addExact(accountSum, accountTotal)
        }
        val five = root.getLong("five_account_total")
        require(abs(accountSum - five) <= 1L) { "Accounts do not reconcile" }
        val employee = root.optJSONObject("employee_shares")
        val employeeValue = employee?.optLong("value", 0L) ?: 0L
        val observed = root.getLong("observed_investments")
        require(observed >= 0 && employeeValue >= 0) { "Invalid investment total" }
        require(abs(observed - Math.addExact(five, employeeValue)) <= 1L) {
            "Observed investments do not reconcile"
        }
        return PortfolioSnapshot(
            root.optString("snapshot_id", "UNKNOWN"),
            root.optString("as_of", "UNKNOWN"),
            observed, five, accounts.length(),
            employee?.optString("status") == "PROVISIONAL"
        )
    }

    fun won(value: Long): String = "₩" + NumberFormat.getIntegerInstance(Locale.KOREA).format(value)
}
