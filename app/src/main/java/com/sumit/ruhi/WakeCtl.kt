package com.sumit.ruhi

import android.os.Handler
import android.os.Looper

/**
 * Background Ruhi ki shared state. Part 2 mein Siri-style popup isi listener se chalega:
 * state 0 = idle (popup chhupa), 1 = sun rahi hai, 2 = soch rahi hai, 3 = bol rahi hai.
 */
object WakeCtl {
    const val IDLE = 0
    const val LISTEN = 1
    const val THINK = 2
    const val SPEAK = 3

    private val main = Handler(Looper.getMainLooper())

    /** App ki apni voice screen mic use kar rahi ho to true, tab background sunna rukta hai. */
    @Volatile var paused: Boolean = false
        set(v) {
            if (field == v) return
            field = v
            WakeService.instance?.pauseChanged()
        }

    @Volatile var session: Boolean = false
    @Volatile var state: Int = 0
    @Volatile var text: String = ""
    @Volatile var level: Float = 0f
    @Volatile var error: String = ""
    @Volatile var listener: ((Int, String) -> Unit)? = null

    fun begin() {
        session = true
        text = ""
        update(LISTEN, "")
    }

    fun update(s: Int, t: String? = null) {
        state = s
        if (t != null) text = t
        val sn = text
        main.post { listener?.invoke(s, sn) }
    }

    fun end() {
        session = false
        state = IDLE
        text = ""
        main.post { listener?.invoke(IDLE, "") }
    }
}
