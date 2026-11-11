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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CITY_NAME = "Seyssinet-Pariset"
private const val LATITUDE = 45.18
private const val LONGITUDE = 5.69
private const val INCLUDE_PHONE_APPS = false
private val FAVORITES = listOf(
    "netflix", "amazonvideo", "disney", "youtube", "spotify", "canal", "plex", "kodi"
)

// Fond animé : identifiant de la vidéo YouTube (la partie après /live/ dans le lien)
private const val VIDEO_ID = "uT_0LxjQIZk"
private const val PLAY_SOUND = false
private const val RELOAD_AFTER_MS = 20 * 60 * 1000L

private data class AppEntry(
    val pkg: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable
)

class MainActivity : Activity() {

    private lateinit var row: LinearLayout
    private lateinit var scroll: HorizontalScrollView
    private lateinit var heroTitle: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherDesc: TextView
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var lastSignature = ""
    private var lastVideoLoad = 0L

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

        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF070B18.toInt(), 0xFF121A3A.toInt(), 0xFF1C1238.toInt())
        )

        // ----- Fond animé (vidéo YouTube dans une WebView) -----
        webView = WebView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            isLongClickable = false
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()
        }
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Voile sombre pour garder le texte lisible
        val shade = View(this)
        shade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x99000000.toInt(), 0x33000000, 0xDD000000.toInt())
        )
        root.addView(
            shade,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // ----- Heure et date (haut gauche) -----
        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 84f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
        }
        val date = TextClock(this).apply {
            format24Hour = "EEEE d MMMM"
            format12Hour = "EEEE d MMMM"
            textSize = 18f
            setAllCaps(true)
            letterSpacing = 0.12f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(dp(4), dp(6), 0, 0)
        }
        val timeBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock)
            addView(date)
        }
        root.addView(
            timeBlock,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply { setMargins(dp(64), dp(44), 0, 0) }
        )

        // ----- Météo (haut droite) -----
        weatherTemp = TextView(this).apply {
            textSize = 40f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.END
        }
        weatherDesc = TextView(this).apply {
            textSize = 16f
            text = CITY_NAME
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.END
        }
        val weatherBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(weatherTemp)
            addView(weatherDesc)
        }
        root.addView(
            weatherBlock,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, dp(52), dp(64), 0) }
        )

        // ----- Bas : titre de l'appli + rangée de cartes -----
        heroTitle = TextView(this).apply {
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(64), 0, dp(64), dp(6))
        }
        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            setPadding(dp(54), dp(20), dp(54), dp(20))
        }
        scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(row)
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(heroTitle)
            addView(scroll)
        }
        root.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { bottomMargin = dp(40) }
        )

        setContentView(root)
        loadVideo()
    }

    private fun loadVideo() {
        lastVideoLoad = System.currentTimeMillis()
        val mute = if (PLAY_SOUND) 0 else 1
        val src = "https://www.youtube.com/embed/" + VIDEO_ID +
                "?autoplay=1&mute=" + mute +
                "&controls=0&disablekb=1&fs=0&rel=0&modestbranding=1" +
                "&iv_load_policy=3&playsinline=1&enablejsapi=0"
        val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}" +
                "iframe{position:absolute;top:50%;left:50%;width:177.78vh;height:56.25vw;" +
                "min-width:100%;min-height:100%;transform:translate(-50%,-50%);border:0;" +
                "pointer-events:none}</style></head><body>" +
                "<iframe src='" + src + "' allow='autoplay; encrypted-media' allowfullscreen></iframe>" +
                "</body></html>"
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        webView.onResume()
        webView.resumeTimers()
        if (System.currentTimeMillis() - lastVideoLoad > RELOAD_AFTER_MS) loadVideo()
        refreshApps()
        handler.removeCallbacks(weatherRefresh)
        handler.post(weatherRefresh)
    }

    override fun onPause() {
        super.onPause()
        // On coupe la vidéo quand une autre appli est ouverte
        webView.onPause()
        webView.pauseTimers()
        handler.removeCallbacks(weatherRefresh)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
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

    // ---------- Applis ----------

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
                val banner: Drawable? = ri.activityInfo.loadBanner(pm)
                    ?: ri.activityInfo.applicationInfo.loadBanner(pm)
                seen[pkg] = AppEntry(pkg, ri.loadLabel(pm).toString(), banner, ri.loadIcon(pm))
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
            row.addView(card(app.label, app.banner, app.icon) { launch(app.pkg) })
        }
        val gear = getDrawable(android.R.drawable.ic_menu_manage)?.mutate()
        gear?.setTint(Color.WHITE)
        row.addView(card("Paramètres", null, gear) {
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

    private fun card(title: String, banner: Drawable?, icon: Drawable?, onClick: () -> Unit): View {
        val radius = dp(16).toFloat()

        val img = ImageView(this)
        if (banner != null) {
            img.setImageDrawable(banner)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            img.setImageDrawable(icon)
            img.scaleType = ImageView.ScaleType.FIT_CENTER
            img.setPadding(dp(36), dp(22), dp(36), dp(22))
        }

        val ring = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(dp(4), Color.WHITE)
        }

        val c = FrameLayout(this)
        c.layoutParams = LinearLayout.LayoutParams(dp(224), dp(126)).apply {
            setMargins(dp(10), 0, dp(10), 0)
        }
        c.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(0xEE1B2140.toInt())
        }
        c.clipToOutline = true
        c.isFocusable = true
        c.isClickable = true
        c.addView(
            img,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        c.setOnClickListener { onClick() }
        c.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.1f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(140).start()
            v.foreground = if (focused) ring else null
            if (focused) heroTitle.text = title
        }
        return c
    }

    // ---------- Météo (Open-Meteo) ----------

    private fun fetchWeather() {
        Thread {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$LATITUDE&longitude=$LONGITUDE" +
                            "&current=temperature_2m,weather_code"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val cur = JSONObject(body).getJSONObject("current")
                val temp = Math.round(cur.getDouble("temperature_2m"))
                val info = weatherInfo(cur.getInt("weather_code"))
                runOnUiThread {
                    weatherTemp.text = info.first + " " + temp + "°"
                    weatherDesc.text = CITY_NAME + " · " + info.second
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun weatherInfo(code: Int): Pair<String, String> = when (code) {
        0 -> Pair("\u2600\uFE0F", "Ciel dégagé")
        1, 2 -> Pair("\u26C5", "Peu nuageux")
        3 -> Pair("\u2601\uFE0F", "Couvert")
        45, 48 -> Pair("\uD83C\uDF2B\uFE0F", "Brouillard")
        in 51..57 -> Pair("\uD83C\uDF26\uFE0F", "Bruine")
        in 61..67 -> Pair("\uD83C\uDF27\uFE0F", "Pluie")
        in 71..77 -> Pair("\u2744\uFE0F", "Neige")
        in 80..82 -> Pair("\uD83C\uDF27\uFE0F", "Averses")
        in 95..99 -> Pair("\u26C8\uFE0F", "Orage")
        else -> Pair("", "")
    }
}
