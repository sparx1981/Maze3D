package com.maze3d.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A closed surface described as the set of points at distance [radius] from a simple
 * "core" (a point, a circle or a box). This gives exact closest-point projections and
 * normals for sphere, torus and rounded cube, which is what the marble needs to stay
 * glued to the surface on every side.
 */
abstract class Surface(val name: String, val radius: Float) {
    /** Closest point of the core to [p]. */
    abstract fun core(p: Vec3): Vec3

    /** Optional height of the floor above the base shape (hills, ramps), given a point on the base shape. */
    var lift: Elevation? = null

    /** Closest point on the base shape, ignoring any [lift]. */
    fun baseProject(p: Vec3): Vec3 {
        val c = core(p)
        var d = p - c
        if (d.lengthSq() < 1e-12f) d = fallbackDir(c)
        return c + d.normalized() * radius
    }

    /** Normal of the base shape, ignoring any [lift] (the direction 'down' points against on hills). */
    fun baseNormal(p: Vec3): Vec3 {
        val c = core(p)
        var d = p - c
        if (d.lengthSq() < 1e-12f) d = fallbackDir(c)
        return d.normalized()
    }

    /** The floor under [p]: the base shape raised by [lift]. */
    fun project(p: Vec3): Vec3 {
        val q = baseProject(p)
        val l = lift ?: return q
        return q + baseNormal(q) * l.at(q)
    }

