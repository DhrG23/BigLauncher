package com.grandpa.biglauncher

import android.content.Context
import android.content.Intent

class Prefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("big", Context.MODE_PRIVATE)
    private val pm = ctx.packageManager

    var pin: String
        get() = sp.getString("pin", "1234") ?: "1234"
        set(v) { sp.edit().putString("pin", v).apply() }

    /** true once the admin pressed "Enable lockdown". */
    var locked: Boolean
        get() = sp.getBoolean("locked", false)
        set(v) { sp.edit().putBoolean("locked", v).apply() }

    private fun load(key: String): List<String>? =
        sp.getString(key, null)?.let { if (it.isEmpty()) emptyList() else it.split(",") }

    private fun store(key: String, l: List<String>) {
        sp.edit().putString(key, l.joinToString(",")).apply()
    }

    // ---- default app discovery (not saved, so apps installed later still appear) ----
    private fun dialerPkg(): String? =
        pm.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0)
            .map { it.activityInfo.packageName }.firstOrNull { it != "android" }

    private fun smsPkg(): String? =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), 0)
            .map { it.activityInfo.packageName }.firstOrNull { it != "android" }

    private val known = listOf(
        "com.whatsapp", "com.google.android.youtube", "com.facebook.katana",
        "com.instagram.android", "com.brave.browser"
    )

    private fun defaultVisible() =
        (listOfNotNull(dialerPkg(), smsPkg()) + known).distinct()
            .filter { pm.getLaunchIntentForPackage(it) != null }

    private fun defaultNotif() = listOfNotNull(dialerPkg(), smsPkg(), "com.whatsapp").distinct()

    // ---- lists ----
    fun visible(): List<String> = load("visible") ?: defaultVisible()
    fun setVisible(l: List<String>) = store("visible", l)

    fun notifAllowed(): List<String> = load("notif") ?: defaultNotif()
    fun setNotifAllowed(l: List<String>) = store("notif", l)

    fun helpers(): List<String> = load("helpers") ?: emptyList()
    fun setHelpers(l: List<String>) = store("helpers", l)

    // ---- favourites: "Name|number" per line ----
    fun favorites(): List<Pair<String, String>> =
        (sp.getString("favs", "") ?: "").split("\n").filter { it.contains("|") }.map {
            val i = it.indexOf('|'); it.substring(0, i) to it.substring(i + 1)
        }

    fun saveFavorites(l: List<Pair<String, String>>) {
        sp.edit().putString("favs", l.joinToString("\n") { "${it.first}|${it.second}" }).apply()
    }

    // ---- look & feel ----
    var columns: Int          // 0 = auto
        get() = sp.getInt("cols", 0)
        set(v) { sp.edit().putInt("cols", v).apply() }
    var textLevel: Int        // 0..3
        get() = sp.getInt("txt", 1)
        set(v) { sp.edit().putInt("txt", v).apply() }
    var bgTheme: Int          // 0 dark, 1 black, 2 navy
        get() = sp.getInt("bg", 0)
        set(v) { sp.edit().putInt("bg", v).apply() }
    var showPlus: Boolean
        get() = sp.getBoolean("plus", true)
        set(v) { sp.edit().putBoolean("plus", v).apply() }
    var showIcons: Boolean
        get() = sp.getBoolean("icons", true)
        set(v) { sp.edit().putBoolean("icons", v).apply() }

    fun color(pkg: String): Int? = if (sp.contains("c_$pkg")) sp.getInt("c_$pkg", 0) else null
    fun setColor(pkg: String, c: Int?) {
        sp.edit().apply { if (c == null) remove("c_$pkg") else putInt("c_$pkg", c) }.apply()
    }
    fun label(pkg: String): String? = sp.getString("n_$pkg", null)
    fun setLabel(pkg: String, s: String?) {
        sp.edit().apply { if (s.isNullOrBlank()) remove("n_$pkg") else putString("n_$pkg", s) }.apply()
    }

    // ---- widgets ----
    companion object {
        val ALL_WIDGETS = listOf("date", "notifs", "flash", "volume", "sos", "info")
        val WIDGET_NAMES = listOf("Big date", "Messages & calls", "Flashlight", "Volume", "SOS button", "Info card")
    }
    fun widgets(): List<String> = load("widgets") ?: ALL_WIDGETS
    fun setWidgets(l: List<String>) = store("widgets", l)

    var infoText: String
        get() = sp.getString("info", "Name:\nAddress:\nMedicines:\nDoctor:") ?: ""
        set(v) { sp.edit().putString("info", v).apply() }
    var sosName: String
        get() = sp.getString("sosn", "") ?: ""
        set(v) { sp.edit().putString("sosn", v).apply() }
    var sosNumber: String
        get() = sp.getString("sosnum", "") ?: ""
        set(v) { sp.edit().putString("sosnum", v).apply() }

    // ---- notification center data ----
    fun resetNotif() { sp.edit().remove("notif").apply() }
    fun seen(): List<String> = load("seen") ?: emptyList()
    fun markSeen(pkg: String) { val s = seen(); if (pkg !in s) store("seen", s + pkg) }

    /** Lines of "millis|pkg|title" */
    fun blockLog(): List<Triple<Long, String, String>> =
        (sp.getString("blog", "") ?: "").split("\n").filter { it.isNotBlank() }.mapNotNull {
            val p = it.split("|", limit = 3)
            if (p.size == 3) Triple(p[0].toLongOrNull() ?: 0L, p[1], p[2]) else null
        }
    fun addBlocked(pkg: String, title: String) {
        val t = title.replace("|", " ").replace("\n", " ")
        val old = (sp.getString("blog", "") ?: "").split("\n").filter { it.isNotBlank() }
        sp.edit().putString("blog", (old + "${System.currentTimeMillis()}|$pkg|$t").takeLast(100).joinToString("\n")).apply()
    }
    fun clearBlockLog() { sp.edit().remove("blog").apply() }
}
