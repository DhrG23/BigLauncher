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
import android.graphics.drawable.Drawable
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

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var pager: ViewPager2
    private lateinit var homePage: LinearLayout
    private lateinit var favPage: LinearLayout
    private lateinit var favScroll: ScrollView
    private lateinit var banner: TextView
    private lateinit var battery: TextView
    private var taps = 0
    private var lastTap = 0L

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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        setContentView(buildUi())
        if (!prefs.locked &&
            checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
    }

    // ------------------------------------------------------------------ UI
    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(d(14), d(26), d(14), d(10))
        }

        // top strip: clock | date + battery
        val clock = TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "h:mm a"
            textSize = 50f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
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
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(clock, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(right, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        // notification banner
        banner = Ui.text(this, "", 24f, Color.BLACK, true).apply {
            setBackgroundColor(0xFFFFD600.toInt()); setPadding(d(14), d(12), d(14), d(12))
            visibility = View.GONE
        }
        root.addView(banner, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = d(8) })

        // pages
        homePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        favPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        favScroll = ScrollView(this).apply { addView(favPage, ViewGroup.LayoutParams(MATCH, WRAP)) }
        pager = ViewPager2(this).apply {
            adapter = PageAdapter()
            offscreenPageLimit = 1
            setCurrentItem(1, false)   // 0 = Family (swipe right), 1 = Home
        }
        root.addView(pager, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = d(8) })

        root.addView(
            Ui.text(this, "Swipe right for Family numbers  >>", 18f, 0xFF888888.toInt()).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        return root
    }

    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 2
        override fun getItemViewType(position: Int) = position
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
            val v: View = if (type == 0) favScroll else homePage
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

    private fun refresh() {
        val pm = packageManager

        // ---- home tiles
        homePage.removeAllViews()
        val items = prefs.visible().mapNotNull { pkg ->
            try {
                if (pm.getLaunchIntentForPackage(pkg) == null) null
                else {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    Triple(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai))
                }
            } catch (e: Exception) { null }
        }
        if (items.isEmpty()) {
            homePage.addView(Ui.text(this, "No apps set up yet.\nTap the clock 5 times to open admin.", 26f))
        }
        val cols = if (items.size <= 2) 1 else 2
        val size = if (cols == 1) 40f else 28f
        for (row in items.chunked(cols)) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((pkg, label, icon) in row) {
                r.addView(
                    tile(label, null, icon, colorFor(pkg), NotifState.counts[pkg] ?: 0, size) { launch(pkg) },
                    LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(d(6), d(6), d(6), d(6)) }
                )
            }
            homePage.addView(r, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }

        // ---- favourites page
        favPage.removeAllViews()
        favPage.addView(Ui.text(this, "Family & Friends", 34f, Color.WHITE, true).apply { setPadding(d(8), d(8), d(8), d(12)) })
        val favs = prefs.favorites()
        if (favs.isEmpty()) {
            favPage.addView(Ui.text(this, "No numbers yet.\nAsk family to add favorites.", 24f, 0xFFCCCCCC.toInt()))
        }
        for ((name, num) in favs) {
            favPage.addView(
                tile(name, num, null, 0xFF2E7D32.toInt(), 0, 34f) { call(num) },
                LinearLayout.LayoutParams(MATCH, d(130)).apply { setMargins(d(6), d(6), d(6), d(6)) }
            )
        }

        updateBanner()
    }

    private fun updateBanner() {
        val pm = packageManager
        val active = NotifState.counts.filter { it.value > 0 }
        val parts = active.map { (pkg, n) ->
            val l = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
            "$l: $n"
        }
        banner.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        banner.text = "NEW  " + parts.joinToString("    ")
        banner.setOnClickListener { active.keys.firstOrNull()?.let { launch(it) } }
    }

    // ------------------------------------------------------------------ actions
    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (e: Exception) {
            Toast.makeText(this, "Cannot open this app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun call(number: String) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val granted = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        try { startActivity(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)) }
        catch (e: Exception) { Toast.makeText(this, "Cannot place call", Toast.LENGTH_SHORT).show() }
    }

    private fun secretTap() {
        val now = System.currentTimeMillis()
        taps = if (now - lastTap > 3000) 1 else taps + 1
        lastTap = now
        if (taps >= 5) { taps = 0; askPin() }
    }

    private fun askPin() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this).setTitle("Admin PIN").setView(et)
            .setPositiveButton("OK") { _, _ ->
                if (et.text.toString() == prefs.pin) startActivity(Intent(this, AdminActivity::class.java))
                else Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
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
        registerReceiver(batteryRx, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { batteryRx.onReceive(this, it) }
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

    // Home button re-delivers the intent: jump back to the main page.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        pager.setCurrentItem(1, false)
    }

    @Deprecated("Back does nothing on the home screen")
    override fun onBackPressed() { /* stay on home */ }
}
