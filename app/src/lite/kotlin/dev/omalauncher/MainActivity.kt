package dev.omalauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.text.TextWatcher
import android.text.Editable

private const val HOST_ID = 7319
private const val BIND_REQUEST = 7319

data class LaunchApp(val title: String, val packageName: String, val intent: Intent)

class MainActivity : Activity() {
    private var widgetHost: AppWidgetHost? = null
    private var widgetManager: AppWidgetManager? = null
    private val widgetIds = mutableListOf<Int>()
    private var pendingWidgetId = 0
    private var widgetsLoaded = false
    private var page = "Home"
    private var searchQuery = ""
    private lateinit var body: LinearLayout

    private val bg = Color.rgb(16, 17, 22)
    private val panel = Color.rgb(27, 29, 37)
    private val ink = Color.rgb(242, 243, 247)
    private val muted = Color.rgb(156, 163, 180)
    private val accent = Color.rgb(185, 166, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First frame does not touch AppWidgetManager, PackageManager or persisted widget state.
        try {
            render("Home")
        } catch (error: Throwable) {
            showFatalFallback(error)
        }
    }

    private fun getHost(): AppWidgetHost {
        return widgetHost ?: AppWidgetHost(this, HOST_ID).also { widgetHost = it }
    }

    private fun getManager(): AppWidgetManager {
        return widgetManager ?: AppWidgetManager.getInstance(this).also { widgetManager = it }
    }

    private fun restoreWidgetIds() {
        if (widgetsLoaded) return
        widgetsLoaded = true
        try {
            val prefs = getSharedPreferences("lite-home", MODE_PRIVATE)
            val count = prefs.getInt("widget-count", 0).coerceIn(0, 50)
            repeat(count) { index ->
                val id = prefs.getInt("widget-$index", 0)
                if (id != 0) {
                    val valid = try { getManager().getAppWidgetInfo(id) != null } catch (_: Throwable) { false }
                    if (valid) widgetIds.add(id)
                }
            }
        } catch (_: Throwable) {
            widgetIds.clear()
        }
    }

    private fun saveWidgetIds() {
        try {
            getSharedPreferences("lite-home", MODE_PRIVATE).edit().apply {
                putInt("widget-count", widgetIds.size)
                widgetIds.forEachIndexed { index, id -> putInt("widget-$index", id) }
                apply()
            }
        } catch (_: Throwable) { }
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
            nav.addView(button(name, name == destination) { safeRender(name) },
                LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        root.addView(nav)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(16))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        try {
            when (destination) {
                "Home" -> renderHome()
                "Apps" -> renderApps()
                "Widgets" -> renderWidgets()
                "Settings" -> renderSettings()
                else -> renderHome()
            }
        } catch (error: Throwable) {
            showSectionFallback(error)
        }
    }

    private fun safeRender(destination: String) {
        try { render(destination) } catch (error: Throwable) { showFatalFallback(error) }
    }

