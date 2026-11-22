package com.grandpa.biglauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
    private val REQ_FAV = 7
    private val REQ_SOS = 8

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        Ui.applyTheme(prefs)
        val p = Ui.dp(this, 16)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, Ui.dp(this@AdminActivity, 32), p, p) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(box) })

        box.addView(Ui.text(this, "Admin", 30f, Color.WHITE, true))
        status = Ui.text(this, "", 16f, 0xFFCCCCCC.toInt())
        box.addView(status)

        section("Look")
        add("Edit look, widgets & app order") { startActivity(Intent(this, UiEditActivity::class.java)) }
        add("Choose home-screen apps") { Pickers.apps(this, "Home screen apps", prefs.visible()) { prefs.setVisible(it); refreshStatus() } }

        section("Notifications")
        add("Notification center (allow / block apps)") { startActivity(Intent(this, NotifCenterActivity::class.java)) }
        add("Grant notification access (Settings)") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (e: Exception) { toast("Blocked. Use Maintenance mode first, or the adb command in README.") }
        }

        section("People")
        add("Add favorite from contacts") { pickContact(REQ_FAV) }
        add("Remove a favorite") { removeFavorite() }
        add("Set SOS contact") { pickContact(REQ_SOS) }

        section("Kiosk")
        add("Extra helper apps (camera, gallery...)") { Pickers.apps(this, "Allowed but hidden helpers", prefs.helpers()) { prefs.setHelpers(it) } }
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

        section("Security")
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
            "Home apps: ${prefs.visible().size}   Favorites: ${prefs.favorites().size}\n" +
            "SOS: ${prefs.sosName.ifBlank { prefs.sosNumber.ifBlank { "not set" } }}"
    }

    private fun section(t: String) {
        box.addView(Ui.text(this, t, 20f, 0xFFFFD600.toInt(), true).apply { setPadding(0, Ui.dp(this@AdminActivity, 18), 0, 0) })
    }

    private fun add(label: String, onClick: () -> Unit) {
        val btn = Button(this).apply { text = label; textSize = 16f; isAllCaps = false; setOnClickListener { onClick() } }
        box.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Ui.dp(this@AdminActivity, 8) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun pickContact(req: Int) {
        startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), req)
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

    private fun readContact(uri: Uri): Pair<String, String>? =
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst())
                (c.getString(0) ?: "?").replace("|", " ").replace("\n", " ") to
                    (c.getString(1) ?: "").replace("|", "").replace("\n", "")
            else null
        }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        val (name, num) = readContact(uri) ?: return
        when (req) {
            REQ_FAV -> { prefs.saveFavorites(prefs.favorites() + (name to num)); toast("Added $name") }
            REQ_SOS -> { prefs.sosName = name; prefs.sosNumber = num; toast("SOS contact: $name") }
        }
        refreshStatus()
    }
}
