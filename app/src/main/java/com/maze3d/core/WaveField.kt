package com.maze3d.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Water-surface wave simulation: the 2D wave equation on a small tile of texels per maze cell.
 * Waves pass between neighbouring cells through open passages and bounce off walls. Raindrops and the rolling
 * ball disturb the surface; only tiles that are disturbed (or next to a disturbed tile) are stepped.
 */
class WaveField(private val maze: Maze, val t: Int = 12) {
    val tile = t + 2
    val perRow = (1008 / tile).coerceAtLeast(1)
    val rows = (maze.cellCount + perRow - 1) / perRow
    val atlasW = perRow * tile
    val atlasH = rows * tile
    private val tex = tile * tile
    val h = FloatArray(maze.cellCount * tex)
    private val v = FloatArray(maze.cellCount * tex)
    /** White-water foam left behind by the ball: 0..1 per texel, fades slowly and does not travel. */
    val f = FloatArray(maze.cellCount * tex)
    private val cs = maze.cellSize
    private val active = HashMap<Int, Float>()
    val dirty = LinkedHashSet<Int>()
    private val nb = IntArray(maze.cellCount * 4) { i ->
        val c = i / 4; val e = maze.cellEdges[i]
        if (maze.edgeCellL[e] == c) maze.edgeCellR[e] else maze.edgeCellL[e]
    }
    private val eU = Array(maze.cellCount) { c -> val vs = maze.mesh.vertices; val m = maze.mesh
        ((vs[m.cellVertex(c, 1)] - vs[m.cellVertex(c, 0)]) + (vs[m.cellVertex(c, 2)] - vs[m.cellVertex(c, 3)])) * 0.5f }
    private val eV = Array(maze.cellCount) { c -> val vs = maze.mesh.vertices; val m = maze.mesh
        ((vs[m.cellVertex(c, 3)] - vs[m.cellVertex(c, 0)]) + (vs[m.cellVertex(c, 2)] - vs[m.cellVertex(c, 1)])) * 0.5f }
    private var acc = 0f
    /** For each cell and gutter texel: where to copy from (neighbour texel through an open passage, own edge texel at a wall). */
    private val gutDst = IntArray(4 * tile)
    private val gutSrc = IntArray(maze.cellCount * 4 * tile)
    private val gutCount: Int

    init {
        var n = 0
        for (j in 0..t + 1) for (i in 0..t + 1) { if (i in 1..t && j in 1..t) continue; gutDst[n++] = j * tile + i }
        gutCount = n
        for (c in 0 until maze.cellCount) {
            var q = 0
            for (j in 0..t + 1) for (i in 0..t + 1) {
                if (i in 1..t && j in 1..t) continue
                val k = when { i == 0 -> 3; i == t + 1 -> 1; j == 0 -> 0; else -> 2 }
                val o = nb[c * 4 + k]
                val ownIdx = idx(c, i.coerceIn(1, t), j.coerceIn(1, t))
                gutSrc[c * 4 * tile + q] = if (o < 0 || !maze.open[maze.cellEdges[c * 4 + k]]) ownIdx else {
                    val p = maze.centers[c] + eU[c] * (texU(i) - 0.5f) + eV[c] * (texU(j) - 0.5f)
                    val (lu, lv) = local(o, p)
                    idx(o, (lu * t + 0.5f).toInt().coerceIn(1, t), (lv * t + 0.5f).toInt().coerceIn(1, t))
                }
                q++
            }
        }
    }

    fun idx(c: Int, i: Int, j: Int) = c * tex + j * tile + i
    private fun texU(i: Int) = (i - 0.5f) / t
    private fun local(c: Int, p: Vec3): Pair<Float, Float> {
        val d = p - maze.centers[c]
        return (d dot maze.axisU[c]) + 0.5f to (d dot maze.axisV[c]) + 0.5f
    }

