package dev.omalauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private const val HOST_ID = 7319
data class LaunchApp(val title: String, val packageName: String, val intent: Intent)

class MainActivity : ComponentActivity() {
    val widgetHost by lazy { AppWidgetHost(this, HOST_ID) }
    val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    val widgetIds = mutableStateListOf<Int>()
    val apps = mutableStateListOf<LaunchApp>()
    val providers = mutableStateListOf<AppWidgetProviderInfo>()
    private var pendingWidgetId = 0

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        pendingWidgetId = 0
        if (result.resultCode == Activity.RESULT_OK && id != 0) {
            if (id !in widgetIds) widgetIds.add(id)
            saveWidgetIds()
        } else if (id != 0) widgetHost.deleteAppWidgetId(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep launcher startup independent from optional package/widget services.
        // Some Android skins throw while enumerating third-party widget providers.
        runCatching { loadApps() }
        runCatching { loadWidgets() }
        runCatching { loadProviders() }
        setContent { LiteHome(this) }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
    }

    override fun onResume() {
        super.onResume()
        // Refresh providers/apps when returning from Settings or installing a widget app.
        runCatching { loadApps() }
        runCatching { loadProviders() }
        runCatching { loadWidgets() }
    }

    override fun onStop() {
        runCatching { widgetHost.stopListening() }
        super.onStop()
    }

    private fun loadApps() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = runCatching {
            packageManager.queryIntentActivities(query, 0).mapNotNull { info ->
                runCatching {
                    val activity = info.activityInfo ?: return@runCatching null
                    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(activity.packageName, activity.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    LaunchApp(info.loadLabel(packageManager).toString(), activity.packageName, launch)
                }.getOrNull()
            }.distinctBy { it.packageName to it.intent.component?.className }.sortedWith(compareBy({ it.title.lowercase() }, { it.packageName }))
        }.getOrDefault(emptyList())
        apps.clear()
        apps.addAll(found)
    }

    private fun loadWidgets() {
        val prefs = getSharedPreferences("lite-home", MODE_PRIVATE)
        val count = prefs.getInt("widget-count", 0)
        widgetIds.clear()
        repeat(count) { index ->
            val id = prefs.getInt("widget-$index", 0)
            if (id != 0) {
                if (runCatching { widgetManager.getAppWidgetInfo(id) != null }.getOrDefault(false)) widgetIds.add(id)
                else runCatching { widgetHost.deleteAppWidgetId(id) }
            }
        }
    }

    private fun saveWidgetIds() {
        getSharedPreferences("lite-home", MODE_PRIVATE).edit().apply {
            putInt("widget-count", widgetIds.size)
            widgetIds.forEachIndexed { index, id -> putInt("widget-$index", id) }
            apply()
        }
    }

    private fun loadProviders() {
        providers.clear()
        val available = runCatching { widgetManager.installedProviders.orEmpty() }.getOrDefault(emptyList())
        providers.addAll(
            available.mapNotNull { info ->
                // A provider can disappear during enumeration (app uninstall/update).
                runCatching { info to info.loadLabel(packageManager).toString() }.getOrNull()
            }.sortedBy { it.second.lowercase() }.map { it.first }
        )
    }

    fun addWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            widgetIds.add(id)
            saveWidgetIds()
        } else {
            pendingWidgetId = id
            bindWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider))
        }
    }

    fun removeWidget(id: Int) {
        widgetIds.remove(id)
        saveWidgetIds()
        widgetHost.deleteAppWidgetId(id)
    }

    fun openApp(app: LaunchApp) { runCatching { startActivity(app.intent) } }
}

