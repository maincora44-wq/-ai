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
    private lateinit var visibilityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
            setBackgroundColor(Color.rgb(12, 21, 35))
        }
        val title = TextView(this).apply {
            text = "Investment OS · Android Widget"
            textSize = 23f
            setTextColor(Color.WHITE)
        }
        layout.addView(title)
        val info = TextView(this).apply {
            text = "\nLocal JSON only. No broker connection, internet permission or cloud sync.\n\n" +
                "Import the private snapshot JSON downloaded on your Samsung phone. " +
                "The app checks account totals before saving.\n"
            textSize = 15f
            setTextColor(Color.rgb(188, 207, 230))
        }
        layout.addView(info)
        status = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 24)
        }
        layout.addView(status)
        fun button(label: String, action: () -> Unit) {
            val b = Button(this).apply { text = label; setOnClickListener { action() } }
            layout.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        button("1. Import local JSON") {
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
            refresh()
        }
        button("Refresh home-screen widget") { refresh(); toast("Widget refreshed") }
        button("Erase private snapshot") {
            android.app.AlertDialog.Builder(this)
                .setTitle("Erase local portfolio data?")
                .setMessage("This cannot be undone. Your original downloaded JSON will not be deleted.")
                .setPositiveButton("Erase") { _, _ -> SnapshotStore.clear(this); refresh() }
                .setNegativeButton("Cancel", null).show()
        }
        val help = TextView(this).apply {
            text = "\nSamsung: long-press home screen → Widgets → Investment OS → Add. " +
                "Resize the widget if needed.\n\nValues are hidden by default. " +
                "This widget is a dated snapshot, not live market information. " +
                "Your existing PWA browser storage is separate from this Android app."
            textSize = 13f
            setTextColor(Color.rgb(153, 176, 205))
        }
        layout.addView(help)
        setContentView(ScrollView(this).apply { addView(layout) })
        refresh()
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
            refresh()
            toast("Imported: " + s.id)
        } catch (e: Exception) {
            toast("Import failed: " + (e.message ?: "Invalid JSON"))
        }
    }

    private fun refresh() {
        val s = SnapshotStore.read(this)
        status.text = if (s == null) "No snapshot imported yet."
        else "Snapshot: " + s.id + "\nDate: " + s.asOf +
            "\nFive accounts: " + s.accountCount +
            "\nObserved investments: " + if (SnapshotStore.valuesVisible(this)) SnapshotStore.won(s.observedTotal) else "Hidden"
        visibilityButton.text = if (SnapshotStore.valuesVisible(this)) "Hide values on widget" else "Show values on widget"
        PortfolioWidgetProvider.refreshAll(this)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
