package com.maze3d.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Height field of loose material (sand or mud) stored as one small tile of texels per maze cell.
 * Heights are in cell units (negative = dug out, positive = pushed up). A rolling ball digs a groove and shoves the displaced
 * material into banks along both sides; sand then slumps back toward its angle of repose, mud stays put.
 *
 * Each tile has a one-texel gutter holding copies of the neighbouring cells' edge texels so the shader can filter and take
 * gradients straight across cell borders without seams.
 */
class SandField(private val maze: Maze, val mud: Boolean, val t: Int) {
    val tile = t + 2
    val perRow = (1008 / tile).coerceAtLeast(1)
    val rows = (maze.cellCount + perRow - 1) / perRow
    val atlasW = perRow * tile
    val atlasH = rows * tile
    private val tex = tile * tile
    val h = FloatArray(maze.cellCount * tex)
    private val eU = Array(maze.cellCount) { c -> val v = maze.mesh.vertices; val m = maze.mesh
        ((v[m.cellVertex(c, 1)] - v[m.cellVertex(c, 0)]) + (v[m.cellVertex(c, 2)] - v[m.cellVertex(c, 3)])) * 0.5f }
    private val eV = Array(maze.cellCount) { c -> val v = maze.mesh.vertices; val m = maze.mesh
        ((v[m.cellVertex(c, 3)] - v[m.cellVertex(c, 0)]) + (v[m.cellVertex(c, 2)] - v[m.cellVertex(c, 1)])) * 0.5f }
    /** Neighbour across each of a cell's four edges. */
    private val nb = IntArray(maze.cellCount * 4) { i ->
        val c = i / 4; val e = maze.cellEdges[i]
        if (maze.edgeCellL[e] == c) maze.edgeCellR[e] else maze.edgeCellL[e]
    }
    private val active = HashMap<Int, Float>()           // cell -> seconds of slumping left
    val dirty = LinkedHashSet<Int>()                      // tiles whose texels changed since the last upload
    private val delta = FloatArray(tex)
    private val cs = maze.cellSize
    private val depth = if (mud) 0.070f else 0.042f
    private val repose = if (mud) 1.2f else 0.55f

    fun idx(cell: Int, i: Int, j: Int) = cell * tex + j * tile + i

    private fun texU(i: Int) = (i - 0.5f) / t

    /** Local cell coordinates (0..1 inside the cell) of world point [p] as seen from [cell]. */
    private fun local(cell: Int, p: Vec3): Pair<Float, Float> {
        val d = p - maze.centers[cell]
        return (d dot maze.axisU[cell]) + 0.5f to (d dot maze.axisV[cell]) + 0.5f
    }

    /**
     * The ball (surface contact point [p], moving along [dir]) ploughs through [cell] with contact radius [radius].
     * Material dug out of the groove piles up on both sides.
     */
    fun stamp(cell: Int, p: Vec3, dir: Vec3, radius: Float, strength: Float) {
        val r = radius / cs
        val cells = IntArray(5); cells[0] = cell
        for (k in 0..3) cells[k + 1] = nb[cell * 4 + k]
        // pass 1: dig the groove and total the displaced volume; pass 2: pile it up
        var removed = 0f; var wsum = 0f
        for (pass in 0..1) {
            var gain = 0f
            if (pass == 1) { if (removed <= 0f || wsum <= 0f) break; gain = removed * 0.9f / wsum }
            for (c in cells) {
                if (c < 0) continue
                val (lu, lv) = local(c, p)
                val i0 = max(1, ((lu - r * 2.1f) * t + 0.5f).toInt()); val i1 = min(t, ((lu + r * 2.1f) * t + 1.5f).toInt())
                val j0 = max(1, ((lv - r * 2.1f) * t + 0.5f).toInt()); val j1 = min(t, ((lv + r * 2.1f) * t + 1.5f).toInt())
                if (i1 < i0 || j1 < j0) continue
                for (j in j0..j1) for (i in i0..i1) {
                    val u = texU(i); val v = texU(j)
                    val dx = u - lu; val dy = v - lv
                    val d = sqrt(dx * dx + dy * dy)
                    val k = idx(c, i, j)
                    if (pass == 0) {
                        val w = 1f - smooth((d - 0.15f * r) / (0.65f * r))
                        if (w <= 0f) { wsum += bankWeight(d, r, dx, dy, c, dir, p); continue }
                        val target = -depth * w * strength
                        if (h[k] > target) { removed += (h[k] - target) * (1f / (t * t)) * 40f; h[k] = target }
                        wsum += bankWeight(d, r, dx, dy, c, dir, p)
                    } else {
                        val bw = bankWeight(d, r, dx, dy, c, dir, p)
                        if (bw > 0f) h[k] += gain * bw * (t * t) / 40f * 1.1f
                    }
                }
                dirty.add(c)
                active[c] = 4f
            }
        }
        for (c in cells) if (c >= 0) { dirty.add(c); active[c] = 4f }
    }

