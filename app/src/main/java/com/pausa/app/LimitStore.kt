package com.pausa.app

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda los límites por app y el estado del día (minutos extra, avisos). */
class LimitStore(context: Context) {
    private val prefs = context.getSharedPreferences("pausa_limits", Context.MODE_PRIVATE)
    private val daily = context.getSharedPreferences("pausa_daily", Context.MODE_PRIVATE)

    fun limitMinutes(pkg: String): Int = prefs.getInt(LIMIT + pkg, 0)

    fun setLimitMinutes(pkg: String, minutes: Int) = prefs.edit {
        if (minutes <= 0) remove(LIMIT + pkg) else putInt(LIMIT + pkg, minutes)
    }

    /** paquete -> minutos de límite diario. */
    fun limits(): Map<String, Int> = prefs.all
        .filterKeys { it.startsWith(LIMIT) }
        .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(LIMIT) to it } }
        .toMap()

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoring", false)
        set(value) = prefs.edit { putBoolean("monitoring", value) }

    var wins: Int
        get() = prefs.getInt("wins", 0)
        set(value) = prefs.edit { putInt("wins", value) }

    fun extraMs(pkg: String): Long = today().getLong(EXTRA + pkg, 0L)

    fun extensionCount(pkg: String): Int = today().getInt(EXT_COUNT + pkg, 0)

    fun addExtension(pkg: String, ms: Long) {
        val d = today()
        d.edit {
            putLong(EXTRA + pkg, d.getLong(EXTRA + pkg, 0L) + ms)
            putInt(EXT_COUNT + pkg, d.getInt(EXT_COUNT + pkg, 0) + 1)
        }
    }

    fun warned(pkg: String): Boolean = today().getBoolean(WARNED + pkg, false)

    fun setWarned(pkg: String) = today().edit { putBoolean(WARNED + pkg, true) }

    /** Devuelve las preferencias diarias, reiniciándolas si cambió el día. */
    private fun today(): android.content.SharedPreferences {
        val key = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (daily.getString("day", null) != key) {
            daily.edit(commit = true) { clear(); putString("day", key) }
        }
        return daily
    }

    private companion object {
        const val LIMIT = "limit_"
        const val EXTRA = "extra_"
        const val EXT_COUNT = "extcount_"
        const val WARNED = "warned_"
    }
}
