package com.maze3d.core

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Height of the floor above the base shape, in world units, as a function of a point on the base shape.
 * Built from rolling hills, round bumps/dips and raised ridges that follow a run of the maze path
 * (a ramp up, a flat top and a slide back down). Everything is smooth so the marble rolls over it cleanly.
 */
class Elevation(private val cs: Float) {
    /** Finite-difference step used for slopes (world units). */
    val step = cs * 0.08f

    class Bump(val at: Vec3, val radius: Float, val height: Float)
    /** A raised run along [pts]; [prof] maps distance along it (world units) to height. [half] is the flat half-width. */
    class Ridge(val pts: List<Vec3>, val half: Float, val fall: Float, val prof: (Float) -> Float) {
        val acc = FloatArray(pts.size).also { for (i in 1 until pts.size) it[i] = it[i - 1] + (pts[i] - pts[i - 1]).length() }
        val lo: Vec3; val hi: Vec3
        init {
            var a = pts[0]; var b = pts[0]
            for (p in pts) { a = Vec3(minOf(a.x, p.x), minOf(a.y, p.y), minOf(a.z, p.z)); b = Vec3(maxOf(b.x, p.x), maxOf(b.y, p.y), maxOf(b.z, p.z)) }
            val m = half + fall
            lo = a - Vec3(m, m, m); hi = b + Vec3(m, m, m)
        }
    }

    var hillAmp = 0f
    var hillFreq = 1f
    val bumps = ArrayList<Bump>()
    val ridges = ArrayList<Ridge>()

    private fun lattice(x: Int, y: Int, z: Int): Float {
        var h = x * 374761393 + y * 668265263 + z * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xffff) / 32767.5f - 1f
    }
    private fun noise(p: Vec3): Float {
        val fx = floor(p.x); val fy = floor(p.y); val fz = floor(p.z)
        val ix = fx.toInt(); val iy = fy.toInt(); val iz = fz.toInt()
        fun s(t: Float) = t * t * t * (t * (t * 6f - 15f) + 10f)          // quintic: smooth slopes, no creases
        val ux = s(p.x - fx); val uy = s(p.y - fy); val uz = s(p.z - fz)
        fun l(a: Float, b: Float, t: Float) = a + (b - a) * t
        return l(l(l(lattice(ix, iy, iz), lattice(ix + 1, iy, iz), ux), l(lattice(ix, iy + 1, iz), lattice(ix + 1, iy + 1, iz), ux), uy),
            l(l(lattice(ix, iy, iz + 1), lattice(ix + 1, iy, iz + 1), ux), l(lattice(ix, iy + 1, iz + 1), lattice(ix + 1, iy + 1, iz + 1), ux), uy), uz)
    }

    fun at(p: Vec3): Float {
        var h = 0f
        if (hillAmp != 0f) h += hillAmp * (noise(p * hillFreq + Vec3(3.1f, 7.7f, 1.3f)) * 0.75f + noise(p * (hillFreq * 2.1f) + Vec3(9.2f, 2.4f, 5.5f)) * 0.25f)
        for (b in bumps) {
            val d2 = (p - b.at).lengthSq() / (b.radius * b.radius)
            if (d2 < 9f) h += b.height * exp(-d2 * 1.6f)
        }
        var top = 0f
        for (r in ridges) {
            if (p.x < r.lo.x || p.y < r.lo.y || p.z < r.lo.z || p.x > r.hi.x || p.y > r.hi.y || p.z > r.hi.z) continue
            // distance along the run, blended between nearby segments so there is no step on the inside of a bend
            val nseg = r.pts.size - 1
            val ds = FloatArray(nseg); val ss = FloatArray(nseg)
            var best = Float.MAX_VALUE
            for (i in 0 until nseg) {
                val a = r.pts[i]; val ab = r.pts[i + 1] - a
                val l2 = ab.lengthSq(); if (l2 < 1e-12f) { ds[i] = Float.MAX_VALUE; continue }
                val t = (((p - a) dot ab) / l2).coerceIn(0f, 1f)
                ds[i] = (p - (a + ab * t)).length(); ss[i] = r.acc[i] + sqrt(l2) * t
                best = minOf(best, ds[i])
            }
            val sig = cs * 0.3f
            var wsum = 0f; var sAcc = 0f
            for (i in 0 until nseg) { if (ds[i] == Float.MAX_VALUE) continue; val w = exp(-((ds[i] - best) / sig).let { it * it }); wsum += w; sAcc += w * ss[i] }
            val bestS = sAcc / wsum
            val d = best
            val w = 1f - smooth((d - r.half) / r.fall)
            if (w > 0f) top = maxOf(top, r.prof(bestS) * w)
        }
        return h + top
    }

    companion object {
        fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    }
}
