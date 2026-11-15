package com.maze3d.gl

import com.maze3d.core.Maze
import com.maze3d.core.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Turns a [Maze] into static triangle meshes: channel floor, walls and start/goal markers. */
class SceneBuilder(private val maze: Maze, private val pal: Palette) {
    private val surface = maze.mesh.surface
    private val cs = maze.cellSize
    val wallHeight = maze.marbleRadius * 1.6f
    val wallHalfThickness = maze.wallHalfThickness

    fun floor(): MeshBuilder {
        val mb = MeshBuilder()
        val sub = 4
        for (c in 0 until maze.cellCount) {
            val p = Array(4) { maze.mesh.vertices[maze.mesh.cellVertex(c, it)] }
            val wallEdges = (0..3).map { maze.cellEdges[c * 4 + it] }.filter { maze.isWall(it) }
            val tone = 0.94f + 0.12f * hash(c)
            val base = IntArray((sub + 1) * (sub + 1))
            for (a in 0..sub) for (b in 0..sub) {
                val u = a / sub.toFloat(); val w = b / sub.toFloat()
                val raw = p[0] * ((1 - u) * (1 - w)) + p[1] * (u * (1 - w)) + p[2] * (u * w) + p[3] * ((1 - u) * w)
                val pt = surface.project(raw)
                var d = 1e9f
                for (e in wallEdges) d = min(d, distToSegment(pt, maze.mesh.vertices[maze.edgeA[e]], maze.mesh.vertices[maze.edgeB[e]]))
                d = max(0f, d - wallHalfThickness)
                val ao = 1f - 0.7f * exp(-d / (cs * 0.2f))
                base[a * (sub + 1) + b] = mb.vertex(pt, surface.normal(pt), pal.floor * (tone * (0.45f + 0.55f * ao)))
            }
            for (a in 0 until sub) for (b in 0 until sub) {
                mb.quad(base[a * (sub + 1) + b], base[(a + 1) * (sub + 1) + b], base[(a + 1) * (sub + 1) + b + 1], base[a * (sub + 1) + b + 1])
            }
        }
        return mb
    }

    /** One half of the wall cross-section: (lateral fraction, height fraction, normal lateral, normal up). */
    private fun halfProfile(): List<FloatArray> {
        val t = wallHalfThickness
        val rr = t * 0.62f
        val list = ArrayList<FloatArray>()
        list.add(floatArrayOf(1f, -0.08f, 1f, 0f))
        list.add(floatArrayOf(1f, (wallHeight - rr) / wallHeight, 1f, 0f))
        for (k in 1..5) {
            val th = (PI / 2 * k / 6).toFloat()
            val lat = (t - rr + rr * cos(th)) / t
            val ht = (wallHeight - rr + rr * sin(th)) / wallHeight
            list.add(floatArrayOf(lat, ht, cos(th), sin(th)))
        }
        list.add(floatArrayOf((t - rr) / t, 1f, 0f, 1f))
        return list
    }

    private fun fullProfile(): List<FloatArray> {
        val half = halfProfile()
        val prof = ArrayList<FloatArray>()
        for (q in half) prof.add(floatArrayOf(-q[0], q[1], -q[2], q[3]))
        for (q in half.reversed()) prof.add(q)
        return prof
    }

    fun walls(): MeshBuilder {
        val mb = MeshBuilder()
        val prof = fullProfile()
        val lowC = pal.wall * 0.35f
        val topC = pal.wall * 1.05f
        for (e in 0 until maze.edgeCount) {
            if (!maze.isWall(e)) continue
            val dl = (maze.depthFromStart[maze.edgeCellL[e]] + maze.depthFromStart[maze.edgeCellR[e]]) * 0.5f / maze.maxDepth
            addWall(mb, e, dl * 0.75f + 0.05f * hash(e), prof, lowC, topC)
        }
        return mb
    }

    /** Sliding doors on open passages. Their "delay" attribute carries the animation phase (0..1). */
    fun doors(edges: IntArray, phases: FloatArray): MeshBuilder {
        val mb = MeshBuilder()
        val prof = fullProfile()
        val tint = pal.door
        for (i in edges.indices) addWall(mb, edges[i], phases[i].coerceIn(0f, 0.999f), prof, tint * 0.45f, tint * 1.1f)
        return mb
    }

