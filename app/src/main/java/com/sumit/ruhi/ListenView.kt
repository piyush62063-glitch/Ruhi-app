package com.sumit.ruhi

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class ListenView(c: Context, private val st: () -> Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tex: Bitmap = BitmapFactory.decodeResource(c.resources, R.drawable.cloud)
    private val shA = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val shB = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val shC = BitmapShader(tex, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val mat = Matrix()
    private val d = c.resources.displayMetrics.density
    private val stars = List(80) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), (Math.random() * 0.8 + 0.2).toFloat(), (Math.random() * 6.28).toFloat()) }

    // 0 idle/muted, 1 listening, 2 speaking, 3 thinking
    private val light = intArrayOf(Color.parseColor("#9FD8FF"), Color.parseColor("#8FFFF0"), Color.parseColor("#FFB0E0"), Color.parseColor("#D2B8FF"))
    private val mid = intArrayOf(Color.parseColor("#4F6BFF"), Color.parseColor("#27D6D2"), Color.parseColor("#E04FA8"), Color.parseColor("#8A5CFF"))
    private val dark = intArrayOf(Color.parseColor("#141A5C"), Color.parseColor("#0A4A6E"), Color.parseColor("#4A1466"), Color.parseColor("#2A1470"))

    private fun layer(cv: Canvas, sh: BitmapShader, cx: Float, cy: Float, r: Float, sc: Float, vx: Float, vy: Float, t: Float, tint: Int, a: Int) {
        mat.reset(); mat.setScale(sc * d, sc * d); mat.postTranslate(t * vx * d, t * vy * d)
        sh.setLocalMatrix(mat)
        p.shader = sh; p.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN); p.alpha = a
        cv.drawCircle(cx, cy, r, p)
        p.shader = null; p.colorFilter = null; p.alpha = 255
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = System.currentTimeMillis() / 1000f
        val s = st().coerceIn(0, 3)
        p.style = Paint.Style.FILL
        // stars
        for (q in stars) {
            val tw = 0.5f + 0.5f * sin(t * 1.4f + q[3])
            p.color = Color.argb((50 + 160 * tw * q[2]).toInt(), 255, 255, 255)
            cv.drawCircle(q[0] * w, q[1] * h, (0.6f + 1.4f * q[2]) * d, p)
        }
        val cx = w / 2f; val cy = h * 0.40f
        val br = if (s == 2) 0.035f * sin(t * 8f) else 0.014f * sin(t * 1.6f)
        val r = w * 0.335f * (1f + br)
        // halo
        p.shader = RadialGradient(cx, cy, r * 1.5f, intArrayOf(Color.TRANSPARENT, Color.argb(200, 255, 255, 255), Color.argb(70, Color.red(mid[s]), Color.green(mid[s]), Color.blue(mid[s])), Color.TRANSPARENT), floatArrayOf(0f, 0.66f, 0.8f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.5f, p); p.shader = null
        // sound rings
        if (s == 1 || s == 2) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d
            for (i in 0 until 3) {
                val ph = ((t * (if (s == 2) 0.9f else 0.45f)) + i / 3f) % 1f
                p.color = Color.argb(((1f - ph) * 110).toInt(), Color.red(light[s]), Color.green(light[s]), Color.blue(light[s]))
                cv.drawCircle(cx, cy, r * (1.04f + 0.3f * ph), p)
            }
            p.style = Paint.Style.FILL
        }
        // body
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.5f, intArrayOf(light[s], mid[s], dark[s]), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p); p.shader = null
        val k = if (s == 0) 0.35f else if (s == 2) 2.2f else 1f
        layer(cv, shA, cx, cy, r, 1.1f, 16f * k, 6f * k, t, light[s], 210)
        layer(cv, shB, cx, cy, r, 0.7f, -11f * k, 9f * k, t, dark[s], 160)
        layer(cv, shC, cx, cy, r, 1.7f, 24f * k, -5f * k, t, Color.WHITE, 85)
        // rim shading
        p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(150, 4, 4, 30)), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p); p.shader = null
        // specular
        val hx = cx - r * 0.38f + r * 0.03f * sin(t); val hy = cy - r * 0.36f
        p.shader = RadialGradient(hx, hy, r * 0.42f, intArrayOf(Color.argb(240, 255, 255, 255), Color.argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(hx, hy, r * 0.42f, p); p.shader = null
        postInvalidateOnAnimation()
    }
}
