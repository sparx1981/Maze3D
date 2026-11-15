package com.maze3d.core

import kotlin.math.exp
import kotlin.math.min

/**
 * Marble that rolls on the surface under a gravity vector given in the labyrinth's local space.
 * It is kept glued to the surface (so it can run around the underside) and collides with the
 * maze walls modelled as thick segments.
 */
class Marble(private val maze: Maze) {
    val surface = maze.mesh.surface
    val radius = maze.marbleRadius
    val wallHalfThickness = maze.wallHalfThickness

    var position: Vec3 = Vec3.ZERO   // marble centre, local space
        private set
    var velocity: Vec3 = Vec3.ZERO
        private set
    var cell: Int = maze.start
        private set

    /** Strongest wall impact during the last [step], for haptics and sound. */
    var lastImpact = 0f
        private set

    private val wallsNear: Array<IntArray>

    init {
        // Walls that touch any vertex of a cell (so corner posts are covered too).
        val vertexCells = HashMap<Int, MutableList<Int>>()
        for (c in 0 until maze.cellCount) for (k in 0..3)
            vertexCells.getOrPut(maze.mesh.cellVertex(c, k)) { ArrayList() }.add(c)
        wallsNear = Array(maze.cellCount) { c ->
            val set = LinkedHashSet<Int>()
            for (k in 0..3) for (oc in vertexCells[maze.mesh.cellVertex(c, k)]!!)
                for (j in 0..3) {
                    val e = maze.cellEdges[oc * 4 + j]
                    if (maze.isWall(e)) set.add(e)
                }
            set.toIntArray()
        }
        reset()
    }

    fun reset() {
        cell = maze.start
        position = maze.centers[cell] + surface.normal(maze.centers[cell]) * radius
        velocity = Vec3.ZERO
    }

    fun step(gravityLocal: Vec3, dt: Float) {
        lastImpact = 0f
        val maxSub = 1f / 240f
        val n = kotlin.math.ceil(dt / maxSub).toInt().coerceIn(1, 16)
        val h = min(dt, maxSub * n) / n
        repeat(n) { sub(gravityLocal, h) }
    }

    private fun sub(g: Vec3, h: Float) {
        val nrm = surface.normal(position)
        val gt = g - nrm * (g dot nrm)
        // 5/7 for a solid ball rolling without slipping.
        velocity += gt * (0.714f * h)
        velocity -= nrm * (velocity dot nrm)
        velocity *= exp(-0.5f * h)
        val maxSpeed = maze.cellSize * 6f
        val sp = velocity.length()
        if (sp > maxSpeed) velocity *= maxSpeed / sp

        position += velocity * h
        // Back onto the surface (marble centre sits one radius above it).
        position = surface.project(position) + surface.normal(position) * radius

        collide()
        updateCell()
    }

    private fun collide() {
        val minDist = wallHalfThickness + radius
        repeat(2) {
            for (e in wallsNear[cell]) {
                val a = maze.mesh.vertices[maze.edgeA[e]]
                val b = maze.mesh.vertices[maze.edgeB[e]]
                val ab = b - a
                val t = (((position - a) dot ab) / ab.lengthSq()).coerceIn(0f, 1f)
                val q = a + ab * t
                val nrm = surface.normal(position)
                var d = position - q
                d -= nrm * (d dot nrm)
                val dist = d.length()
                if (dist < minDist) {
                    val dir = if (dist > 1e-6f) d / dist else (nrm cross ab).normalized()
                    position += dir * (minDist - dist)
                    val vn = velocity dot dir
                    if (vn < 0f) {
                        velocity -= dir * (vn * 1.35f) // restitution ~0.35
                        lastImpact = maxOf(lastImpact, -vn / maze.cellSize)
                    }
                }
            }
            position = surface.project(position) + surface.normal(position) * radius
        }
    }

    private fun updateCell() {
        var best = cell
        var bestD = (maze.centers[best] - position).lengthSq()
        var moved = true
        var guard = 0
        while (moved && guard++ < 8) {
            moved = false
            for (k in 0..3) {
                val e = maze.cellEdges[best * 4 + k]
                val o = if (maze.edgeCellL[e] == best) maze.edgeCellR[e] else maze.edgeCellL[e]
                val d = (maze.centers[o] - position).lengthSq()
                if (d < bestD) { bestD = d; best = o; moved = true }
            }
        }
        cell = best
    }

    val atGoal: Boolean
        get() = cell == maze.goal &&
            (maze.centers[maze.goal] - (position - surface.normal(position) * radius)).length() < maze.cellSize * 0.45f
}
