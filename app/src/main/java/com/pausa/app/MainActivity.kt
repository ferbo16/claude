package com.pausa.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var store: LimitStore
    private lateinit var adapter: AppAdapter
    private lateinit var switchMonitor: MaterialSwitch
    private lateinit var tvTotal: TextView
    private lateinit var tvWins: TextView
    private lateinit var cardPermissions: MaterialCardView
    private lateinit var btnUsage: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnNotif: MaterialButton
    private val executor = Executors.newSingleThreadExecutor()

    private val limitOptions = intArrayOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = LimitStore(this)

        val root = findViewById<View>(R.id.root)
        val basePad = root.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(basePad + bars.left, basePad + bars.top, basePad + bars.right, basePad + bars.bottom)
            insets
        }

        cardPermissions = findViewById(R.id.cardPermissions)
        btnUsage = findViewById(R.id.btnUsage)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnNotif = findViewById(R.id.btnNotif)
        switchMonitor = findViewById(R.id.switchMonitor)
        tvTotal = findViewById(R.id.tvTotal)
        tvWins = findViewById(R.id.tvWins)

        btnUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btnOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        btnNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener {
            if (!Permissions.canOverlay(this)) {
                Toast.makeText(this, R.string.need_overlay_first, Toast.LENGTH_LONG).show()
            } else {
                MonitorService.start(this, MonitorService.ACTION_TEST)
            }
        }

        adapter = AppAdapter { row -> showLimitDialog(row) }
        findViewById<RecyclerView>(R.id.recycler).adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        bindSwitch()
        if (store.monitoringEnabled && Permissions.hasUsageAccess(this)) {
            MonitorService.start(this)
        }
        loadApps()
    }

    private fun bindSwitch() {
        switchMonitor.setOnCheckedChangeListener(null)
        switchMonitor.isChecked = store.monitoringEnabled
        switchMonitor.setOnCheckedChangeListener { sw, checked -> setMonitoring(checked, sw as MaterialSwitch) }
    }

    private fun setMonitoring(enabled: Boolean, sw: MaterialSwitch? = null) {
        if (enabled && !Permissions.hasUsageAccess(this)) {
            Toast.makeText(this, R.string.need_usage_first, Toast.LENGTH_LONG).show()
            sw?.isChecked = false
            return
        }
        store.monitoringEnabled = enabled
        if (enabled) MonitorService.start(this) else MonitorService.stop(this)
        if (sw == null) bindSwitch()
    }

    private fun refreshPermissions() {
        val usage = Permissions.hasUsageAccess(this)
        val overlay = Permissions.canOverlay(this)
        val notif = Permissions.hasNotifications(this)
        mark(btnUsage, usage)
        mark(btnOverlay, overlay)
        mark(btnNotif, notif)
        findViewById<View>(R.id.rowNotif).visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        cardPermissions.visibility = if (usage && overlay && notif) View.GONE else View.VISIBLE
    }

    private fun mark(btn: MaterialButton, granted: Boolean) {
        btn.text = getString(if (granted) R.string.perm_ok else R.string.perm_grant)
        btn.isEnabled = !granted
    }

    private fun loadApps() {
        if (adapter.itemCount == 0) tvTotal.text = getString(R.string.loading)
        val hasUsage = Permissions.hasUsageAccess(this)
        executor.execute {
            val usage = if (hasUsage) UsageTracker.snapshot(this).usageMs else emptyMap()
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val activities = pm.queryIntentActivities(launcher, 0)
            val rows = activities
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { ai ->
                    AppRow(
                        pkg = ai.packageName,
                        label = ai.loadLabel(pm).toString(),
                        icon = try { ai.loadIcon(pm) } catch (_: Exception) { null },
                        usedMs = usage[ai.packageName] ?: 0L,
                        limitMinutes = store.limitMinutes(ai.packageName),
                        extraMs = store.extraMs(ai.packageName),
                    )
                }
                .sortedWith(
                    compareByDescending<AppRow> { it.limitMinutes > 0 }
                        .thenByDescending { it.usedMs }
                        .thenBy { it.label.lowercase() }
                )
            val total = rows.sumOf { it.usedMs }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                adapter.submit(rows)
                tvTotal.text = getString(R.string.today_total, formatDuration(total))
                tvWins.text = getString(R.string.wins, store.wins)
            }
        }
    }

    private fun showLimitDialog(row: AppRow) {
        val labels = limitOptions.map { if (it == 0) getString(R.string.no_limit) else formatMinutes(it) }
            .toTypedArray()
        val checked = limitOptions.indexOf(row.limitMinutes)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.limit_dialog_title, row.label))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val minutes = limitOptions[which]
                store.setLimitMinutes(row.pkg, minutes)
                dialog.dismiss()
                if (minutes > 0 && !store.monitoringEnabled && Permissions.hasUsageAccess(this)) {
                    setMonitoring(true)
                }
                loadApps()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
