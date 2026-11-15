package com.maze3d

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated loading screen: a golden thread draws itself around a maze-carved sphere while a marble
 * rolls at its head, then the name fades in. Drawn entirely with Canvas so it shows instantly.
 */
class LoadingView(context: Context, private val title: String = "ROLLABYRINTH", private val subtitle: String = "3D MARBLE MAZE") : View(context) {

    /** Animation clock in seconds. Uses the wall clock, so it still plays at full length when the system's animation scale is off. */
    private var t = 0f
    private var startNanos = 0L
    private var running = false

    // maze-like lattice drawn on the sphere: (lat0, lon0, lat1, lon1) in degrees
    private val segs: Array<FloatArray>

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    init {
        var seed = 7L
        fun rnd(): Float { seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL; return seed / 4294967296f }
        val list = ArrayList<FloatArray>()
        val la = 14; val lo = 28
        for (i in 0 until la) for (j in 0 until lo) {
            val la0 = -80f + 160f * i / la; val la1 = -80f + 160f * (i + 1) / la
            val lo0 = 360f * j / lo; val lo1 = 360f * (j + 1) / lo
            if (rnd() < 0.6f) list.add(floatArrayOf(la0, lo0, la0, lo1))
            if (rnd() < 0.55f) list.add(floatArrayOf(la0, lo0, la1, lo0))
        }
        segs = list.toTypedArray()
        isClickable = true   // swallow touches while loading
    }

    /** Seconds the animation has been running. */
    val seconds get() = t

