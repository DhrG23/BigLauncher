package com.grandpa.biglauncher

import android.app.admin.DeviceAdminReceiver

/** Needed so the app can become "device owner" (kiosk lock). */
class AdminReceiver : DeviceAdminReceiver()
