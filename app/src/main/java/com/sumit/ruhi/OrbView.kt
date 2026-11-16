package com.sumit.ruhi

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

private const val TAU = 6.2831855f

class OrbView(c: Context) : View(c) {
    var style = 0
    var state = 0 // 0 idle, 1 listening, 2 speaking

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val rect = RectF()
    private val mx = Matrix()
    private val rnd = java.util.Random(7)
    private val dots = List(110) {
        floatArrayOf((Math.random() * TAU).toFloat(), (Math.random() * 0.95).toFloat(), (Math.random() * TAU).toFloat())
    }
    private val shaders = HashMap<Int, BitmapShader>()
    private val bolts = ArrayList<FloatArray>()
    private var boltT = 0L

    companion object {
        val names = listOf(
            "Plasma", "Ocean", "Solar", "Lava", "Classic", "Crystal", "Galaxy", "Sound ring", "Black hole",
            "Eclipse", "Hologram", "Saturn", "Bubble", "Tesla ball", "Moon", "Candy", "Fireflies", "Aurora"
        )
        private val TEXS = setOf(0, 1, 2, 3, 4, 5, 14, 15, 17)
        private val pal = mapOf(
            0 to intArrayOf(0xFFFFA0E6.toInt(), 0xFFC4409F.toInt(), 0xFF2A0E4A.toInt()),
            1 to intArrayOf(0xFF9CF6FF.toInt(), 0xFF1AA5C8.toInt(), 0xFF053A66.toInt()),
            2 to intArrayOf(0xFFFFE066.toInt(), 0xFFFF7A10.toInt(), 0xFF7A1A02.toInt()),
            3 to intArrayOf(0xFFFFD040.toInt(), 0xFFE0310A.toInt(), 0xFF1A0505.toInt()),
            4 to intArrayOf(0xFFE0C8FF.toInt(), 0xFF7B4FE0.toInt(), 0xFF1E0F5A.toInt()),
            5 to intArrayOf(0xFFE8FBFF.toInt(), 0xFF6FB0D8.toInt(), 0xFF1C4A78.toInt()),
            14 to intArrayOf(0xFFF2F2F5.toInt(), 0xFFA8A9B5.toInt(), 0xFF4A4B58.toInt()),
            15 to intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF9AC8.toInt(), 0xFF8FA8FF.toInt()),
            17 to intArrayOf(0xFFB8FFD8.toInt(), 0xFF2FD4A0.toInt(), 0xFF3A1A8A.toInt()),
            -1 to intArrayOf(0xFFC8FFF6.toInt(), 0xFF2EC4C0.toInt(), 0xFF0B3A66.toInt())
        )
        private val glows = intArrayOf(
            0xFFFF4FA3.toInt(), 0xFF35E0E8.toInt(), 0xFFFFB020.toInt(), 0xFFFF5A1F.toInt(), 0xFFB58CFF.toInt(),
            0xFF9FE8FF.toInt(), 0xFFB58CFF.toInt(), 0xFF5CE1FF.toInt(), 0xFFFFB020.toInt(), 0xFFFFFFFF.toInt(),
            0xFF35E0E8.toInt(), 0xFFE0B070.toInt(), 0xFFFF8AE0.toInt(), 0xFFA070FF.toInt(), 0xFFC0C8E0.toInt(),
            0xFFFF9AD0.toInt(), 0xFFFFD040.toInt(), 0xFF3DFFB0.toInt()
        )
        private val LISTEN_GLOW = 0xFF5CF2E0.toInt()
        private val texCache = HashMap<Int, Bitmap>()

