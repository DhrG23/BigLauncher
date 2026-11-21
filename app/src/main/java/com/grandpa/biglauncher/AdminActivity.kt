package com.grandpa.biglauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class AdminActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var box: LinearLayout
    private val REQ_CONTACT = 7

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@AdminActivity, 16), Ui.dp(this@AdminActivity, 32), Ui.dp(this@AdminActivity, 16), Ui.dp(this@AdminActivity, 16))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(box) }
        setContentView(scroll)

        box.addView(Ui.text(this, "Admin", 30f, Color.WHITE, true))
        status = Ui.text(this, "", 16f, 0xFFCCCCCC.toInt())
        box.addView(status)

        add("Choose home-screen apps") { pickApps("Home screen apps", prefs.visible()) { prefs.setVisible(it) } }
        add("Choose apps allowed to notify") { pickApps("Allowed notifications", prefs.notifAllowed()) { prefs.setNotifAllowed(it) } }
        add("Extra helper apps (camera, gallery...)") { pickApps("Allowed but hidden helpers", prefs.helpers()) { prefs.setHelpers(it) } }
        add("Add favorite from contacts") {
            startActivityForResult(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_CONTACT
            )
        }
        add("Remove a favorite") { removeFavorite() }
        add("Grant notification access (Settings)") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (e: Exception) { toast("Blocked. Use Maintenance mode first, or the adb command in README.") }
        }
        add("ENABLE LOCKDOWN (kiosk)") {
            Policy.lockOn(this)
            toast(if (Policy.isOwner(this)) "Lockdown on" else "Not device owner: soft lock only")
            finish()
        }
        add("MAINTENANCE MODE (unlock + open Settings)") {
            try { stopLockTask() } catch (_: Exception) {}
            Policy.lockOff(this)
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        add("Change PIN") { changePin() }
        add("Remove device owner (undo everything)") {
            AlertDialog.Builder(this).setTitle("Remove device owner?")
                .setMessage("Kiosk lock and all restrictions are removed.")
                .setPositiveButton("Remove") { _, _ ->
                    try { stopLockTask() } catch (_: Exception) {}
                    Policy.removeOwner(this); refreshStatus()
                }.setNegativeButton("Cancel", null).show()
        }
        add("Back to home") { finish() }
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun refreshStatus() {
        val nm = getSystemService(NotificationManager::class.java)
        val listener = nm.isNotificationListenerAccessGranted(ComponentName(this, NotifService::class.java))
        status.text = "Device owner: ${Policy.isOwner(this)}\n" +
            "Lockdown on: ${prefs.locked}\n" +
            "Notification access: $listener\n" +
            "Home apps: ${prefs.visible().size}   Favorites: ${prefs.favorites().size}"
    }

    private fun add(label: String, onClick: () -> Unit) {
        val btn = Button(this).apply { text = label; textSize = 16f; isAllCaps = false; setOnClickListener { onClick() } }
        box.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Ui.dp(this@AdminActivity, 8) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun pickApps(title: String, current: List<String>, save: (List<String>) -> Unit) {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.lowercase() }
        val checked = BooleanArray(apps.size) { apps[it].first in current }
        AlertDialog.Builder(this).setTitle(title)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Save") { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }
                val kept = current.filter { chosen.contains(it) }
                save(kept + chosen.filter { it !in kept })
                refreshStatus()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun removeFavorite() {
        val favs = prefs.favorites()
        if (favs.isEmpty()) { toast("No favorites"); return }
        AlertDialog.Builder(this).setTitle("Remove which?")
            .setItems(favs.map { "${it.first}  ${it.second}" }.toTypedArray()) { _, i ->
                prefs.saveFavorites(favs.filterIndexed { idx, _ -> idx != i }); refreshStatus()
            }.show()
    }

    private fun changePin() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "New PIN (4+ digits)"
        }
        AlertDialog.Builder(this).setTitle("Change PIN").setView(et)
            .setPositiveButton("Save") { _, _ ->
                val p = et.text.toString()
                if (p.length >= 4) { prefs.pin = p; toast("PIN changed") } else toast("Too short")
            }.setNegativeButton("Cancel", null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (req == REQ_CONTACT && res == RESULT_OK) {
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = (c.getString(0) ?: "?").replace("|", " ").replace("\n", " ")
                    val num = (c.getString(1) ?: "").replace("|", "").replace("\n", "")
                    prefs.saveFavorites(prefs.favorites() + (name to num))
                    toast("Added $name")
                    refreshStatus()
                }
            }
        }
    }
}
