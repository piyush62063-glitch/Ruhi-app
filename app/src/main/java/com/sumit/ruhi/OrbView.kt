package com.sumit.ruhi

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class OrbView(c: Context) : View(c) {
    var style = 0
    var state = 0 // 0 idle, 1 listening, 2 speaking
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dots = List(90) { floatArrayOf((Math.random() * 6.28).toFloat(), (Math.random() * 0.95).toFloat()) }

    companion object {
        val names = listOf("Plasma", "Ocean", "Solar", "Lava", "Classic", "Crystal", "Galaxy", "Sound ring", "Black hole", "Eclipse")
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

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val t = System.currentTimeMillis() / 1000f
        val sp = if (state == 1) 5f else if (state == 2) 7f else 1.5f
        val a = (if (state == 0) 0.02f else 0.07f) * (1f + sin(t * sp))
        val r = min(cx, cy) * 0.62f * (1f + a)
        val cc = (if (state == 1) LISTEN else cols[style % cols.size]).split(",").map { Color.parseColor(it) }
        p.style = Paint.Style.FILL; p.shader = null
        p.shader = RadialGradient(cx, cy, r * 1.7f, intArrayOf(Color.argb(110, Color.red(cc[0]), Color.green(cc[0]), Color.blue(cc[0])), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.7f, p)
        p.shader = null
        when (style) {
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
