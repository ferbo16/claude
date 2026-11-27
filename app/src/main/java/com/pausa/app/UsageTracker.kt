package com.pausa.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/**
 * Calcula el tiempo de uso de hoy por app a partir de los eventos del sistema,
 * y cuál app está en primer plano ahora mismo.
 */
object UsageTracker {
    // Valores de UsageEvents.Event (ACTIVITY_RESUMED / PAUSED / STOPPED).
    private const val RESUMED = 1
    private const val PAUSED = 2
    private const val STOPPED = 23

    data class Snapshot(val usageMs: Map<String, Long>, val foreground: String?)

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun snapshot(context: Context): Snapshot {
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return Snapshot(emptyMap(), null)
        val start = startOfToday()
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(start, now) ?: return Snapshot(emptyMap(), null)

        // Por paquete: qué actividades siguen abiertas y desde cuándo.
        val openActivities = HashMap<String, MutableSet<String>>()
        val openSince = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        var lastResumed: String? = null

        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val cls = e.className ?: ""
            when (e.eventType) {
                RESUMED -> {
                    val set = openActivities.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) openSince[pkg] = e.timeStamp
                    set.add(cls)
                    lastResumed = pkg
                }
                PAUSED, STOPPED -> {
                    val set = openActivities[pkg] ?: continue
                    if (set.remove(cls) && set.isEmpty()) {
                        val since = openSince.remove(pkg) ?: continue
                        totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - since)
                    }
                }
            }
        }
        // Apps que siguen abiertas: contar hasta ahora.
        for ((pkg, since) in openSince) {
            totals[pkg] = (totals[pkg] ?: 0L) + (now - since)
        }
        val fg = lastResumed?.takeIf { openActivities[it]?.isNotEmpty() == true }
        return Snapshot(totals, fg)
    }
}

fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    if (totalMin < 1) return "<1 min"
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h ${m} min"
    }
}

fun formatMinutes(minutes: Int): String = formatDuration(minutes * 60_000L)
