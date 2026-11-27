package com.pausa.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Servicio en primer plano que revisa cada pocos segundos qué app estás usando,
 * cuánto tiempo llevas hoy y lanza la alarma cuando pasas tu límite.
 */
class MonitorService : Service() {

    companion object {
        const val ACTION_TEST = "com.pausa.app.action.TEST_ALARM"
        const val ACTION_STOP = "com.pausa.app.action.STOP"

        private const val CH_MONITOR = "monitor"
        private const val CH_WARN = "warn"
        private const val NOTIF_ID = 1
        private const val WARN_ID = 2
        private const val TICK_MS = 4_000L
        private const val WARN_BEFORE_MS = 2 * 60_000L
        private const val COOLDOWN_MS = 6_000L

        fun start(context: Context, action: String? = null) {
            val i = Intent(context, MonitorService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: LimitStore
    private var overlay: AlarmOverlay? = null
    private var lastNotifText: String? = null
    private var cooldownUntil = 0L

    private val tick = object : Runnable {
        override fun run() {
            try { check() } catch (_: Exception) {}
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = LimitStore(this)
        createChannels()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(getString(R.string.notif_watching)), type
        )
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST -> showAlarm(
                AlarmOverlay.Info(
                    pkg = packageName, label = getString(R.string.app_name),
                    usedMs = 0, limitMinutes = 0, extensionCount = 0, isTest = true,
                )
            )
            ACTION_STOP -> {
                store.monitoringEnabled = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.dismiss()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun check() {
        if (overlay?.isShowing == true) return
        if (System.currentTimeMillis() < cooldownUntil) return
        if (!Permissions.hasUsageAccess(this)) return
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) return

        val limits = store.limits()
        if (limits.isEmpty()) {
            updateNotification(getString(R.string.notif_no_limits))
            return
        }

        val snap = UsageTracker.snapshot(this)
        val fg = snap.foreground
        val limitMin = fg?.let { limits[it] }
        if (fg == null || fg == packageName || limitMin == null) {
            updateNotification(getString(R.string.notif_watching))
            return
        }

        val label = appLabel(fg)
        val allowed = limitMin * 60_000L + store.extraMs(fg)
        val used = snap.usageMs[fg] ?: 0L
        updateNotification(
            getString(R.string.notif_app_progress, label, formatDuration(used), formatDuration(allowed))
        )

        val remaining = allowed - used
        if (remaining <= 0) {
            showAlarm(AlarmOverlay.Info(fg, label, used, limitMin, store.extensionCount(fg)))
        } else if (remaining <= WARN_BEFORE_MS && !store.warned(fg)) {
            store.setWarned(fg)
            warn(label, remaining)
        }
    }

    private fun showAlarm(info: AlarmOverlay.Info) {
        if (overlay?.isShowing == true) return
        if (!Permissions.canOverlay(this)) {
            notifyOverLimit(info.label)
            if (!info.isTest) cooldownUntil = System.currentTimeMillis() + 60_000
            return
        }
        overlay = AlarmOverlay(
            service = this,
            info = info,
            onLeave = {
                store.wins = store.wins + 1
                cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
            },
            onExtend = { minutes ->
                store.addExtension(info.pkg, minutes * 60_000L)
            },
        ).also {
            try {
                it.show()
            } catch (e: Exception) {
                notifyOverLimit(info.label)
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    // ---- Notificaciones ----

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WARN, getString(R.string.channel_warn), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_pausa)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
    }

    private fun updateNotification(text: String) {
        if (text == lastNotifText) return
        lastNotifText = text
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(text))
    }

    private fun warn(label: String, remainingMs: Long) {
        if (!Permissions.hasNotifications(this)) return
        val n = NotificationCompat.Builder(this, CH_WARN)
            .setSmallIcon(R.drawable.ic_stat_pausa)
            .setContentTitle(getString(R.string.warn_title))
            .setContentText(getString(R.string.warn_text, formatDuration(remainingMs.coerceAtLeast(60_000)), label))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java)?.notify(WARN_ID, n)
    }

    private fun notifyOverLimit(label: String) {
        if (!Permissions.hasNotifications(this)) return
        val n = NotificationCompat.Builder(this, CH_WARN)
            .setSmallIcon(R.drawable.ic_stat_pausa)
            .setContentTitle(getString(R.string.over_title, label))
            .setContentText(getString(R.string.over_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java)?.notify(WARN_ID, n)
    }
}
