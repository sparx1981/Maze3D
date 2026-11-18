package com.maze3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** "Find ball": rings that pulse outward from the ball's position on screen for a few seconds. Touches pass through. */
class PulseView(context: Context, private val anchor: () -> Triple<Float, Float, Boolean>, private val colour: () -> Int) : View(context) {
    private var start = 0L
    private var running = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun start() { start = System.nanoTime(); running = true; invalidate() }

    override fun onDraw(c: Canvas) {
        if (!running) return
        val t = (System.nanoTime() - start) / 1e9f
        if (t > 3.6f) { running = false; return }
        val (nx, ny, on) = anchor()
        if (!on) { postInvalidateOnAnimation(); return }
        val x = nx * width; val y = ny * height
        val d = resources.displayMetrics.density
        val base = colour()
        val fade = min(1f, (3.6f - t) / 0.8f)
        // a steady halo that breathes, plus three rings expanding in turn
        val breathe = 0.5f + 0.5f * kotlin.math.sin(t * 7f)
        fill.color = Color.argb((60 * fade * (0.5f + 0.5f * breathe)).toInt(), Color.red(base), Color.green(base), Color.blue(base))
        c.drawCircle(x, y, 26f * d, fill)
        for (k in 0 until 3) {
            val f = ((t * 0.85f) + k / 3f) % 1f
            val r = (14f + 120f * f) * d
            p.strokeWidth = (5f * (1f - f) + 1.5f) * d
            p.color = Color.argb((235 * (1f - f) * fade).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawCircle(x, y, r, p)
            p.strokeWidth = (3f * (1f - f) + 1f) * d
            p.color = Color.argb((200 * (1f - f) * fade).toInt().coerceIn(0, 255), Color.red(base), Color.green(base), Color.blue(base))
            c.drawCircle(x, y, r + 5f * d, p)
        }
        postInvalidateOnAnimation()
    }
}
