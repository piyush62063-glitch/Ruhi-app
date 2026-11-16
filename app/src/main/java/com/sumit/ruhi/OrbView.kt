package com.sumit.ruhi

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.view.View
import kotlin.math.*

object Clk {
    // small, always-moving seconds counter (System.currentTimeMillis()/1000f loses precision in Float)
    fun t(): Float = (SystemClock.uptimeMillis() % 1000000L) / 1000f
}

@android.annotation.TargetApi(33)
class PlasmaShader {
    private val s = RuntimeShader(SRC)
    private val paint = Paint()
    init { paint.shader = s }

    fun draw(cv: Canvas, w: Float, h: Float, t: Float, level: Float, rad: Float, a: Int, b: Int) {
        s.setFloatUniform("res", w, h)
        s.setFloatUniform("time", t)
        s.setFloatUniform("level", level)
        s.setFloatUniform("rad", rad)
        s.setFloatUniform("c0", Color.red(a) / 255f, Color.green(a) / 255f, Color.blue(a) / 255f)
        s.setFloatUniform("c1", Color.red(b) / 255f, Color.green(b) / 255f, Color.blue(b) / 255f)
        cv.drawRect(0f, 0f, w, h, paint)
    }

    companion object {
        const val SRC = """
uniform float2 res;
uniform float time;
uniform float level;
uniform float rad;
uniform float3 c0;
uniform float3 c1;

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 frag) {
    float2 uv = (frag - 0.5 * res) / (0.5 * min(res.x, res.y));
    float r = length(uv);
    float R = rad;
    if (r > R) {
        float g = exp(-(r - R) * 5.5) * (0.45 + 0.55 * level);
        float3 gc = mix(c0, float3(1.0, 1.0, 1.0), 0.15) * g;
        return half4(half3(gc), half(g));
    }
    float z = sqrt(max(R * R - r * r, 0.0));
    float3 n = float3(uv.x, uv.y, z) / R;
    float2 p = n.xy * 2.1 + float2(time * 0.10, -time * 0.07);
    float sp = 0.18 + 0.5 * level;
    float2 q = float2(fbm(p + time * sp), fbm(p + float2(5.2, 1.3) - time * sp));
    float f = fbm(p + (1.4 + 2.2 * level) * q);
    float3 base = mix(c1, c0, smoothstep(0.25, 0.85, f));
    base = mix(base, float3(1.0, 1.0, 1.0), pow(clamp(f, 0.0, 1.0), 3.0) * 0.55);
    float3 L = normalize(float3(-0.45, -0.6, 0.75));
    float diff = clamp(dot(n, L), 0.0, 1.0);
    float rim = pow(1.0 - clamp(n.z, 0.0, 1.0), 2.4);
    float3 refl = reflect(-L, n);
    float spec = pow(clamp(refl.z, 0.0, 1.0), 26.0);
    float3 col = base * (0.30 + 0.95 * diff) + c0 * rim * (0.9 + 0.8 * level) + float3(1.0, 1.0, 1.0) * spec * 0.9;
    float edge = smoothstep(R, R - 0.012, r);
    return half4(half3(col * edge), half(edge));
}
"""
    }
}

class OrbView(c: Context) : View(c) {
    var style = 0
    var state = 0 // 0 idle, 1 listening, 2 speaking
    var rms = 0f  // 0..1 live mic level
    private var lvl = 0f
    private var sh: PlasmaShader? = null
    private var shFail = false
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

    private fun bars(cv: Canvas, cx: Float, cy: Float, r: Float, t: Float, c0: Int, c1: Int) {
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.05f; p.strokeCap = Paint.Cap.ROUND
        val amp = if (state == 0) 0.05f else 0.12f + 0.3f * lvl
        for (i in 0 until 60) {
            val ang = i * 6.2832f / 60; val l = r * (0.08f + amp * abs(sin(t * 6 + i * 0.7f)))
            p.color = if (i % 2 == 0) c0 else c1
            cv.drawLine(cx + cos(ang) * r * 1.02f, cy + sin(ang) * r * 1.02f, cx + cos(ang) * (r * 1.02f + l), cy + sin(ang) * (r * 1.02f + l), p)
        }
        p.style = Paint.Style.FILL
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val t = Clk.t()
        val target = when (state) {
            1 -> max(rms, 0.28f + 0.1f * sin(t * 4f))
            2 -> 0.45f + 0.35f * abs(sin(t * 7f) * sin(t * 3.1f))
            else -> 0.08f
        }
        lvl += (target - lvl) * 0.14f
        val pulse = (if (state == 0) 0.018f else 0.03f) * sin(t * (if (state == 0) 1.5f else 3.2f)) + lvl * 0.05f
        val cc = (if (state == 1) LISTEN else cols[style % cols.size]).split(",").map { Color.parseColor(it) }

        if (style <= 7 && Build.VERSION.SDK_INT >= 33 && !shFail) {
            try {
                val s = sh ?: PlasmaShader().also { sh = it }
                val rad = 0.62f + pulse
                s.draw(cv, width.toFloat(), height.toFloat(), t, lvl, rad, cc[0], cc[1])
                if (style == 7) bars(cv, cx, cy, min(cx, cy) * rad, t, cc[0], cc[1])
                postInvalidateOnAnimation()
                return
            } catch (e: Throwable) { shFail = true }
        }

        val r = min(cx, cy) * 0.62f * (1f + pulse * 1.6f)
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
                bars(cv, cx, cy, r, t, cc[0], cc[1])
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

class StarsView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val s = List(80) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.6f + Math.random().toFloat() * 1.6f, (Math.random() * 6.28).toFloat(), 0.4f + Math.random().toFloat() * 1.3f) }
    override fun onDraw(cv: Canvas) {
        val t = Clk.t(); val d = resources.displayMetrics.density
        for (a in s) {
            var y = (a[1] - t * 0.004f * a[2]) % 1f; if (y < 0f) y += 1f
            val tw = 0.5f + 0.5f * sin(t * a[4] + a[3])
            p.color = Color.argb((60 + 150 * tw).toInt(), 255, 255, 255)
            cv.drawCircle(a[0] * width, y * height, a[2] * d * 0.9f, p)
        }
        postInvalidateOnAnimation()
    }
}
