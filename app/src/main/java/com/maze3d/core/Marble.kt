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

    /** Moving hazards for this level, and the shared game clock they run on. */
    var hazards: Hazards? = null
        set(v) { field = v; buildDoorLists() }
    var clock = 0f

    /** What the floor is made of (sand and mud slow the marble). */
    var terrain: Terrain = Terrain.NONE

    /** Puddles slow the marble when set. */
    var rain: Rain? = null

    /** Depth of puddle water the marble is rolling through right now (0..1), for effects and sound. */
    var wet = 0f
        private set

    /** Where the strongest impact of the last [step] happened (marble surface point) and the wall's push direction. */
    var impactPos: Vec3 = Vec3.ZERO
        private set
    var impactDir: Vec3 = Vec3.ZERO
        private set
    private var doorsNear: Array<IntArray> = emptyArray()
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

    private fun buildDoorLists() {
        val h = hazards
        if (h == null || !h.hasDoors) { doorsNear = emptyArray(); return }
        val doorIdx = HashMap<Int, Int>()
        h.doorEdges.forEachIndexed { i, e -> doorIdx[e] = i }
        val vertexCells = HashMap<Int, MutableList<Int>>()
        for (c in 0 until maze.cellCount) for (k in 0..3)
            vertexCells.getOrPut(maze.mesh.cellVertex(c, k)) { ArrayList() }.add(c)
        doorsNear = Array(maze.cellCount) { c ->
            val set = LinkedHashSet<Int>()
            for (k in 0..3) for (oc in vertexCells[maze.mesh.cellVertex(c, k)]!!)
                for (j in 0..3) { val e = maze.cellEdges[oc * 4 + j]; if (e in doorIdx) set.add(doorIdx[e]!!) }
            set.toIntArray()
        }
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
        velocity += gt * (0.714f * h * terrain.accel)
        velocity -= nrm * (velocity dot nrm)
        val rn = rain
        wet = 0f
        var drag = 0.5f + terrain.drag
        var speedCap = 6f * terrain.speedCap
        if (rn != null) {
            wet = rn.depthAt(cell, position - nrm * radius)
            drag += wet * 5.5f            // thick water: strong viscous drag
            speedCap -= wet * 3.2f
        }
        velocity *= exp(-drag * h)
        val maxSpeed = maze.cellSize * speedCap
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
            for (e in wallsNear[cell]) hit(e, minDist)
            val h = hazards
            if (h != null && doorsNear.isNotEmpty()) {
                for (i in doorsNear[cell]) if (h.doorClosed(i, clock)) hit(h.doorEdges[i], minDist)
            }
            position = surface.project(position) + surface.normal(position) * radius
        }
    }

    private fun hit(e: Int, minDist: Float) {
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
                val strength = -vn / maze.cellSize
                if (strength > lastImpact) {
                    lastImpact = strength
                    impactPos = position - dir * radius
                    impactDir = dir
                }
            }
        }
    }

    /** How far [p] is from being inside [c], in cell-local units (<= 0.5 means inside). Works for long thin cells too. */
    private fun cellMetric(c: Int, p: Vec3): Float {
        val d = p - maze.centers[c]
        return maxOf(kotlin.math.abs(d dot maze.axisU[c]), kotlin.math.abs(d dot maze.axisV[c]))
    }

    private fun updateCell() {
        var best = cell
        var bestM = cellMetric(best, position)
        var moved = true
        var guard = 0
        while (moved && guard++ < 8) {
            moved = false
            for (k in 0..3) {
                val e = maze.cellEdges[best * 4 + k]
                val o = if (maze.edgeCellL[e] == best) maze.edgeCellR[e] else maze.edgeCellL[e]
                val m = cellMetric(o, position)
                if (m < bestM - 1e-4f) { bestM = m; best = o; moved = true }
            }
        }
        cell = best
    }

    val atGoal: Boolean
        get() = cell == maze.goal &&
            (maze.centers[maze.goal] - (position - surface.normal(position) * radius)).length() < maze.cellSize * 0.45f
}
