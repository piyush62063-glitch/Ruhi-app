package com.sumit.ruhi

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.FrameLayout

/** Locked phone par jo kaam unlock maangta hai, uska command yahan rukta hai aur unlock ke baad khud chalta hai. */
object Handoff {
    private var cmd: String? = null
    private var at = 0L

    @Synchronized fun set(c: String) {
        cmd = c
        at = SystemClock.elapsedRealtime()
    }

    @Synchronized fun pending(): Boolean = cmd != null && SystemClock.elapsedRealtime() - at < 90000L

    @Synchronized fun take(): String? {
        val c = cmd
        val fresh = SystemClock.elapsedRealtime() - at < 90000L
        cmd = null
        return if (c != null && fresh) c else null
    }

    @Synchronized fun clear() { cmd = null }
}

/**
 * Lock screen par system ka unlock prompt (PIN / pattern / fingerprint) dikhati hai.
 * Unlock hote hi pending command Ruhi khud chala deti hai. PIN Ruhi ko kabhi nahi milta.
 */
class UnlockActivity : Activity() {
    companion object {
        fun launch(c: Context) {
            try {
                val i = Intent(c, UnlockActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                c.startActivity(i)
            } catch (e: Exception) { }
        }
    }

    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(FrameLayout(this))
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= 26 && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { done(true) }
                override fun onDismissCancelled() { done(false) }
                override fun onDismissError() { done(false) }
            })
        } else {
            // purane Android par ya lock pehle hi khul gaya: USER_PRESENT receiver ya yahi command chala dega
            h.postDelayed({ done(!km.isKeyguardLocked) }, 1200)
        }
    }

    private fun done(ok: Boolean) {
        if (ok) WakeService.instance?.runHandoff() else Handoff.clear()
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