    override fun onAttachedToWindow() { super.onAttachedToWindow(); startNanos = System.nanoTime(); running = true; postInvalidateOnAnimation() }
    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }

    private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }
    private fun easeOut(x: Float): Float { val c = x.coerceIn(0f, 1f); return 1 - (1 - c) * (1 - c) * (1 - c) }

    private fun proj(lat: Float, lon: Float, rot: Float, tilt: Float, out: FloatArray) {
        val a = Math.toRadians(lat.toDouble()); val b = Math.toRadians((lon + rot).toDouble())
        val x = cos(a) * sin(b); val y = -sin(a); val z = cos(a) * cos(b)
        val ct = cos(tilt.toDouble()); val st = sin(tilt.toDouble())
        out[0] = x.toFloat(); out[1] = (y * ct - z * st).toFloat(); out[2] = (y * st + z * ct).toFloat()
    }

    override fun onDraw(c: Canvas) {
        t = (System.nanoTime() - startNanos) / 1e9f
        if (running) postInvalidateOnAnimation()
        val w = width.toFloat(); val h = height.toFloat()
        // background (reset the paint first: a leftover colour alpha would make the whole screen translucent)
        p.style = Paint.Style.FILL; p.color = Color.WHITE; p.alpha = 255; p.xfermode = null
        p.shader = LinearGradient(0f, 0f, 0f, h, Color.rgb(16, 26, 51), Color.rgb(4, 6, 12), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.color = Color.WHITE; p.alpha = 255
        p.shader = RadialGradient(w / 2, h * 0.45f, h * 0.75f, intArrayOf(Color.TRANSPARENT, Color.argb(150, 0, 0, 0)), floatArrayOf(0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val cx = w / 2; val cy = h * 0.42f
        val R = min(w * 0.32f, h * 0.2f)
        val rot = t * 55f; val tilt = 0.35f
        val grow = easeOut(t / 0.9f)

        // glow
        p.color = Color.WHITE; p.alpha = 255
        p.shader = RadialGradient(cx, cy, R * 1.9f * grow + 1f, intArrayOf(Color.argb(60, 80, 160, 255), Color.TRANSPARENT), floatArrayOf(R * 0.8f / (R * 1.9f), 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, R * 1.9f * grow + 1f, p)
        p.shader = null

        c.save()
        c.translate(cx, cy); c.scale(grow, grow)
        // sphere body
        p.color = Color.WHITE; p.alpha = 255
        p.shader = RadialGradient(-R * 0.35f, -R * 0.4f, R * 1.3f, intArrayOf(Color.rgb(61, 106, 138), Color.rgb(20, 40, 61), Color.rgb(6, 11, 20)), floatArrayOf(0.1f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, R, p)
        p.shader = null

        // maze lattice (front-facing parts only)
        p.style = Paint.Style.STROKE; p.strokeWidth = R * 0.016f; p.strokeCap = Paint.Cap.ROUND
        val o = FloatArray(3)
        for (s in segs) {
            val n = if (s[1] != s[3]) 4 else 2
            path.rewind()
            var on = false; var zmax = -1f
            for (k in 0..n) {
                val f = k / n.toFloat()
                proj(s[0] + (s[2] - s[0]) * f, s[1] + (s[3] - s[1]) * f, rot, tilt, o)
                if (o[2] > 0f) { if (on) path.lineTo(o[0] * R, o[1] * R) else path.moveTo(o[0] * R, o[1] * R); on = true; zmax = max(zmax, o[2]) } else on = false
            }
            if (zmax > 0f) {
                p.color = Color.argb(((0.25f + 0.55f * zmax) * 255).toInt(), 160 + (60 * zmax).toInt(), 190 + (40 * zmax).toInt(), 255)
                c.drawPath(path, p)
            }
        }

        // golden thread spiralling from the south pole to the north pole
        val prog = ease((t - 0.5f) / 2.2f)
        val steps = (260 * prog).toInt()
        var hx = 0f; var hy = 0f; var hz = -1f; var haveHead = false
        for (layer in 0..2) {
            val width = R * floatArrayOf(0.05f, 0.028f, 0.015f)[layer]
            val alpha = floatArrayOf(0.22f, 0.55f, 1f)[layer]
            p.strokeWidth = width
            path.rewind(); var on = false
            for (k in 0..steps) {
                val f = k / 260f
                proj(-78f + 156f * f, f * 1080f, rot, tilt, o)
                if (o[2] > 0.03f) { if (on) path.lineTo(o[0] * R, o[1] * R) else path.moveTo(o[0] * R, o[1] * R); on = true } else on = false
                if (layer == 0 && k == steps) { hx = o[0] * R; hy = o[1] * R; hz = o[2]; haveHead = true }
            }
            p.color = Color.argb((alpha * 255).toInt(), 255, 205 + layer * 12, 95 + layer * 35)
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL

        // marble at the head of the thread
        if (haveHead && steps > 0) {
            val mr = R * 0.085f
            p.color = Color.WHITE; p.alpha = if (hz > 0f) 255 else 80
            p.shader = RadialGradient(hx - mr * 0.35f, hy - mr * 0.4f, mr * 1.4f,
                intArrayOf(Color.WHITE, Color.rgb(207, 220, 240), Color.rgb(58, 70, 96), Color.rgb(159, 180, 214)),
                floatArrayOf(0f, 0.35f, 0.8f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(hx, hy, mr, p)
            p.shader = null; p.alpha = 255
        }
        c.restore()

        // rim light
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Color.argb(90, 140, 200, 255)
        c.drawArc(cx - R * grow, cy - R * grow, cx + R * grow, cy + R * grow, 190f, 100f, false, p)
        p.style = Paint.Style.FILL

        // title
        val ta = ease((t - 2.0f) / 0.8f)
        if (ta > 0f) {
            text.textSize = min(w * 0.082f, 46f * resources.displayMetrics.density)
            text.letterSpacing = 0.28f
            text.alpha = (ta * 255).toInt()
            text.setShadowLayer(18f, 0f, 0f, Color.argb((ta * 150).toInt(), 120, 200, 255))
            c.drawText(title, cx + text.textSize * 0.14f, h * 0.7f, text)
            text.setShadowLayer(0f, 0f, 0f, 0)
            text.textSize = text.textSize * 0.28f
            text.letterSpacing = 0.4f
            text.alpha = (ta * 150).toInt()
            c.drawText(subtitle, cx + text.textSize * 0.2f, h * 0.7f + text.textSize * 2.8f, text)
        }

        // progress line
        p.style = Paint.Style.FILL
        val pw = w * 0.42f; val px = (w - pw) / 2; val py = h * 0.9f
        p.color = Color.argb(40, 255, 255, 255)
        c.drawRect(px, py, px + pw, py + 3f * resources.displayMetrics.density, p)
        p.color = Color.WHITE; p.alpha = 255
        p.shader = LinearGradient(px, 0f, px + pw, 0f, Color.rgb(88, 214, 255), Color.rgb(255, 211, 106), Shader.TileMode.CLAMP)
        c.drawRect(px, py, px + pw * (t / 2.9f).coerceIn(0f, 1f), py + 3f * resources.displayMetrics.density, p)
        p.shader = null
    }
}
