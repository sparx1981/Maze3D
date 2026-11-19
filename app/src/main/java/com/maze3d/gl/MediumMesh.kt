package com.maze3d.gl

import com.maze3d.core.Maze
import com.maze3d.core.Medium
import com.maze3d.core.Vec3

/**
 * Renders a [com.maze3d.core.MediumField]. Water is one finely subdivided sheet per cell that follows the curved floor (so it
 * never dips through it) with levels interpolated across cell corners; gas and fog are a few stacked translucent sheets.
 */
class MediumMesh(private val maze: Maze, val kind: Medium, private val wallHeight: Float, volumeLayers: Int = 6) {
    /** Water is one sheet; fog and gas are a stack of slices through a drifting 3D density field (more slices = denser, smoother volume). */
    val layers = if (kind == Medium.WATER) 1 else volumeLayers
    private val sub = if (kind == Medium.WATER) 4 else 1
    private val per = (sub + 1) * (sub + 1)
    private val cells = maze.cellCount
    private val stride = MeshBuilder.STRIDE + 3
    val indices: IntArray
    private val verts = FloatArray(cells * per * layers * stride)
    private val cornerCells: Array<IntArray>
    private val basePos = FloatArray(cells * per * 3)
    private val baseNrm = FloatArray(cells * per * 3)
    private val cl = FloatArray(cells * 4)

    init {
        val surface = maze.mesh.surface
        val touching = HashMap<Int, MutableList<Int>>()
        for (c in 0 until cells) for (k in 0..3) touching.getOrPut(maze.mesh.cellVertex(c, k)) { ArrayList() }.add(c)
        cornerCells = Array(cells * 4) { i -> touching[maze.mesh.cellVertex(i / 4, i % 4)]!!.filter { it != i / 4 }.toIntArray() }
        for (c in 0 until cells) {
            val p = Array(4) { maze.mesh.vertices[maze.mesh.cellVertex(c, it)] }
            for (a in 0..sub) for (b in 0..sub) {
                val u = a / sub.toFloat(); val w = b / sub.toFloat()
                val raw = p[0] * ((1 - u) * (1 - w)) + p[1] * (u * (1 - w)) + p[2] * (u * w) + p[3] * ((1 - u) * w)
                val pt = surface.project(raw)
                val n = surface.normal(pt)
                val o = (c * per + a * (sub + 1) + b) * 3
                basePos[o] = pt.x; basePos[o + 1] = pt.y; basePos[o + 2] = pt.z
                baseNrm[o] = n.x; baseNrm[o + 1] = n.y; baseNrm[o + 2] = n.z
            }
        }
        val ix = IntArray(layers * cells * sub * sub * 6)
        var q = 0
        for (l in 0 until layers) for (c in 0 until cells) {
            val base = (l * cells + c) * per
            for (a in 0 until sub) for (b in 0 until sub) {
                val v00 = base + a * (sub + 1) + b; val v10 = v00 + (sub + 1); val v11 = v10 + 1; val v01 = v00 + 1
                ix[q++] = v00; ix[q++] = v10; ix[q++] = v11; ix[q++] = v00; ix[q++] = v11; ix[q++] = v01
            }
        }
        indices = ix
    }

    /** Rebuilds the vertex data for the given per-cell levels. */
    fun update(level: FloatArray): FloatArray {
        for (c in 0 until cells) for (k in 0..3) {
            val nb = cornerCells[c * 4 + k]
            var sum = level[c]
            for (oc in nb) sum += level[oc]
            cl[c * 4 + k] = sum / (nb.size + 1)
        }
        val cs = maze.cellSize
        var o = 0
        for (l in 0 until layers) for (c in 0 until cells) for (a in 0..sub) for (b in 0..sub) {
            val u = a / sub.toFloat(); val w = b / sub.toFloat()
            val lv = cl[c * 4] * ((1 - u) * (1 - w)) + cl[c * 4 + 1] * (u * (1 - w)) + cl[c * 4 + 2] * (u * w) + cl[c * 4 + 3] * ((1 - u) * w)
            val lift = when (kind) {
                Medium.WATER -> cs * 0.03f + lv * wallHeight * 0.5f
                Medium.GAS -> wallHeight * (0.04f + 1.25f * l / maxOf(1, layers - 1))
                else -> wallHeight * (0.06f + 2.1f * l / maxOf(1, layers - 1))
            }
            val g = (c * per + a * (sub + 1) + b) * 3
            verts[o++] = basePos[g] + baseNrm[g] * lift; verts[o++] = basePos[g + 1] + baseNrm[g + 1] * lift; verts[o++] = basePos[g + 2] + baseNrm[g + 2] * lift
            verts[o++] = baseNrm[g]; verts[o++] = baseNrm[g + 1]; verts[o++] = baseNrm[g + 2]
            verts[o++] = lv; verts[o++] = l / maxOf(1, layers - 1).toFloat(); verts[o++] = 0f
            verts[o++] = 0f; verts[o++] = 0f; verts[o++] = 0f
            verts[o++] = -1f
            verts[o++] = u; verts[o++] = w; verts[o++] = c.toFloat()
        }
        return verts
    }
}
