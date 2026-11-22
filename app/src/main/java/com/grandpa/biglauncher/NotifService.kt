package com.grandpa.biglauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class NotifItem(val pkg: String, val title: String, val text: String, val time: Long)

/** Shared state the launcher reads to draw badges, the banner and the notification widget. */
object NotifState {
    @Volatile var counts: Map<String, Int> = emptyMap()
    @Volatile var items: List<NotifItem> = emptyList()
    @Volatile var listener: (() -> Unit)? = null
}

class NotifService : NotificationListenerService() {

    override fun onListenerConnected() {
        try { activeNotifications?.forEach { filter(it) } } catch (_: Exception) {}
        publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { filter(sbn); publish() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { publish() }

    private fun title(sbn: StatusBarNotification): String =
        sbn.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

    /** Dismiss anything from an app that is not on the allowed list, and log it. */
    private fun filter(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in Policy.NOTIF_CORE) return
        val prefs = Prefs(this)
        prefs.markSeen(pkg)
        if (!sbn.isClearable || sbn.isOngoing) return
        if (pkg !in prefs.notifAllowed()) {
            try { cancelNotification(sbn.key); prefs.addBlocked(pkg, title(sbn)) } catch (_: Exception) {}
        }
    }

    private fun publish() {
        val allowed = Prefs(this).notifAllowed()
        val m = HashMap<String, Int>()
        val list = ArrayList<NotifItem>()
        try {
            for (s in activeNotifications ?: emptyArray()) {
                if (s.isOngoing) continue
                if (s.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
                if (s.packageName !in allowed) continue
                m[s.packageName] = (m[s.packageName] ?: 0) + 1
                val text = s.notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                list.add(NotifItem(s.packageName, title(s), text, s.postTime))
            }
        } catch (_: Exception) {}
        NotifState.counts = m
        NotifState.items = list.sortedByDescending { it.time }
        NotifState.listener?.invoke()
    }
}
