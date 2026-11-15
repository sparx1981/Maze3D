package com.maze3d.gl

import com.maze3d.core.Maze
import com.maze3d.core.Medium
import com.maze3d.core.Vec3

/**
 * Renders a [com.maze3d.core.MediumField]: one quad per cell, with the concentration of its corners interpolated.
 * Water is a single sheet whose height follows the level; gas and fog are a few stacked translucent sheets.
 */
class MediumMesh(private val maze: Maze, val kind: Medium, private val wallHeight: Float) {
    val layers = if (kind == Medium.WATER) 1 else 3
    private val cells = maze.cellCount
    val indices: IntArray
    private val verts = FloatArray(cells * 4 * layers * MeshBuilder.STRIDE)
    private val cornerCells: Array<IntArray>             // per cell corner: other cells touching that vertex
    private val surface = maze.mesh.surface
    private val corner = Array(cells * 4) { maze.mesh.vertices[maze.mesh.cellVertex(it / 4, it % 4)] }
    private val normals = Array(cells * 4) { surface.normal(corner[it]) }

    init {
        val touching = HashMap<Int, MutableList<Int>>()
        for (c in 0 until cells) for (k in 0..3) touching.getOrPut(maze.mesh.cellVertex(c, k)) { ArrayList() }.add(c)
        cornerCells = Array(cells * 4) { i -> touching[maze.mesh.cellVertex(i / 4, i % 4)]!!.filter { it != i / 4 }.toIntArray() }
        val ix = IntArray(cells * layers * 6)
        var q = 0
        for (l in 0 until layers) for (c in 0 until cells) {
            val b = (l * cells + c) * 4
            ix[q++] = b; ix[q++] = b + 1; ix[q++] = b + 2; ix[q++] = b; ix[q++] = b + 2; ix[q++] = b + 3
        }
        indices = ix
    }

    /** Rebuilds the vertex data for the given per-cell levels. */
    fun update(level: FloatArray): FloatArray {
        var o = 0
        for (l in 0 until layers) for (c in 0 until cells) for (k in 0..3) {
            val i = c * 4 + k
            var sum = 0f
            val nb = cornerCells[i]
            for (oc in nb) sum += level[oc]
            val lv = if (nb.isEmpty()) level[c] else 0.6f * level[c] + 0.4f * sum / nb.size
            val lift = when (kind) {
                Medium.WATER -> maze.cellSize * 0.015f + lv * wallHeight * 0.55f
                Medium.GAS -> wallHeight * (0.12f + 0.42f * l)
                else -> wallHeight * (0.25f + 0.7f * l)
            }
            val n = normals[i]
            val p = corner[i] + n * lift
            verts[o++] = p.x; verts[o++] = p.y; verts[o++] = p.z
            verts[o++] = n.x; verts[o++] = n.y; verts[o++] = n.z
            verts[o++] = lv; verts[o++] = l / 3f; verts[o++] = 0f
            verts[o++] = 0f; verts[o++] = 0f; verts[o++] = 0f
            verts[o++] = -1f
        }
        return verts
    }
}
