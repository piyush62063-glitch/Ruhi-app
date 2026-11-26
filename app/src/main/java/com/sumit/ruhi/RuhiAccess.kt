package com.sumit.ruhi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Part 4B: Accessibility service. Ruhi ke haath: back/home/recents, scroll, screenshot, lock, tap,
 * screen padhna, quick settings se WiFi/Bluetooth on-off, aur WhatsApp ka message auto-send.
 * Screen ka text sirf bolne ke liye padha jata hai, kahin bheja nahi jata.
 */
class RuhiAccess : AccessibilityService() {

    companion object {
        @Volatile var instance: RuhiAccess? = null

        /** WhatsApp ka prefilled message khulne ke baad send button khud dabane ke liye taiyar karta hai. */
        fun armSend(): Boolean {
            val s = instance ?: return false
            s.arm()
            return true
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var sendUntil = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }

    override fun onInterrupt() { }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- helpers
    private fun walk(n: AccessibilityNodeInfo?, depth: Int, budget: IntArray, f: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (n == null || depth > 45 || budget[0] <= 0) return false
        budget[0] = budget[0] - 1
        if (f(n)) return true
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (walk(c, depth + 1, budget, f)) return true
        }
        return false
    }

    private fun find(root: AccessibilityNodeInfo?, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var hit: AccessibilityNodeInfo? = null
        walk(root, 0, intArrayOf(1500)) { n ->
            if (pred(n)) {
                hit = n
                true
            } else false
        }
        return hit
    }

    private fun label(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString() ?: ""
        val d = n.contentDescription?.toString() ?: ""
        return (t + " " + d).trim()
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        var n = node
        var hops = 0
        while (n != null && !n.isClickable && hops < 6) {
            n = n.parent
            hops++
        }
        return n != null && n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun hidePopup() {
        if (WakeCtl.session && WakeCtl.state != WakeCtl.IDLE) WakeCtl.update(WakeCtl.IDLE)
    }

    // ---------------------------------------------------------------- global kaam
    fun act(code: Int): Boolean = try { performGlobalAction(code) } catch (e: Exception) { false }

    /** Popup chhupa kar screenshot leta hai, taaki popup photo mein na aaye. */
    fun screenshot(cb: (Boolean) -> Unit) {
        hidePopup()
        h.postDelayed({ cb(act(GLOBAL_ACTION_TAKE_SCREENSHOT)) }, 800)
    }

    fun scroll(down: Boolean, cb: (Boolean) -> Unit) {
        hidePopup()
        h.postDelayed({
            val dm = resources.displayMetrics
            val x = dm.widthPixels / 2f
            val y1 = dm.heightPixels * 0.72f
            val y2 = dm.heightPixels * 0.30f
            val p = Path()
            if (down) {
                p.moveTo(x, y1)
                p.lineTo(x, y2)
            } else {
                p.moveTo(x, y2)
                p.lineTo(x, y1)
            }
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0L, 350L)).build()
            val ok = try { dispatchGesture(g, null, null) } catch (e: Exception) { false }
            cb(ok)
        }, 600)
    }

    // ---------------------------------------------------------------- screen par kaam
    fun tapText(q0: String): Boolean {
        val q = q0.trim().lowercase()
        if (q.isEmpty()) return false
        val root = rootInActiveWindow ?: return false
        var node = find(root) { n -> label(n).lowercase().contains(q) }
        if (node == null) {
            val sq = Actions.skel(q)
            if (sq.length >= 3) {
                node = find(root) { n ->
                    val l = label(n)
                    l.isNotEmpty() && l.length < 40 && Actions.skel(l) == sq
                }
            }
        }
        return clickNode(node)
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val out = LinkedHashSet<String>()
        walk(root, 0, intArrayOf(1500)) { n ->
            if (n.isVisibleToUser) {
                val l = label(n)
                if (l.isNotBlank() && l.length < 200) out.add(l.trim())
            }
            out.size >= 30
        }
        val sb = StringBuilder()
        for (s in out) {
            if (sb.length > 380) break
            if (sb.isNotEmpty()) sb.append(". ")
            sb.append(s)
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- quick settings tile (WiFi / Bluetooth)
    private fun tileState(n: AccessibilityNodeInfo): Boolean? {
        val sb = StringBuilder(label(n))
        if (Build.VERSION.SDK_INT >= 30) sb.append(" ").append(n.stateDescription ?: "")
        val m = Regex("\\b(on|off)\\b").find(sb.toString().lowercase())
        if (m != null) return m.value == "on"
        if (n.isCheckable) return n.isChecked
        return null
    }

    /**
     * Quick settings kholkar tile dhoondhta hai aur dabata hai. cb: null = tile nahi mila,
     * "already" = pehle se waisa hi tha, "done" = badal diya.
     */
    fun toggleTile(names: List<String>, want: Boolean?, cb: (String?) -> Unit) {
        if (!act(GLOBAL_ACTION_QUICK_SETTINGS)) {
            cb(null)
            return
        }
        var tries = 0
        val step = object : Runnable {
            override fun run() {
                tries++
                val root = rootInActiveWindow
                var node: AccessibilityNodeInfo? = null
                if (root != null) {
                    for (nm in names) {
                        node = find(root) { n ->
                            label(n).lowercase().contains(nm) && (n.isClickable || n.isCheckable || n.parent?.isClickable == true)
                        }
                        if (node != null) break
                    }
                }
                val tile = node
                if (tile == null) {
                    if (tries < 4) {
                        h.postDelayed(this, 450)
                        return
                    }
                    act(GLOBAL_ACTION_BACK)
                    cb(null)
                    return
                }
                val cur = tileState(tile)
                if (want != null && cur != null && cur == want) {
                    h.postDelayed({ act(GLOBAL_ACTION_BACK) }, 250)
                    cb("already")
                    return
                }
                clickNode(tile)
                h.postDelayed({ act(GLOBAL_ACTION_BACK) }, 500)
                cb("done")
            }
        }
        h.postDelayed(step, 800)
    }

    // ---------------------------------------------------------------- WhatsApp auto-send
    private val tick = object : Runnable {
        override fun run() {
            if (sendUntil == 0L || SystemClock.elapsedRealtime() > sendUntil) {
                sendUntil = 0L
                return
            }
            trySend()
            if (sendUntil != 0L) h.postDelayed(this, 900)
        }
    }

    fun arm() {
        sendUntil = SystemClock.elapsedRealtime() + 20000L
        h.removeCallbacks(tick)
        h.postDelayed(tick, 1500)
    }

    private fun trySend() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != "com.whatsapp") return
        var node: AccessibilityNodeInfo? = null
        try {
            val l = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
            if (l != null) node = l.firstOrNull { it.isVisibleToUser }
        } catch (e: Exception) { }
        if (node == null) {
            node = find(root) { n ->
                val d = (n.contentDescription?.toString() ?: "").trim().lowercase()
                d == "send" || d == "\u092D\u0947\u091C\u0947\u0902"
            }
        }
        if (node != null && clickNode(node)) {
            sendUntil = 0L
            h.removeCallbacks(tick)
        }
    }
}
