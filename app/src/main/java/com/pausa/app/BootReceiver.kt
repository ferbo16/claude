package com.pausa.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reinicia la vigilancia al encender el teléfono o actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (LimitStore(context).monitoringEnabled && Permissions.hasUsageAccess(context)) {
            MonitorService.start(context)
        }
    }
}