    private fun addWall(mb: MeshBuilder, e: Int, delay: Float, prof: List<FloatArray>, lowC: Vec3, topC: Vec3) {
        val t = wallHalfThickness
        val a0 = maze.mesh.vertices[maze.edgeA[e]]
        val b0 = maze.mesh.vertices[maze.edgeB[e]]
        val dir = (b0 - a0).normalized()
        val ends = arrayOf(a0 - dir * t, b0 + dir * t)
        val rings = Array(2) { r ->
            val base = ends[r]
            val n = surface.normal(if (r == 0) a0 else b0)
            val side = (dir cross n).normalized()
            IntArray(prof.size) { k ->
                val q = prof[k]
                val pos = base + side * (q[0] * t)
                val nrm = (side * q[2] + n * q[3]).normalized()
                val col = Vec3.lerp(lowC, topC, kotlin.math.sqrt(max(0f, q[1])))
                // positions sit at the base plane; the lift vector raises them as the wall grows
                mb.vertex(pos, nrm, col, n * (q[1] * wallHeight), delay)
            }
        }
        for (k in 0 until prof.size - 1) mb.quad(rings[0][k], rings[0][k + 1], rings[1][k + 1], rings[1][k])
        for (r in 0..1) {
            val base = ends[r]
            val n = surface.normal(if (r == 0) a0 else b0)
            val side = (dir cross n).normalized()
            val cn = if (r == 0) -dir else dir
            val idx = IntArray(prof.size) { k ->
                val q = prof[k]
                mb.vertex(base + side * (q[0] * t), cn, Vec3.lerp(lowC, topC, kotlin.math.sqrt(max(0f, q[1]))) * 0.9f, n * (q[1] * wallHeight), delay)
            }
            for (k in 1 until idx.size - 1) mb.tri(idx[0], idx[k], idx[k + 1])
        }
    }

    /** Floor holes: a quad per hole; the fragment shader draws the animated opening from uv in aCol.xy, phase in aCol.z. */
    fun holes(cells: IntArray, phases: FloatArray, radius: Float): MeshBuilder {
        val mb = MeshBuilder()
        val ext = 1.4f
        for (i in cells.indices) {
            val c = cells[i]
            val ctr = maze.centers[c]
            val n = surface.normal(ctr)
            var t1 = maze.mesh.vertices[maze.mesh.cellVertex(c, 1)] - maze.mesh.vertices[maze.mesh.cellVertex(c, 0)]
            t1 = (t1 - n * (t1 dot n)).normalized()
            val t2 = n cross t1
            val ids = IntArray(4)
            val us = floatArrayOf(-1f, 1f, 1f, -1f)
            val vs = floatArrayOf(-1f, -1f, 1f, 1f)
            for (k in 0..3) {
                val p = surface.project(ctr + (t1 * us[k] + t2 * vs[k]) * (radius * ext))
                ids[k] = mb.vertex(p + surface.normal(p) * (cs * 0.01f), surface.normal(p), Vec3(us[k] * ext, vs[k] * ext, phases[i]))
            }
            mb.quad(ids[0], ids[1], ids[2], ids[3])
        }
        return mb
    }

    fun markers(doorEdges: IntArray = IntArray(0)): MeshBuilder {
        val mb = MeshBuilder()
        for (e in doorEdges) track(mb, e)
        disc(mb, maze.start, cs * 0.42f, cs * 0.30f, Vec3(0.35f, 0.75f, 1f))
        disc(mb, maze.goal, cs * 0.64f, cs * 0.40f, Vec3(0.55f, 0.42f, 0.12f), 0.0004f)
        disc(mb, maze.goal, cs * 0.40f, 0f, Vec3(1f, 0.86f, 0.30f))
        disc(mb, maze.goal, cs * 0.22f, 0f, Vec3(0.55f, 1f, 0.6f), 0.0012f)
        return mb
    }