    /** Floor normal, tilted by the slope of [lift]. */
    fun normal(p: Vec3): Vec3 {
        val l = lift ?: return baseNormal(p)
        val q = baseProject(p)
        val n0 = baseNormal(q)
        val a = if (abs(n0.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
        val t1 = (a cross n0).normalized(); val t2 = n0 cross t1
        val e = l.step
        val hx = (l.at(baseProject(q + t1 * e)) - l.at(baseProject(q - t1 * e))) / (2f * e)
        val hy = (l.at(baseProject(q + t2 * e)) - l.at(baseProject(q - t2 * e))) / (2f * e)
        if (hx == 0f && hy == 0f) return n0
        return (n0 - t1 * hx - t2 * hy).normalized()
    }

    protected open fun fallbackDir(c: Vec3) = Vec3(0f, 1f, 0f)

    /**
     * Where the ray from the origin along [dir] leaves the shape (convex shapes only). Used to lay a quad grid over
     * elongated or faceted forms so cells stay evenly spread.
     */
    fun rayHit(dir: Vec3): Vec3 {
        var lo = 0f; var hi = 6f
        repeat(40) {
            val mid = (lo + hi) * 0.5f
            val p = dir * mid
            if ((p - core(p)).length() < radius) lo = mid else hi = mid
        }
        return dir * ((lo + hi) * 0.5f)
    }
}

class SphereSurface(radius: Float = 1f) : Surface("Sphere", radius) {
    override fun core(p: Vec3) = Vec3.ZERO
    override fun fallbackDir(c: Vec3) = Vec3(0f, 1f, 0f)
}

class TorusSurface(val majorRadius: Float = 1f, minor: Float = 0.4f) : Surface("Torus", minor) {
    override fun core(p: Vec3): Vec3 {
        val l = kotlin.math.sqrt(p.x * p.x + p.z * p.z)
        return if (l < 1e-6f) Vec3(majorRadius, 0f, 0f) else Vec3(p.x / l * majorRadius, 0f, p.z / l * majorRadius)
    }
}

class RoundedCubeSurface(val half: Float = 0.8f, rounding: Float = 0.3f) : Surface("Rounded cube", rounding) {
    private val inner = half - rounding
    override fun core(p: Vec3) = Vec3(
        max(-inner, min(inner, p.x)),
        max(-inner, min(inner, p.y)),
        max(-inner, min(inner, p.z))
    )

    override fun fallbackDir(c: Vec3): Vec3 {
        val ax = abs(c.x); val ay = abs(c.y); val az = abs(c.z)
        return when {
            ax >= ay && ax >= az -> Vec3(if (c.x >= 0) 1f else -1f, 0f, 0f)
            ay >= az -> Vec3(0f, if (c.y >= 0) 1f else -1f, 0f)
            else -> Vec3(0f, 0f, if (c.z >= 0) 1f else -1f)
        }
    }
}

/** Pill: a segment along Y thickened by [rr]. */
class CapsuleSurface(val halfLen: Float = 0.7f, rr: Float = 0.42f) : Surface("Capsule", rr) {
    override fun core(p: Vec3) = Vec3(0f, max(-halfLen, min(halfLen, p.y)), 0f)
    override fun fallbackDir(c: Vec3) = Vec3(1f, 0f, 0f)
}

/** Rounded cylinder: a solid cylinder core (radius [coreR], half height [halfH]) thickened by [rr]. Also makes coins. */
class BarrelSurface(val coreR: Float = 0.55f, val halfH: Float = 0.55f, rr: Float = 0.4f, label: String = "Barrel") : Surface(label, rr) {
    override fun core(p: Vec3): Vec3 {
        val l = kotlin.math.sqrt(p.x * p.x + p.z * p.z)
        val k = if (l > coreR) coreR / l else 1f
        return Vec3(p.x * k, max(-halfH, min(halfH, p.y)), p.z * k)
    }
    override fun fallbackDir(c: Vec3) = Vec3(0f, 1f, 0f)
}

/** Rounded box with independent half extents. */
class BrickSurface(val half: Vec3, rounding: Float, label: String = "Brick") : Surface(label, rounding) {
    private val inner = Vec3(half.x - rounding, half.y - rounding, half.z - rounding)
    override fun core(p: Vec3) = Vec3(
        max(-inner.x, min(inner.x, p.x)), max(-inner.y, min(inner.y, p.y)), max(-inner.z, min(inner.z, p.z))
    )
    override fun fallbackDir(c: Vec3): Vec3 {
        val ax = abs(c.x) / inner.x.coerceAtLeast(1e-3f); val ay = abs(c.y) / inner.y.coerceAtLeast(1e-3f); val az = abs(c.z) / inner.z.coerceAtLeast(1e-3f)
        return when {
            ax >= ay && ax >= az -> Vec3(if (c.x >= 0) 1f else -1f, 0f, 0f)
            ay >= az -> Vec3(0f, if (c.y >= 0) 1f else -1f, 0f)
            else -> Vec3(0f, 0f, if (c.z >= 0) 1f else -1f)
        }
    }
}

/** Solid octahedron (|x|+|y|+|z| <= a) thickened by [rr]: a rounded gem. */
class GemSurface(val a: Float = 0.62f, rr: Float = 0.34f) : Surface("Gem", rr) {
    override fun core(p: Vec3): Vec3 {
        val ax = abs(p.x); val ay = abs(p.y); val az = abs(p.z)
        if (ax + ay + az <= a) return p
        // project onto the L1 ball: soft-threshold the magnitudes
        val m = floatArrayOf(ax, ay, az)
        val sorted = m.sortedDescending()
        var cum = 0f; var tau = 0f
        for (k in 0..2) {
            cum += sorted[k]
            val t = (cum - a) / (k + 1)
            if (sorted[k] - t > 0f) tau = t
        }
        return Vec3(
            kotlin.math.sign(p.x) * max(ax - tau, 0f),
            kotlin.math.sign(p.y) * max(ay - tau, 0f),
            kotlin.math.sign(p.z) * max(az - tau, 0f)
        )
    }
}

/** Solid regular tetrahedron thickened by [rr]: a rounded pyramid. */
class PyramidSurface(val a: Float = 0.62f, rr: Float = 0.3f) : Surface("Pyramid", rr) {
    private val v = arrayOf(Vec3(1f, 1f, 1f) * a, Vec3(1f, -1f, -1f) * a, Vec3(-1f, 1f, -1f) * a, Vec3(-1f, -1f, 1f) * a)
    // faces as (i,j,k) with the opposite vertex index
    private val faces = arrayOf(intArrayOf(1, 2, 3, 0), intArrayOf(0, 3, 2, 1), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3))

    private fun closestOnTriangle(p: Vec3, a: Vec3, b: Vec3, c: Vec3): Vec3 {
        val ab = b - a; val ac = c - a; val ap = p - a
        val d1 = ab dot ap; val d2 = ac dot ap
        if (d1 <= 0f && d2 <= 0f) return a
        val bp = p - b; val d3 = ab dot bp; val d4 = ac dot bp
        if (d3 >= 0f && d4 <= d3) return b
        val vc = d1 * d4 - d3 * d2
        if (vc <= 0f && d1 >= 0f && d3 <= 0f) return a + ab * (d1 / (d1 - d3))
        val cp = p - c; val d5 = ab dot cp; val d6 = ac dot cp
        if (d6 >= 0f && d5 <= d6) return c
        val vb = d5 * d2 - d1 * d6
        if (vb <= 0f && d2 >= 0f && d6 <= 0f) return a + ac * (d2 / (d2 - d6))
        val va = d3 * d6 - d5 * d4
        if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) return b + (c - b) * ((d4 - d3) / ((d4 - d3) + (d5 - d6)))
        val denom = 1f / (va + vb + vc)
        return a + ab * (vb * denom) + ac * (vc * denom)
    }

