package com.sumit.ruhi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class OrbView(c: Context) : View(c) {
    var style = 0
    var live = false
    private var mode = 0
    private var level = 0f
    private var sm = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t0 = System.nanoTime()

    private fun tint(): Int = when (mode) {
        1 -> 0xFF22D3C5.toInt()
        2 -> 0xFFFF5FA2.toInt()
        3 -> 0xFFFFB86B.toInt()
        else -> 0xFF8B5CF6.toInt()
    }

    private fun al(c: Int, a: Float): Int = (c and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) postInvalidateOnAnimation()
    }

    override fun onDraw(cv: Canvas) {
        val t = (System.nanoTime() - t0) / 1e9f
        if (live) {
            mode = if (Hub.muted) 0 else Hub.state
            level = Hub.level
        } else {
            mode = 0
            level = 0.2f
        }
        val target = when (mode) {
            1 -> 0.15f + level * 0.85f
            3 -> 0.45f + 0.35f * sin(t * 9f)
            2 -> 0.25f + 0.15f * sin(t * 5f)
            else -> 0.12f + 0.08f * sin(t * 1.6f)
        }
        sm += (target - sm) * 0.12f
        val cx = width / 2f
        val cy = height / 2f
        val r0 = min(width, height) * 0.30f
        val col = tint()
        when (style) {
            1 -> aurora(cv, t, cx, cy, r0, sm, col)
            2 -> pulse(cv, t, cx, cy, r0, sm, col)
            3 -> galaxy(cv, t, cx, cy, r0, sm, col)
            else -> plasma(cv, t, cx, cy, r0, sm, col)
        }
        postInvalidateOnAnimation()
    }

    private fun glow(cv: Canvas, cx: Float, cy: Float, rad: Float, col: Int, a: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, rad, intArrayOf(al(col, a), al(col, 0f)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, rad, p)
        p.shader = null
    }

    private fun plasma(cv: Canvas, t: Float, cx: Float, cy: Float, r0: Float, e: Float, col: Int) {
        val r = r0 * (1f + e * 0.10f)
        glow(cv, cx, cy, r * 1.7f, col, 0.45f)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy - r * 0.2f, r * 1.1f,
            intArrayOf(0xFF6D4BD8.toInt(), 0xFF2A1670.toInt(), 0xFF140B40.toInt()),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(cx, cy, r, p)
        cv.save()
        val path = Path()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        cv.clipPath(path)
        for (i in 0 until 3) {
            val a = t * (0.6f + i * 0.35f) + i * 2.1f
            val bx = cx + cos(a) * r * 0.45f
            val by = cy + sin(a * 1.3f) * r * 0.45f
            val br = r * (0.55f + e * 0.25f)
            p.shader = RadialGradient(bx, by, br, intArrayOf(al(col, 0.85f), al(col, 0f)), null, Shader.TileMode.CLAMP)
            cv.drawCircle(bx, by, br, p)
        }
        val hx = cx - r * 0.45f
        val hy = cy - r * 0.45f
        p.shader = RadialGradient(hx, hy, r * 0.35f, intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        cv.drawCircle(hx, hy, r * 0.35f, p)
        cv.restore()
        p.shader = null
    }

    private fun aurora(cv: Canvas, t: Float, cx: Float, cy: Float, r0: Float, e: Float, col: Int) {
        glow(cv, cx, cy, r0 * 1.7f, col, 0.35f)
        p.style = Paint.Style.STROKE
        for (i in 0 until 4) {
            val rr = r0 * (0.55f + i * 0.17f) * (1f + e * 0.18f * sin(t * 2f + i))
            p.strokeWidth = r0 * (0.11f - i * 0.02f)
            val sw = SweepGradient(
                cx, cy,
                intArrayOf(al(col, 0f), al(col, 0.95f), al(0xFFFF5FA2.toInt(), 0.85f), al(col, 0f)),
                null
            )
            val m = Matrix()
            m.setRotate(t * (30f + i * 20f) * (if (i % 2 == 0) 1f else -1f), cx, cy)
            sw.setLocalMatrix(m)
            p.shader = sw
            cv.drawCircle(cx, cy, rr, p)
        }
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = al(col, 0.9f)
        cv.drawCircle(cx, cy, r0 * 0.18f * (1f + e * 0.5f), p)
    }

    private fun pulse(cv: Canvas, t: Float, cx: Float, cy: Float, r0: Float, e: Float, col: Int) {
        p.shader = null
        p.style = Paint.Style.STROKE
        for (i in 0 until 4) {
            val ph = (t * 0.35f * (1f + e) + i / 4f) % 1f
            p.strokeWidth = 3f + e * 4f
            p.color = al(col, (1f - ph) * 0.6f)
            cv.drawCircle(cx, cy, r0 * (0.45f + ph * 1.1f), p)
        }
        p.style = Paint.Style.FILL
        val rr = r0 * 0.8f * (1f + e * 0.25f)
        p.shader = RadialGradient(
            cx, cy, rr,
            intArrayOf(al(0xFFFFFFFF.toInt(), 0.95f), al(col, 0.9f), al(col, 0f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(cx, cy, rr, p)
        p.shader = null
    }

    private fun galaxy(cv: Canvas, t: Float, cx: Float, cy: Float, r0: Float, e: Float, col: Int) {
        glow(cv, cx, cy, r0 * 1.4f, col, 0.3f)
        p.style = Paint.Style.FILL
        p.shader = null
        for (i in 0 until 110) {
            val f = i / 110f
            val ang = i * 0.42f + t * (0.35f + e * 1.2f) * (1f - f * 0.5f)
            val rad = r0 * (0.15f + f * 1.05f) * (1f + e * 0.15f * sin(t * 3f + i))
            val x = cx + cos(ang) * rad
            val y = cy + sin(ang) * rad * 0.9f
            p.color = al(if (i % 4 == 0) 0xFFFFFFFF.toInt() else col, 0.35f + 0.65f * (1f - f))
            cv.drawCircle(x, y, 1.5f + (i % 3) + e * 2f, p)
        }
        glow(cv, cx, cy, r0 * 0.35f * (1f + e), 0xFFFFFFFF.toInt(), 0.9f)
    }
}
