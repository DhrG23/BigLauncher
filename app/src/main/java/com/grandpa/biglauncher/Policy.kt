package com.grandpa.biglauncher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.ContactsContract
import android.provider.MediaStore

/** Everything that needs "device owner" lives here. All calls are safe no-ops if not owner. */
object Policy {

    /** Packages that must always be allowed so calls, permission pop-ups and pickers work. */
    private val CORE = setOf(
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.android.server.telecom", "com.android.phone", "com.android.incallui",
        "com.android.dialer", "com.google.android.dialer",
        "com.android.contacts", "com.google.android.contacts",
        "com.google.android.documentsui", "com.android.documentsui"
    )

    /** System packages whose notifications are never touched. */
    val NOTIF_CORE = setOf(
        "android", "com.android.systemui", "com.android.server.telecom",
        "com.android.phone", "com.android.incallui"
    )

    private val RESTRICTIONS = listOf(
        "no_install_apps", "no_install_unknown_sources", "no_uninstall_apps",
        "no_add_user", "no_safe_boot", "no_config_private_dns"
    )

    /** Hidden (truly disabled) while locked. Chrome is simply not allowed in kiosk. */
    private val HIDE = listOf("com.android.vending", "com.heytap.market", "com.oppo.market")

    private fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
    private fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)

    fun isOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)

    /** Packages allowed to run inside the kiosk. Play Store is never in here. */
    fun allowedPackages(c: Context): Array<String> {
        val pm = c.packageManager
        val s = LinkedHashSet<String>()
        s.add(c.packageName)
        val p = Prefs(c)
        s.addAll(p.visible()); s.addAll(p.helpers()); s.addAll(CORE)
        val probes = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
            Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        )
        for (i in probes) pm.queryIntentActivities(i, 0).forEach { s.add(it.activityInfo.packageName) }
        s.remove("com.android.vending")
        s.remove("com.android.chrome")
        return s.toTypedArray()
    }

    /** Called on every launcher resume while locked: keeps the kiosk allow-list fresh. */
    fun applyLockTask(c: Context) {
        if (!isOwner(c)) return
        val d = dpm(c); val a = admin(c)
        d.setLockTaskPackages(a, allowedPackages(c))
        d.setLockTaskFeatures(
            a,
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
        )
    }

    fun lockOn(c: Context) {
        Prefs(c).locked = true
        if (!isOwner(c)) return
        val d = dpm(c); val a = admin(c)
        RESTRICTIONS.forEach { try { d.addUserRestriction(a, it) } catch (_: Exception) {} }
        HIDE.forEach { try { d.setApplicationHidden(a, it, true) } catch (_: Exception) {} }
        try { d.setGlobalPrivateDnsModeSpecifiedHost(a, "dns.adguard-dns.com") } catch (_: Exception) {}
        // make this launcher the permanent home screen
        val f = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT)
        }
        try { d.addPersistentPreferredActivity(a, f, ComponentName(c, MainActivity::class.java)) } catch (_: Exception) {}
        applyLockTask(c)
    }

    fun lockOff(c: Context) {
        Prefs(c).locked = false
        if (!isOwner(c)) return
        val d = dpm(c); val a = admin(c)
        RESTRICTIONS.forEach { try { d.clearUserRestriction(a, it) } catch (_: Exception) {} }
        HIDE.forEach { try { d.setApplicationHidden(a, it, false) } catch (_: Exception) {} }
        try { d.clearPackagePersistentPreferredActivities(a, c.packageName) } catch (_: Exception) {}
        d.setLockTaskPackages(a, arrayOf(c.packageName))
    }

    @Suppress("DEPRECATION")
    fun removeOwner(c: Context) {
        lockOff(c)
        if (isOwner(c)) dpm(c).clearDeviceOwnerApp(c.packageName)
    }
}