    override fun core(p: Vec3): Vec3 {
        var best: Vec3? = null; var bestD = Float.MAX_VALUE
        var outside = false
        for (f in faces) {
            val a0 = v[f[0]]; val b0 = v[f[1]]; val c0 = v[f[2]]
            var n = (b0 - a0) cross (c0 - a0)
            if ((n dot (v[f[3]] - a0)) > 0f) n = -n                  // make the normal point away from the tetrahedron
            if ((n dot (p - a0)) > 0f) {
                outside = true
                val q = closestOnTriangle(p, a0, b0, c0)
                val d = (q - p).lengthSq()
                if (d < bestD) { bestD = d; best = q }
            }
        }
        return if (!outside) p else best!!
    }
    override fun fallbackDir(c: Vec3) = Vec3(0f, 1f, 0f)
}

/** Tube of radius [rr] around a trefoil knot. Closest point on the curve comes from a dense table plus refinement. */
class KnotSurface(val k: Float = 0.34f, rr: Float = 0.165f) : Surface("Trefoil knot", rr) {
    companion object { const val SAMPLES = 900 }

    fun curve(u: Float): Vec3 {
        val s1 = kotlin.math.sin(u); val c1 = kotlin.math.cos(u)
        val s2 = kotlin.math.sin(2 * u); val c2 = kotlin.math.cos(2 * u)
        val s3 = kotlin.math.sin(3 * u)
        return Vec3(s1 + 2 * s2, c1 - 2 * c2, -s3) * k
    }

    private val table = Array(SAMPLES) { curve((2.0 * Math.PI * it / SAMPLES).toFloat()) }
    private val du = (2.0 * Math.PI / SAMPLES).toFloat()

    override fun core(p: Vec3): Vec3 {
        // coarse-to-fine nearest table entry with no remembered state, so every caller (physics, camera, scenery) gets the same,
        // correct answer: every 10th sample first, then the full-resolution neighbourhood of the best two coarse hits
        var c1 = 0; var c2 = 0; var d1 = Float.MAX_VALUE; var d2 = Float.MAX_VALUE
        var i0 = 0
        while (i0 < SAMPLES) {
            val l = (table[i0] - p).lengthSq()
            if (l < d1) { d2 = d1; c2 = c1; d1 = l; c1 = i0 } else if (l < d2) { d2 = l; c2 = i0 }
            i0 += 10
        }
        var best = c1; var bestD = Float.MAX_VALUE
        for (cc in intArrayOf(c1, c2)) for (d in -10..10) {
            val i = (cc + d + SAMPLES) % SAMPLES
            val l = (table[i] - p).lengthSq()
            if (l < bestD) { bestD = l; best = i }
        }
        // refine along the curve between the neighbours by golden-section search
        var a = (best - 1) * du; var b = (best + 1) * du
        val gr = 0.618034f
        var c = b - (b - a) * gr; var d = a + (b - a) * gr
        var fc = (curve(c) - p).lengthSq(); var fd = (curve(d) - p).lengthSq()
        repeat(14) {
            if (fc < fd) { b = d; d = c; fd = fc; c = b - (b - a) * gr; fc = (curve(c) - p).lengthSq() }
            else { a = c; c = d; fc = fd; d = a + (b - a) * gr; fd = (curve(d) - p).lengthSq() }
        }
        return curve((a + b) * 0.5f)
    }
    override fun fallbackDir(c: Vec3) = Vec3(0f, 0f, 1f)
}