    /** Pushes the surface around world point [p] (a dent for negative [amount], a bump for positive). [radius] is in world units. */
    fun disturb(cell: Int, p: Vec3, radius: Float, amount: Float) {
        val r = radius / cs
        val (lu, lv) = local(cell, p)
        val i0 = max(1, ((lu - r * 2f) * t + 0.5f).toInt()); val i1 = min(t, ((lu + r * 2f) * t + 1.5f).toInt())
        val j0 = max(1, ((lv - r * 2f) * t + 0.5f).toInt()); val j1 = min(t, ((lv + r * 2f) * t + 1.5f).toInt())
        for (j in j0..j1) for (i in i0..i1) {
            val dx = texU(i) - lu; val dy = texU(j) - lv
            val w = kotlin.math.exp(-(dx * dx + dy * dy) / (r * r * 0.6f))
            h[idx(cell, i, j)] += amount * w
        }
        wake(cell)
    }

    private fun wake(c: Int, secs: Float = 2.2f) { if ((active[c] ?: 0f) < secs) active[c] = secs; dirty.add(c) }

    /** Lays foam around world point [p] (radius in world units); stronger values for a faster ball. */
    fun foam(cell: Int, p: Vec3, radius: Float, amount: Float) {
        val r = radius / cs
        val (lu, lv) = local(cell, p)
        val i0 = max(1, ((lu - r * 1.6f) * t + 0.5f).toInt()); val i1 = min(t, ((lu + r * 1.6f) * t + 1.5f).toInt())
        val j0 = max(1, ((lv - r * 1.6f) * t + 0.5f).toInt()); val j1 = min(t, ((lv + r * 1.6f) * t + 1.5f).toInt())
        for (j in j0..j1) for (i in i0..i1) {
            val dx = texU(i) - lu; val dy = texU(j) - lv
            val w = kotlin.math.exp(-(dx * dx + dy * dy) / (r * r * 0.8f))
            val k = idx(cell, i, j)
            f[k] = min(1f, f[k] + amount * w)
        }
        wake(cell, 5f)
    }

    fun update(dt: Float) {
        if (active.isEmpty()) return
        acc += dt
        var steps = 0
        while (acc >= STEP && steps < 4) { acc -= STEP; steps++; step() }
        if (acc > STEP * 4) acc = 0f
        val done = ArrayList<Int>()
        for ((c, left) in active.entries.toList()) {
            val l = left - dt
            if (l <= 0f) done.add(c) else active[c] = l
        }
        for (c in done) { active.remove(c); java.util.Arrays.fill(f, c * tex, (c + 1) * tex, 0f); java.util.Arrays.fill(h, c * tex, (c + 1) * tex, 0f); java.util.Arrays.fill(v, c * tex, (c + 1) * tex, 0f); syncOne(c); dirty.add(c) }
    }

    private fun step() {
        val cells = HashSet<Int>()
        for (c in active.keys) { cells.add(c); for (k in 0..3) { val o = nb[c * 4 + k]; if (o >= 0 && maze.open[maze.cellEdges[c * 4 + k]]) cells.add(o) } }
        for (c in cells) syncOne(c)
        val fd = FOAM_DECAY
        val k2 = C * C * STEP * STEP * t * t
        val damp = DAMP
        for (c in cells) {
            for (j in 1..t) for (i in 1..t) {
                val a = idx(c, i, j)
                val lap = h[a - 1] + h[a + 1] + h[a - tile] + h[a + tile] - 4f * h[a]
                v[a] = (v[a] + lap * k2 / STEP) * damp
            }
        }
        for (c in cells) {
            for (j in 1..t) for (i in 1..t) { val a = idx(c, i, j); h[a] += v[a] * STEP; f[a] *= fd }
            dirty.add(c)
            if (!active.containsKey(c)) active[c] = 1.0f
        }
    }

    /** Fills the gutter ring of [c] from the precomputed sources. */
    private fun syncOne(c: Int) {
        val base = c * tex
        val sb = c * 4 * tile
        for (q in 0 until gutCount) { h[base + gutDst[q]] = h[gutSrc[sb + q]]; f[base + gutDst[q]] = f[gutSrc[sb + q]] }
    }

    fun tileOrigin(c: Int) = (c % perRow) * tile to (c / perRow) * tile
    fun clear() { java.util.Arrays.fill(f, 0f); java.util.Arrays.fill(h, 0f); java.util.Arrays.fill(v, 0f); active.clear(); dirty.clear() }
    fun energy() = h.sumOf { abs(it).toDouble() }.toFloat()

    companion object { const val STEP = 1f / 120f; const val C = 1.8f; const val DAMP = 0.994f; const val FOAM_DECAY = 0.99955f }
}
