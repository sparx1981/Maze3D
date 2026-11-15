package com.maze3d.core

import java.util.Random
import kotlin.math.floor
import kotlin.math.sin

/**
 * Per-cell concentration (0..1) of water, gas or fog.
 *
 * WATER and GAS are advected on the cell graph: they spread only through open passages (walls block them),
 * are injected by a few pulsing sources and fade away again, so a level always has dry stretches and clear air
 * to run through. FOG is a drifting 3D noise field and is purely visual.
 */
class MediumField(private val maze: Maze, val kind: Medium, rnd: Random, private val outer: Float) {
    val level = FloatArray(maze.cellCount)
    private val next = FloatArray(maze.cellCount)
    private val sources: IntArray
    private val period = FloatArray(8)
    private val phase = FloatArray(8)
    private val seed = rnd.nextFloat() * 100f
    private val wind = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalized()
    private var clock = 0f
    private var acc = 0f

    init {
        val safe = HashSet<Int>()
        // keep the start and its surroundings clear so a level never begins in the middle of a flood or cloud
        var frontier = listOf(maze.start); safe.add(maze.start)
        repeat(4) { val nf = ArrayList<Int>(); for (c in frontier) for (o in maze.links[c]) if (safe.add(o)) nf.add(o); frontier = nf }
        val cand = (0 until maze.cellCount).filter { it !in safe }.shuffled(rnd)
        val want = if (kind == Medium.GAS) 5 else 7
        val picked = ArrayList<Int>()
        for (c in cand) {
            if (picked.size >= want) break
            if (picked.none { (maze.centers[it] - maze.centers[c]).length() > 0f && (maze.centers[it] - maze.centers[c]).length() < maze.cellSize * 6f }) picked.add(c)
        }
        sources = picked.toIntArray()
        for (i in sources.indices) { period[i] = (if (kind == Medium.GAS) 20f else 34f) + rnd.nextFloat() * 8f; phase[i] = rnd.nextFloat() * period[i] }
    }

    private fun noise3(x: Float, y: Float, z: Float): Float {
        fun h(i: Int, j: Int, k: Int): Float {
            var n = i * 374761393 + j * 668265263 + k * 1274126177 + (seed * 1000).toInt()
            n = (n xor (n shr 13)) * 1274126177
            return ((n xor (n shr 16)) and 0xffff) / 65535f
        }
        val ix = floor(x).toInt(); val iy = floor(y).toInt(); val iz = floor(z).toInt()
        var fx = x - ix; var fy = y - iy; var fz = z - iz
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz)
        fun l(a: Float, b: Float, t: Float) = a + (b - a) * t
        return l(l(l(h(ix, iy, iz), h(ix + 1, iy, iz), fx), l(h(ix, iy + 1, iz), h(ix + 1, iy + 1, iz), fx), fy),
            l(l(h(ix, iy, iz + 1), h(ix + 1, iy, iz + 1), fx), l(h(ix, iy + 1, iz + 1), h(ix + 1, iy + 1, iz + 1), fx), fy), fz)
    }

    /** Advances the field; internally steps at 20 Hz. */
    fun update(dt: Float) {
        acc += dt
        while (acc >= 0.05f) { acc -= 0.05f; step(0.05f) }
    }

    private fun step(h: Float) {
        clock += h
        if (kind == Medium.FOG) {
            val k = 1.4f / maze.cellSize / 9f
            for (c in 0 until maze.cellCount) {
                val p = maze.centers[c]
                val t = clock * 0.35f
                val n = 0.62f * noise3(p.x * k + wind.x * t, p.y * k + wind.y * t, p.z * k + wind.z * t) +
                        0.38f * noise3(p.x * k * 2.3f - wind.x * t * 1.4f, p.y * k * 2.3f - wind.y * t * 1.4f, p.z * k * 2.3f - wind.z * t * 1.4f)
                val target = ((n - 0.5f) * 3.2f).coerceIn(0f, 1f)
                level[c] += (target - level[c]) * (1f - kotlin.math.exp(-1.5f * h))
            }
            return
        }
        val gas = kind == Medium.GAS
        val k = if (gas) 4f else 6f            // how readily neighbours equalise
        val decay = if (gas) 0.025f else 0.005f
        for (i in sources.indices) {
            val ph = ((clock + phase[i]) % period[i]) / period[i]
            val on = if (gas) ph < 0.28f else ph < 0.55f
            if (on) level[sources[i]] = (level[sources[i]] + (if (gas) 2f else 2.2f) * h).coerceAtMost(1f)
        }
        for (c in 0 until maze.cellCount) {
            var v = level[c]
            val ls = maze.links[c]
            for (o in ls) v += (level[o] - level[c]) * k * h / 2f
            v -= decay * h
            next[c] = v.coerceIn(0f, 1f)
        }
        System.arraycopy(next, 0, level, 0, level.size)
    }

    fun levelAt(cell: Int) = level[cell]

    /** Dry run so a level can start with some of the medium already around. */
    fun warmUp(seconds: Float) { var t = 0f; while (t < seconds) { step(0.05f); t += 0.05f } }
}