    private fun renderHome() {
        heading("Your space", "A lightweight Android home screen.")
        body.addView(button("Restore saved widgets", false) {
            try {
                restoreWidgetIds()
                safeRender("Home")
            } catch (error: Throwable) { showSectionFallback(error) }
        }, margins())
        if (widgetIds.isEmpty()) {
            card("No widgets loaded", "You can use the app drawer now. Add Android widgets from the Widgets tab.")
        } else {
            widgetIds.toList().forEach { id ->
                try {
                    val info = getManager().getAppWidgetInfo(id)
                    if (info == null) return@forEach
                    val host = getHost()
                    try { host.startListening() } catch (_: Throwable) { }
                    val container = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        setBackgroundColor(panel)
                    }
                    try {
                        val view = host.createView(this, id, info)
                        view.setAppWidget(id, info)
                        container.addView(view, LinearLayout.LayoutParams(-1, -2))
                    } catch (error: Throwable) {
                        textInto(container, "Widget unavailable", 13, muted)
                    }
                    container.addView(button("Remove widget", false) {
                        widgetIds.remove(id)
                        saveWidgetIds()
                        try { host.deleteAppWidgetId(id) } catch (_: Throwable) { }
                        safeRender("Home")
                    })
                    body.addView(container, margins())
                } catch (_: Throwable) { }
            }
        }
        body.addView(button("Browse Android widgets", true) { safeRender("Widgets") }, margins())
        body.addView(button("Open app drawer", false) { safeRender("Apps") }, margins())
    }

    private fun renderApps() {
        heading("App drawer", "Search and open installed apps.")
        val search = EditText(this).apply {
            hint = "Search apps…"
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(muted)
            setBackgroundColor(panel)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setText(searchQuery)
        }
        body.addView(search, margins())
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)

        fun populate(query: String) {
            list.removeAllViews()
            val apps = try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                packageManager.queryIntentActivities(intent, 0).mapNotNull { result ->
                    try {
                        val activity = result.activityInfo ?: return@mapNotNull null
                        LaunchApp(
                            result.loadLabel(packageManager).toString(),
                            activity.packageName,
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                .setClassName(activity.packageName, activity.name)
                        )
                    } catch (_: Throwable) { null }
                }.distinctBy { it.packageName + "/" + it.intent.component?.className }
                    .sortedBy { it.title.lowercase() }
            } catch (_: Throwable) { emptyList() }

            apps.filter { it.title.contains(query, true) || it.packageName.contains(query, true) }
                .forEach { app ->
                    list.addView(button(app.title, false) {
                        try { startActivity(app.intent) } catch (_: Throwable) {
                            Toast.makeText(this, "Couldn't open this app", Toast.LENGTH_SHORT).show()
                        }
                    }, margins())
                }
            if (list.childCount == 0) textInto(list, "No matching apps found.", 13, muted)
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                populate(searchQuery)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        populate(searchQuery)
    }

    private fun renderWidgets() {
        heading("Android widgets", "Widgets supplied by apps installed on this device.")
        val providers = try { getManager().installedProviders.orEmpty() } catch (_: Throwable) { emptyList<AppWidgetProviderInfo>() }
        if (providers.isEmpty()) {
            card("No widget providers found", "Install an app that offers Android home-screen widgets.")
            return
        }
        providers.sortedBy {
            try { it.loadLabel(packageManager).toString().lowercase() } catch (_: Throwable) { it.provider?.packageName.orEmpty() }
        }.forEach { info ->
            val label = try { info.loadLabel(packageManager).toString() } catch (_: Throwable) { info.provider?.packageName ?: "Widget" }
            body.addView(button("$label  +", false) { addWidget(info) }, margins())
        }
    }

    private fun addWidget(info: AppWidgetProviderInfo) {
        val host = try { getHost() } catch (_: Throwable) {
            Toast.makeText(this, "Widget service unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        val id = try { host.allocateAppWidgetId() } catch (_: Throwable) {
            Toast.makeText(this, "Couldn't allocate widget", Toast.LENGTH_SHORT).show()
            return
        }
        val bound = try { getManager().bindAppWidgetIdIfAllowed(id, info.provider) } catch (_: Throwable) { false }
        if (bound) {
            if (!widgetIds.contains(id)) widgetIds.add(id)
            widgetsLoaded = true
            saveWidgetIds()
            safeRender("Home")
        } else {
            pendingWidgetId = id
            try {
                startActivityForResult(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
                    BIND_REQUEST
                )
            } catch (_: Throwable) {
                pendingWidgetId = 0
                try { host.deleteAppWidgetId(id) } catch (_: Throwable) { }
                Toast.makeText(this, "Widget permission screen unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != BIND_REQUEST) return
        val id = pendingWidgetId
        pendingWidgetId = 0
        if (resultCode == RESULT_OK && id != 0) {
            if (!widgetIds.contains(id)) widgetIds.add(id)
            widgetsLoaded = true
            saveWidgetIds()
            safeRender("Home")
        } else if (id != 0) {
            try { getHost().deleteAppWidgetId(id) } catch (_: Throwable) { }
        }
    }

    private fun renderSettings() {
        heading("Settings", "Basic launcher information.")
        card("Omalauncher Lite", "A lightweight Android launcher with app search and Android widget support.")
        body.addView(button("Clear saved widgets", false) {
            widgetIds.toList().forEach { id -> try { getHost().deleteAppWidgetId(id) } catch (_: Throwable) { } }
            widgetIds.clear()
            widgetsLoaded = true
            saveWidgetIds()
            safeRender("Home")
        }, margins())
    }

    private fun showSectionFallback(error: Throwable) {
        if (!::body.isInitialized) { showFatalFallback(error); return }
        try {
            body.removeAllViews()
            text("This section couldn't be opened.", 18, ink, true)
            text(error.javaClass.simpleName, 12, muted)
            body.addView(button("Return to Home", true) { safeRender("Home") })
        } catch (_: Throwable) { showFatalFallback(error) }
    }

    private fun showFatalFallback(error: Throwable) {
        try {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setBackgroundColor(bg)
            }
            root.addView(TextView(this).apply {
                text = "Omalauncher Lite couldn't start"
                textSize = 20f
                setTextColor(ink)
            })
            root.addView(TextView(this).apply {
                text = error.javaClass.simpleName
                textSize = 13f
                setTextColor(muted)
            })
            root.addView(button("Try opening Home again", true) { safeRender("Home") })
            setContentView(root)
        } catch (_: Throwable) {
            try { finish() } catch (_: Throwable) { }
        }
    }

    private fun heading(title: String, subtitle: String) {
        text(title, 24, ink, true)
        text(subtitle, 13, muted)
    }

    private fun card(title: String, detail: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(panel)
        }
        textInto(box, title, 16, ink, true)
        textInto(box, detail, 13, muted)
        body.addView(box, margins())
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) =
        textInto(body, value, size, color, bold)

    private fun textInto(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
    }

    private fun button(label: String, primary: Boolean, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(if (primary) Color.BLACK else ink)
            setBackgroundColor(if (primary) accent else panel)
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }

    private fun margins() = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(5)
        bottomMargin = dp(5)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
