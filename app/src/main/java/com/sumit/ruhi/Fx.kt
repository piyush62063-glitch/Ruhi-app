package com.sumit.ruhi

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class StarView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dens = c.resources.displayMetrics.density
    private val s = List(70) {
        floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), (Math.random() * 6.28).toFloat(), 0.6f + Math.random().toFloat() * 1.8f)
    }

    override fun onDraw(cv: Canvas) {
        val t = (System.currentTimeMillis() % 100000000L) / 1000f
        p.style = Paint.Style.FILL
        for (d in s) {
            val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * d[3] + d[2]))
            p.color = Color.argb((a * 200).toInt(), 255, 255, 255)
            cv.drawCircle(d[0] * width, d[1] * height, d[3] * dens * 0.8f, p)
        }
        postInvalidateDelayed(50)
    }
}

class SparkleView(c: Context, private val st: () -> Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dens = c.resources.displayMetrics.density
    private val s = List(26) {
        floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.04f + Math.random().toFloat() * 0.07f, 1.5f + Math.random().toFloat() * 3f, (Math.random() * 3).toFloat())
    }

    override fun onDraw(cv: Canvas) {
        val t = (System.currentTimeMillis() % 100000000L) / 1000f
        val mult = if (st() == 2) 2.4f else if (st() == 1) 1.5f else 1f
        for (d in s) {
            val prog = (t * d[2] * mult + d[1]) % 1f
            val x = d[0] * width + sin(t * 0.8f + d[1] * 6f) * dens * 14f
            val y = height * (1f - prog)
            val fade = sin(prog * 3.1416f)
            val rad = d[3] * dens
            val col = when (d[4].toInt()) { 0 -> intArrayOf(255, 150, 220); 1 -> intArrayOf(190, 160, 255); else -> intArrayOf(255, 255, 255) }
            p.style = Paint.Style.FILL
            p.color = Color.argb((fade * 55).toInt(), col[0], col[1], col[2])
            cv.drawCircle(x, y, rad * 2.6f, p)
            p.color = Color.argb((fade * 220).toInt(), col[0], col[1], col[2])
            cv.drawCircle(x, y, rad * 0.7f, p)
            if (d[3] > 3f) {
                p.style = Paint.Style.STROKE; p.strokeWidth = dens * 1.2f
                cv.drawLine(x - rad * 2.4f, y, x + rad * 2.4f, y, p)
                cv.drawLine(x, y - rad * 2.4f, x, y + rad * 2.4f, p)
            }
        }
        postInvalidateOnAnimation()
    }
}
