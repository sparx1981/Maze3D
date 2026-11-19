package com.maze3d.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Orbit-follow camera: which side of the piece should face the viewer so the ball is seen well.
 * On convex shapes that is simply where the ball is; on the inside of a doughnut the ball faces the hole and the far side of
 * the ring would hide it, so the view tilts up over the near rim instead.
 */
object FollowView {
    private val candidates: List<Vec3> = (0 until 72).map { i ->
        val y = 1f - 2f * (i + 0.5f) / 72f
        val r = sqrt(1f - y * y)
        val a = i * 2.3999632f
        Vec3(cos(a) * r, y, sin(a) * r)
    }

    /** True when nothing solid lies between the ball and a viewer far out along [d]. */
    fun clear(s: Surface, p: Vec3, n: Vec3, d: Vec3): Boolean {
        val start = p + n * (s.radius * 0.03f)
        for (t in floatArrayOf(0.12f, 0.25f, 0.4f, 0.6f, 0.9f, 1.3f, 1.9f)) {
            val q = start + d * t
            if ((q - s.core(q)).length() < s.radius * 0.985f) return false
        }
        return true
    }

    private fun score(s: Surface, p: Vec3, n: Vec3, d: Vec3, cur: Vec3): Float {
        val c = n dot d
        if (c < 0.22f || !clear(s, p, n, d)) return -1e9f
        val lat = (p - d * (p dot d)).length()
        return c - 0.3f * lat + 0.5f * (d dot cur)
    }

    fun pick(s: Surface, p: Vec3, n: Vec3, cur: Vec3): Vec3 {
        var best = n; var bs = score(s, p, n, n, cur)
        val pool = candidates + listOf(cur, p.normalized(), (p.normalized() + n).normalized())
        for (d in pool) { val sc = score(s, p, n, d, cur); if (sc > bs) { bs = sc; best = d } }
        if (bs <= -1e8f) return n
        // refine by hill climbing in a few directions
        var step = 0.18f
        repeat(4) {
            val u = (if (kotlin.math.abs(best.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)).let { (it cross best).normalized() }
            val v = best cross u
            for (k in 0 until 6) {
                val a = k * (PI / 3).toFloat()
                val d = (best + (u * cos(a) + v * sin(a)) * step).normalized()
                val sc = score(s, p, n, d, cur)
                if (sc > bs) { bs = sc; best = d }
            }
            step *= 0.5f
        }
        return best
    }
}
