package dev.omalauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import android.text.TextWatcher
import android.text.Editable

private const val HOST_ID = 7319
private const val BIND_REQUEST = 7319

data class LaunchApp(val title: String, val packageName: String, val intent: Intent)

class MainActivity : Activity() {
    private val host by lazy { AppWidgetHost(this, HOST_ID) }
    private val manager by lazy { AppWidgetManager.getInstance(this) }
    private val widgetIds = mutableListOf<Int>()
    private var pendingId = 0
    private var page = "Home"
    private var query = ""
    private lateinit var body: LinearLayout
    private val bg = Color.rgb(16,17,22)
    private val panel = Color.rgb(27,29,37)
    private val ink = Color.rgb(242,243,247)
    private val muted = Color.rgb(156,163,180)
    private val accent = Color.rgb(185,166,255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { loadIds() } catch (_: Exception) { widgetIds.clear() }
        render("Home")
    }

    override fun onStart() { super.onStart(); try { host.startListening() } catch (_: Exception) {} }
    override fun onStop() { try { host.stopListening() } catch (_: Exception) {}; super.onStop() }
    override fun onResume() { super.onResume(); if (::body.isInitialized) try { render(page) } catch (_: Exception) {} }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != BIND_REQUEST) return
        val id = pendingId
        pendingId = 0
        if (resultCode == RESULT_OK && id != 0) {
            if (!widgetIds.contains(id)) widgetIds.add(id)
            saveIds()
            render("Home")
        } else if (id != 0) try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
    }

    private fun loadIds() {
        val p = getSharedPreferences("lite-home", MODE_PRIVATE)
        val count = p.getInt("widget-count", 0).coerceIn(0, 100)
        widgetIds.clear()
        repeat(count) { i ->
            val id = p.getInt("widget-" + i, 0)
            if (id != 0 && try { manager.getAppWidgetInfo(id) != null } catch (_: Exception) { false }) widgetIds.add(id)
            else if (id != 0) try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
        }
    }

    private fun saveIds() {
        getSharedPreferences("lite-home", MODE_PRIVATE).edit().apply {
            putInt("widget-count", widgetIds.size)
            widgetIds.forEachIndexed { i, id -> putInt("widget-" + i, id) }
            apply()
        }
    }

    private fun render(destination: String) {
        page = destination
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(bg)
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Home", "Apps", "Widgets", "Settings").forEach { name ->
            nav.addView(btn(name, name == destination) { render(name) },
                LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        root.addView(nav)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(16)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        try {
            when (destination) {
                "Home" -> home()
                "Apps" -> apps()
                "Widgets" -> widgets()
                else -> settings()
            }
        } catch (e: Exception) {
            body.removeAllViews()
            text("Couldn't load this section.", 18, ink, true)
            text(e.javaClass.simpleName, 12, muted)
            body.addView(btn("Back to Home", true) { render("Home") })
        }
    }

    private fun home() {
        heading("Your space", "A lightweight Android home screen.")
        if (widgetIds.isEmpty()) card("No widgets added", "Browse widgets to add one from an installed app.")
        widgetIds.toList().forEach { id ->
            val info = try { manager.getAppWidgetInfo(id) } catch (_: Exception) { null }
            if (info == null) { widgetIds.remove(id); saveIds() }
            else {
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8)); setBackgroundColor(panel) }
                try {
                    val view = host.createView(this, id, info)
                    view.setAppWidget(id, info)
                    box.addView(view, LinearLayout.LayoutParams(-1, -2))
                } catch (e: Exception) { textInto(box, "Widget unavailable (" + e.javaClass.simpleName + ")", 12, muted) }
                box.addView(btn("Remove widget", false) {
                    widgetIds.remove(id); saveIds()
                    try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
                    render("Home")
                })
                body.addView(box, margins())
            }
        }
        body.addView(btn("Browse widgets", true) { render("Widgets") }, margins())
        body.addView(btn("Open app drawer", false) { render("Apps") }, margins())
    }

    private fun apps() {
        heading("App drawer", "Search and open installed apps.")
        val field = EditText(this).apply {
            hint = "Search apps…"; setSingleLine(true); setTextColor(ink); setHintTextColor(muted)
            setBackgroundColor(panel); setPadding(dp(12), dp(8), dp(12), dp(8)); setText(query)
        }
        body.addView(field, margins())
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        fun fill(q: String) {
            list.removeAllViews()
            val found = try {
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .mapNotNull { r ->
                        try {
                            val a = r.activityInfo ?: return@mapNotNull null
                            LaunchApp(r.loadLabel(packageManager).toString(), a.packageName,
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(a.packageName, a.name))
                        } catch (_: Exception) { null }
                    }.distinctBy { it.packageName + "/" + it.intent.component?.className }.sortedBy { it.title.lowercase() }
            } catch (_: Exception) { emptyList() }
            found.filter { it.title.contains(q, true) || it.packageName.contains(q, true) }.forEach { app ->
                list.addView(btn(app.title, false) {
                    try { startActivity(app.intent) } catch (_: Exception) {
                        Toast.makeText(this, "Couldn't open app", Toast.LENGTH_SHORT).show()
                    }
                }, margins())
            }
            if (list.childCount == 0) textInto(list, "No matching apps found.", 13, muted)
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString() ?: ""; fill(query) }
            override fun afterTextChanged(s: Editable?) {}
        })
        fill(query)
    }

    private fun widgets() {
        heading("Android widgets", "Widgets provided by apps installed on this device.")
        val providers = try { manager.installedProviders.orEmpty() } catch (_: Exception) { emptyList<AppWidgetProviderInfo>() }
        if (providers.isEmpty()) card("No widget providers found", "Install an app that provides Android home-screen widgets.")
        providers.sortedBy { try { it.loadLabel(packageManager).toString().lowercase() } catch (_: Exception) { "" } }.forEach { info ->
            val label = try { info.loadLabel(packageManager).toString() } catch (_: Exception) { info.provider.packageName }
            body.addView(btn(label + "   +", false) { addWidget(info) }, margins())
        }
    }

    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = try { host.allocateAppWidgetId() } catch (_: Exception) {
            Toast.makeText(this, "Couldn't allocate widget", Toast.LENGTH_SHORT).show(); return
        }
        val allowed = try { manager.bindAppWidgetIdIfAllowed(id, info.provider) } catch (_: Exception) { false }
        if (allowed) {
            widgetIds.add(id); saveIds(); render("Home")
        } else {
            pendingId = id
            try {
                startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider), BIND_REQUEST)
            } catch (_: Exception) {
                pendingId = 0
                try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
                Toast.makeText(this, "Widget permission screen unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun settings() {
        heading("Settings", "Basic launcher information.")
        card("Omalauncher Lite", "A lightweight Android launcher with app search, an app drawer and Android widget support.")
        body.addView(btn("Clear saved widgets", false) {
            widgetIds.toList().forEach { try { host.deleteAppWidgetId(it) } catch (_: Exception) {} }
            widgetIds.clear(); saveIds(); render("Home")
        }, margins())
    }

    private fun heading(title: String, subtitle: String) { text(title, 24, ink, true); text(subtitle, 13, muted) }
    private fun card(title: String, detail: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(14)); setBackgroundColor(panel) }
        textInto(box, title, 16, ink, true); textInto(box, detail, 13, muted)
        body.addView(box, margins())
    }
    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = textInto(body, value, size, color, bold)
    private fun textInto(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color); setPadding(dp(4),dp(6),dp(4),dp(6))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
    }
    private fun btn(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false
        setTextColor(if (primary) Color.BLACK else ink); setBackgroundColor(if (primary) accent else panel)
        setOnClickListener { action() }
    }
    private fun margins() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(5) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