    private fun smooth(x: Float): Float { val y = x.coerceIn(0f, 1f); return y * y * (3 - 2 * y) }

    /** Banks sit just outside the groove and mostly to the sides of the direction of travel. */
    private fun bankWeight(d: Float, r: Float, dx: Float, dy: Float, c: Int, dir: Vec3, p: Vec3): Float {
        val a = 0.95f * r; val b = 1.9f * r
        if (d < a || d > b) return 0f
        val bump = smooth((d - a) / (0.45f * r)) * (1f - smooth((d - (a + 0.45f * r)) / (b - a - 0.45f * r)))
        // lateral factor: component of (offset) perpendicular to the motion, in cell-local axes
        val ex = dir dot maze.axisU[c]; val ey = dir dot maze.axisV[c]
        val l = sqrt(ex * ex + ey * ey)
        val side = if (l < 1e-5f || d < 1e-5f) 1f else abs((dx * ey - dy * ex) / (l * d))
        return bump * (0.25f + 0.75f * side)
    }

    /** Slumping (sand) and gutter refresh for recently touched tiles. */
    fun update(dt: Float) {
        if (active.isEmpty()) return
        val done = ArrayList<Int>()
        val cellsNow = active.keys.toList()
        for (c in cellsNow) {
            relaxTile(c)
            val left = active[c]!! - dt
            if (left <= 0f) done.add(c) else active[c] = left
        }
        for (c in done) active.remove(c)
        // refresh gutters of everything that changed, and of its neighbours (they copy our edge texels)
        val toSync = HashSet<Int>()
        for (c in cellsNow) { toSync.add(c); for (k in 0..3) toSync.add(nb[c * 4 + k]) }
        for (c in toSync) if (c >= 0) { syncGutter(c); dirty.add(c) }
    }

    private fun relaxTile(c: Int) {
        java.util.Arrays.fill(delta, 0f)
        val lim = repose / t
        val rate = if (mud) 0.12f else 0.3f
        for (j in 1..t) for (i in 1..t) {
            val a = idx(c, i, j)
            val ha = h[a]
            for (o in 0..1) {
                val ni = i + o; val nj = j + 1 - o
                val b = idx(c, ni, nj)
                val diff = ha - h[b]
                val ex = abs(diff) - lim
                if (ex > 0f) {
                    val m = ex * rate * (if (diff > 0f) 1f else -1f)
                    delta[(j * tile + i)] -= m
                    if (ni in 1..t && nj in 1..t) delta[nj * tile + ni] += m
                }
            }
            // neighbours to the left/up (only receive from interior pairs handled above, so also check them as sources)
            for (o in 0..1) {
                val ni = i - o; val nj = j - (1 - o)
                if (ni in 1..t && nj in 1..t) continue
                val diff = ha - h[idx(c, ni, nj)]
                val ex = abs(diff) - lim
                if (ex > 0f) delta[j * tile + i] -= ex * rate * (if (diff > 0f) 1f else -1f)
            }
        }
        for (j in 1..t) for (i in 1..t) h[idx(c, i, j)] += delta[j * tile + i]
    }

    private fun syncGutter(c: Int) {
        for (j in 0..t + 1) for (i in 0..t + 1) {
            if (i in 1..t && j in 1..t) continue
            val e = when {
                i == 0 -> 3
                i == t + 1 -> 1
                j == 0 -> 0
                else -> 2
            }
            val o = nb[c * 4 + e]
            if (o < 0) continue
            val u = texU(i); val v = texU(j)
            val p = maze.centers[c] + eU[c] * (u - 0.5f) + eV[c] * (v - 0.5f)
            val (lu, lv) = local(o, p)
            val ni = (lu * t + 0.5f).toInt().coerceIn(1, t); val nj = (lv * t + 0.5f).toInt().coerceIn(1, t)
            h[idx(c, i, j)] = h[idx(o, ni, nj)]
        }
    }

    /** Height at the texel under [p] in [cell], in cell units (for sinking the ball). */
    fun heightAt(cell: Int, p: Vec3): Float {
        val (lu, lv) = local(cell, p)
        return h[idx(cell, (lu * t + 0.5f).toInt().coerceIn(1, t), (lv * t + 0.5f).toInt().coerceIn(1, t))]
    }

    fun tileOrigin(cell: Int) = (cell % perRow) * tile to (cell / perRow) * tile

    fun clear() { java.util.Arrays.fill(h, 0f); active.clear(); dirty.clear() }
}
