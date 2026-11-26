package com.sumit.ruhi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Lock screen ke upar Siri jaisa popup. Normal overlay lock screen par nahi dikhta,
 * isliye phone locked ho to yeh transparent activity wahi SiriView dikhati hai.
 */
class LockPopupActivity : Activity() {
    companion object {
        @Volatile var inst: LockPopupActivity? = null

        fun push(c: Context, s: Int, t: String) {
            val a = inst
            if (a != null) {
                a.runOnUiThread { a.apply(s, t) }
                return
            }
            if (s == WakeCtl.IDLE) return
            try {
                val i = Intent(c, LockPopupActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                c.startActivity(i)
            } catch (e: Exception) { }
        }
    }

    private var sv: SiriView? = null
    private val h = Handler(Looper.getMainLooper())
    private val closeRun = Runnable { finish(); overridePendingTransition(0, 0) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        inst = this
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val root = FrameLayout(this)
        root.setOnClickListener { WakeService.instance?.dismiss() }
        val v = SiriView(this)
        v.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        root.addView(v)
        sv = v
        setContentView(root)
        v.set(WakeCtl.state, WakeCtl.text)
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        apply(WakeCtl.state, WakeCtl.text)
    }

    fun apply(s: Int, t: String) {
        h.removeCallbacks(closeRun)
        if (s == WakeCtl.IDLE) h.postDelayed(closeRun, 450) else sv?.set(s, t)
    }

    override fun onBackPressed() {
        WakeService.instance?.dismiss()
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (inst === this) inst = null
        h.removeCallbacks(closeRun)
        super.onDestroy()
    }
}
