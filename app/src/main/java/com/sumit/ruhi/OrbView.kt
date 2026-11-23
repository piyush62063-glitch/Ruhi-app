package com.sumit.ruhi

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class OrbView(c: Context) : View(c) {
    var style = 0
    var state = 0 // 0 idle, 1 listening, 2 speaking, 3 thinking
    var showBase = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()
    private val dots = List(90) { floatArrayOf((Math.random() * 6.28).toFloat(), (Math.random() * 0.95).toFloat()) }

    companion object {
        val names = listOf("Lotus Glass", "Plasma", "Ocean", "Solar", "Lava", "Classic", "Crystal", "Galaxy", "Sound ring", "Black hole", "Eclipse")
        private val cols = listOf("#FF4FA3,#4A1D7A", "#35E0E8,#0B4F7A", "#FFB020,#C2410C", "#FF5A1F,#4A0E0E", "#B58CFF,#3B1D8F",
            "#9FE8FF,#2A5F8F", "#B58CFF,#120A2E", "#5CE1FF,#7A3CFF", "#FFB020,#6A2A00", "#FFFFFF,#222222")
        private const val LISTEN = "#5CF2E0,#0E5E78"
    }

    private fun sphere(cv: Canvas, cx: Float, cy: Float, r: Float, c0: Int, c1: Int, t: Float) {
        val hx = cx - r * 0.35f + r * 0.12f * sin(t)
        p.shader = RadialGradient(hx, cy - r * 0.35f, r * 1.5f, intArrayOf(Color.WHITE, c0, c1), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        p.shader = null
    }

    // naya Lotus Glass orb: glass sphere + orbit rings + lotus + waveform
    private fun lotusGlass(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        val (inner, outer, accent) = when (state) {
            1 -> Triple("#3FD2FF", "#0A1F5C", "#5CF2E0")
            2 -> Triple("#FF63D8", "#2E0A5A", "#FF8AE8")
            3 -> Triple("#B25CFF", "#1E0A4A", "#C9A5FF")
            else -> Triple("#7C4DFF", "#120A3A", "#9B7CFF")
        }
        val ac = Color.parseColor(accent)
        val spin = when (state) { 1 -> 60f; 2 -> 90f; 3 -> 150f; else -> 25f }
        // glow
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 1.9f, intArrayOf(Color.argb(120, Color.red(ac), Color.green(ac), Color.blue(ac)), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.9f, p)
        // base platform
        if (showBase) {
            p.shader = null; p.style = Paint.Style.STROKE
            for (k in 0..2) {
                p.strokeWidth = r * 0.025f; p.color = Color.argb(160 - k * 40, Color.red(ac), Color.green(ac), Color.blue(ac))
                oval.set(cx - r * (1.1f + k * 0.22f), cy + r * (1.12f - k * 0.0f), cx + r * (1.1f + k * 0.22f), cy + r * (1.12f + 0.28f + k * 0.12f))
                cv.drawOval(oval, p)
            }
            p.style = Paint.Style.FILL
        }
        // glass sphere
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.5f,
            intArrayOf(Color.argb(230, 255, 255, 255), Color.parseColor(inner), Color.parseColor(outer)), floatArrayOf(0f, 0.28f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.TRANSPARENT, Color.argb(90, 0, 0, 0)), floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        // rim light
        p.shader = SweepGradient(cx, cy, intArrayOf(Color.argb(30, 255, 255, 255), ac, Color.parseColor("#5CE1FF"), Color.argb(30, 255, 255, 255)), null)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.035f
        cv.save(); cv.rotate(t * spin, cx, cy); cv.drawCircle(cx, cy, r, p); cv.restore()
        // lotus
        p.shader = null; p.style = Paint.Style.FILL
        val ly = cy - r * 0.12f; val ph = r * 0.5f; val pw = r * 0.17f
        val angs = floatArrayOf(-62f, -31f, 0f, 31f, 62f)
        for (a in angs) {
            cv.save(); cv.translate(cx, ly + ph * 0.55f); cv.rotate(a)
            path.reset(); path.moveTo(0f, 0f); path.quadTo(-pw, -ph * 0.5f, 0f, -ph); path.quadTo(pw, -ph * 0.5f, 0f, 0f)
            p.shader = LinearGradient(0f, 0f, 0f, -ph, Color.parseColor("#C9A5FF"), Color.parseColor("#7C4DFF"), Shader.TileMode.CLAMP)
            p.style = Paint.Style.FILL; cv.drawPath(path, p)
            p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.012f; p.color = Color.argb(200, 255, 255, 255); cv.drawPath(path, p)
            cv.restore()
        }
        // sparkle
        p.style = Paint.Style.FILL; p.shader = null; p.color = Color.WHITE
        val sx = cx; val sy = ly - ph * 0.55f; val sr = r * 0.09f * (1f + 0.25f * sin(t * 4f))
        path.reset(); path.moveTo(sx, sy - sr); path.quadTo(sx, sy, sx + sr, sy); path.quadTo(sx, sy, sx, sy + sr); path.quadTo(sx, sy, sx - sr, sy); path.quadTo(sx, sy, sx, sy - sr)
        cv.drawPath(path, p)
        // waveform
        val bars = 21; val bw = r * 0.04f; val gap = r * 0.075f; val wy = cy + r * 0.5f
        p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = bw; p.style = Paint.Style.STROKE
        for (i in 0 until bars) {
            val x = cx + (i - bars / 2) * gap
            val env = 1f - abs(i - bars / 2) / (bars / 2f + 1f)
            val amp = if (state == 0) 0.04f else if (state == 3) 0.1f else 0.34f
            val h = r * (0.03f + amp * env * abs(sin(t * 6f + i * 0.55f)))
            p.color = if (i % 2 == 0) Color.parseColor("#5CE1FF") else ac
            cv.drawLine(x, wy - h, x, wy + h, p)
        }
        // orbit rings
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.03f
        for (k in 0..1) {
            cv.save(); cv.rotate((if (k == 0) -22f else 28f) + t * spin * (if (k == 0) 1f else -0.8f), cx, cy)
            p.shader = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, ac, Color.parseColor("#5CE1FF"), Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 0.7f, 1f))
            oval.set(cx - r * 1.32f, cy - r * 0.36f, cx + r * 1.32f, cy + r * 0.36f)
            cv.drawOval(oval, p); cv.restore()
        }
        p.shader = null; p.style = Paint.Style.FILL
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val t = System.currentTimeMillis() / 1000f
        val sp = if (state == 1) 5f else if (state == 2) 7f else if (state == 3) 3f else 1.5f
        val a = (if (state == 0) 0.02f else 0.05f) * (1f + sin(t * sp))
        if (style == 0) {
            val r0 = min(cx, cy) * (if (showBase) 0.58f else 0.56f) * (1f + a)
            lotusGlass(cv, cx, cy - (if (showBase) min(cx, cy) * 0.08f else 0f), r0, t)
            postInvalidateOnAnimation(); return
        }
        val s = style - 1
        val r = min(cx, cy) * 0.62f * (1f + a)
        val cc = (if (state == 1) LISTEN else cols[s % cols.size]).split(",").map { Color.parseColor(it) }
        p.style = Paint.Style.FILL; p.shader = null
        p.shader = RadialGradient(cx, cy, r * 1.7f, intArrayOf(Color.argb(110, Color.red(cc[0]), Color.green(cc[0]), Color.blue(cc[0])), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.7f, p)
        p.shader = null
        when (s) {
            6 -> {
                sphere(cv, cx, cy, r, cc[1], Color.BLACK, t)
                for (d in dots) {
                    val ang = d[0] + t * 0.5f * (1.2f - d[1]); val rad = d[1] * r * 0.92f
                    p.color = Color.argb(200, 255, 255, 255)
                    cv.drawCircle(cx + cos(ang) * rad, cy + sin(ang) * rad, r * 0.013f, p)
                }
            }
            7 -> {
                p.strokeWidth = r * 0.05f; p.strokeCap = Paint.Cap.ROUND
                val amp = if (state == 0) 0.05f else 0.25f
                for (i in 0 until 60) {
                    val ang = i * 6.2832f / 60; val l = r * (0.08f + amp * abs(sin(t * 6 + i * 0.7f)))
                    p.color = if (i % 2 == 0) cc[0] else cc[1]
                    cv.drawLine(cx + cos(ang) * r, cy + sin(ang) * r, cx + cos(ang) * (r + l), cy + sin(ang) * (r + l), p)
                }
                sphere(cv, cx, cy, r * 0.85f, cc[1], Color.BLACK, t)
            }
            8 -> {
                cv.save(); cv.rotate(t * 40f, cx, cy)
                p.shader = SweepGradient(cx, cy, intArrayOf(cc[0], Color.parseColor("#FF6A00"), cc[0]), null)
                p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.22f
                cv.drawCircle(cx, cy, r * 0.95f, p)
                cv.restore()
                p.shader = null; p.style = Paint.Style.FILL; p.color = Color.BLACK
                cv.drawCircle(cx, cy, r * 0.72f, p)
            }
            9 -> {
                p.color = Color.BLACK; cv.drawCircle(cx, cy, r, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.04f; p.color = cc[0]
                cv.drawCircle(cx, cy, r * 1.02f, p)
            }
            else -> sphere(cv, cx, cy, r, cc[0], cc[1], t)
        }
        p.style = Paint.Style.FILL
        postInvalidateOnAnimation()
    }
}
