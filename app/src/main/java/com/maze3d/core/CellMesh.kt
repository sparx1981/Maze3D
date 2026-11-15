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
