package com.sumit.ruhi

import android.os.Handler
import android.os.Looper

object Hub {
    const val IDLE = 0
    const val LISTENING = 1
    const val THINKING = 2
    const val SPEAKING = 3

    @Volatile var state = IDLE
    @Volatile var level = 0f
    @Volatile var partial = ""
    @Volatile var status = ""
    @Volatile var running = false
    @Volatile var muted = false
    val msgs = ArrayList<Pair<Boolean, String>>()

    private val main = Handler(Looper.getMainLooper())
    @Volatile var onChange: (() -> Unit)? = null

    fun changed() {
        main.post { onChange?.invoke() }
    }

    @JvmName("applyState") fun setState(s: Int) {
        state = s
        changed()
    }

    fun addMsg(user: Boolean, t: String) {
        synchronized(msgs) {
            msgs.add(Pair(user, t))
            while (msgs.size > 60) msgs.removeAt(0)
        }
        changed()
    }
}
