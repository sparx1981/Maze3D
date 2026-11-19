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
                // glowing walls light the floor beside them
                val tint = when (pal.themeId) { 17 -> (pal.wallTop ?: pal.wall) * 0.12f; 11 -> (pal.wallTop ?: pal.wall) * 0.20f; 9 -> pal.wall * 0.22f + (pal.wall2) * 0.08f; else -> Vec3.ZERO }
                val lightOn = tint * exp(-d / (cs * 0.32f))
                base[a * (sub + 1) + b] = mb.vertex(pt, surface.normal(pt), pal.floor * (tone * (0.45f + 0.55f * ao)) + lightOn, Vec3.ZERO, -1f, tile)
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
            Profile.KERB -> { ws = 1.9f; hs = 0.85f; p(1f, -0.08f); p(1f, 0.22f); p(0.72f, 0.62f); p(0.4f, 0.9f); p(0.2f, 1f); p(0f, 1f) }
            Profile.TAPER -> { ws = 1.05f; hs = 1.55f; p(1f, -0.08f); p(0.82f, 0.3f); p(0.6f, 0.62f); p(0.34f, 0.86f); p(0.1f, 1f); p(0f, 1f) }
            Profile.TUBE -> {
                ws = 1.15f; hs = 0.95f
                p(1f, -0.08f); p(1f, 0.42f)
                for (k in 1..6) { val th = (PI / 2 * k / 6).toFloat(); p(cos(th), 0.42f + 0.58f * sin(th)) }
            }
            Profile.BLOCK -> { ws = 1.35f; hs = 0.9f; p(1f, -0.08f); p(1f, 0.9f); p(0.96f, 0.975f); p(0.9f, 1f); p(0f, 1f) }
            Profile.FLAT -> { ws = 2.1f; hs = 0.8f; p(1f, -0.08f); p(1f, 0.85f); p(0.96f, 1f); p(0f, 1f) }
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
            addWall(mb, e, delay, prof, c1, c2, if (pal.stripes) 1 else if (altEdge && pal.wall2 !== pal.wall) 2 else 0, styled = true)
            if (pal.knobs) for (v in intArrayOf(maze.edgeA[e], maze.edgeB[e])) if (bulbs.add(v)) bulb(mb, v, delay, if (hash(v + 77) < 0.5f) c1 else c2)
        }
        return mb
    }

    /** Gates for Keys & Gates: solid coloured bars across the shut passages. */
    fun gates(edges: IntArray, open: BooleanArray, cols: Array<Vec3>): MeshBuilder {
        val mb = MeshBuilder()
        val prof = profileData(Profile.ROUND)
        for (i in edges.indices) {
            if (open[i]) continue
            val c = (cols[i % cols.size] * 0.55f) to (cols[i % cols.size] * 1.15f)
            addWall(mb, edges[i], 0.25f, prof, c, c, 0)
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

    // ---- themed wall shapes: walls need not be straight bars
    private object WallStyle { const val NONE = 0; const val HEDGE = 1; const val ROOT = 2; const val ROCK = 3; const val BLOCKS = 4; const val BEADS = 5; const val PILLOW = 6 }
    /** 1 = full wall detail; lower quality settings use fewer segments along the shaped walls. */
    var wallDetail = 1f
    private val wallStyle = when (pal.themeId) {
        7, 12 -> WallStyle.HEDGE          // snowy hedges, autumn hedgerows
        10, 11 -> WallStyle.ROOT          // jungle roots and vines, glowing mycelium logs
        15, 17 -> WallStyle.ROCK          // piled boulders, cooled lava rock
        4, 13, 16 -> WallStyle.BLOCKS     // hull panels, toy bricks, riveted steel sections
        8 -> WallStyle.BLOCKS             // dressed temple stone
        0, 18 -> WallStyle.BEADS          // candy ropes and gummy strings
        14 -> WallStyle.PILLOW            // stuffed, stitched fabric rolls
        else -> WallStyle.NONE
    }

    private fun lattice(x: Int, y: Int, z: Int): Float {
        var h = x * 374761393 + y * 668265263 + z * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xffff) / 32767.5f - 1f
    }
    /** Smooth value noise in -1..1. */
    private fun vnoise(p: Vec3): Float {
        val fx = kotlin.math.floor(p.x); val fy = kotlin.math.floor(p.y); val fz = kotlin.math.floor(p.z)
        val ix = fx.toInt(); val iy = fy.toInt(); val iz = fz.toInt()
        fun s(t: Float) = t * t * (3f - 2f * t)
        val ux = s(p.x - fx); val uy = s(p.y - fy); val uz = s(p.z - fz)
        fun l(a: Float, b: Float, t: Float) = a + (b - a) * t
        return l(l(l(lattice(ix, iy, iz), lattice(ix + 1, iy, iz), ux), l(lattice(ix, iy + 1, iz), lattice(ix + 1, iy + 1, iz), ux), uy),
            l(l(lattice(ix, iy, iz + 1), lattice(ix + 1, iy, iz + 1), ux), l(lattice(ix, iy + 1, iz + 1), lattice(ix + 1, iy + 1, iz + 1), ux), uy), uz)
    }

    /** mode: 0 = colour set 1, 2 = colour set 2, 1 = alternate the two along the wall (stripes). */
    private fun addWall(mb: MeshBuilder, e: Int, delay: Float, pd: ProfileData, c1: Pair<Vec3, Vec3>, c2: Pair<Vec3, Vec3>, mode: Int, styled: Boolean = false) {
        val prof = pd.pts
        val style = if (styled) wallStyle else WallStyle.NONE
        // each wall differs by a hair in width and height so overlapping joints never share a coplanar surface (z-fight flicker)
        val t = wallHalfThickness * pd.ws * (1f + 0.014f * hash(e + 4242))
        val hgt = wallHeight * pd.hs * (1f + 0.007f * hash(e + 5151))
        val a0 = maze.mesh.vertices[maze.edgeA[e]]
        val b0 = maze.mesh.vertices[maze.edgeB[e]]
        val dir = (b0 - a0).normalized()
        val ea = a0 - dir * wallHalfThickness
        val eb = b0 + dir * wallHalfThickness
        val len = (eb - ea).length()
        val stripeSegs = if (mode == 1) max(1, Math.round(len / (cs * 0.26f))) else 1
        val per = if (style == WallStyle.NONE) 1 else max(1, Math.round(len / (cs * (if (style == WallStyle.HEDGE) 0.1f else 0.13f) / wallDetail) / stripeSegs))
        val segs = stripeSegs * per
        val nk = prof.size
        val freq = 1f / (cs * 0.16f)
        val chunks = max(2, Math.round(len / (cs * (if (style == WallStyle.BLOCKS) 0.34f else 0.24f))))
        val beads = max(2, Math.round(len / (cs * (if (style == WallStyle.PILLOW) 0.32f else 0.17f))))
        // shared grid of positions (base + lift) so the surface stays watertight; normals come from the grid itself
        val basePos = Array(segs + 1) { arrayOfNulls<Vec3>(nk) }
        val liftV = Array(segs + 1) { arrayOfNulls<Vec3>(nk) }
        val tint = Array(segs + 1) { FloatArray(nk) { 1f } }
        val snow = Array(segs + 1) { FloatArray(nk) }
        val seam = FloatArray(segs + 1)
        for (r in 0..segs) {
            val f = r / segs.toFloat()
            val p = if (f <= 0f) ea else if (f >= 1f) eb else surface.project(ea + (eb - ea) * f)
            val n = surface.normal(if (f <= 0f) a0 else if (f >= 1f) b0 else p)
            val side = (dir cross n).normalized()
            val taper = kotlin.math.sin(f * PI.toFloat())
            var bend = 0f; var wsc = 1f; var hsc = 1f
            when (style) {
                WallStyle.ROOT -> { bend = vnoise(p * (freq * 0.5f) + Vec3(3.1f, 0f, 0f)) * 1.8f * taper; wsc = 1f + 0.35f * vnoise(p * freq); hsc = 1f + 0.15f * vnoise(p * (freq * 0.7f) + Vec3(7f, 1f, 2f)) }
                WallStyle.HEDGE -> { hsc = 1.05f + 0.16f * vnoise(p * (freq * 0.8f) + Vec3(5f, 2f, 1f)); wsc = 1.12f }
                WallStyle.ROCK, WallStyle.BLOCKS -> {
                    val cf = f * chunks; val ci = kotlin.math.floor(cf).toInt().coerceAtMost(chunks - 1); val fr = cf - ci
                    fun hOf(i: Int) = if (style == WallStyle.ROCK) 0.78f + 0.42f * hash(e * 31 + i) else 0.94f + 0.08f * hash(e * 31 + i)
                    fun wOf(i: Int) = if (style == WallStyle.ROCK) 0.95f + 0.4f * hash(e * 17 + i + 500) else 1.03f
                    val edgeK = if (style == WallStyle.ROCK) 0.22f else 0.08f
                    val blendL = (1f - (fr / edgeK).coerceIn(0f, 1f)).let { it * it }
                    val blendR = (1f - ((1f - fr) / edgeK).coerceIn(0f, 1f)).let { it * it }
                    val sink = if (style == WallStyle.ROCK) 0.55f else 0.85f        // stones round off where they meet
                    hsc = hOf(ci) * (1f - (1f - sink) * maxOf(blendL, blendR)); wsc = wOf(ci) * (1f - 0.25f * maxOf(blendL, blendR))
                    seam[r] = maxOf(blendL, blendR)
                }
                WallStyle.BEADS -> { val b = 0.5f + 0.5f * kotlin.math.cos(f * beads * 2f * PI.toFloat()); wsc = 0.9f + 0.32f * b; hsc = 0.94f + 0.12f * b }
                WallStyle.PILLOW -> { val b = kotlin.math.abs(kotlin.math.sin(f * beads * PI.toFloat())); wsc = 0.88f + 0.3f * b; hsc = 0.9f + 0.16f * b; seam[r] = 1f - b }
            }
            for (k in 0 until nk) {
                val q = prof[k]
                var lat = q[0] * t * wsc; var up = q[1] * hgt * hsc
                if (style == WallStyle.HEDGE && q[1] > 0.05f) {          // bushy, uneven foliage
                    val bump = vnoise(p * freq + Vec3(k * 0.61f, k * 0.37f, 0f)) * 0.5f + vnoise(p * (freq * 2.3f) + Vec3(0f, k * 0.9f, 3f)) * 0.25f
                    lat += kotlin.math.sign(q[0]) * t * 0.55f * bump * q[1]
                    up += hgt * 0.14f * bump * q[1]
                    tint[r][k] = 0.82f + 0.32f * vnoise(p * (freq * 3f) + Vec3(k * 1.3f, 0f, 9f))
                    if (pal.themeId == 7) snow[r][k] = ((q[1] - 0.62f - 0.18f * vnoise(p * (freq * 1.7f) + Vec3(k * 0.4f, 4f, 0f))) / 0.18f).coerceIn(0f, 1f)
                }
                if (style == WallStyle.ROOT) {
                    lat += kotlin.math.sign(q[0]) * t * 0.25f * vnoise(p * (freq * 1.8f) + Vec3(k * 0.7f, 2f, 0f)) * q[1]
                    tint[r][k] = 0.8f + 0.35f * vnoise(p * (freq * 2.5f) + Vec3(0f, k.toFloat(), 4f))
                }
                if (style == WallStyle.ROCK) {
                    val rough = vnoise(p * (freq * 2.2f) + Vec3(k * 0.8f, 0f, 1f))
                    lat += kotlin.math.sign(q[0]) * t * 0.18f * rough; up += hgt * 0.06f * rough * q[1]
                    tint[r][k] = 0.84f + 0.3f * vnoise(p * (freq * 1.3f) + Vec3(e.toFloat() * 0.1f, 0f, 0f))
                }
                if (style == WallStyle.BLOCKS || style == WallStyle.PILLOW) tint[r][k] = 1f - (if (style == WallStyle.BLOCKS) 0.35f else 0.25f) * seam[r]
                basePos[r][k] = p + side * (lat + bend * t)
                liftV[r][k] = n * up
            }
        }
        // normals from the deformed grid (around the profile x along the wall)
        fun posAt(r: Int, k: Int) = basePos[r][k]!! + liftV[r][k]!!
        val nrm = Array(segs + 1) { r ->
            val p0 = if (r <= 0) a0 else if (r >= segs) b0 else basePos[r][0]!!
            val n = surface.normal(p0)
            val side = (dir cross n).normalized()
            Array(nk) { k ->
                val q = prof[k]
                val baseN = (side * q[2] + n * q[3]).normalized()
                if (style == WallStyle.NONE) baseN else {
                    val ra = (r - 1).coerceAtLeast(0); val rb = (r + 1).coerceAtMost(segs)
                    val ka = (k - 1).coerceAtLeast(0); val kb = (k + 1).coerceAtMost(nk - 1)
                    val along = posAt(rb, k) - posAt(ra, k)
                    val around = posAt(r, kb) - posAt(r, ka)
                    var g = along cross around
                    if (g.lengthSq() < 1e-14f) baseN else { g = g.normalized(); if ((g dot baseN) < 0f) g = -g; (g * 0.75f + baseN * 0.25f).normalized() }
                }
            }
        }
        val snowC = Vec3(0.93f, 0.96f, 1f)
        for (sgi in 0 until segs) {
            val stripe = sgi / per
            val use2 = when (mode) { 2 -> true; 1 -> stripe % 2 == 1; else -> false }
            val (lowC, topC) = if (use2) c2 else c1
            val rings = Array(2) { rr ->
                val r = sgi + rr
                IntArray(nk) { k ->
                    val q = prof[k]
                    var col = Vec3.lerp(lowC, topC, kotlin.math.sqrt(max(0f, q[1]))) * tint[r][k]
                    if (snow[r][k] > 0f) col = Vec3.lerp(col, snowC, snow[r][k])
                    mb.vertex(basePos[r][k]!!, nrm[r][k], col, liftV[r][k]!!, delay)
                }
            }
            for (k in 0 until nk - 1) mb.quad(rings[0][k], rings[0][k + 1], rings[1][k + 1], rings[1][k])
        }
        // end caps (flat)
        for (rr in 0..1) {
            val r = rr * segs
            val cn = if (rr == 0) -dir else dir
            val (lowC, topC) = if (mode == 2 || (mode == 1 && rr == 1 && stripeSegs % 2 == 0)) c2 else c1
            val idx = IntArray(nk) { k ->
                val q = prof[k]
                var col = Vec3.lerp(lowC, topC, kotlin.math.sqrt(max(0f, q[1]))) * 0.9f * tint[r][k]
                if (snow[r][k] > 0f) col = Vec3.lerp(col, snowC, snow[r][k])
                mb.vertex(basePos[r][k]!!, cn, col, liftV[r][k]!!, delay)
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
    fun pits(cells: IntArray, phases: FloatArray, radius: Float, depth: Float, centres: List<Vec3>? = null, axes: List<Vec3>? = null, halfLen: Float = 0f): MeshBuilder {
        val mb = MeshBuilder()
        val half = 14
        val stad = halfLen > 0f && axes != null
        val seg = if (stad) 2 * half + 2 else 28
        for (i in cells.indices) {
            val c = cells[i]
            val ctr = centres?.get(i) ?: maze.centers[c]
            val n = surface.normal(ctr)
            var t1 = if (stad) axes!![i] else maze.mesh.vertices[maze.mesh.cellVertex(c, 1)] - maze.mesh.vertices[maze.mesh.cellVertex(c, 0)]
            t1 = (t1 - n * (t1 dot n)).normalized()
            val t2 = n cross t1
            val ph = phases[i]
            // outline point k at radius rr, and the outward direction there (a stadium has two half-circles joined by straight sides)
            fun outDir(k: Int): Vec3 {
                val kk = k % seg
                val ang = if (!stad) (2 * PI * kk / seg).toFloat() else if (kk <= half) (-PI / 2 + PI * kk / half).toFloat() else (PI / 2 + PI * (kk - half - 1) / half).toFloat()
                return t1 * cos(ang) + t2 * sin(ang)
            }
            fun outOff(k: Int): Vec3 = if (!stad) Vec3.ZERO else t1 * (if (k % seg <= half) halfLen else -halfLen)
            fun rimPt(k: Int, rr: Float): Vec3 = surface.project(ctr + outOff(k) + outDir(k) * rr)
            val rings = 6
            val wall = Array(rings + 1) { j ->
                IntArray(seg + 1) { k ->
                    val dir = outDir(k)
                    val top = rimPt(k, radius)
                    val d01 = j / rings.toFloat()
                    mb.vertex(top - n * (depth * d01), -dir, Vec3(d01, 0f, ph))
                }
            }
            for (j in 0 until rings) for (k in 0 until seg) mb.quad(wall[j][k], wall[j][k + 1], wall[j + 1][k + 1], wall[j + 1][k])
            // bottom
            val centre = mb.vertex(ctr - n * depth, n, Vec3(0f, 1f, ph))
            val bot = IntArray(seg + 1) { k -> mb.vertex(ctr - n * depth + outOff(k) + outDir(k) * radius, n, Vec3(1f, 1f, ph)) }
            for (k in 0 until seg) mb.tri(centre, bot[k], bot[k + 1])
            // hazard rim (an annulus lying on the floor)
            val inner = IntArray(seg + 1); val outer = IntArray(seg + 1)
            for (k in 0..seg) {
                val a01 = k / seg.toFloat()
                val pi0 = rimPt(k, radius * 0.98f); val po = rimPt(k, radius * 1.34f)
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

    /** A glowing ring on the floor with a pole and pennant above it. */
    private fun flag(mb: MeshBuilder, cell: Int, color: Vec3) {
        val c = maze.centers[cell]
        val n = surface.normal(c)
        var t1 = maze.mesh.vertices[maze.mesh.cellVertex(cell, 1)] - maze.mesh.vertices[maze.mesh.cellVertex(cell, 0)]
        t1 = (t1 - n * (t1 dot n)).normalized()
        val t2 = n cross t1
        disc(mb, cell, cs * 0.46f, cs * 0.34f, color)
        val h = cs * 1.15f; val r = cs * 0.025f
        val pole = Vec3(0.92f, 0.94f, 1f)
        val ring = Array(4) { k -> val a = k * (PI / 2).toFloat(); (t1 * cos(a) + t2 * sin(a)) * r }
        for (k in 0..3) {
            val k2 = (k + 1) % 4
            val nrm = ((ring[k] + ring[k2]) * 0.5f).normalized()
            val a = mb.vertex(c + ring[k] + n * (cs * 0.01f), nrm, pole); val b = mb.vertex(c + ring[k2] + n * (cs * 0.01f), nrm, pole)
            val d = mb.vertex(c + ring[k2] + n * h, nrm, pole); val e = mb.vertex(c + ring[k] + n * h, nrm, pole)
            mb.quad(a, b, d, e)
        }
        val top = c + n * h; val low = c + n * (h * 0.72f)
        for (side in 0..1) {
            val nn = if (side == 0) t1 else -t1
            val v0 = mb.vertex(top, nn, color); val v1 = mb.vertex(top + t2 * (cs * 0.34f) - n * (cs * 0.10f), nn, color * 1.15f); val v2 = mb.vertex(low, nn, color)
            if (side == 0) mb.tri(v0, v1, v2) else mb.tri(v0, v2, v1)
        }
    }

    fun markers(doorEdges: IntArray = IntArray(0), checkpoints: IntArray = IntArray(0), reached: Int = -1): MeshBuilder {
        val mb = MeshBuilder()
        for (e in doorEdges) track(mb, e)
        for ((i, cp) in checkpoints.withIndex()) flag(mb, cp, if (i <= reached) Vec3(0.35f, 1f, 0.5f) else Vec3(1f, 0.72f, 0.2f))
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
    private class Placed(val mesh: PropMesh, val pos: Vec3, val n: Vec3, val t2: Vec3, val size: Float, val delay: Float, val skip: Boolean = false)

    /** A moving prop (or moving part of one): [frame] maps prop units to local space (columns t1, t2, n scaled by size, then the origin). */
    class AnimInst(val spec: PropLibrary.AnimSpec?, val frame: FloatArray, val phase: Float, val delay: Float,
                   val bird: PropLibrary.Bird? = null, val radius: Float = 0f, val height: Float = 0f, val dir: Float = 1f) {
        /** 0..1: how startled the prop is by the ball rolling past (decays over time). */
        var react = 0f
    }
    val anims = ArrayList<AnimInst>()
    private fun frameOf(pos: Vec3, n: Vec3, t2in: Vec3, size: Float): FloatArray {
        val t2 = (t2in - n * (t2in dot n)).normalized(); val t1 = t2 cross n
        return floatArrayOf(t1.x * size, t1.y * size, t1.z * size, 0f, t2.x * size, t2.y * size, t2.z * size, 0f,
            n.x * size, n.y * size, n.z * size, 0f, pos.x, pos.y, pos.z, 1f)
    }
    /** Records the animation of a placed prop; returns true when the whole prop moves (so it is left out of the static mesh). */
    private fun animate(lib: PropLibrary.ThemeProps, mesh: PropMesh, pos: Vec3, n: Vec3, t2: Vec3, size: Float, delay: Float, seed: Int): Boolean {
        val spec = lib.anims[mesh] ?: return false
        if (anims.size >= 140) return false                              // keep the number of moving props (draw calls) in check
        anims.add(AnimInst(spec, frameOf(pos, n, t2, size), hash(seed + 7101) * 6.2831853f, delay))
        return spec.whole
    }

    private val placed = ArrayList<Placed>()
    /** Dead-end props the marble can bump into. */
    val obstacles = ArrayList<Obstacle>()
    /** Local-space positions of lamp posts (winter level) that light the floor around them. */
    val lamps = ArrayList<Vec3>()
    /** Colour slot (0..3) of each lamp: the four spore colours on the Glow level. */
    val lampIdx = ArrayList<Int>()
    /** Linear light colour (with strength) of each lamp, and how far it reaches in cells. */
    val lampCol = ArrayList<Vec3>()
    val lampReach = ArrayList<Float>()
    private fun themeLampCol(k: Int): Vec3 = when (pal.themeId) {
        11 -> when (k) { 0 -> Vec3(0.55f, 1f, 0.36f); 1 -> Vec3(0.30f, 1f, 0.75f); 2 -> Vec3(0.85f, 1f, 0.35f); else -> Vec3(0.25f, 0.88f, 1f) }
        9 -> if (k % 2 == 0) Vec3(1f, 0.28f, 0.85f) else Vec3(0.2f, 0.9f, 1f)
        17 -> Vec3(1f, 0.42f, 0.1f)
        else -> Vec3(0.55f, 0.72f, 1f)
    }
    private fun addLamp(v: Vec3, col: Vec3? = null, reach: Float = 4.2f) {
        val k = (hash(lamps.size * 7 + 3) * 4f).toInt().coerceIn(0, 3)
        lamps.add(v); lampIdx.add(k); lampCol.add(col ?: themeLampCol(k)); lampReach.add(reach)
    }

    /** Where a prop glows (vertices brighter than 1) and in what colour; null for a prop with no lit parts. */
    private val glowCache = HashMap<PropMesh, Pair<Vec3, Vec3>?>()
    private fun glowOf(m: PropMesh): Pair<Vec3, Vec3>? = glowCache.getOrPut(m) {
        var w = 0f; var cx = 0f; var cy = 0f; var cz = 0f; var r = 0f; var g = 0f; var b = 0f; var n = 0
        for (i in 0 until m.vertexCount) {
            val cr = m.col[i * 3]; val cg = m.col[i * 3 + 1]; val cb = m.col[i * 3 + 2]
            val vm = maxOf(cr, maxOf(cg, cb))
            if (vm <= 1.02f) continue
            val k = vm - 1f
            w += k; n++
            cx += m.pos[i * 3] * k; cy += m.pos[i * 3 + 1] * k; cz += m.pos[i * 3 + 2] * k
            r += (cr / vm).let { it * it } * k; g += (cg / vm).let { it * it } * k; b += (cb / vm).let { it * it } * k
        }
        if (n < 6 || w <= 0f) null
        else {
            val strength = (w / n * 1.6f + 0.5f).coerceIn(0.5f, 1.8f)
            Vec3(cx / w, cy / w, cz / w) to Vec3(r / w, g / w, b / w) * strength
        }
    }
    private fun lampFromProp(mesh: PropMesh, pos: Vec3, n: Vec3, t2in: Vec3, size: Float) {
        val gl = glowOf(mesh) ?: return
        val t2 = (t2in - n * (t2in dot n)).normalized(); val t1 = t2 cross n
        val c = gl.first
        addLamp(pos + t1 * (c.x * size) + t2 * (c.y * size) + n * (c.z * size + size * 0.1f), gl.second, 3.4f)
    }

    /** Decides where theme props go. Called once per level; does nothing for classic finishes. */
    fun planProps(outerRadius: Float) {
        placed.clear(); anims.clear(); obstacles.clear(); lamps.clear(); lampIdx.clear(); lampCol.clear(); lampReach.clear()
        val explicitLamps = pal.themeId == 7 || pal.themeId == 9 || pal.themeId == 11 || pal.themeId == 17
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
                if (!(v in special) && hash(v + 311) > lib.junctionShare) continue
                if (junctions > (if (lib.junctionShare > 0.3f) 160 else 70)) continue
                val p = maze.mesh.vertices[v]
                val n = surface.normal(p)
                val dl = maze.depthFromStart[maze.edgeCellL[e]].toFloat() / maze.maxDepth
                val jopts = listOfNotNull(lib.junction, lib.junction2) + lib.junctionMore
                val jm = jopts[(hash(v + 19) * jopts.size).toInt().coerceAtMost(jopts.size - 1)]
                val jt = tangent(n, hash(v + 5))
                val jScale = if (jm in lib.junctionMore) 2.2f else 1f            // plants are smaller models than toppers
                val jw = animate(lib, jm, p + n * topH, n, jt, sizeJ * jScale, dl * 0.75f + 0.05f, v)
                placed.add(Placed(jm, p + n * topH, n, jt, sizeJ * jScale, dl * 0.75f + 0.05f, jw))
                if (!explicitLamps) lampFromProp(jm, p + n * topH, n, jt, sizeJ)
                if (pal.themeId == 7 || pal.themeId == 9 || pal.themeId == 11 || pal.themeId == 17) addLamp(p + n * (topH + sizeJ * (if (pal.themeId == 7) 0.95f else 0.7f)))
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
            val opts = listOfNotNull(lib.deadA, lib.deadB, lib.deadC) + lib.more
            val mesh = opts[(hash(c + 13) * opts.size).toInt().coerceAtMost(opts.size - 1)]
            val dd = maze.depthFromStart[c].toFloat() / maze.maxDepth * 0.75f + 0.05f
            val dw = animate(lib, mesh, p, surface.normal(p), -dir, size, dd, c + 50000)
            placed.add(Placed(mesh, p, surface.normal(p), -dir, size, dd, dw))
            obstacles.add(Obstacle(c, p, size * 0.4f))
            if (!explicitLamps) lampFromProp(mesh, p, surface.normal(p), -dir, size)
            if (pal.themeId == 9 || pal.themeId == 11 || pal.themeId == 17) addLamp(p + surface.normal(p) * (size * 0.55f))
            deads++
        }
        // 2b. undergrowth: small plants tucked against the foot of the walls (jungle)
        if (lib.floor.isNotEmpty()) {
            var n = 0
            for (c in 0 until maze.cellCount) {
                if (c == maze.start || c == maze.goal || hash(c + 4242) > 0.38f || n > 110) continue
                val walls = (0..3).map { maze.cellEdges[c * 4 + it] }.filter { maze.isWall(it) }
                if (walls.isEmpty()) continue
                val e = walls[(hash(c + 77) * walls.size).toInt().coerceAtMost(walls.size - 1)]
                val mid = (maze.mesh.vertices[maze.edgeA[e]] + maze.mesh.vertices[maze.edgeB[e]]) * 0.5f
                val along = (maze.mesh.vertices[maze.edgeB[e]] - maze.mesh.vertices[maze.edgeA[e]]) * ((hash(c + 91) - 0.5f) * 0.6f)
                val p = surface.project(Vec3.lerp(maze.centers[c], mid + along, 0.74f))
                val n0 = surface.normal(p)
                val mesh = lib.floor[(hash(c + 13) * lib.floor.size).toInt().coerceAtMost(lib.floor.size - 1)]
                placed.add(Placed(mesh, p, n0, tangent(n0, hash(c + 5)), cs * (0.95f + 0.45f * hash(c + 3)), maze.depthFromStart[c].toFloat() / maze.maxDepth * 0.75f + 0.05f))
                n++
            }
        }
        // 3. landmarks floating around the shape
        val count = 7
        for (i in 0 until count) {
            // fibonacci-ish directions so they spread over all sides
            val y = 1f - 2f * (i + 0.5f) / count
            val r = kotlin.math.sqrt(1f - y * y); val phi = i * 2.399963f
            val d = Vec3(cos(phi) * r, y, sin(phi) * r).normalized()
            val size = outerRadius * 0.4f
            val lt = tangent(d, hash(i + 4000))
            val lw = animate(lib, lib.landmark, d * (outerRadius * 1.30f), d, lt, size, 0.9f, i + 90000)
            placed.add(Placed(lib.landmark, d * (outerRadius * 1.30f), d, lt, size, 0.9f, lw))
        }
        // 4. a few birds (or butterflies, drones...) circling over the maze
        for ((fi, bd) in listOfNotNull(lib.bird, lib.birds2).withIndex()) {
            for (k0 in 0 until bd.count) { val k = k0 + fi * 50
                val c = (hash(k * 977 + 31) * maze.cellCount).toInt().coerceIn(0, maze.cellCount - 1)
                val ctr = maze.centers[c]; val n = surface.normal(ctr)
                anims.add(AnimInst(null, frameOf(ctr, n, tangent(n, hash(k + 61)), cs), hash(k + 3) * 6.2831853f, 0.6f, bd,
                    radius = 1.6f + 2.4f * hash(k + 17), height = bd.height * (0.8f + 0.5f * hash(k + 29)), dir = if (hash(k + 41) < 0.5f) -1f else 1f))
            }
        }
    }

    /** Debug: the four props of this theme side by side, upright, at their natural size. */
    fun gallery(): MeshBuilder {
        val mb = MeshBuilder()
        val lib = PropLibrary.forTheme(pal.themeId) ?: return mb
        val list = listOfNotNull(lib.junction, lib.deadA, lib.deadB, lib.landmark, lib.deadC, lib.junction2) + lib.more
        fun put(m: PropMesh, o: Vec3, sc: Float = 1f) {
            val base = mb.vertexCount
            for (i in 0 until m.vertexCount)
                mb.vertex(Vec3(m.pos[i * 3] * sc + o.x, m.pos[i * 3 + 1] * sc + o.y, m.pos[i * 3 + 2] * sc + o.z), Vec3(m.nrm[i * 3], m.nrm[i * 3 + 1], m.nrm[i * 3 + 2]), Vec3(m.col[i * 3], m.col[i * 3 + 1], m.col[i * 3 + 2]))
            var q = 0
            while (q < m.idx.size) { mb.tri(base + m.idx[q], base + m.idx[q + 1], base + m.idx[q + 2]); q += 3 }
        }
        val n = list.size + (if (lib.bird != null) 1 else 0)
        for ((k, m) in list.withIndex()) {
            val ox = (k - (n - 1) / 2f) * 1.15f
            put(m, Vec3(ox, 0f, 0f))
            lib.anims[m]?.let { sp -> if (!sp.whole) for (o in sp.at) put(sp.part, Vec3(ox + o.x, o.y, o.z)) }
        }
        lib.bird?.let { bd ->
            val o = Vec3((list.size - (n - 1) / 2f) * 1.15f, 0f, 0.5f)
            for (m in listOf(bd.body, bd.wingL, bd.wingR)) put(m, o, 2f)
        }
        return mb
    }

    /** Glowing mycelium threads along the floor between nearby glowing mushrooms (Glow level). */
    fun mycelium(): MeshBuilder {
        val mb = MeshBuilder()
        val pts = lamps.map { surface.project(it) }
        var segs = 0
        for (i in pts.indices) for (j in i + 1 until pts.size) {
            if (segs > 160) break
            val d = (pts[i] - pts[j]).length()
            if (d > cs * 3.4f || d < cs * 0.4f) continue
            if (hash(i * 131 + j) > 0.55f) continue
            segs++
            val steps = 9
            var prevL = -1; var prevR = -1
            for (k in 0..steps) {
                val f = k / steps.toFloat()
                var q = surface.project(Vec3.lerp(pts[i], pts[j], f))
                val nn = surface.normal(q)
                val side = ((pts[j] - pts[i]) cross nn).normalized()
                q = surface.project(q + side * (kotlin.math.sin(f * 9f + i.toFloat()) * cs * 0.05f))
                val w = cs * 0.022f * (0.35f + 0.65f * kotlin.math.sin(f * PI.toFloat()))
                val lift = nn * (cs * 0.01f)
                val col = Vec3(0.30f, 1.0f, 0.75f) * (0.8f + 0.5f * kotlin.math.sin(f * 6f))
                val l = mb.vertex(q - side * w + lift, nn, col); val r = mb.vertex(q + side * w + lift, nn, col)
                if (prevL >= 0) mb.quad(prevL, prevR, r, l)
                prevL = l; prevR = r
            }
        }
        return mb
    }

    fun props(): MeshBuilder {
        val mb = MeshBuilder()
        for (pl in placed) {
            if (pl.skip) continue
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
