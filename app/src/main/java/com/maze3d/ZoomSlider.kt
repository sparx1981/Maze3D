package com.maze3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Slim vertical slider: slide up to zoom in, down to zoom out. Value is 0..1. */
class ZoomSlider(context: Context, private val onChange: (Float) -> Unit) : View(context) {
    var value = 0.67f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val thumbBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2F3A.toInt() }
    private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND }
    private val pad = 22f * d

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val top = pad; val bottom = height - pad
        val w = 3f * d
        c.drawRoundRect(RectF(cx - w, top, cx + w, bottom), w, w, track)
        val y = bottom - (bottom - top) * value
        c.drawRoundRect(RectF(cx - w, y, cx + w, bottom), w, w, fill)
        c.drawCircle(cx, y, 13f * d, fill)
        c.drawCircle(cx, y, 10f * d, thumbBg)
        c.drawLine(cx - 4f * d, y, cx + 4f * d, y, plus)
        c.drawLine(cx, y - 4f * d, cx, y + 4f * d, plus)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> Unit
            else -> return true
        }
        val top = pad; val bottom = height - pad
        value = 1f - ((e.y - top) / (bottom - top)).coerceIn(0f, 1f)
        onChange(value)
        return true
    }
}
