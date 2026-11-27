package com.pausa.app

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

data class AppRow(
    val pkg: String,
    val label: String,
    val icon: Drawable?,
    val usedMs: Long,
    val limitMinutes: Int,
    val extraMs: Long,
)

class AppAdapter(private val onLimitClick: (AppRow) -> Unit) :
    RecyclerView.Adapter<AppAdapter.VH>() {

    private var rows: List<AppRow> = emptyList()

    fun submit(newRows: List<AppRow>) {
        rows = newRows
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val usage: TextView = v.findViewById(R.id.usage)
        val progress: LinearProgressIndicator = v.findViewById(R.id.progress)
        val btn: MaterialButton = v.findViewById(R.id.btnLimit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        val ctx = h.itemView.context
        h.icon.setImageDrawable(r.icon)
        h.name.text = r.label
        if (r.limitMinutes > 0) {
            val allowed = r.limitMinutes * 60_000L + r.extraMs
            h.usage.text = ctx.getString(R.string.usage_of_limit, formatDuration(r.usedMs), formatDuration(allowed))
            h.progress.visibility = View.VISIBLE
            val pct = ((r.usedMs * 100) / allowed.coerceAtLeast(1)).toInt().coerceIn(0, 100)
            h.progress.progress = pct
            h.progress.setIndicatorColor(
                ContextCompat.getColor(
                    ctx, if (r.usedMs >= allowed) R.color.pausa_alarm else R.color.pausa_primary
                )
            )
            h.btn.text = formatMinutes(r.limitMinutes)
        } else {
            h.usage.text = ctx.getString(R.string.usage_today, formatDuration(r.usedMs))
            h.progress.visibility = View.GONE
            h.btn.text = ctx.getString(R.string.no_limit)
        }
        h.btn.setOnClickListener { onLimitClick(r) }
        h.itemView.setOnClickListener { onLimitClick(r) }
    }
}
