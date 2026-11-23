package com.sumit.ruhi

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.util.TypedValue
import android.widget.TextView
import android.view.Gravity
import kotlin.math.abs
import kotlin.math.sin

/** Design image ke pixel coordinates mein views rakhta hai; shapes kabhi stretch nahi hote (uniform fit). */
class FracLayout(c: Context, private val rw: Float, private val rh: Float) : ViewGroup(c) {
    class P(val l: Float, val t: Float, val r: Float, val b: Float) : ViewGroup.LayoutParams(0, 0)

    private fun sc(w: Int, h: Int) = minOf(w / rw, h / rh)

    override fun onMeasure(wm: Int, hm: Int) {
        val w = MeasureSpec.getSize(wm)
        val h = MeasureSpec.getSize(hm)
        setMeasuredDimension(w, h)
        val s = sc(w, h)
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val p = v.layoutParams as P
            v.measure(
                MeasureSpec.makeMeasureSpec(((p.r - p.l) * s).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(((p.b - p.t) * s).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(ch: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val s = sc(w, h)
        val ox = (w - rw * s) / 2
        val oy = (h - rh * s) / 2
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val p = v.layoutParams as P
            v.layout((ox + p.l * s).toInt(), (oy + p.t * s).toInt(), (ox + p.r * s).toInt(), (oy + p.b * s).toInt())
        }
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is P

    fun add(v: View, l: Float, t: Float, r: Float, b: Float) = addView(v, P(l, t, r, b))
}

/** Orb ke andar ka status: waveform + Listening/Thinking/Speaking text. 0 idle, 1 listening, 2 thinking, 3 speaking */
class StateView(c: Context) : View(c) {
    var state = 0
    var level = 0f
    private val cover = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB9B9DD.toInt(); textAlign = Paint.Align.CENTER }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val dark = 0xFF02020A.toInt()
        val sh = RadialGradient(0f, 0f, 1f, intArrayOf(dark, dark, 0x0002020A), floatArrayOf(0f, 0.9f, 1f), Shader.TileMode.CLAMP)
        val m = Matrix()
        m.setScale(w / 2f, h / 2f)
        m.postTranslate(w / 2f, h / 2f)
        sh.setLocalMatrix(m)
        cover.shader = sh
        bar.strokeWidth = w * 0.017f
        bar.shader = LinearGradient(w * 0.12f, 0f, w * 0.88f, 0f,
            intArrayOf(0xFF38BDF8.toInt(), 0xFF7C6CFF.toInt(), 0xFFB455F7.toInt(), 0xFF38BDF8.toInt()), null, Shader.TileMode.CLAMP)
        big.textSize = h * 0.15f
        small.textSize = h * 0.10f
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        cv.drawRect(0f, 0f, w, h, cover)
        val t = SystemClock.uptimeMillis() / 1000f
        val n = 27
        val base = h * 0.40f
        val maxH = h * 0.30f
        for (i in 0 until n) {
            val x = w * 0.12f + i * (w * 0.76f / (n - 1))
            val env = 1f - abs(i - (n - 1) / 2f) / ((n - 1) / 2f)
            val amp = when (state) {
                1 -> 0.12f + level * 0.88f * abs(sin(t * 6f + i * 0.7f))
                2 -> 0.22f + 0.14f * sin(t * 3f + i * 0.45f)
                3 -> 0.30f + 0.60f * abs(sin(t * 9f + i * 0.9f))
                else -> 0.07f
            }
            val bh = maxH * amp * (0.30f + 0.70f * env) + 2f
            cv.drawLine(x, base - bh, x, base + bh, bar)
        }
        val label = when (state) { 1 -> "Listening..."; 2 -> "Thinking..."; 3 -> "Speaking..."; else -> "Ruhi" }
        val sub = when (state) { 1 -> "Speak now..."; 2 -> "Soch rahi hu..."; 3 -> "Bol rahi hu..."; else -> "Orb tap karo" }
        cv.drawText(label, w / 2f, h * 0.76f, big)
        cv.drawText(sub, w / 2f, h * 0.93f, small)
        postInvalidateOnAnimation()
    }
}

/** Active state wale pill/panel par neon glow. */
class GlowView(c: Context) : View(c) {
    var on = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF8B7BFF.toInt()
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(cv: Canvas) {
        if (!on) return
        val t = SystemClock.uptimeMillis() / 1000f
        p.alpha = (110 + 100 * sin(t * 4f)).toInt().coerceIn(0, 255)
        p.strokeWidth = width * 0.035f
        val r = RectF(width * 0.06f, height * 0.06f, width * 0.94f, height * 0.94f)
        cv.drawRoundRect(r, height * 0.3f, height * 0.3f, p)
        postInvalidateOnAnimation()
    }
}

/** Loading screen ka progress bar (design ke bar ke upar). */
class ProgressView(c: Context) : View(c) {
    var p = 0f
    var label = "Initializing Ruhi AI..."
    private val bg = Paint().apply { color = 0xFF00031C.toInt() }
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF3F37B8.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        cv.drawRect(0f, 0f, w, h, bg)
        tx.textSize = h * 0.25f
        tx.textAlign = Paint.Align.CENTER
        cv.drawText(label, w * 0.50f, h * 0.36f, tx)
        val l = w * 0.03f
        val r = w * 0.84f
        val t = h * 0.58f
        val b = h * 0.84f
        track.strokeWidth = h * 0.03f
        val rr = (b - t) / 2f
        cv.drawRoundRect(RectF(l, t, r, b), rr, rr, track)
        fill.shader = LinearGradient(l, 0f, r, 0f, 0xFF8B4DF6.toInt(), 0xFF5AA9FF.toInt(), Shader.TileMode.CLAMP)
        val e = l + (r - l) * p.coerceIn(0f, 1f)
        if (e > l + rr) cv.drawRoundRect(RectF(l + 3f, t + 3f, e - 3f, b - 3f), rr, rr, fill)
        tx.textSize = h * 0.26f
        cv.drawText("${(p * 100).toInt()}%", w * 0.92f, h * 0.84f, tx)
    }
}


/** Text jo design ke scale ke saath apna size khud set karta hai. */
class FitText(c: Context, private val frac: Float) : TextView(c) {
    init { gravity = Gravity.CENTER }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, h * frac)
    }
}

/** Minimize / End jaise gradient pill buttons. */
class PillButton(c: Context, private val label: String) : View(c) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x66FFFFFF }
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        fill.shader = LinearGradient(0f, 0f, w, 0f, 0xFF7C4DFF.toInt(), 0xFFC13DFF.toInt(), Shader.TileMode.CLAMP)
        fill.alpha = if (isPressed) 150 else 255
        edge.strokeWidth = h * 0.02f
        val r = RectF(2f, 2f, w - 2f, h - 2f)
        cv.drawRoundRect(r, h / 2f, h / 2f, fill)
        cv.drawRoundRect(r, h / 2f, h / 2f, edge)
        tx.textSize = h * 0.36f
        cv.drawText(label, w / 2f, h / 2f + h * 0.13f, tx)
    }
}