    /** Glowing slot in the floor under a door, so you can see where it will close. */
    private fun track(mb: MeshBuilder, e: Int) {
        val a = maze.mesh.vertices[maze.edgeA[e]]
        val b = maze.mesh.vertices[maze.edgeB[e]]
        val dir = (b - a).normalized()
        val w = wallHalfThickness * 1.5f
        val col = pal.door * 0.9f
        val ids = IntArray(4)
        val pts = arrayOf(a - dir * wallHalfThickness, b + dir * wallHalfThickness)
        var k = 0
        for (pt in pts) {
            val n = surface.normal(pt)
            val side = (dir cross n).normalized() * w
            for (sgn in intArrayOf(1, -1)) {
                val p = surface.project(pt + side * sgn.toFloat())
                ids[k++] = mb.vertex(p + surface.normal(p) * (cs * 0.008f), surface.normal(p), col)
            }
        }
        mb.quad(ids[0], ids[1], ids[3], ids[2])
    }

    private fun disc(mb: MeshBuilder, cell: Int, outer: Float, inner: Float, color: Vec3, lift: Float = 0.0006f) {
        val c = maze.centers[cell]
        val n = surface.normal(c)
        var t1 = maze.mesh.vertices[maze.mesh.cellVertex(cell, 1)] - maze.mesh.vertices[maze.mesh.cellVertex(cell, 0)]
        t1 = (t1 - n * (t1 dot n)).normalized()
        val t2 = n cross t1
        val segs = 36
        fun at(r: Float, k: Int): Vec3 {
            val ang = (2 * PI * k / segs).toFloat()
            val p = surface.project(c + (t1 * cos(ang) + t2 * sin(ang)) * r)
            return p + surface.normal(p) * (cs * 0.012f + lift)
        }
        val centerIdx = if (inner <= 0f) mb.vertex(c + n * (cs * 0.012f + lift), n, color) else -1
        val ring = IntArray(segs * 2)
        for (k in 0 until segs) {
            val po = at(outer, k)
            ring[k * 2] = mb.vertex(po, surface.normal(po), color)
            if (inner > 0f) { val pi = at(inner, k); ring[k * 2 + 1] = mb.vertex(pi, surface.normal(pi), color) }
        }
        for (k in 0 until segs) {
            val k2 = (k + 1) % segs
            if (inner > 0f) mb.quad(ring[k * 2], ring[k2 * 2], ring[k2 * 2 + 1], ring[k * 2 + 1])
            else mb.tri(centerIdx, ring[k * 2], ring[k2 * 2])
        }
    }

    /** Ariadne's thread: a golden ribbon along the shortest route from [cell] to the goal. */
    fun thread(cell: Int): MeshBuilder {
        val mb = MeshBuilder()
        val path = maze.pathFrom(cell)
        if (path.size < 2) return mb
        val pts = ArrayList<Vec3>()
        pts.add(maze.centers[path[0]])
        for (k in 0 until path.size - 1) {
            val a = path[k]; val b = path[k + 1]
            for (j in 0..3) {
                val e = maze.cellEdges[a * 4 + j]
                val o = if (maze.edgeCellL[e] == a) maze.edgeCellR[e] else maze.edgeCellL[e]
                if (o == b && maze.open[e]) {
                    pts.add(surface.project((maze.mesh.vertices[maze.edgeA[e]] + maze.mesh.vertices[maze.edgeB[e]]) * 0.5f))
                    break
                }
            }
            pts.add(maze.centers[b])
        }
        val w = cs * 0.045f
        val col = Vec3(1f, 0.80f, 0.32f)
        var prevL = -1; var prevR = -1
        for (k in pts.indices) {
            val p = pts[k]
            val n = surface.normal(p)
            val tan = (pts[min(k + 1, pts.size - 1)] - pts[max(k - 1, 0)]).normalized()
            val side = (tan cross n).normalized() * w
            val lift = n * (cs * 0.02f)
            val l = mb.vertex(p + side + lift, n, col)
            val r = mb.vertex(p - side + lift, n, col)
            if (prevL >= 0) mb.quad(prevL, prevR, r, l)
            prevL = l; prevR = r
        }
        return mb
    }

    private fun hash(i: Int): Float {
        var h = i * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xffff) / 65535f
    }

    private fun distToSegment(p: Vec3, a: Vec3, b: Vec3): Float {
        val ab = b - a
        val t = (((p - a) dot ab) / ab.lengthSq()).coerceIn(0f, 1f)
        return (p - (a + ab * t)).length()
    }
}
