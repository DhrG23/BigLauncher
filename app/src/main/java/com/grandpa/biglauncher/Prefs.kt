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
}
