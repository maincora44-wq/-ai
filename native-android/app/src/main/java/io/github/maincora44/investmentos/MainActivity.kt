package io.github.maincora44.investmentos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var marketStatus: TextView
    private lateinit var visibilityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
            setBackgroundColor(Color.rgb(12, 21, 35))
        }
        layout.addView(TextView(this).apply {
            text = "Investment OS v2.0"
            textSize = 25f
            setTextColor(Color.WHITE)
        })
        layout.addView(TextView(this).apply {
            text = "\nPrivate portfolio JSON stays inside this Android app. " +
                "Internet access is used only to download the public market-data.json feed; " +
                "portfolio holdings and balances are never uploaded.\n"
            textSize = 14f
            setTextColor(Color.rgb(188, 207, 230))
        })

        status = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 12)
        }
        layout.addView(status)

        marketStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(142, 188, 255))
            setPadding(0, 0, 0, 20)
        }
        layout.addView(marketStatus)

        fun button(label: String, action: () -> Unit) {
            val b = Button(this).apply { text = label; setOnClickListener { action() } }
            layout.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        button("1. Import local portfolio JSON") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, 101)
        }

        visibilityButton = Button(this)
        layout.addView(visibilityButton)
        visibilityButton.setOnClickListener {
            SnapshotStore.setValuesVisible(this, !SnapshotStore.valuesVisible(this))
            refreshUi()
            PortfolioWidgetProvider.refreshAll(this)
        }

        button("Update public market data now") {
            toast("Updating market data…")
            PortfolioWidgetProvider.refreshAll(this, refreshMarket = true) { ok ->
                runOnUiThread {
                    refreshUi()
                    toast(if (ok) "Market data updated" else "Market update failed; cached data kept")
                }
            }
        }

        button("Refresh home-screen widget") {
            PortfolioWidgetProvider.refreshAll(this)
            refreshUi()
            toast("Widget refreshed")
        }

        button("Erase private portfolio snapshot") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Erase local portfolio data?")
                .setMessage("This removes only the app's local private snapshot. Public market cache is unaffected.")
                .setPositiveButton("Erase") { _, _ ->
                    SnapshotStore.clear(this)
                    refreshUi()
                    PortfolioWidgetProvider.refreshAll(this)
                }
                .setNegativeButton("Cancel", null).show()
        }

        layout.addView(TextView(this).apply {
            text = "\nSamsung: long-press the home screen → Widgets → Investment OS → Add. " +
                "v2 is designed for a 4×3 widget and can be resized.\n\n" +
                "Market data refreshes automatically when Android updates the widget (requested interval: 30 minutes). " +
                "Samsung battery optimization and GitHub Pages refresh timing can delay updates. " +
                "Market quotes are informational snapshots, not broker execution prices."
            textSize = 13f
            setTextColor(Color.rgb(153, 176, 205))
        })

        setContentView(ScrollView(this).apply { addView(layout) })
        refreshUi()
        PortfolioWidgetProvider.refreshAll(this, refreshMarket = true) { runOnUiThread { refreshUi() } }
    }

    @Deprecated("Used for the system document picker without AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 101 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val raw = contentResolver.openInputStream(uri)?.use {
                val bytes = it.readBytes()
                require(bytes.size <= 2_000_000) { "File exceeds 2 MB" }
                String(bytes, Charsets.UTF_8)
            } ?: error("Could not open selected file")
            val s = SnapshotStore.save(this, raw)
            refreshUi()
            PortfolioWidgetProvider.refreshAll(this)
            toast("Imported: " + s.id)
        } catch (e: Exception) {
            toast("Import failed: " + (e.message ?: "Invalid JSON"))
        }
    }

    private fun refreshUi() {
        val s = SnapshotStore.read(this)
        val show = SnapshotStore.valuesVisible(this)
        status.text = if (s == null) "No portfolio snapshot imported."
        else "Snapshot: " + s.id +
            "\nDate: " + s.asOf +
            "\nObserved: " + if (show) SnapshotStore.won(s.observedTotal) else "Hidden" +
            "\nActive / retirement / ISA / employee: " +
            if (show) listOf(s.activeTotal, s.retirementTotal, s.isaTotal, s.employeeTotal)
                .joinToString(" / ") { SnapshotStore.shortWon(it) } else "Hidden"

        val m = MarketDataStore.read(this)
        marketStatus.text = if (m == null) "Market: waiting for first public-data download."
        else "Market: " + m.generatedAt +
            "\n" + MarketDataStore.marketLabel("US", m.sp500) +
            " · " + MarketDataStore.marketLabel("KR", m.kospi) +
            "\n" + MarketDataStore.macroLabel(m)

        visibilityButton.text = if (show) "Hide values on widget" else "Show values on widget"
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
