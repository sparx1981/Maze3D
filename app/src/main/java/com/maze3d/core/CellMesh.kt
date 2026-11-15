package com.maze3d.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** A quad-cell tiling of a closed surface. Cells are 4 vertex indices in cyclic order. */
class CellMesh(val surface: Surface, val vertices: List<Vec3>, val cells: IntArray) {
    val cellCount get() = cells.size / 4
    fun cellVertex(cell: Int, k: Int) = cells[cell * 4 + k]

    companion object {
        /** Cube-sphere style tiling (equiangular) shared by the sphere and the rounded cube. */
        fun cubeBased(surface: Surface, n: Int, scale: Float): CellMesh {
            val verts = ArrayList<Vec3>()
            val weld = HashMap<Int, Int>()
            val cells = ArrayList<Int>()
            val stride = n + 1

            fun t(i: Int) = tan((PI / 4).toFloat() * (2f * i / n - 1f))

            fun vertexFor(axis: Int, sign: Int, i: Int, j: Int): Int {
                val idx = IntArray(3)
                val a1 = (axis + 1) % 3
                val a2 = (axis + 2) % 3
                idx[axis] = if (sign > 0) n else 0
                idx[a1] = i
                idx[a2] = j
                val key = (idx[0] * stride + idx[1]) * stride + idx[2]
                return weld.getOrPut(key) {
                    val c = FloatArray(3)
                    c[axis] = sign.toFloat()
                    c[a1] = t(i)
                    c[a2] = t(j)
                    verts.add(surface.project(Vec3(c[0], c[1], c[2]) * scale))
                    verts.size - 1
                }
            }

            for (axis in 0..2) for (sign in intArrayOf(-1, 1)) {
                for (i in 0 until n) for (j in 0 until n) {
                    cells.add(vertexFor(axis, sign, i, j))
                    cells.add(vertexFor(axis, sign, i + 1, j))
                    cells.add(vertexFor(axis, sign, i + 1, j + 1))
                    cells.add(vertexFor(axis, sign, i, j + 1))
                }
            }
            return CellMesh(surface, verts, cells.toIntArray())
        }

        /**
         * Quad grid over any convex shape, with independent subdivisions per axis so stretched forms keep evenly sized
         * cells. Grid points are cast along rays from the origin through an (equiangular) cube whose proportions are [scale].
         */
        fun cubeRays(surface: Surface, nx: Int, ny: Int, nz: Int, scale: Vec3): CellMesh {
            val verts = ArrayList<Vec3>()
            val weld = HashMap<Int, Int>()
            val cells = ArrayList<Int>()
            val dims = intArrayOf(nx, ny, nz)
            val sc = floatArrayOf(scale.x, scale.y, scale.z)

            fun t(i: Int, n: Int) = tan((PI / 4).toFloat() * (2f * i / n - 1f))

            fun vertexFor(axis: Int, sign: Int, i: Int, j: Int): Int {
                val a1 = (axis + 1) % 3; val a2 = (axis + 2) % 3
                val idx = IntArray(3)
                idx[axis] = if (sign > 0) dims[axis] else 0
                idx[a1] = i; idx[a2] = j
                val key = (idx[0] * (ny + 1) + idx[1]) * (nz + 1) + idx[2]
                return weld.getOrPut(key) {
                    val c = FloatArray(3)
                    c[axis] = sign.toFloat()
                    c[a1] = t(i, dims[a1]); c[a2] = t(j, dims[a2])
                    val dir = Vec3(c[0] * sc[0], c[1] * sc[1], c[2] * sc[2]).normalized()
                    verts.add(surface.rayHit(dir))
                    verts.size - 1
                }
            }
            for (axis in 0..2) for (sign in intArrayOf(-1, 1)) {
                val a1 = (axis + 1) % 3; val a2 = (axis + 2) % 3
                for (i in 0 until dims[a1]) for (j in 0 until dims[a2]) {
                    cells.add(vertexFor(axis, sign, i, j)); cells.add(vertexFor(axis, sign, i + 1, j))
                    cells.add(vertexFor(axis, sign, i + 1, j + 1)); cells.add(vertexFor(axis, sign, i, j + 1))
                }
            }
            return CellMesh(surface, verts, cells.toIntArray())
        }

        /** Tube around the trefoil knot: (u along the curve, v around the tube), seamless in both. */
        fun knot(surface: KnotSurface, nu: Int, nv: Int): CellMesh {
            val n = nu
            // parallel-transport frames along the curve, then spread the leftover twist evenly so the ends meet
            val pts = Array(n + 1) { surface.curve((2.0 * PI * it / n).toFloat()) }
            val tang = Array(n + 1) { (pts[(it + 1) % n] - pts[(it + n - 1) % n]).normalized() }
            val nrm = Array(n + 1) { Vec3.ZERO }
            var ref = if (kotlin.math.abs(tang[0].y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
            nrm[0] = (ref - tang[0] * (ref dot tang[0])).normalized()
            for (i in 1..n) {
                var v = nrm[i - 1] - tang[i] * (nrm[i - 1] dot tang[i])
                if (v.lengthSq() < 1e-8f) v = nrm[i - 1]
                nrm[i] = v.normalized()
            }
            // twist between the transported frame at the end and the starting frame
            val b0 = tang[0] cross nrm[0]
            val twist = kotlin.math.atan2((nrm[n] dot b0), (nrm[n] dot nrm[0]))
            val verts = ArrayList<Vec3>()
            for (i in 0 until n) {
                val ang = -twist * i / n
                val tN = nrm[i]; val tB = tang[i] cross nrm[i]
                val cN = tN * cos(ang) + tB * sin(ang)
                val cB = tang[i] cross cN
                for (j in 0 until nv) {
                    val ph = (2 * PI * j / nv).toFloat()
                    verts.add(pts[i] + (cN * cos(ph) + cB * sin(ph)) * surface.radius)
                }
            }
            fun v(i: Int, j: Int) = (i % n) * nv + (j % nv)
            val cells = ArrayList<Int>()
            for (i in 0 until n) for (j in 0 until nv) {
                cells.add(v(i, j)); cells.add(v(i + 1, j)); cells.add(v(i + 1, j + 1)); cells.add(v(i, j + 1))
            }
            return CellMesh(surface, verts, cells.toIntArray())
        }

        /** Seamless (u, v) ring tiling of a torus. */
        fun torus(surface: TorusSurface, nu: Int, nv: Int): CellMesh {
            val verts = ArrayList<Vec3>()
            for (i in 0 until nu) for (j in 0 until nv) {
                val th = (2 * PI * i / nu).toFloat()
                val ph = (2 * PI * j / nv).toFloat()
                val r = surface.majorRadius + surface.radius * cos(ph)
                verts.add(Vec3(r * cos(th), surface.radius * sin(ph), r * sin(th)))
            }
            fun v(i: Int, j: Int) = (i % nu) * nv + (j % nv)
            val cells = ArrayList<Int>()
            for (i in 0 until nu) for (j in 0 until nv) {
                cells.add(v(i, j)); cells.add(v(i + 1, j)); cells.add(v(i + 1, j + 1)); cells.add(v(i, j + 1))
            }
            return CellMesh(surface, verts, cells.toIntArray())
        }
    }
}
