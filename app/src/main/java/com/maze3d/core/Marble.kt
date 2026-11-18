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
    /** Flood water slows the ball and makes it splash. */
    var field: MediumField? = null
    /** 0 basic, 1 detailed, 2 ultra: how much the loose-material behaviour is simulated. */
    var simDetail = 1
    private var mudLoad = 0f

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

    /** Adds an outside push (a hammer blow). */
    fun kick(v: Vec3) { velocity += v }

    /** 0..1 slipperiness (oil slick): cuts rolling drag so the ball keeps sliding. */
    var slick = 0f
    var gates: KeyGates? = null

    /** Pitch of the Lego-style studs under the ball (0 = a smooth floor); the studs rattle and slow it as it rolls over them. */
    var studPitch = 0f
    private fun studH(p: Vec3, n: Vec3): Float {
        val ax = kotlin.math.abs(n.x); val ay = kotlin.math.abs(n.y); val az = kotlin.math.abs(n.z)
        val u: Float; val v: Float
        if (ax >= ay && ax >= az) { u = p.y; v = p.z } else if (ay >= az) { u = p.x; v = p.z } else { u = p.x; v = p.y }
        val fu = u / studPitch; val fv = v / studPitch
        val du = fu - kotlin.math.floor(fu) - 0.5f; val dv = fv - kotlin.math.floor(fv) - 0.5f
        val d = kotlin.math.sqrt(du * du + dv * dv)
        val t = ((0.42f - d) / 0.2f).coerceIn(0f, 1f)           // a softened stud: the ball is wider than the edge it rides over
        return t * t * (3f - 2f * t)
    }
    private fun studPush(p: Vec3, n: Vec3, h: Float) {
        val t1 = (if (kotlin.math.abs(n.x) < 0.9f) Vec3(1f, 0f, 0f) else Vec3(0f, 1f, 0f)).let { (it - n * (it dot n)).normalized() }
        val t2 = n cross t1
        val e = studPitch * 0.12f
        val g1 = (studH(p + t1 * e, n) - studH(p - t1 * e, n)) / (2f * e)
        val g2 = (studH(p + t2 * e, n) - studH(p - t2 * e, n)) / (2f * e)
        val k = -0.12f * studPitch * 3.2f * 0.714f * h        // stud height 0.12 pitch; downhill pull while on a stud flank
        velocity += (t1 * g1 + t2 * g2) * k
    }

    fun reset(at: Int = maze.start) {
        cell = at
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
        if (studPitch > 0f) studPush(position, nrm, h)
        val rn = rain
        wet = 0f
        var drag = 0.5f + terrain.drag
        var speedCap = 6f * terrain.speedCap
        if (rn != null) {
            wet = rn.depthAt(cell, position - nrm * radius)
            drag += wet * 5.5f            // thick water: strong viscous drag
            speedCap -= wet * 3.2f
        }
        if (simDetail >= 1 && terrain != Terrain.NONE) {
            val spd = velocity.length() / maze.cellSize
            if (terrain == Terrain.SAND) {
                // slow rolling digs in; speed lets the ball plane over the surface
                drag *= 1f + 0.55f * exp(-spd / 1.4f) - 0.25f * (spd / (spd + 1.5f))
            } else {
                // mud clings: the longer the ball ploughs through, the more it carries and the harder it pulls
                mudLoad = (mudLoad + (0.12f + 0.2f * spd.coerceAtMost(2f)) * h).coerceAtMost(1f)
                drag *= 1f + (if (simDetail >= 2) 0.7f else 0.4f) * mudLoad
            }
        } else mudLoad = maxOf(0f, mudLoad - 0.5f * h)
        val f = field
        if (f != null && f.kind == Medium.WATER) {
            val w = f.levelAt(cell)
            wet = maxOf(wet, w)
            drag += w * 3.4f
            speedCap -= w * 2.4f
        }
        drag *= 1f - 0.82f * slick
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

    private var obstacleAt: Map<Int, Triple<Int, Vec3, Float>> = emptyMap()
    /** Solid props standing in dead ends: (cell, position, radius). */
    fun setObstacles(list: List<Triple<Int, Vec3, Float>>) { obstacleAt = list.associateBy { it.first } }

    private fun collide() {
        val minDist = wallHalfThickness + radius
        repeat(2) {
            for (e in wallsNear[cell]) hit(e, minDist)
            val h = hazards
            if (h != null && doorsNear.isNotEmpty()) {
                for (i in doorsNear[cell]) if (h.doorClosed(i, clock)) hit(h.doorEdges[i], minDist)
            }
            gates?.let { kg -> for (g in kg.gateEdges.indices) if (!kg.gateOpen[g]) { val e = kg.gateEdges[g]; if (maze.edgeCellL[e] == cell || maze.edgeCellR[e] == cell) hit(e, minDist) } }
            obstacleAt[cell]?.let { ob ->
                val nrm = surface.normal(position)
                var d = position - ob.second
                d -= nrm * (d dot nrm)
                val dist = d.length(); val minO = ob.third + radius
                if (dist < minO) {
                    val dir = if (dist > 1e-6f) d / dist else (nrm cross velocity).normalized()
                    position += dir * (minO - dist)
                    val vn = velocity dot dir
                    if (vn < 0f) velocity -= dir * (vn * 1.3f)
                }
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
