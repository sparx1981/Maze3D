package com.maze3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Draws the steering stick wherever the finger went down in chase-camera mode. Does not take touches itself. */
class JoystickView(context: Context) : View(context) {
    private var active = false
    private var cx = 0f; private var cy = 0f; private var kx = 0f; private var ky = 0f
    val radius = 70f * resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * resources.displayMetrics.density; color = 0x66FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB0FFFFFF.toInt() }

    fun show(x: Float, y: Float) { active = true; cx = x; cy = y; kx = x; ky = y; invalidate() }

    /** Moves the knob, limited to the ring. */
    fun move(x: Float, y: Float) {
        var dx = x - cx; var dy = y - cy
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len > radius) { dx = dx / len * radius; dy = dy / len * radius }
        kx = cx + dx; ky = cy + dy
        invalidate()
    }

    fun hide() { active = false; invalidate() }

    override fun onDraw(c: Canvas) {
        if (!active) return
        c.drawCircle(cx, cy, radius, fill)
        c.drawCircle(cx, cy, radius, ring)
        c.drawCircle(kx, ky, radius * 0.38f, knob)
    }
}
