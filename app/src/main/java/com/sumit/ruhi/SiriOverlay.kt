package com.sumit.ruhi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.sin

/**
 * Siri jaisa bottom popup. App band ho ya koi aur app khuli ho, "Hello Ruhi" bolte hi
 * screen ke neeche glow wala orb aata hai, jo bola wo text dikhata hai, aur kaam ke baad khud chala jata hai.
 * WakeCtl.listener se chalta hai.
 */
class SiriOverlay(c: Context) {
    private val ctx = c.applicationContext
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var view: SiriView? = null
    private var shown = false
    private val hideRun = Runnable { realHide() }

    fun update(state: Int, text: String) {
        if (state == WakeCtl.IDLE) {
            main.removeCallbacks(hideRun)
            main.postDelayed(hideRun, 350)
            return
        }
        main.removeCallbacks(hideRun)
        if (!shown && !show()) return
        view?.set(state, text)
    }

    private fun show(): Boolean {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) return false
        val v = SiriView(ctx)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM
        try { wm.addView(v, lp) } catch (e: Exception) { return false }
        view = v
        shown = true
        v.translationY = 260f * ctx.resources.displayMetrics.density
        v.animate().translationY(0f).setDuration(280).start()
        return true
    }

    private fun realHide() {
        val v = view ?: return
        view = null
        shown = false
        v.animate().translationY(260f * ctx.resources.displayMetrics.density).setDuration(220).withEndAction {
            try { wm.removeView(v) } catch (e: Exception) { }
        }.start()
    }

    fun destroy() {
        main.removeCallbacks(hideRun)
        val v = view
        view = null
        shown = false
        if (v != null) try { wm.removeViewImmediate(v) } catch (e: Exception) { }
    }
}

class SiriView(c: Context) : View(c) {
    private var state = WakeCtl.LISTEN
    private var text = ""
    private var layout: StaticLayout? = null
    private var layoutText = "\u0000"
    private var layoutW = 0
    private var cardH = 0f
    private var sm = 0f

    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens

    private val scrim = Paint()
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shineP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6161228.toInt() }
    private val edgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x40FFFFFF }
    private val labelP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = dp(13f)
    }
    private val textP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = dp(16f) }

    private val cols = intArrayOf(0xFF38BDF8.toInt(), 0xFF8B5CF6.toInt(), 0xFFEC4899.toInt(), 0xFFFB923C.toInt())
    private val blobs = ArrayList<Pair<Paint, RadialGradient>>()
    private var sweep: SweepGradient? = null
    private val mx = Matrix()

    init {
        for (col in cols) {
            val g = RadialGradient(0f, 0f, 1f, intArrayOf(col, (col and 0x00FFFFFF) or 0x55000000, col and 0x00FFFFFF),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = g
            blobs.add(Pair(p, g))
        }
    }

    fun set(s: Int, t: String) {
        state = s
        if (t != text) {
            text = t
            requestLayout()
        }
        invalidate()
    }

    private fun buildLayout(w: Int) {
        if (text == layoutText && w == layoutW) return
        layoutText = text
        layoutW = w
        if (text.isBlank() || w <= 0) { layout = null; cardH = 0f; return }
        val l = StaticLayout.Builder.obtain(text, 0, text.length, textP, w)
            .setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        layout = l
        cardH = l.height + dp(26f)
    }

    override fun onMeasure(wm: Int, hm: Int) {
        val w = MeasureSpec.getSize(wm)
        buildLayout((w - dp(32f) - dp(36f)).toInt())
        val h = dp(150f) + (if (cardH > 0f) cardH + dp(14f) else 0f)
        setMeasuredDimension(w, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        scrim.shader = LinearGradient(0f, h * 0.30f, 0f, h.toFloat(), 0x00000010, 0xE6000010.toInt(), Shader.TileMode.CLAMP)
        sweep = SweepGradient(w / 2f, h - dp(78f),
            intArrayOf(0xFF38BDF8.toInt(), 0xFF6366F1.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFFFB923C.toInt(), 0xFF38BDF8.toInt()), null)
        orbP.shader = sweep
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) WakeService.instance?.dismiss()
        return true
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) { postInvalidateOnAnimation(); return }
        val t = SystemClock.uptimeMillis() / 1000f
        val target = when (state) {
            WakeCtl.LISTEN -> 0.25f + 0.75f * WakeCtl.level
            WakeCtl.THINK -> 0.50f + 0.20f * sin(t * 4f)
            else -> 0.55f + 0.45f * abs(sin(t * 7f))
        }
        sm += (target - sm) * 0.15f

        cv.drawRect(0f, 0f, w, h, scrim)

        // neeche ki rangeen glow (Siri jaisi)
        for (i in blobs.indices) {
            val x = w * (0.5f + 0.42f * sin(t * (0.7f + 0.2f * i) + i * 1.9f))
            val rad = w * 0.55f * (0.80f + 0.25f * sm)
            mx.reset()
            mx.setScale(rad, rad * 0.55f)
            mx.postTranslate(x, h + dp(10f))
            blobs[i].second.setLocalMatrix(mx)
            blobs[i].first.shader = blobs[i].second
            blobs[i].first.alpha = (110 + 110 * sm).toInt().coerceIn(0, 255)
            cv.drawRect(0f, h - dp(150f), w, h, blobs[i].first)
        }

        // orb
        val cx = w / 2f
        val cy = h - dp(78f)
        val r = dp(30f) * (1f + 0.18f * sm)
        for (k in 3 downTo 1) {
            glowP.color = 0xFFA855F7.toInt()
            glowP.alpha = (46 / k + 10 * sm).toInt()
            cv.drawCircle(cx, cy, r + dp(8f) * k, glowP)
        }
        mx.reset()
        mx.setRotate(t * (if (state == WakeCtl.THINK) 260f else 90f), cx, cy)
        sweep?.setLocalMatrix(mx)
        orbP.shader = sweep
        cv.drawCircle(cx, cy, r, orbP)
        shineP.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 0.9f,
            intArrayOf(0xAAFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, shineP)

        // status
        val lb = when (state) { WakeCtl.LISTEN -> "Sun rahi hu..."; WakeCtl.THINK -> "Soch rahi hu..."; else -> "Bol rahi hu..." }
        val ly = cy - r - dp(14f)
        cv.drawText(lb, cx, ly, labelP)

        // bola hua text
        val l = layout
        if (l != null && cardH > 0f) {
            val bottom = ly - dp(22f)
            val top = bottom - cardH
            val rect = RectF(dp(16f), top, w - dp(16f), bottom)
            cv.drawRoundRect(rect, dp(22f), dp(22f), cardP)
            edgeP.strokeWidth = dp(1f)
            cv.drawRoundRect(rect, dp(22f), dp(22f), edgeP)
            cv.save()
            cv.translate(dp(16f) + dp(18f), top + dp(13f))
            l.draw(cv)
            cv.restore()
        }
        postInvalidateOnAnimation()
    }
}
