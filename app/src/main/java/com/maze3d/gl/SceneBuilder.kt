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

    fun floor(tiles: Boolean = false): MeshBuilder {
        val mb = MeshBuilder(if (tiles) MeshBuilder.STRIDE + 3 else MeshBuilder.STRIDE)
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
                var tile: Vec3? = null
                if (tiles) { val dd = pt - maze.centers[c]; tile = Vec3((dd dot maze.axisU[c]) + 0.5f, (dd dot maze.axisV[c]) + 0.5f, c.toFloat()) }
                base[a * (sub + 1) + b] = mb.vertex(pt, surface.normal(pt), pal.floor * (tone * (0.45f + 0.55f * ao)), Vec3.ZERO, -1f, tile)
            }
            for (a in 0 until sub) for (b in 0 until sub) {
                mb.quad(base[a * (sub + 1) + b], base[(a + 1) * (sub + 1) + b], base[(a + 1) * (sub + 1) + b + 1], base[a * (sub + 1) + b + 1])
            }
        }
        return mb
    }

    /** A wall cross-section: points (lateral in wall half-widths, height in wall heights, normal x, normal y) around the whole loop. */
    private class ProfileData(val pts: List<FloatArray>, val ws: Float, val hs: Float)

    private val profiles = HashMap<Int, ProfileData>()

    private fun profileData(kind: Int): ProfileData = profiles.getOrPut(kind) { buildProfile(kind) }

    private fun buildProfile(kind: Int): ProfileData {
        val half = ArrayList<FloatArray>()   // (lat, v) from the base up to the top centre
        var ws = 1f; var hs = 1f
        fun p(lat: Float, v: Float) { half.add(floatArrayOf(lat, v, 0f, 0f)) }
        when (kind) {
            Profile.SQUARE -> { ws = 1.15f; hs = 1f; p(1f, -0.08f); p(1f, 0.93f); p(0.95f, 0.985f); p(0.86f, 1f); p(0f, 1f) }
            Profile.KERB -> { ws = 1.9f; hs = 0.5f; p(1f, -0.08f); p(1f, 0.22f); p(0.72f, 0.62f); p(0.4f, 0.9f); p(0.2f, 1f); p(0f, 1f) }
            Profile.TAPER -> { ws = 1.05f; hs = 1.55f; p(1f, -0.08f); p(0.82f, 0.3f); p(0.6f, 0.62f); p(0.34f, 0.86f); p(0.1f, 1f); p(0f, 1f) }
            Profile.TUBE -> {
                ws = 1.15f; hs = 0.95f
                p(1f, -0.08f); p(1f, 0.42f)
                for (k in 1..6) { val th = (PI / 2 * k / 6).toFloat(); p(cos(th), 0.42f + 0.58f * sin(th)) }
            }
            Profile.BLOCK -> { ws = 1.35f; hs = 0.9f; p(1f, -0.08f); p(1f, 0.9f); p(0.96f, 0.975f); p(0.9f, 1f); p(0f, 1f) }
            Profile.FLAT -> { ws = 2.1f; hs = 0.22f; p(1f, -0.08f); p(1f, 0.85f); p(0.96f, 1f); p(0f, 1f) }
            else -> {                                   // ROUND
                val rr = 0.62f
                p(1f, -0.08f); p(1f, 1f - rr * 0.4f)
                for (k in 1..5) { val th = (PI / 2 * k / 6).toFloat(); p(1f - rr + rr * cos(th), 1f - rr * 0.4f + rr * 0.4f * sin(th)) }
                p(1f - rr, 1f); p(0f, 1f)
            }
        }
        // full loop: left side up and over, then down the right side
        val loop = ArrayList<FloatArray>()
        for (q in half) loop.add(floatArrayOf(-q[0], q[1], 0f, 0f))
        for (q in half.reversed()) loop.add(floatArrayOf(q[0], q[1], 0f, 0f))
        // smooth normals from neighbouring segments, measured in world units so wide/low walls shade correctly
        val t = wallHalfThickness * ws; val h = wallHeight * hs
        for (i in loop.indices) {
            fun segNormal(a: FloatArray, b: FloatArray): Vec3 {
                val dx = (b[0] - a[0]) * t; val dy = (b[1] - a[1]) * h
                val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9f)
                return Vec3(-dy / len, dx / len, 0f)            // outward normal of a left-up-over-right loop
            }
            var nrm = Vec3.ZERO
            if (i > 0) nrm += segNormal(loop[i - 1], loop[i])
            if (i < loop.size - 1) nrm += segNormal(loop[i], loop[i + 1])
            nrm = nrm.normalized()
            loop[i][2] = nrm.x; loop[i][3] = nrm.y
        }
        return ProfileData(loop, ws, hs)
    }

    private fun wallColors(base: Vec3): Pair<Vec3, Vec3> {
        val top = (pal.wallTop ?: base) * (1.05f * pal.glow)
        val low = base * (0.35f * (if (pal.glow > 1.5f) 2.4f else 1f))
        return low to top
    }

    fun walls(): MeshBuilder {
        val mb = MeshBuilder()
        val prof = profileData(pal.profile)
        val bulbs = HashSet<Int>()
        for (e in 0 until maze.edgeCount) {
            if (!maze.isWall(e)) continue
            val dl = (maze.depthFromStart[maze.edgeCellL[e]] + maze.depthFromStart[maze.edgeCellR[e]]) * 0.5f / maze.maxDepth
            val delay = dl * 0.75f + 0.05f * hash(e)
            val altEdge = hash(e + 9000) < 0.5f
            val c1 = wallColors(pal.wall); val c2 = wallColors(pal.wall2)
            addWall(mb, e, delay, prof, c1, c2, if (pal.stripes) 1 else if (altEdge && pal.wall2 !== pal.wall) 2 else 0)
            if (pal.knobs) for (v in intArrayOf(maze.edgeA[e], maze.edgeB[e])) if (bulbs.add(v)) bulb(mb, v, delay, if (hash(v + 77) < 0.5f) c1 else c2)
        }
        return mb
    }

    /** Sliding doors on open passages. Their "delay" attribute carries the animation phase (0..1). */
    fun doors(edges: IntArray, phases: FloatArray): MeshBuilder {
        val mb = MeshBuilder()
        val prof = profileData(Profile.ROUND)
        val tint = pal.door
        val c = (tint * 0.45f) to (tint * 1.1f)
        for (i in edges.indices) addWall(mb, edges[i], phases[i].coerceIn(0f, 0.999f), prof, c, c, 0)
        return mb
    }

    /** mode: 0 = colour set 1, 2 = colour set 2, 1 = alternate the two along the wall (stripes). */
    private fun addWall(mb: MeshBuilder, e: Int, delay: Float, pd: ProfileData, c1: Pair<Vec3, Vec3>, c2: Pair<Vec3, Vec3>, mode: Int) {
        val prof = pd.pts
        val t = wallHalfThickness * pd.ws
        val hgt = wallHeight * pd.hs
        val a0 = maze.mesh.vertices[maze.edgeA[e]]
        val b0 = maze.mesh.vertices[maze.edgeB[e]]
        val dir = (b0 - a0).normalized()
        val ea = a0 - dir * wallHalfThickness
        val eb = b0 + dir * wallHalfThickness
        val len = (eb - ea).length()
        val segs = if (mode == 1) max(1, Math.round(len / (cs * 0.26f))) else 1
        fun ringAt(f: Float): Triple<Vec3, Vec3, Vec3> {              // position, normal, side
            val p = if (f <= 0f) ea else if (f >= 1f) eb else surface.project(ea + (eb - ea) * f)
            val n = surface.normal(if (f <= 0f) a0 else if (f >= 1f) b0 else p)
            return Triple(p, n, (dir cross n).normalized())
        }
        for (sgi in 0 until segs) {
            val use2 = when (mode) { 2 -> true; 1 -> sgi % 2 == 1; else -> false }
            val (lowC, topC) = if (use2) c2 else c1
            val rings = Array(2) { r ->
                val (base, n, side) = ringAt((sgi + r) / segs.toFloat())
                IntArray(prof.size) { k ->
                    val q = prof[k]
                    val pos = base + side * (q[0] * t)
                    val nrm = (side * q[2] + n * q[3]).normalized()
                    val col = Vec3.lerp(lowC, topC, kotlin.math.sqrt(max(0f, q[1])))
                    mb.vertex(pos, nrm, col, n * (q[1] * hgt), delay)
                }
            }
            for (k in 0 until prof.size - 1) mb.quad(rings[0][k], rings[0][k + 1], rings[1][k + 1], rings[1][k])
        }
        // end caps (flat)
        for (r in 0..1) {
            val (base, n, side) = ringAt(r.toFloat())
            val cn = if (r == 0) -dir else dir
            val (lowC, topC) = if (mode == 2 || (mode == 1 && r == 1 && segs % 2 == 0)) c2 else c1
            val idx = IntArray(prof.size) { k ->
                val q = prof[k]
                mb.vertex(base + side * (q[0] * t), cn, Vec3.lerp(lowC, topC, kotlin.math.sqrt(max(0f, q[1]))) * 0.9f, n * (q[1] * hgt), delay)
            }
            for (k in 1 until idx.size - 1) mb.tri(idx[0], idx[k], idx[k + 1])
        }
    }

    /** A rounded knuckle / bead on top of a wall joint (bone ends, glass beads). */
    private fun bulb(mb: MeshBuilder, vertex: Int, delay: Float, cols: Pair<Vec3, Vec3>) {
        val p = maze.mesh.vertices[vertex]
        val n = surface.normal(p)
        val a = if (kotlin.math.abs(n.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
        val t1 = (a cross n).normalized(); val t2 = n cross t1
        val r = wallHalfThickness * 2.0f
        val centerH = wallHeight * 0.55f
        val st = 7; val sl = 10
        val ids = Array(st + 1) { i ->
            val phi = (PI * i / st).toFloat()                 // 0 = pole up
            IntArray(sl + 1) { j ->
                val th = (2 * PI * j / sl).toFloat()
                val dx = sin(phi) * cos(th); val dy = sin(phi) * sin(th); val dz = cos(phi)
                val nrm = (t1 * dx + t2 * dy + n * dz).normalized()
                val pos = p + t1 * (dx * r) + t2 * (dy * r)
                val col = Vec3.lerp(cols.first, cols.second, (0.5f + 0.5f * dz))
                mb.vertex(pos, nrm, col, n * (centerH + dz * r), delay)
            }
        }
        for (i in 0 until st) for (j in 0 until sl) mb.quad(ids[i][j], ids[i][j + 1], ids[i + 1][j + 1], ids[i + 1][j])
    }

    /** One quad per puddle; the shader draws the water from uv (aCol.xy) and the puddle id (aCol.z). */
    fun puddles(cells: IntArray, radius: Float): MeshBuilder {
        val mb = MeshBuilder()
        val ext = 1.3f
        val g = 8                                         // grid so the water follows the curved floor
        for (i in cells.indices) {
            val c = cells[i]
            val ctr = maze.centers[c]
            val n0 = surface.normal(ctr)
            var t1 = maze.mesh.vertices[maze.mesh.cellVertex(c, 1)] - maze.mesh.vertices[maze.mesh.cellVertex(c, 0)]
            t1 = (t1 - n0 * (t1 dot n0)).normalized()
            val t2 = n0 cross t1
            val ids = IntArray((g + 1) * (g + 1))
            for (a in 0..g) for (b in 0..g) {
                val u = (a / g.toFloat() * 2f - 1f)
                val v = (b / g.toFloat() * 2f - 1f)
                val p = surface.project(ctr + (t1 * u + t2 * v) * (radius * ext))
                val n = surface.normal(p)
                ids[a * (g + 1) + b] = mb.vertex(p + n * (cs * 0.012f), n, Vec3(u * ext, v * ext, i.toFloat()))
            }
            for (a in 0 until g) for (b in 0 until g)
                mb.quad(ids[a * (g + 1) + b], ids[(a + 1) * (g + 1) + b], ids[(a + 1) * (g + 1) + b + 1], ids[a * (g + 1) + b + 1])
        }
        return mb
    }

    /** Shaft wall, glowing bottom and hazard rim for each hole (see Shaders.PIT_FS for the vertex encoding). */
    fun pits(cells: IntArray, phases: FloatArray, radius: Float, depth: Float): MeshBuilder {
        val mb = MeshBuilder()
        val seg = 28
        for (i in cells.indices) {
            val c = cells[i]
            val ctr = maze.centers[c]
            val n = surface.normal(ctr)
            var t1 = maze.mesh.vertices[maze.mesh.cellVertex(c, 1)] - maze.mesh.vertices[maze.mesh.cellVertex(c, 0)]
            t1 = (t1 - n * (t1 dot n)).normalized()
            val t2 = n cross t1
            val ph = phases[i]
            fun rimPt(k: Int, rr: Float): Vec3 {
                val ang = (2 * PI * k / seg).toFloat()
                val p = surface.project(ctr + (t1 * cos(ang) + t2 * sin(ang)) * rr)
                return p
            }
            // shaft: three rings so the gradient and seam lines sample smoothly
            val rings = 6
            val wall = Array(rings + 1) { j ->
                IntArray(seg + 1) { k ->
                    val ang = (2 * PI * k / seg).toFloat()
                    val dir = t1 * cos(ang) + t2 * sin(ang)
                    val top = rimPt(k, radius)
                    val d01 = j / rings.toFloat()
                    val pos = top - n * (depth * d01) - dir * (0f)
                    mb.vertex(pos, -dir, Vec3(d01, 0f, ph))
                }
            }
            for (j in 0 until rings) for (k in 0 until seg) mb.quad(wall[j][k], wall[j][k + 1], wall[j + 1][k + 1], wall[j + 1][k])
            // bottom disc
            val centre = mb.vertex(ctr - n * depth, n, Vec3(0f, 1f, ph))
            val bot = IntArray(seg + 1) { k ->
                val ang = (2 * PI * k / seg).toFloat()
                mb.vertex(ctr - n * depth + (t1 * cos(ang) + t2 * sin(ang)) * radius, n, Vec3(1f, 1f, ph))
            }
            for (k in 0 until seg) mb.tri(centre, bot[k], bot[k + 1])
            // hazard rim (an annulus lying on the floor)
            val inner = IntArray(seg + 1); val outer = IntArray(seg + 1)
            for (k in 0..seg) {
                val a01 = k / seg.toFloat()
                val pi0 = rimPt(k % seg, radius * 0.98f); val po = rimPt(k % seg, radius * 1.34f)
                inner[k] = mb.vertex(pi0 + surface.normal(pi0) * (cs * 0.012f), surface.normal(pi0), Vec3(a01, 2.0f, ph))
                outer[k] = mb.vertex(po + surface.normal(po) * (cs * 0.012f), surface.normal(po), Vec3(a01, 2.99f, ph))
            }
            for (k in 0 until seg) mb.quad(inner[k], inner[k + 1], outer[k + 1], outer[k])
        }
        return mb
    }

    /** Two half-disc lids per hole that slide apart along t1; aLift carries the slide vector, aDelay the phase. */
    fun lids(cells: IntArray, phases: FloatArray, radius: Float): MeshBuilder {
        val mb = MeshBuilder()
        val seg = 14
        val lift = cs * 0.018f
        for (i in cells.indices) {
            val c = cells[i]
            val ctr = maze.centers[c]
            val n = surface.normal(ctr)
            var t1 = maze.mesh.vertices[maze.mesh.cellVertex(c, 1)] - maze.mesh.vertices[maze.mesh.cellVertex(c, 0)]
            t1 = (t1 - n * (t1 dot n)).normalized()
            val t2 = n cross t1
            val ph = phases[i].coerceIn(0f, 0.999f)
            val rr = radius * 1.04f
            for (side in intArrayOf(-1, 1)) {
                val slide = t1 * (side * radius * 1.3f)
                // fan of the half disc on the side where u has the sign [side]
                val cn = mb.vertex(ctr + n * lift, n, Vec3(0f, 0f, 0f), slide, ph)
                val arc = IntArray(seg + 1)
                for (k in 0..seg) {
                    val ang = (-PI / 2 + PI * k / seg).toFloat()
                    val u = cos(ang) * side; val v = sin(ang)
                    val p = surface.project(ctr + (t1 * u + t2 * v) * rr)
                    arc[k] = mb.vertex(p + surface.normal(p) * lift, surface.normal(p), Vec3(u, v, 0f), slide, ph)
                }
                for (k in 0 until seg) mb.tri(cn, arc[k], arc[k + 1])
                // thin skirt down the straight cut so the slab has thickness
                val e0 = ctr + t2 * (-rr) ; val e1 = ctr + t2 * rr
                val a0 = surface.project(e0); val a1 = surface.project(e1)
                val v0 = mb.vertex(a0 + surface.normal(a0) * lift, t1 * side.toFloat(), Vec3(0f, 0f, 0f), slide, ph)
                val v1 = mb.vertex(a1 + surface.normal(a1) * lift, t1 * side.toFloat(), Vec3(0f, 0f, 0f), slide, ph)
                val v2 = mb.vertex(a1 - surface.normal(a1) * (cs * 0.01f), t1 * side.toFloat(), Vec3(0f, 0f, 0f), slide, ph)
                val v3 = mb.vertex(a0 - surface.normal(a0) * (cs * 0.01f), t1 * side.toFloat(), Vec3(0f, 0f, 0f), slide, ph)
                mb.quad(v0, v1, v2, v3)
            }
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


    // ------------------------------------------------------------------ theme props
    class Obstacle(val cell: Int, val pos: Vec3, val radius: Float)
    private class Placed(val mesh: PropMesh, val pos: Vec3, val n: Vec3, val t2: Vec3, val size: Float, val delay: Float)

    private val placed = ArrayList<Placed>()
    /** Dead-end props the marble can bump into. */
    val obstacles = ArrayList<Obstacle>()

    /** Decides where theme props go. Called once per level; does nothing for classic finishes. */
    fun planProps(outerRadius: Float) {
        placed.clear(); obstacles.clear()
        val lib = PropLibrary.forTheme(pal.themeId) ?: return
        val prof = profileData(pal.profile)
        val topH = wallHeight * prof.hs
        fun tangent(n: Vec3, seed: Float): Vec3 {
            val a = if (kotlin.math.abs(n.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
            val t = (a cross n).normalized(); val u = n cross t
            val ang = seed * 6.2831853f
            return (t * cos(ang) + u * sin(ang)).normalized()
        }
        // 1. wall junctions: a small topper on a share of the joints, always on the corners of start and goal
        val seen = HashSet<Int>()
        val special = HashSet<Int>()
        for (c in intArrayOf(maze.start, maze.goal)) for (k in 0..3) special.add(maze.mesh.cellVertex(c, k))
        val sizeJ = wallHalfThickness * 9f
        var junctions = 0
        for (e in 0 until maze.edgeCount) {
            if (!maze.isWall(e)) continue
            for (v in intArrayOf(maze.edgeA[e], maze.edgeB[e])) {
                if (!seen.add(v)) continue
                if (!(v in special) && hash(v + 311) > 0.2f) continue
                if (junctions > 70) continue
                val p = maze.mesh.vertices[v]
                val n = surface.normal(p)
                val dl = maze.depthFromStart[maze.edgeCellL[e]].toFloat() / maze.maxDepth
                placed.add(Placed(lib.junction, p + n * topH, n, tangent(n, hash(v + 5)), sizeJ, dl * 0.75f + 0.05f))
                junctions++
            }
        }
        // 2. dead ends: an object standing at the closed end of the passage
        var deads = 0
        for (c in 0 until maze.cellCount) {
            if (maze.links[c].size != 1 || c == maze.start || c == maze.goal) continue
            if (hash(c + 977) > 0.7f || deads >= 26) continue
            val centre = maze.centers[c]
            val toward = (maze.centers[maze.links[c][0]] - centre).normalized()
            val n = surface.normal(centre)
            val dir = (toward - n * (toward dot n)).normalized()
            val p = surface.project(centre - dir * (cs * 0.14f))
            val size = cs * 0.78f
            val mesh = if (hash(c + 13) < 0.5f) lib.deadA else lib.deadB
            placed.add(Placed(mesh, p, surface.normal(p), -dir, size, maze.depthFromStart[c].toFloat() / maze.maxDepth * 0.75f + 0.05f))
            obstacles.add(Obstacle(c, p, size * 0.4f))
            deads++
        }
        // 3. landmarks floating around the shape
        val count = 7
        for (i in 0 until count) {
            // fibonacci-ish directions so they spread over all sides
            val y = 1f - 2f * (i + 0.5f) / count
            val r = kotlin.math.sqrt(1f - y * y); val phi = i * 2.399963f
            val d = Vec3(cos(phi) * r, y, sin(phi) * r).normalized()
            val size = outerRadius * 0.4f
            placed.add(Placed(lib.landmark, d * (outerRadius * 1.30f), d, tangent(d, hash(i + 4000)), size, 0.9f))
        }
    }

    /** Debug: the four props of this theme side by side, upright, at their natural size. */
    fun gallery(): MeshBuilder {
        val mb = MeshBuilder()
        val lib = PropLibrary.forTheme(pal.themeId) ?: return mb
        val list = listOf(lib.junction, lib.deadA, lib.deadB, lib.landmark)
        for ((k, m) in list.withIndex()) {
            val base = mb.vertexCount
            val ox = (k - 1.5f) * 1.15f
            for (i in 0 until m.vertexCount)
                mb.vertex(Vec3(m.pos[i * 3] + ox, m.pos[i * 3 + 1], m.pos[i * 3 + 2]), Vec3(m.nrm[i * 3], m.nrm[i * 3 + 1], m.nrm[i * 3 + 2]), Vec3(m.col[i * 3], m.col[i * 3 + 1], m.col[i * 3 + 2]))
            var q = 0
            while (q < m.idx.size) { mb.tri(base + m.idx[q], base + m.idx[q + 1], base + m.idx[q + 2]); q += 3 }
        }
        return mb
    }

    fun props(): MeshBuilder {
        val mb = MeshBuilder()
        for (pl in placed) {
            val t2 = (pl.t2 - pl.n * (pl.t2 dot pl.n)).normalized()
            val t1 = t2 cross pl.n
            val m = pl.mesh
            val sink = pl.size * 1.25f
            val base = mb.vertexCount
            for (i in 0 until m.vertexCount) {
                val x = m.pos[i * 3]; val y = m.pos[i * 3 + 1]; val z = m.pos[i * 3 + 2]
                val nx = m.nrm[i * 3]; val ny = m.nrm[i * 3 + 1]; val nz = m.nrm[i * 3 + 2]
                val p = pl.pos + t1 * (x * pl.size) + t2 * (y * pl.size) + pl.n * (z * pl.size)
                val nn = (t1 * nx + t2 * ny + pl.n * nz).normalized()
                mb.vertex(p - pl.n * sink, nn, Vec3(m.col[i * 3], m.col[i * 3 + 1], m.col[i * 3 + 2]), pl.n * sink, pl.delay)
            }
            val ix = m.idx
            var k = 0
            while (k < ix.size) { mb.tri(base + ix[k], base + ix[k + 1], base + ix[k + 2]); k += 3 }
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
