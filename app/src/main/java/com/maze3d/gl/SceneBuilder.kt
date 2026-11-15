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
    val wallHeight = cs * 0.36f
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

    fun walls(): MeshBuilder {
        val mb = MeshBuilder()
        val t = wallHalfThickness
        val sink = cs * 0.03f
        for (e in 0 until maze.edgeCount) {
            if (!maze.isWall(e)) continue
            val a0 = maze.mesh.vertices[maze.edgeA[e]]
            val b0 = maze.mesh.vertices[maze.edgeB[e]]
            val dir = (b0 - a0).normalized()
            val a = a0 - dir * t
            val b = b0 + dir * t
            val na = surface.normal(a0)
            val nb = surface.normal(b0)
            val sa = (dir cross na).normalized() * t
            val sb = (dir cross nb).normalized() * t
            val dl = (maze.depthFromStart[maze.edgeCellL[e]] + maze.depthFromStart[maze.edgeCellR[e]]) * 0.5f / maze.maxDepth
            val delay = dl * 0.75f + 0.05f * hash(e)

            val lowC = pal.wall * 0.42f
            val topC = pal.wall * 1.12f
            val midC = pal.wall * 0.8f
            val hA = na * wallHeight
            val hB = nb * wallHeight
            val dA = na * sink
            val dB = nb * sink

            fun base(p: Vec3, s: Vec3, dn: Vec3) = p + s - dn
            val a1 = base(a, sa, dA); val a2 = base(a, -sa, dA)
            val b1 = base(b, sb, dB); val b2 = base(b, -sb, dB)
            val z = Vec3.ZERO

            fun face(p0: Vec3, p1: Vec3, l0: Vec3, l1: Vec3, n: Vec3, c0: Vec3, c1: Vec3, flip: Boolean) {
                val i0 = mb.vertex(p0, n, c0, z, delay)
                val i1 = mb.vertex(p1, n, c0, z, delay)
                val i2 = mb.vertex(p1, n, c1, l1, delay)
                val i3 = mb.vertex(p0, n, c1, l0, delay)
                if (flip) mb.quad(i0, i3, i2, i1) else mb.quad(i0, i1, i2, i3)
            }
            val nAvg = (na + nb).normalized()
            // side +
            face(a1, b1, hA, hB, sa.normalized(), lowC, midC, false)
            // side -
            face(a2, b2, hA, hB, -sa.normalized(), lowC, midC, true)
            // end caps
            face(a1, a2, hA, hA, -dir, lowC, midC, false)
            face(b1, b2, hB, hB, dir, lowC, midC, true)
            // top: four corners lifted, fully on the lift vector
            val t0 = mb.vertex(a1, nAvg, topC, hA, delay)
            val t1 = mb.vertex(b1, nAvg, topC, hB, delay)
            val t2 = mb.vertex(b2, nAvg, topC, hB, delay)
            val t3 = mb.vertex(a2, nAvg, topC, hA, delay)
            mb.quad(t0, t1, t2, t3)
        }
        return mb
    }

    fun markers(): MeshBuilder {
        val mb = MeshBuilder()
        disc(mb, maze.start, cs * 0.42f, cs * 0.30f, Vec3(0.35f, 0.75f, 1f))
        disc(mb, maze.goal, cs * 0.64f, cs * 0.40f, Vec3(0.55f, 0.42f, 0.12f), 0.0004f)
        disc(mb, maze.goal, cs * 0.40f, 0f, Vec3(1f, 0.86f, 0.30f))
        disc(mb, maze.goal, cs * 0.22f, 0f, Vec3(0.55f, 1f, 0.6f), 0.0012f)
        return mb
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
