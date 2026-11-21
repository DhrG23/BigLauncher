package com.grandpa.biglauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Shared state the launcher reads to draw badges and the "New:" banner. */
object NotifState {
    @Volatile var counts: Map<String, Int> = emptyMap()
    @Volatile var listener: (() -> Unit)? = null
}

class NotifService : NotificationListenerService() {

    override fun onListenerConnected() {
        try { activeNotifications?.forEach { filter(it) } } catch (_: Exception) {}
        publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { filter(sbn); publish() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { publish() }

    /** Dismiss anything from an app that is not on the allowed list. */
    private fun filter(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in Policy.NOTIF_CORE) return
        if (!sbn.isClearable || sbn.isOngoing) return
        if (pkg !in Prefs(this).notifAllowed()) {
            try { cancelNotification(sbn.key) } catch (_: Exception) {}
        }
    }

    private fun publish() {
        val allowed = Prefs(this).notifAllowed()
        val m = HashMap<String, Int>()
        try {
            for (s in activeNotifications ?: emptyArray()) {
                if (s.isOngoing) continue
                if (s.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
                if (s.packageName in allowed) m[s.packageName] = (m[s.packageName] ?: 0) + 1
            }
        } catch (_: Exception) {}
        NotifState.counts = m
        NotifState.listener?.invoke()
    }
}
