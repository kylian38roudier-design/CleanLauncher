package com.example.cleanlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CITY_NAME = "Lille"
private const val LATITUDE = 50.63
private const val LONGITUDE = 3.06
private const val INCLUDE_PHONE_APPS = false
private val FAVORITES = listOf(
    "netflix", "amazonvideo", "disney", "youtube", "spotify", "canal", "plex", "kodi"
)

private data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

class MainActivity : Activity() {

    private lateinit var row: LinearLayout
    private lateinit var scroll: HorizontalScrollView
    private lateinit var weatherView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var lastSignature = ""

    private val weatherRefresh = object : Runnable {
        override fun run() {
            fetchWeather()
            handler.postDelayed(this, 30 * 60 * 1000L)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0A0F1C.toInt(), 0xFF16243B.toInt(), 0xFF0A0F1C.toInt())
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply { setMargins(dp(64), dp(48), 0, 0) }
        }
        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "HH:mm"
            textSize = 72f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }
        val date = TextClock(this).apply {
            format24Hour = "EEEE d MMMM"; format12Hour = "EEEE d MMMM"
            textSize = 20f; setTextColor(0xCCFFFFFF.toInt())
        }
        weatherView = TextView(this).apply {
            textSize = 18f; setTextColor(0x99FFFFFF.toInt()); text = CITY_NAME
        }
        header.addView(clock); header.addView(date); header.addView(weatherView)

        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            setPadding(dp(56), dp(24), dp(56), dp(24))
        }
        scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { bottomMargin = dp(56) }
            addView(row)
        }

        root.addView(header)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        refreshApps()
        handler.removeCallbacks(weatherRefresh)
        handler.post(weatherRefresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(weatherRefresh)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scroll.smoothScrollTo(0, 0)
        row.getChildAt(0)?.requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val categories = mutableListOf(Intent.CATEGORY_LEANBACK_LAUNCHER)
        if (INCLUDE_PHONE_APPS) categories.add(Intent.CATEGORY_LAUNCHER)

        val seen = LinkedHashMap<String, AppEntry>()
        for (cat in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || seen.containsKey(pkg)) continue
                seen[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm))
            }
        }
        fun rank(e: AppEntry): Int {
            val i = FAVORITES.indexOfFirst { e.pkg.contains(it, true) || e.label.contains(it, true) }
            return if (i == -1) Int.MAX_VALUE else i
        }
        return seen.values.sortedWith(compareBy<AppEntry> { rank(it) }.thenBy { it.label.lowercase() })
    }

    private fun refreshApps() {
        val apps = loadApps()
        val signature = apps.joinToString("|") { it.pkg }
        if (signature == lastSignature) return
        lastSignature = signature

        row.removeAllViews()
        for (app in apps) {
            row.addView(tile(app.label, app.icon) { launch(app.pkg) })
        }
        row.addView(tile("Paramètres", getDrawable(android.R.drawable.ic_menu_manage)) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        row.getChildAt(0)?.requestFocus()
    }

    private fun launch(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
    }

    private fun tile(label: String, icon: Drawable?, onClick: () -> Unit): View {
        val focusBg = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0x33FFFFFF)
            setStroke(dp(2), 0xCCFFFFFF.toInt())
        }
        val img = ImageView(this).apply {
            setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96))
        }
        val text = TextView(this).apply {
            this.text = label
            textSize = 14f
            setTextColor(0xCCFFFFFF.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            setPadding(dp(14), dp(14), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(136), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(6), 0, dp(6), 0) }
            addView(img)
            addView(text)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, focused ->
                v.animate().scaleX(if (focused) 1.12f else 1f)
                    .scaleY(if (focused) 1.12f else 1f).setDuration(120).start()
                v.background = if (focused) focusBg else null
                text.setTextColor(if (focused) Color.WHITE else 0xCCFFFFFF.toInt())
            }
        }
    }

    private fun fetchWeather() {
        Thread {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$LATITUDE&longitude=$LONGITUDE" +
                            "&current=temperature_2m,weather_code"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val cur = JSONObject(body).getJSONObject("current")
                val temp = Math.round(cur.getDouble("temperature_2m"))
                val desc = describe(cur.getInt("weather_code"))
                runOnUiThread { weatherView.text = "$CITY_NAME · $temp° · $desc" }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Ciel dégagé"
        1, 2 -> "Peu nuageux"
        3 -> "Couvert"
        45, 48 -> "Brouillard"
        in 51..57 -> "Bruine"
        in 61..67 -> "Pluie"
        in 71..77 -> "Neige"
        in 80..82 -> "Averses"
        in 95..99 -> "Orage"
        else -> ""
    }
}
