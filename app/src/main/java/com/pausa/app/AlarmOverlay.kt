package com.pausa.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Alarma a pantalla completa que se dibuja encima de la app que estás usando.
 * Fases: alarma → respiración → reto matemático → (minutos extra).
 */
class AlarmOverlay(
    private val service: Context,
    private val info: Info,
    private val onLeave: () -> Unit,
    private val onExtend: (minutes: Int) -> Unit,
) {
    data class Info(
        val pkg: String,
        val label: String,
        val usedMs: Long,
        val limitMinutes: Int,
        val extensionCount: Int,
        val isTest: Boolean = false,
    )

    private val ctx = ContextThemeWrapper(service, R.style.Theme_Pausa)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val sound = AlarmSound(service)
    private val root: View = LayoutInflater.from(ctx).inflate(R.layout.overlay_alarm, null)

    private val tvEmoji: TextView = root.findViewById(R.id.tvEmoji)
    private val tvTitle: TextView = root.findViewById(R.id.tvTitle)
    private val tvMessage: TextView = root.findViewById(R.id.tvMessage)
    private val groupRing: View = root.findViewById(R.id.groupRing)
    private val groupBreathe: View = root.findViewById(R.id.groupBreathe)
    private val groupChallenge: View = root.findViewById(R.id.groupChallenge)
    private val tvCountdown: TextView = root.findViewById(R.id.tvCountdown)
    private val progressBreathe: LinearProgressIndicator = root.findViewById(R.id.progressBreathe)
    private val tvProblem: TextView = root.findViewById(R.id.tvProblem)
    private val tvChallengeProgress: TextView = root.findViewById(R.id.tvChallengeProgress)
    private val options: List<MaterialButton> =
        listOf(R.id.opt0, R.id.opt1, R.id.opt2, R.id.opt3).map { root.findViewById(it) }

    private val extendMinutes = 5
    private val challengesNeeded = (1 + info.extensionCount).coerceAtMost(4)
    private val difficulty = (1 + info.extensionCount).coerceAtMost(4)
    private var solved = 0
    private var timer: CountDownTimer? = null
    private var pulse: ValueAnimator? = null

    var isShowing = false
        private set

    fun show() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )
        wm.addView(root, params)
        isShowing = true

        // El botón "atrás" no cierra la alarma.
        root.isFocusableInTouchMode = true
        root.requestFocus()
        root.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        root.findViewById<MaterialButton>(R.id.btnLeave).setOnClickListener { leave() }
        root.findViewById<MaterialButton>(R.id.btnBreatheLeave).setOnClickListener { leave() }
        root.findViewById<MaterialButton>(R.id.btnChallengeLeave).setOnClickListener { leave() }
        root.findViewById<MaterialButton>(R.id.btnMore).apply {
            text = ctx.getString(R.string.alarm_more, extendMinutes)
            setOnClickListener { sound.stop(); startBreathing(15) }
        }

        showRing()
        sound.start()
        handler.postDelayed({ sound.stop() }, 60_000)
    }

    private fun showRing() {
        tvEmoji.text = "⏰"
        tvTitle.text = ctx.getString(R.string.alarm_title)
        tvMessage.text = if (info.isTest) {
            ctx.getString(R.string.alarm_test_message)
        } else {
            ctx.getString(
                R.string.alarm_message,
                formatDuration(info.usedMs), info.label, formatMinutes(info.limitMinutes)
            )
        }
        showGroup(groupRing)
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            tvEmoji,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.25f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.25f),
        ).apply {
            duration = 450
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun startBreathing(seconds: Int) {
        stopPulse()
        tvEmoji.text = "🫁"
        tvTitle.text = ctx.getString(R.string.breathe_title)
        tvMessage.text = ctx.getString(R.string.breathe_question)
        showGroup(groupBreathe)
        val totalMs = seconds * 1000L
        timer?.cancel()
        timer = object : CountDownTimer(totalMs, 100) {
            override fun onTick(left: Long) {
                val elapsed = totalMs - left
                tvCountdown.text = ((left + 999) / 1000).toString()
                progressBreathe.progress = (elapsed * 1000 / totalMs).toInt()
                // Ciclo de 8 s: 4 inhalar, 4 exhalar.
                tvMessage.text = ctx.getString(
                    if ((elapsed / 4000) % 2 == 0L) R.string.breathe_in else R.string.breathe_out
                )
            }

            override fun onFinish() = showChallenge()
        }.start()
    }

    private fun showChallenge() {
        tvEmoji.text = "🧠"
        tvTitle.text = ctx.getString(R.string.challenge_title, extendMinutes)
        tvMessage.text = ""
        showGroup(groupChallenge)
        nextProblem()
    }

    private fun nextProblem() {
        val c = Challenge.generate(difficulty)
        tvProblem.text = c.text
        tvChallengeProgress.text =
            ctx.getString(R.string.challenge_progress, solved + 1, challengesNeeded)
        options.forEachIndexed { i, btn ->
            val value = c.options[i]
            btn.text = value.toString()
            btn.setOnClickListener {
                if (value == c.answer) {
                    solved++
                    if (solved >= challengesNeeded) extend() else nextProblem()
                } else {
                    Toast.makeText(service, R.string.challenge_wrong, Toast.LENGTH_SHORT).show()
                    startBreathing(10)
                }
            }
        }
    }

    private fun extend() {
        val msg = if (info.isTest) {
            service.getString(R.string.toast_test_done)
        } else {
            service.getString(R.string.toast_extend, extendMinutes)
        }
        Toast.makeText(service, msg, Toast.LENGTH_LONG).show()
        dismiss()
        if (!info.isTest) onExtend(extendMinutes)
    }

    private fun leave() {
        sound.stop()
        Toast.makeText(service, R.string.toast_leave, Toast.LENGTH_SHORT).show()
        try {
            service.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
        // Dejar la alarma un instante para que el inicio aparezca detrás.
        handler.postDelayed({
            dismiss()
            if (!info.isTest) onLeave()
        }, 300)
    }

    private fun showGroup(group: View) {
        for (g in listOf(groupRing, groupBreathe, groupChallenge)) {
            g.visibility = if (g === group) View.VISIBLE else View.GONE
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        tvEmoji.scaleX = 1f
        tvEmoji.scaleY = 1f
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        timer?.cancel()
        stopPulse()
        sound.stop()
        if (isShowing) {
            try { wm.removeView(root) } catch (_: Exception) {}
            isShowing = false
        }
    }
}
