package com.grandpa.biglauncher

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private val SCALES = floatArrayOf(0.85f, 1f, 1.2f, 1.45f)

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var rootView: LinearLayout
    private lateinit var pager: ViewPager2
    private lateinit var homePage: LinearLayout
    private lateinit var homeScroll: ScrollView
    private lateinit var favPage: LinearLayout
    private lateinit var favScroll: ScrollView
    private lateinit var widgetPage: LinearLayout
    private lateinit var widgetScroll: ScrollView
    private lateinit var banner: TextView
    private lateinit var battery: TextView
    private lateinit var indicators: List<TextView>
    private var taps = 0
    private var lastTap = 0L
    private var torchOn = false

    private val batteryRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            battery.text = "Battery ${level * 100 / scale}%" + if (charging) " (charging)" else ""
        }
    }

    private fun d(v: Int) = Ui.dp(this, v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun appLabel(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        Ui.applyTheme(prefs)
        setContentView(buildUi())
        if (!prefs.locked &&
            checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
    }

    // ------------------------------------------------------------------ UI skeleton
    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(d(14), d(26), d(14), d(10))
        }
        rootView = root

        val clock = TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "h:mm a"
            textSize = 50f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { secretTap() }
        }
        val date = TextClock(this).apply {
            format12Hour = "EEE, d MMM"; format24Hour = "EEE, d MMM"
            textSize = 22f; setTextColor(0xFFCCCCCC.toInt()); gravity = Gravity.END
        }
        battery = Ui.text(this, "", 20f, 0xFFCCCCCC.toInt()).apply { gravity = Gravity.END }
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(date, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(battery, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(clock, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(right, LinearLayout.LayoutParams(WRAP, WRAP))
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        banner = Ui.text(this, "", 24f, Color.BLACK, true).apply {
            setBackgroundColor(0xFFFFD600.toInt()); setPadding(d(14), d(12), d(14), d(12))
            visibility = View.GONE
        }
        root.addView(banner, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(8) })

        // page indicator (also tappable) : Family <- HOME -> Widgets
        val names = listOf("<  Family", "HOME", "Widgets  >")
        indicators = names.mapIndexed { i, n ->
            Ui.text(this, n, 18f, 0xFF888888.toInt()).apply {
                gravity = Gravity.CENTER; setPadding(0, d(10), 0, d(10))
                setOnClickListener { pager.setCurrentItem(i, true) }
            }
        }

        homePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeScroll = ScrollView(this).apply { isFillViewport = true; addView(homePage, ViewGroup.LayoutParams(MATCH, MATCH)) }
        favPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        favScroll = ScrollView(this).apply { addView(favPage, ViewGroup.LayoutParams(MATCH, WRAP)) }
        widgetPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        widgetScroll = ScrollView(this).apply { addView(widgetPage, ViewGroup.LayoutParams(MATCH, WRAP)) }

        pager = ViewPager2(this).apply {
            adapter = PageAdapter()
            offscreenPageLimit = 2
            setCurrentItem(1, false)   // 0 = Family, 1 = Home, 2 = Widgets
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) { updateIndicator(position) }
            })
        }
        root.addView(pager, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = d(8) })

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                indicators.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP, 1f)) }
            }, LinearLayout.LayoutParams(MATCH, WRAP)
        )
        updateIndicator(1)
        return root
    }

    private fun updateIndicator(p: Int) {
        indicators.forEachIndexed { i, t ->
            t.setTextColor(if (i == p) Color.WHITE else 0xFF888888.toInt())
            t.textSize = if (i == p) 24f else 18f
            t.typeface = if (i == p) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 3
        override fun getItemViewType(position: Int) = position
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
            val v: View = when (type) { 0 -> favScroll; 1 -> homeScroll; else -> widgetScroll }
            v.layoutParams = RecyclerView.LayoutParams(MATCH, MATCH)
            return object : RecyclerView.ViewHolder(v) {}
        }
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {}
    }

    private fun colorFor(pkg: String): Int = when {
        pkg.contains("whatsapp") -> 0xFF2E7D32.toInt()
        pkg.contains("youtube") -> 0xFFC62828.toInt()
        pkg.contains("facebook") -> 0xFF1565C0.toInt()
        pkg.contains("instagram") -> 0xFFAD1457.toInt()
        pkg.contains("brave") -> 0xFFE65100.toInt()
        else -> 0xFF37474F.toInt()
    }

    private fun tile(
        label: String, sub: String?, icon: Drawable?, color: Int, badge: Int, size: Float, onClick: () -> Unit
    ): View {
        val f = FrameLayout(this)
        f.background = Ui.round(this, color)
        f.isClickable = true
        f.setOnClickListener { onClick() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        if (icon != null) {
            col.addView(
                ImageView(this).apply { setImageDrawable(icon) },
                LinearLayout.LayoutParams(d(88), d(88)).apply { bottomMargin = d(8) }
            )
        }
        col.addView(Ui.text(this, label, size, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        if (sub != null) col.addView(Ui.text(this, sub, size * 0.7f, 0xFFE0E0E0.toInt()).apply { gravity = Gravity.CENTER })
        f.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        if (badge > 0) {
            val bd = Ui.text(this, badge.toString(), 22f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER; background = Ui.oval(0xFFFF1744.toInt())
            }
            f.addView(bd, FrameLayout.LayoutParams(d(50), d(50), Gravity.TOP or Gravity.END).apply {
                setMargins(0, d(10), d(10), 0)
            })
        }
        return f
    }

    private fun margins() = LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(d(6), d(6), d(6), d(6)) }

    // ------------------------------------------------------------------ refresh
    private fun refresh() {
        Ui.applyTheme(prefs)
        rootView.setBackgroundColor(Ui.BG)
        refreshHome(); refreshFavs(); refreshWidgets(); updateBanner()
    }

    private fun refreshHome() {
        val pm = packageManager
        homePage.removeAllViews()
        val scale = SCALES[prefs.textLevel.coerceIn(0, 3)]
        val items = prefs.visible().mapNotNull { pkg ->
            try { if (pm.getLaunchIntentForPackage(pkg) == null) null else Pair(pkg, pm.getApplicationIcon(pkg)) }
            catch (e: Exception) { null }
        }
        val total = items.size + if (prefs.showPlus) 1 else 0
        val cols = if (prefs.columns > 0) prefs.columns else if (total <= 2) 1 else 2
        val size = (when (cols) { 1 -> 40f; 2 -> 28f; else -> 22f }) * scale

        val tiles = ArrayList<View>()
        for ((pkg, icon) in items) {
            tiles.add(
                tile(
                    prefs.label(pkg) ?: appLabel(pkg), null, if (prefs.showIcons) icon else null,
                    prefs.color(pkg) ?: colorFor(pkg), NotifState.counts[pkg] ?: 0, size
                ) { launch(pkg) }
            )
        }
        if (prefs.showPlus) {
            tiles.add(tile("+  Add app", null, null, 0xFF263238.toInt(), 0, size) {
                askPin { Pickers.apps(this, "Home screen apps", prefs.visible()) { prefs.setVisible(it); refresh() } }
            })
        }
        if (tiles.isEmpty()) {
            homePage.addView(Ui.text(this, "No apps yet.\nTap the clock 5 times for admin.", 26f))
        }
        val rows = tiles.chunked(cols)
        for (row in rows) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { r.addView(it, margins()) }
            homePage.addView(
                r, if (rows.size > 4) LinearLayout.LayoutParams(MATCH, d(170)) else LinearLayout.LayoutParams(MATCH, 0, 1f)
            )
        }
    }

    private fun refreshFavs() {
        favPage.removeAllViews()
        favPage.addView(Ui.text(this, "Family & Friends", 34f, Color.WHITE, true).apply { setPadding(d(8), d(8), d(8), d(12)) })
        val favs = prefs.favorites()
        if (favs.isEmpty()) favPage.addView(Ui.text(this, "No numbers yet.\nAsk family to add favorites.", 24f, 0xFFCCCCCC.toInt()))
        for ((name, num) in favs) {
            favPage.addView(
                tile(name, num, null, 0xFF2E7D32.toInt(), 0, 34f) { call(num) },
                LinearLayout.LayoutParams(MATCH, d(130)).apply { setMargins(d(6), d(6), d(6), d(6)) }
            )
        }
        favPage.addView(Ui.text(this, "Swipe left to go back Home  >>", 18f, 0xFF888888.toInt()).apply {
            gravity = Gravity.CENTER; setPadding(0, d(16), 0, d(16))
        })
    }

    // ------------------------------------------------------------------ widgets page
    private fun card(color: Int, vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.round(this@MainActivity, color)
        setPadding(d(18), d(14), d(18), d(14))
        views.forEach { addView(it) }
    }

    private fun addW(v: View, h: Int = WRAP) {
        widgetPage.addView(v, LinearLayout.LayoutParams(MATCH, h).apply { setMargins(d(6), d(6), d(6), d(6)) })
    }

    private fun refreshWidgets() {
        widgetPage.removeAllViews()
        val ws = prefs.widgets()
        val grey = 0xFF263238.toInt()
        widgetPage.addView(Ui.text(this, "Widgets", 34f, Color.WHITE, true).apply { setPadding(d(8), d(8), d(8), d(12)) })

        if ("date" in ws) {
            addW(card(grey,
                TextClock(this).apply { format12Hour = "EEEE"; format24Hour = "EEEE"; textSize = 38f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD },
                TextClock(this).apply { format12Hour = "d MMMM yyyy"; format24Hour = "d MMMM yyyy"; textSize = 28f; setTextColor(0xFFCCCCCC.toInt()) }
            ))
        }
        if ("notifs" in ws) {
            val c = card(grey, Ui.text(this, "Messages & calls", 24f, 0xFFFFD600.toInt(), true))
            val list = NotifState.items.take(6)
            if (list.isEmpty()) c.addView(Ui.text(this, "Nothing new", 22f, 0xFFCCCCCC.toInt()))
            for (n in list) {
                val line = appLabel(n.pkg) + ": " + listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(" - ")
                c.addView(Ui.text(this, line, 21f).apply {
                    setPadding(0, d(8), 0, d(8)); setOnClickListener { launch(n.pkg) }
                })
            }
            addW(c)
        }
        if ("sos" in ws) {
            val has = prefs.sosNumber.isNotBlank()
            addW(
                tile(if (has) "SOS - Call ${prefs.sosName.ifBlank { prefs.sosNumber }}" else "SOS (not set)", null, null,
                    0xFFB71C1C.toInt(), 0, 30f) { sos() },
                d(120)
            )
        }
        if ("flash" in ws || "volume" in ws) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if ("flash" in ws) row.addView(
                tile(if (torchOn) "Flashlight ON" else "Flashlight", null, null,
                    if (torchOn) 0xFFF9A825.toInt() else 0xFF455A64.toInt(), 0, 24f) { toggleTorch() }, margins()
            )
            if ("volume" in ws) {
                row.addView(tile("Volume +", volPct(), null, 0xFF00695C.toInt(), 0, 24f) { vol(true) }, margins())
                row.addView(tile("Volume -", volPct(), null, 0xFF00695C.toInt(), 0, 24f) { vol(false) }, margins())
            }
            addW(row, d(120))
        }
        if ("info" in ws) {
            addW(card(grey,
                Ui.text(this, "My info", 24f, 0xFFFFD600.toInt(), true),
                Ui.text(this, prefs.infoText, 24f).apply { setPadding(0, d(6), 0, 0) }
            ))
        }
        widgetPage.addView(Ui.text(this, "<<  Swipe right to go back Home", 18f, 0xFF888888.toInt()).apply {
            gravity = Gravity.CENTER; setPadding(0, d(16), 0, d(16))
        })
    }

    private fun volPct(): String {
        val am = getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return "${am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}%"
    }

    private fun vol(up: Boolean) {
        val am = getSystemService(AudioManager::class.java)
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        refreshWidgets()
    }

    private fun toggleTorch() {
        try {
            val cm = getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: run { toast("No flashlight"); return }
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            refreshWidgets()
        } catch (e: Exception) { torchOn = false; toast("Flashlight not available") }
    }

    private fun sos() {
        if (prefs.sosNumber.isBlank()) { toast("Ask family to set the SOS contact"); return }
        AlertDialog.Builder(this).setTitle("Call for help?")
            .setMessage("Call ${prefs.sosName.ifBlank { prefs.sosNumber }} now?")
            .setPositiveButton("YES, CALL") { _, _ -> call(prefs.sosNumber) }
            .setNegativeButton("No", null).show()
    }

    // ------------------------------------------------------------------ banner / actions
    private fun updateBanner() {
        val active = NotifState.counts.filter { it.value > 0 }
        val parts = active.map { (pkg, n) -> "${appLabel(pkg)}: $n" }
        banner.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        banner.text = "NEW  " + parts.joinToString("    ")
        banner.setOnClickListener { active.keys.firstOrNull()?.let { launch(it) } }
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (e: Exception) { toast("Cannot open this app") }
    }

    private fun call(number: String) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val granted = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        try { startActivity(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)) }
        catch (e: Exception) { toast("Cannot place call") }
    }

    private fun secretTap() {
        val now = System.currentTimeMillis()
        taps = if (now - lastTap > 3000) 1 else taps + 1
        lastTap = now
        if (taps >= 5) { taps = 0; askPin { startActivity(Intent(this, AdminActivity::class.java)) } }
    }

    private fun askPin(onOk: () -> Unit) {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this).setTitle("Admin PIN").setView(et)
            .setPositiveButton("OK") { _, _ ->
                if (et.text.toString() == prefs.pin) onOk() else toast("Wrong PIN")
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ------------------------------------------------------------------ lifecycle / kiosk
    private fun enforceKiosk() {
        val am = getSystemService(ActivityManager::class.java)
        if (!prefs.locked) {
            if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) try { stopLockTask() } catch (_: Exception) {}
            return
        }
        if (!Policy.isOwner(this)) return
        Policy.applyLockTask(this)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try { startLockTask() } catch (_: Exception) {}
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(batteryRx, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { batteryRx.onReceive(this, it) }
    }

    override fun onStop() {
        try { unregisterReceiver(batteryRx) } catch (_: Exception) {}
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        NotifState.listener = { runOnUiThread { refresh() } }
        refresh()
        enforceKiosk()
    }

    override fun onPause() {
        NotifState.listener = null
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        pager.setCurrentItem(1, false)   // Home button -> main page
    }

    @Deprecated("Back does nothing on the home screen")
    override fun onBackPressed() { /* stay on home */ }
}