@Composable
private fun LiteHome(activity: MainActivity) {
    var page by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(activity.getSharedPreferences("lite-home", 0).getBoolean("dark", true)) }
    val bg = if (dark) Color(0xFF101116) else Color(0xFFF3F4F7)
    val panel = if (dark) Color(0xFF1B1D25) else Color.White
    val ink = if (dark) Color(0xFFF2F3F7) else Color(0xFF171923)
    val muted = if (dark) Color(0xFF9CA3B4) else Color(0xFF626879)
    val accent = if (dark) Color(0xFFB9A6FF) else Color(0xFF6245C5)

    Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("OMALAUNCHER LITE", ink, 17, true)
                Txt("A focused Android home", muted, 11)
            }
            listOf("Home", "Apps", "Widgets", "Settings").forEach { destination ->
                SmallAction(destination, page == destination, accent, ink) { page = destination }
                Spacer(Modifier.width(4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        when (page) {
            "Home" -> {
                Txt("Your space", ink, 22, true)
                Txt("Long-press a widget card's Remove action to manage it.", muted, 12)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activity.widgetIds.isEmpty()) Panel(panel) {
                        Txt("No widgets added yet", ink, 16, true)
                        Txt("Open Widgets to browse widgets supplied by your installed apps.", muted, 13)
                    }
                    activity.widgetIds.toList().forEach { id ->
                        val info = runCatching { activity.widgetManager.getAppWidgetInfo(id) }.getOrNull()
                        if (info != null) {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel).padding(8.dp)) {
                                AndroidView(
                                    factory = { context ->
                                        runCatching {
                                            activity.widgetHost.createView(context, id, info).apply { setAppWidget(id, info) }
                                        }.getOrElse { error ->
                                            TextView(context).apply {
                                                text = "Widget unavailable\n${error.javaClass.simpleName}"
                                                setPadding(24, 24, 24, 24)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 300.dp)
                                )
                                Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(bg)
                                    .clickable { activity.removeWidget(id) }.padding(horizontal = 9.dp, vertical = 5.dp)) {
                                    Txt("Remove", ink, 10, true)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigAction("Browse widgets", accent, Color.White, Modifier.weight(1f)) { page = "Widgets" }
                    BigAction("Open app drawer", panel, ink, Modifier.weight(1f)) { page = "Apps" }
                }
            }
            "Apps" -> {
                Txt("App drawer", ink, 22, true)
                Spacer(Modifier.height(10.dp))
                SearchField(query, { query = it }, panel, ink, muted)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    activity.apps.filter { it.title.contains(query, true) || it.packageName.contains(query, true) }.forEach { app ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { activity.openApp(app) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt(app.title, ink, 15, true)
                            Spacer(Modifier.weight(1f))
                            Txt("Open  ›", accent, 12)
                        }
                    }
                }
            }
            "Widgets" -> {
                Txt("Widget marketplace", ink, 22, true)
                Txt("Browse widgets available from apps installed on this device.", muted, 12)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (activity.providers.isEmpty()) Panel(panel) {
                        Txt("No widget providers found", ink, 15, true)
                        Txt("Install an app that includes Android home-screen widgets, then return here.", muted, 12)
                    }
                    activity.providers.forEach { provider ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(panel)
                            .clickable { activity.addWidget(provider) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Txt(provider.loadLabel(activity.packageManager).toString(), ink, 14, true)
                                Txt(provider.provider.packageName, muted, 10)
                            }
                            Txt("ADD  +", accent, 12, true)
                        }
                    }
                }
            }
            else -> {
                Txt("Settings", ink, 22, true)
                Spacer(Modifier.height(12.dp))
                Panel(panel) {
                    Txt("Appearance", ink, 15, true)
                    Txt(if (dark) "Dark theme is enabled" else "Light theme is enabled", muted, 12)
                    Spacer(Modifier.height(8.dp))
                    BigAction(if (dark) "Switch to light" else "Switch to dark", accent, Color.White, Modifier.fillMaxWidth()) {
                        dark = !dark
                        activity.getSharedPreferences("lite-home", 0).edit().putBoolean("dark", dark).apply()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Panel(panel) {
                    Txt("About", ink, 15, true)
                    Txt("Omalauncher Lite keeps the Android launcher essentials: app search, an app drawer and Android widgets.", muted, 12)
                }
            }
        }
    }
}

@Composable
private fun Txt(value: String, color: Color, size: Int, bold: Boolean = false) {
    androidx.compose.foundation.text.BasicText(value, style = TextStyle(
        color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    ))
}

@Composable
private fun Panel(color: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color).padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
    }
}

@Composable
private fun SmallAction(label: String, selected: Boolean, accent: Color, ink: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) accent else Color.Transparent)
        .clickable { onClick() }.padding(horizontal = 7.dp, vertical = 8.dp)) {
        Txt(label, if (selected) Color.White else ink, 9, selected)
    }
}

@Composable
private fun BigAction(label: String, background: Color, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(background).clickable { onClick() }.padding(13.dp), contentAlignment = Alignment.Center) {
        Txt(label, ink, 12, true)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, panel: Color, ink: Color, muted: Color) {
    BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = ink, fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(panel).padding(14.dp),
        decorationBox = { inner -> if (value.isEmpty()) Txt("Search apps…", muted, 14); inner() })
}