        private fun lerpC(a: Int, b: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
            )
        }

        private fun tex(id: Int): Bitmap = texCache.getOrPut(id) { makeTex(pal[id] ?: pal[0]!!, id + 11) }

        private fun makeTex(cols: IntArray, seed: Int): Bitmap {
            val n = 192
            val px = IntArray(n * n)
            val rr = java.util.Random(seed.toLong())
            val grids = Array(4) { o -> val g = 4 shl o; Array(g) { FloatArray(g) { rr.nextFloat() } } }
            for (y in 0 until n) for (x in 0 until n) {
                var v = 0f; var amp = 0.5f; var tot = 0f
                for (o in 0 until 4) {
                    val g = 4 shl o
                    val fx = x.toFloat() / n * g; val fy = y.toFloat() / n * g
                    val x0 = fx.toInt(); val y0 = fy.toInt()
                    val tx = fx - x0; val ty = fy - y0
                    val sx = tx * tx * (3f - 2f * tx); val sy = ty * ty * (3f - 2f * ty)
                    val a = grids[o][y0 % g][x0 % g]; val b = grids[o][y0 % g][(x0 + 1) % g]
                    val c = grids[o][(y0 + 1) % g][x0 % g]; val d = grids[o][(y0 + 1) % g][(x0 + 1) % g]
                    val top = a + (b - a) * sx; val bot = c + (d - c) * sx
                    v += (top + (bot - top) * sy) * amp; tot += amp; amp *= 0.5f
                }
                v /= tot
                val k = ((v - 0.28f) * 2.1f).coerceIn(0f, 1f)
                px[y * n + x] = if (k < 0.5f) lerpC(cols[2], cols[1], k * 2f) else lerpC(cols[1], cols[0], (k - 0.5f) * 2f)
            }
            return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        }
    }

    private fun withA(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private fun disc(cv: Canvas, x: Float, y: Float, r: Float, col: Int) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = col
        cv.drawCircle(x, y, r, p)
    }

    private fun shade(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float, glow: Int) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.35f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(190, 0, 0, 0)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        val hx = cx - r * 0.38f + r * 0.1f * sin(t * 0.7f); val hy = cy - r * 0.38f
        p.shader = RadialGradient(hx, hy, r * 0.45f,
            intArrayOf(Color.argb(235, 255, 255, 255), Color.argb(70, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(hx, hy, r * 0.45f, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.03f; p.color = withA(glow, 130)
        cv.drawCircle(cx, cy, r * 0.985f, p)
        p.style = Paint.Style.FILL
    }

    private fun planet(cv: Canvas, cx: Float, cy: Float, r: Float, id: Int, st: Int, t: Float, glow: Int) {
        val bmp = tex(id)
        val sh = shaders.getOrPut(id) { BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        val sc = r * 2.4f / bmp.width
        val spd = if (state == 2) 0.5f else if (state == 1) 0.3f else 0.12f
        p.style = Paint.Style.FILL
        mx.reset(); mx.setScale(sc, sc)
        mx.postTranslate(cx - r * 1.2f + t * spd * r * 2f, cy - r * 1.2f + sin(t * 0.6f) * r * 0.08f)
        sh.setLocalMatrix(mx); p.shader = sh; p.alpha = 255
        cv.drawCircle(cx, cy, r, p)
        mx.reset(); mx.setScale(sc * 1.5f, sc * 1.5f)
        mx.postTranslate(cx - r * 0.9f - t * spd * r * 1.2f, cy - r * 1.4f + cos(t * 0.5f) * r * 0.1f)
        sh.setLocalMatrix(mx); p.alpha = 90
        cv.drawCircle(cx, cy, r, p)
        p.shader = null; p.alpha = 255
        if (st == 14) {
            p.style = Paint.Style.STROKE
            for (i in 0 until 8) {
                val d = dots[i]; val rad = d[1] * r * 0.7f
                val x = cx + cos(d[0]) * rad; val y = cy + sin(d[0]) * rad; val cr = r * (0.06f + 0.08f * (d[2] / TAU))
                p.strokeWidth = r * 0.02f; p.color = Color.argb(110, 40, 40, 55)
                cv.drawCircle(x, y, cr, p)
                p.color = Color.argb(70, 255, 255, 255)
                cv.drawCircle(x - cr * 0.15f, y - cr * 0.15f, cr, p)
            }
        }
        shade(cv, cx, cy, r, t, glow)
    }

    private fun galaxy(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float, glow: Int) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.rgb(60, 28, 130), Color.rgb(6, 3, 24)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        for (i in dots.indices) {
            val d = dots[i]; val f = d[1]
            val ang = (i % 3) * 2.094f + f * 5.5f + d[2] * 0.04f + t * 0.3f * (1.2f - f)
            val rad = f * r * 0.95f
            val col = when (i % 3) { 0 -> Color.WHITE; 1 -> Color.rgb(200, 160, 255); else -> Color.rgb(255, 170, 220) }
            p.shader = null; p.color = withA(col, 130 + (i % 5) * 25)
            cv.drawCircle(cx + cos(ang) * rad, cy + sin(ang) * rad, r * (0.008f + 0.012f * (d[2] / TAU)), p)
        }
        p.shader = RadialGradient(cx, cy, r * 0.4f, intArrayOf(Color.argb(230, 255, 240, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 0.4f, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.03f; p.color = withA(glow, 120)
        cv.drawCircle(cx, cy, r * 0.985f, p)
        p.style = Paint.Style.FILL
    }

    private fun soundRing(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        val amp = if (state == 0) 0.06f else if (state == 1) 0.3f else 0.42f
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = r * 0.045f
        val n = 72
        for (i in 0 until n) {
            val ang = i * TAU / n
            val l = r * (0.05f + amp * (0.5f + 0.5f * sin(t * 7f + i * 0.55f)) * (0.6f + 0.4f * sin(t * 3.3f + i * 0.2f)))
            p.color = lerpC(0xFF5CE1FF.toInt(), 0xFF9B5CFF.toInt(), abs(sin(ang + t * 0.5f)))
            cv.drawLine(cx + cos(ang) * r, cy + sin(ang) * r, cx + cos(ang) * (r + l), cy + sin(ang) * (r + l), p)
        }
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - r * 0.2f, cy - r * 0.25f, r * 1.2f, intArrayOf(Color.rgb(120, 70, 210), Color.rgb(30, 14, 70), Color.rgb(8, 4, 24)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 0.88f, p)
        p.shader = null
    }

    private fun blackHole(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        val sw = SweepGradient(cx, cy, intArrayOf(0xFFFFE9B0.toInt(), 0xFFFF8A00.toInt(), 0xFF7A1A00.toInt(), 0xFFFFB347.toInt(), 0xFFFFE9B0.toInt()), null)
        mx.reset(); mx.setRotate(t * 60f, cx, cy); sw.setLocalMatrix(mx)
        cv.save(); cv.rotate(-14f, cx, cy)
        p.style = Paint.Style.STROKE; p.shader = sw
        for (k in 0..2) {
            val s = 1.0f + k * 0.12f
            rect.set(cx - r * 1.3f * s, cy - r * 0.34f * s, cx + r * 1.3f * s, cy + r * 0.34f * s)
            p.strokeWidth = r * (0.16f - k * 0.04f); p.alpha = 255 - k * 70
            cv.drawArc(rect, 180f, 180f, false, p)
        }
        p.shader = null; p.alpha = 255
        disc(cv, cx, cy, r * 0.62f, Color.BLACK)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.025f; p.color = Color.argb(200, 255, 190, 110)
        cv.drawCircle(cx, cy, r * 0.65f, p)
        p.shader = sw
        for (k in 0..2) {
            val s = 1.0f + k * 0.12f
            rect.set(cx - r * 1.3f * s, cy - r * 0.34f * s, cx + r * 1.3f * s, cy + r * 0.34f * s)
            p.strokeWidth = r * (0.16f - k * 0.04f); p.alpha = 255 - k * 70
            cv.drawArc(rect, 0f, 180f, false, p)
        }
        p.shader = null; p.alpha = 255
        cv.restore()
        p.style = Paint.Style.FILL
    }

    private fun eclipse(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 1.75f,
            intArrayOf(Color.WHITE, Color.WHITE, Color.argb(190, 255, 238, 214), Color.argb(50, 255, 200, 150), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 0.62f, 0.8f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.75f, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.012f
        for (i in 0 until 48) {
            val ang = i * TAU / 48
            val l = r * (0.12f + 0.2f * abs(sin(t * 0.9f + i * 1.7f)))
            p.color = Color.argb(70 + (i % 4) * 25, 255, 245, 230)
            cv.drawLine(cx + cos(ang) * r * 1.02f, cy + sin(ang) * r * 1.02f, cx + cos(ang) * (r * 1.02f + l), cy + sin(ang) * (r * 1.02f + l), p)
        }
        disc(cv, cx, cy, r * 0.98f, Color.BLACK)
    }

    private fun hologram(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        val cyan = 0xFF35E0E8.toInt()
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.argb(10, 53, 224, 232), Color.argb(80, 53, 224, 232)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.018f; p.color = cyan
        cv.drawCircle(cx, cy, r, p)
        for (k in 0 until 6) {
            val w = abs(cos(t * 0.5f + k * 0.5236f)) * r
            rect.set(cx - w, cy - r, cx + w, cy + r)
            cv.drawOval(rect, p)
        }
        for (k in -2..2) {
            val y = k * r * 0.42f
            val half = sqrt(max(r * r - y * y, 1f))
            rect.set(cx - half, cy + y - half * 0.16f, cx + half, cy + y + half * 0.16f)
            cv.drawOval(rect, p)
        }
        val yy = sin(t * 1.5f) * r * 0.9f
        val hw = sqrt(max(r * r - yy * yy, 1f))
        p.strokeWidth = r * 0.04f; p.color = Color.argb(110, 200, 255, 255)
        cv.drawLine(cx - hw, cy + yy, cx + hw, cy + yy, p)
        p.style = Paint.Style.FILL
    }

    private fun saturn(cv: Canvas, cx: Float, cy: Float, r: Float) {
        val rb = r * 0.62f
        cv.save(); cv.rotate(-20f, cx, cy)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.BUTT
        for (k in 0..2) {
            val s = 1.25f + k * 0.14f
            rect.set(cx - r * s, cy - r * s * 0.3f, cx + r * s, cy + r * s * 0.3f)
            p.strokeWidth = r * (0.09f - k * 0.02f)
            p.color = if (k == 1) Color.argb(150, 120, 90, 60) else Color.argb(210, 214, 184, 140)
            cv.drawArc(rect, 180f, 180f, false, p)
        }
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx, cy - rb, cx, cy + rb,
            intArrayOf(0xFFE8D4A8.toInt(), 0xFFB8895A.toInt(), 0xFFEBD9B0.toInt(), 0xFFA9774A.toInt(), 0xFFE2C998.toInt(), 0xFF8E6038.toInt(), 0xFFD6B885.toInt()),
            floatArrayOf(0f, 0.17f, 0.33f, 0.5f, 0.67f, 0.83f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, rb, p)
        p.shader = RadialGradient(cx - rb * 0.3f, cy - rb * 0.35f, rb * 1.5f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(205, 0, 0, 0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, rb, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.BUTT
        for (k in 0..2) {
            val s = 1.25f + k * 0.14f
            rect.set(cx - r * s, cy - r * s * 0.3f, cx + r * s, cy + r * s * 0.3f)
            p.strokeWidth = r * (0.09f - k * 0.02f)
            p.color = if (k == 1) Color.argb(150, 120, 90, 60) else Color.argb(235, 226, 196, 150)
            cv.drawArc(rect, 0f, 180f, false, p)
        }
        cv.restore()
        p.style = Paint.Style.FILL
    }

    private fun bubble(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.argb(8, 255, 255, 255), Color.argb(30, 255, 255, 255), Color.argb(90, 255, 140, 230)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        val sw = SweepGradient(cx, cy, intArrayOf(0xFFFF6BD6.toInt(), 0xFF7C5CFF.toInt(), 0xFF5CE1FF.toInt(), 0xFFFFE066.toInt(), 0xFFFF6BD6.toInt()), null)
        mx.reset(); mx.setRotate(t * 30f, cx, cy); sw.setLocalMatrix(mx)
        p.shader = sw; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.07f; p.alpha = 210
        cv.drawCircle(cx, cy, r * 0.97f, p)
        p.shader = null; p.alpha = 255; p.style = Paint.Style.FILL
        disc(cv, cx - r * 0.38f, cy - r * 0.4f, r * 0.1f, Color.argb(230, 255, 255, 255))
        disc(cv, cx + r * 0.42f, cy + r * 0.45f, r * 0.05f, Color.argb(150, 255, 255, 255))
    }

    private fun tesla(cv: Canvas, cx: Float, cy: Float, r: Float, glow: Int) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.rgb(40, 18, 90), Color.rgb(8, 3, 22)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        val now = System.currentTimeMillis()
        if (now - boltT > (if (state == 0) 260L else 90L)) {
            boltT = now; bolts.clear()
            val n = if (state == 0) 3 else 6
            for (b in 0 until n) {
                val base = rnd.nextFloat() * TAU
                bolts.add(FloatArray(10) { base + (rnd.nextFloat() - 0.5f) * 0.55f })
            }
        }
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        for (a in bolts) {
            val path = Path(); path.moveTo(cx, cy)
            for (k in a.indices) {
                val rad = r * 0.97f * (k + 1) / a.size
                path.lineTo(cx + cos(a[k]) * rad, cy + sin(a[k]) * rad)
            }
            p.strokeWidth = r * 0.07f; p.color = Color.argb(90, 160, 110, 255); cv.drawPath(path, p)
            p.strokeWidth = r * 0.022f; p.color = Color.WHITE; cv.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 0.35f, intArrayOf(Color.argb(240, 255, 255, 255), Color.argb(120, 170, 120, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 0.35f, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.03f; p.color = withA(glow, 150)
        cv.drawCircle(cx, cy, r * 0.985f, p)
        p.style = Paint.Style.FILL
    }

    private fun fireflies(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float, glow: Int) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.rgb(30, 36, 16), Color.rgb(8, 10, 4)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p)
        for (i in 0 until 60) {
            val d = dots[i]
            val ang = d[0] + t * 0.1f * (1f + d[1]) + sin(t * 0.7f + d[2]) * 0.1f
            val rad = d[1] * r * 0.9f
            val a = 0.5f + 0.5f * sin(t * 2f + d[2] * 5f)
            val x = cx + cos(ang) * rad; val y = cy + sin(ang) * rad
            p.shader = null; p.color = Color.argb((a * 70).toInt(), 255, 224, 102)
            cv.drawCircle(x, y, r * 0.05f, p)
            p.color = Color.argb((a * 255).toInt(), 255, 236, 140)
            cv.drawCircle(x, y, r * 0.014f, p)
        }
        shade(cv, cx, cy, r, t, glow)
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val t = (System.currentTimeMillis() % 100000000L) / 1000f
        val sp = if (state == 1) 5f else if (state == 2) 8f else 1.5f
        val a = (if (state == 0) 0.02f else 0.06f) * (1f + sin(t * sp))
        val r = min(cx, cy) * 0.62f * (1f + a)
        val st = style % names.size
        val glow = if (state == 1) LISTEN_GLOW else glows[st]
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 1.75f, intArrayOf(withA(glow, if (state == 0) 90 else 150), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.75f, p)
        p.shader = null
        when (st) {
            6 -> galaxy(cv, cx, cy, r, t, glow)
            7 -> soundRing(cv, cx, cy, r, t)
            8 -> blackHole(cv, cx, cy, r, t)
            9 -> eclipse(cv, cx, cy, r, t)
            10 -> hologram(cv, cx, cy, r, t)
            11 -> saturn(cv, cx, cy, r)
            12 -> bubble(cv, cx, cy, r, t)
            13 -> tesla(cv, cx, cy, r, glow)
            16 -> fireflies(cv, cx, cy, r, t, glow)
            else -> planet(cv, cx, cy, r, if (state == 1 && st in TEXS) -1 else st, st, t, glow)
        }
        p.style = Paint.Style.FILL; p.shader = null
        if (width < 400) postInvalidateDelayed(66) else postInvalidateOnAnimation()
    }
}
