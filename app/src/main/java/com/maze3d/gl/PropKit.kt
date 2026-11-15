package com.maze3d.gl

import com.maze3d.core.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A finished prop: interleaved local-space vertices (z is up, about 1 unit tall) ready to be placed anywhere. */
class PropMesh(val pos: FloatArray, val nrm: FloatArray, val col: FloatArray, val idx: IntArray) {
    val vertexCount get() = pos.size / 3
}

/**
 * Tiny procedural modelling kit used to build the theme props: lathes, spheres, boxes, tubes, tori and extrusions
 * with a transform stack and per-vertex colours (values above 1 glow).
 */
class PM {
    private val vp = ArrayList<Float>(); private val vn = ArrayList<Float>(); private val vc = ArrayList<Float>()
    private val ix = ArrayList<Int>()
    private var L = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)      // row-major 3x3
    private var T = floatArrayOf(0f, 0f, 0f)
    private val stack = ArrayList<Pair<FloatArray, FloatArray>>()
    var col: Vec3 = Vec3(1f, 1f, 1f)

    fun push() { stack.add(L.copyOf() to T.copyOf()) }
    fun pop() { val (l, t) = stack.removeAt(stack.lastIndex); L = l; T = t }

    private fun mulL(v: Vec3) = Vec3(L[0] * v.x + L[1] * v.y + L[2] * v.z, L[3] * v.x + L[4] * v.y + L[5] * v.z, L[6] * v.x + L[7] * v.y + L[8] * v.z)

    fun translate(x: Float, y: Float, z: Float): PM { val d = mulL(Vec3(x, y, z)); T[0] += d.x; T[1] += d.y; T[2] += d.z; return this }
    fun scale(sx: Float, sy: Float = sx, sz: Float = sx): PM {
        for (r in 0..2) { L[r * 3] *= sx; L[r * 3 + 1] *= sy; L[r * 3 + 2] *= sz }
        return this
    }
    private fun rmul(R: FloatArray) {
        val o = FloatArray(9)
        for (r in 0..2) for (c in 0..2) o[r * 3 + c] = L[r * 3] * R[c] + L[r * 3 + 1] * R[3 + c] + L[r * 3 + 2] * R[6 + c]
        L = o
    }
    fun rotZ(a: Float): PM { val c = cos(a); val s = sin(a); rmul(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f)); return this }
    fun rotX(a: Float): PM { val c = cos(a); val s = sin(a); rmul(floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c)); return this }
    fun rotY(a: Float): PM { val c = cos(a); val s = sin(a); rmul(floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)); return this }

    /** Run [body] with the transform restored afterwards. */
    inline fun at(x: Float = 0f, y: Float = 0f, z: Float = 0f, body: PM.() -> Unit) { push(); translate(x, y, z); body(); pop() }

    // normal matrix = cofactor of L (inverse-transpose up to a scale)
    private fun nrmXform(n: Vec3): Vec3 {
        val a = L[0]; val b = L[1]; val c = L[2]; val d = L[3]; val e = L[4]; val f = L[5]; val g = L[6]; val h = L[7]; val i = L[8]
        val c00 = e * i - f * h; val c01 = -(d * i - f * g); val c02 = d * h - e * g
        val c10 = -(b * i - c * h); val c11 = a * i - c * g; val c12 = -(a * h - b * g)
        val c20 = b * f - c * e; val c21 = -(a * f - c * d); val c22 = a * e - b * d
        val det = a * c00 + b * c01 + c * c02
        var r = Vec3(c00 * n.x + c10 * n.y + c20 * n.z, c01 * n.x + c11 * n.y + c21 * n.z, c02 * n.x + c12 * n.y + c22 * n.z)
        if (det < 0f) r = -r
        return r.normalized()
    }

    private fun addV(p: Vec3, n: Vec3, c: Vec3): Int {
        val q = mulL(p)
        vp.add(q.x + T[0]); vp.add(q.y + T[1]); vp.add(q.z + T[2])
        val m = nrmXform(n)
        vn.add(m.x); vn.add(m.y); vn.add(m.z)
        vc.add(c.x); vc.add(c.y); vc.add(c.z)
        return vp.size / 3 - 1
    }
    private fun tri(a: Int, b: Int, c: Int) { ix.add(a); ix.add(b); ix.add(c) }
    private fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, c); tri(a, c, d) }

    /**
     * Surface of revolution about Z. [profile] points are (radius, z) from bottom to top. With [smooth] the normals blend
     * across profile points; otherwise each segment is shaded flat (use for cylinders and boxes).
     */
    fun lathe(profile: List<FloatArray>, seg: Int = 24, smooth: Boolean = true, colorFn: ((Float, Float) -> Vec3)? = null) {
        val n = profile.size
        fun segN(i: Int): Vec3 {                       // normal of segment i -> i+1 in the (r, z) plane
            val dr = profile[i + 1][0] - profile[i][0]; val dz = profile[i + 1][1] - profile[i][1]
            val l = sqrt(dr * dr + dz * dz).coerceAtLeast(1e-9f)
            return Vec3(dz / l, 0f, -dr / l)
        }
        fun ring(i: Int, nrm: Vec3, v: Float): IntArray = IntArray(seg + 1) { j ->
            val a = (2 * PI * j / seg).toFloat(); val ca = cos(a); val sa = sin(a)
            val p = Vec3(profile[i][0] * ca, profile[i][0] * sa, profile[i][1])
            val nn = Vec3(nrm.x * ca, nrm.x * sa, nrm.z)
            addV(p, nn, colorFn?.invoke(j / seg.toFloat(), v) ?: col)
        }
        if (smooth) {
            val rings = Array(n) { i ->
                var nr = Vec3.ZERO
                if (i > 0) nr += segN(i - 1)
                if (i < n - 1) nr += segN(i)
                ring(i, if (nr.lengthSq() < 1e-9f) Vec3(0f, 0f, 1f) else nr.normalized(), i / (n - 1f))
            }
            for (i in 0 until n - 1) for (j in 0 until seg) quad(rings[i][j], rings[i][j + 1], rings[i + 1][j + 1], rings[i + 1][j])
        } else {
            for (i in 0 until n - 1) {
                if (abs(profile[i + 1][0] - profile[i][0]) + abs(profile[i + 1][1] - profile[i][1]) < 1e-6f) continue
                val nr = segN(i)
                val r0 = ring(i, nr, i / (n - 1f)); val r1 = ring(i + 1, nr, (i + 1) / (n - 1f))
                for (j in 0 until seg) quad(r0[j], r0[j + 1], r1[j + 1], r1[j])
            }
        }
    }

    private fun prof(vararg rz: Float): List<FloatArray> = rz.toList().chunked(2).map { floatArrayOf(it[0], it[1]) }

    fun sphere(r: Float = 0.5f, seg: Int = 20, stacks: Int = 12, colorFn: ((Float, Float) -> Vec3)? = null) {
        val pts = (0..stacks).map { val ph = (PI * it / stacks).toFloat(); floatArrayOf(sin(ph) * r, -cos(ph) * r) }
        lathe(pts, seg, true, colorFn)
    }

    /** Cylinder standing on z=0. */
    fun cylinder(r: Float, h: Float, seg: Int = 24, rTop: Float = r, caps: Boolean = true) {
        val p = ArrayList<FloatArray>()
        if (caps) p.add(floatArrayOf(0f, 0f))
        p.add(floatArrayOf(r, 0f)); p.add(floatArrayOf(rTop, h))
        if (caps) p.add(floatArrayOf(0f, h))
        lathe(p, seg, false)
    }

    fun cone(r: Float, h: Float, seg: Int = 24) = lathe(prof(0f, 0f, r, 0f, 0f, h), seg, false)

    fun box(sx: Float, sy: Float, sz: Float) {          // centred on the origin
        val hx = sx / 2; val hy = sy / 2; val hz = sz / 2
        fun face(n: Vec3, u: Vec3, v: Vec3) {
            val c = n * 1f
            val a = addV(Vec3(c.x * hx, c.y * hy, c.z * hz) + Vec3(-u.x * hx - v.x * hx, -u.y * hy - v.y * hy, -u.z * hz - v.z * hz), n, col)
            val b = addV(Vec3(c.x * hx, c.y * hy, c.z * hz) + Vec3(u.x * hx - v.x * hx, u.y * hy - v.y * hy, u.z * hz - v.z * hz), n, col)
            val cc = addV(Vec3(c.x * hx, c.y * hy, c.z * hz) + Vec3(u.x * hx + v.x * hx, u.y * hy + v.y * hy, u.z * hz + v.z * hz), n, col)
            val d = addV(Vec3(c.x * hx, c.y * hy, c.z * hz) + Vec3(-u.x * hx + v.x * hx, -u.y * hy + v.y * hy, -u.z * hz + v.z * hz), n, col)
            quad(a, b, cc, d)
        }
        face(Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f)); face(Vec3(-1f, 0f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, 1f))
        face(Vec3(0f, 1f, 0f), Vec3(-1f, 0f, 0f), Vec3(0f, 0f, 1f)); face(Vec3(0f, -1f, 0f), Vec3(1f, 0f, 0f), Vec3(0f, 0f, 1f))
        face(Vec3(0f, 0f, 1f), Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f)); face(Vec3(0f, 0f, -1f), Vec3(1f, 0f, 0f), Vec3(0f, -1f, 0f))
    }

    /** Ring in the XY plane around Z, centre at the origin. */
    fun torus(R: Float, r: Float, seg: Int = 28, rseg: Int = 12, colorFn: ((Float, Float) -> Vec3)? = null) {
        val rings = Array(seg + 1) { i ->
            val a = (2 * PI * i / seg).toFloat(); val ca = cos(a); val sa = sin(a)
            IntArray(rseg + 1) { j ->
                val b = (2 * PI * j / rseg).toFloat()
                val n = Vec3(cos(b) * ca, cos(b) * sa, sin(b))
                addV(Vec3((R + r * cos(b)) * ca, (R + r * cos(b)) * sa, r * sin(b)), n, colorFn?.invoke(i / seg.toFloat(), j / rseg.toFloat()) ?: col)
            }
        }
        for (i in 0 until seg) for (j in 0 until rseg) quad(rings[i][j], rings[i + 1][j], rings[i + 1][j + 1], rings[i][j + 1])
    }

    /** Round tube along a polyline, with optional colour along its length. */
    fun tube(path: List<Vec3>, r: Float, seg: Int = 10, closed: Boolean = false, colorFn: ((Float) -> Vec3)? = null, taper: ((Float) -> Float)? = null) {
        val n = path.size
        val tang = Array(n) { i -> (path[min(i + 1, n - 1)] - path[max(i - 1, 0)]).normalized() }
        var ref = if (abs(tang[0].z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(1f, 0f, 0f)
        var nrm = (ref - tang[0] * (ref dot tang[0])).normalized()
        val rings = Array(n) { i ->
            if (i > 0) { var v = nrm - tang[i] * (nrm dot tang[i]); if (v.lengthSq() < 1e-9f) v = nrm; nrm = v.normalized() }
            val b = tang[i] cross nrm
            val rr = r * (taper?.invoke(i / (n - 1f)) ?: 1f)
            val c = colorFn?.invoke(i / (n - 1f)) ?: col
            IntArray(seg + 1) { j ->
                val a = (2 * PI * j / seg).toFloat()
                val d = nrm * cos(a) + b * sin(a)
                addV(path[i] + d * rr, d, c)
            }
        }
        for (i in 0 until n - 1) for (j in 0 until seg) quad(rings[i][j], rings[i][j + 1], rings[i + 1][j + 1], rings[i + 1][j])
        if (!closed) {
            for (end in intArrayOf(0, n - 1)) {
                val cidx = addV(path[end], if (end == 0) -tang[0] else tang[n - 1], colorFn?.invoke(end.toFloat()) ?: col)
                for (j in 0 until seg) if (end == 0) tri(cidx, rings[0][j + 1], rings[0][j]) else tri(cidx, rings[n - 1][j], rings[n - 1][j + 1])
            }
        }
    }

    /** Prism: [poly] (counter-clockwise) extruded from z=0 to z=[h]. */
    fun prism(poly: List<FloatArray>, h: Float) {
        val n = poly.size
        for (i in 0 until n) {
            val a = poly[i]; val b = poly[(i + 1) % n]
            val dx = b[0] - a[0]; val dy = b[1] - a[1]; val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9f)
            val nr = Vec3(dy / l, -dx / l, 0f)
            val v0 = addV(Vec3(a[0], a[1], 0f), nr, col); val v1 = addV(Vec3(b[0], b[1], 0f), nr, col)
            val v2 = addV(Vec3(b[0], b[1], h), nr, col); val v3 = addV(Vec3(a[0], a[1], h), nr, col)
            quad(v0, v1, v2, v3)
        }
        for (top in 0..1) {
            val nz = if (top == 1) Vec3(0f, 0f, 1f) else Vec3(0f, 0f, -1f)
            val z = if (top == 1) h else 0f
            val ids = IntArray(n) { addV(Vec3(poly[it][0], poly[it][1], z), nz, col) }
            for (i in 1 until n - 1) if (top == 1) tri(ids[0], ids[i], ids[i + 1]) else tri(ids[0], ids[i + 1], ids[i])
        }
    }

    /** Flat double-sided quad (flags, sails, leaves). */
    fun flat(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, c0: Vec3 = col, c1: Vec3 = c0, c2: Vec3 = c0, c3: Vec3 = c0) {
        val n = ((p1 - p0) cross (p3 - p0)).normalized()
        for (side in 0..1) {
            val nn = if (side == 0) n else -n
            val a = addV(p0, nn, c0); val b = addV(p1, nn, c1); val c = addV(p2, nn, c2); val d = addV(p3, nn, c3)
            if (side == 0) quad(a, b, c, d) else quad(a, d, c, b)
        }
    }

    fun build() = PropMesh(vp.toFloatArray(), vn.toFloatArray(), vc.toFloatArray(), ix.toIntArray())

    companion object {
        fun rgb(r: Int, g: Int, b: Int) = Vec3(r / 255f, g / 255f, b / 255f)
    }
}
