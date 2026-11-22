package com.grandpa.biglauncher

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotifCenterActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var box: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        Ui.applyTheme(prefs)
        val p = Ui.dp(this, 16)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, Ui.dp(this@NotifCenterActivity, 32), p, p) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(box) })
    }

    override fun onResume() { super.onResume(); build() }

    private fun appLabel(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun btn(label: String, onClick: () -> Unit) {
        val b = Button(this).apply { text = label; textSize = 16f; isAllCaps = false; setOnClickListener { onClick() } }
        box.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Ui.dp(this@NotifCenterActivity, 8) })
    }

    private fun build() {
        Ui.applyTheme(prefs)
        box.removeAllViews()
        box.addView(Ui.text(this, "Notification center", 30f, Color.WHITE, true))

        val nm = getSystemService(NotificationManager::class.java)
        val ok = nm.isNotificationListenerAccessGranted(ComponentName(this, NotifService::class.java))
        box.addView(Ui.text(this,
            if (ok) "Listener is ON. Switch ON = app may notify. Switch OFF = its notifications are deleted instantly."
            else "Notification access is OFF. Nothing can be blocked yet (see README step 5).",
            16f, if (ok) 0xFFCCCCCC.toInt() else 0xFFFF8A80.toInt()))
        if (!ok) btn("Open notification access settings") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
        }

        // ---- per-app switches
        box.addView(Ui.text(this, "Apps", 20f, 0xFFFFD600.toInt(), true).apply { setPadding(0, Ui.dp(this@NotifCenterActivity, 16), 0, 0) })
        val blockedCount = prefs.blockLog().groupingBy { it.second }.eachCount()
        val pkgs = (prefs.seen() + prefs.notifAllowed()).distinct()
            .filter { it !in Policy.NOTIF_CORE && it != packageName }
            .sortedBy { appLabel(it).lowercase() }
        if (pkgs.isEmpty()) box.addView(Ui.text(this, "No app has sent a notification yet.", 16f, 0xFFCCCCCC.toInt()))
        for (pkg in pkgs) {
            val sw = Switch(this).apply {
                isChecked = pkg in prefs.notifAllowed()
                setOnCheckedChangeListener { _, on ->
                    val cur = prefs.notifAllowed()
                    prefs.setNotifAllowed(if (on) (cur + pkg).distinct() else cur - pkg)
                }
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val label = appLabel(pkg) + (blockedCount[pkg]?.let { "  (blocked $it)" } ?: "")
            row.addView(Ui.text(this, label, 20f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(sw)
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(this@NotifCenterActivity, 10) })
        }

        // ---- recent blocked log
        box.addView(Ui.text(this, "Recently blocked", 20f, 0xFFFFD600.toInt(), true).apply { setPadding(0, Ui.dp(this@NotifCenterActivity, 18), 0, 0) })
        val fmt = SimpleDateFormat("d MMM h:mm a", Locale.getDefault())
        val log = prefs.blockLog().reversed().take(30)
        if (log.isEmpty()) box.addView(Ui.text(this, "Nothing blocked yet.", 16f, 0xFFCCCCCC.toInt()))
        for ((t, pkg, title) in log) {
            box.addView(Ui.text(this, "${fmt.format(Date(t))}  ${appLabel(pkg)}: $title", 15f, 0xFFCCCCCC.toInt()))
        }

        btn("Reset: allow only calls, SMS, WhatsApp") { prefs.resetNotif(); build() }
        btn("Clear log") { prefs.clearBlockLog(); build() }
        btn("Back") { finish() }
    }
}
