package com.sumit.ruhi

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class MicView(c: Context, private val st: () -> Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val s = st(); val t = System.currentTimeMillis() / 1000f
        val rr = min(cx, cy) * 0.74f * (1f + (if (s == 0) 0f else 0.05f * (1f + sin(t * 6f))))
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, rr * 1.5f, intArrayOf(Color.argb(if (s == 0) 70 else 150, 155, 92, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, rr * 1.5f, p)
        p.shader = RadialGradient(cx, cy, rr, intArrayOf(Color.parseColor("#2A1B5E"), Color.parseColor("#0D0820")), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, rr, p)
        p.shader = SweepGradient(cx, cy, intArrayOf(Color.parseColor("#FF6BD6"), Color.parseColor("#7C5CFF"), Color.parseColor("#5CE1FF"), Color.parseColor("#FF6BD6")), null)
        p.style = Paint.Style.STROKE; p.strokeWidth = rr * 0.09f
        cv.save(); cv.rotate(t * (if (s == 0) 20f else 120f), cx, cy); cv.drawCircle(cx, cy, rr, p); cv.restore()
        p.shader = null; p.color = Color.parseColor("#B58CFF"); p.style = Paint.Style.FILL
        val w = rr * 0.2f
        cv.drawRoundRect(cx - w, cy - rr * 0.42f, cx + w, cy + rr * 0.1f, w, w, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = rr * 0.06f; p.strokeCap = Paint.Cap.ROUND
        cv.drawArc(cx - rr * 0.34f, cy - rr * 0.3f, cx + rr * 0.34f, cy + rr * 0.28f, 20f, 140f, false, p)
        cv.drawLine(cx, cy + rr * 0.28f, cx, cy + rr * 0.45f, p)
        cv.drawLine(cx - rr * 0.15f, cy + rr * 0.45f, cx + rr * 0.15f, cy + rr * 0.45f, p)
        p.style = Paint.Style.FILL
        postInvalidateOnAnimation()
    }
}

class WaveView(c: Context, private val st: () -> Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val n = 34; val gap = width / (n + 1f); val cy = height / 2f
        val t = System.currentTimeMillis() / 1000f; val s = st()
        p.strokeCap = Paint.Cap.ROUND; p.color = Color.parseColor("#9B5CFF"); p.strokeWidth = gap * 0.32f
        for (i in 0 until n) {
            val x = gap * (i + 1)
            val h = if (s == 0) (if (i == 5 || i == 17 || i == 29) height * 0.5f else 0f) else height * 0.9f * abs(sin(t * 5f + i * 0.6f))
            if (h > 1f) cv.drawLine(x, cy - h / 2, x, cy + h / 2, p) else cv.drawPoint(x, cy, p)
        }
        postInvalidateOnAnimation()
    }
}
