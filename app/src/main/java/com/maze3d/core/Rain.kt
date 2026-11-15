package com.maze3d.core

import java.util.Random
import kotlin.math.sqrt

/**
 * Rainy-level state: puddles that fill while it rains and slowly dry afterwards. A marble rolling
 * through a puddle is slowed in proportion to how deep and how close to the middle it is.
 */
class Rain(private val maze: Maze, rnd: Random) {
    val cells: IntArray
    val level: FloatArray
    private val fillRate: FloatArray
    private val index = IntArray(maze.cellCount) { -1 }

    /** Largest puddle radius in world units (reached at level 1). */
    val maxRadius = maze.cellSize * 0.55f

    /** 0..1 how hard it is raining right now. */
    var intensity = 0f

    init {
        val protectedCells = HashSet<Int>()
        for (c in listOf(maze.start, maze.goal)) {
            protectedCells.add(c)
            for (o in maze.links[c]) protectedCells.add(o)
        }
        val pick = ArrayList<Int>()
        val target = (maze.cellCount / 32).coerceIn(7, 20)
        // a few puddles are always on the solution path so the rain matters for the shortest route
        maze.solution.filter { it !in protectedCells }.shuffled(rnd).take(3).forEach { pick.add(it) }
        val others = (0 until maze.cellCount).filter { it !in protectedCells && it !in pick }.shuffled(rnd)
        for (c in others) {
            if (pick.size >= target) break
            if (pick.none { (maze.centers[it] - maze.centers[c]).length() < maze.cellSize * 1.6f }) pick.add(c)
        }
        cells = pick.toIntArray()
        level = FloatArray(cells.size)
        fillRate = FloatArray(cells.size) { 0.07f + rnd.nextFloat() * 0.09f }
        cells.forEachIndexed { i, c -> index[c] = i }
    }

    fun update(dt: Float, raining: Boolean) {
        for (i in level.indices) {
            level[i] = if (raining) (level[i] + fillRate[i] * intensity * dt).coerceAtMost(1f)
            else (level[i] - 0.02f * dt).coerceAtLeast(0f)
        }
    }

    fun radius(i: Int) = maxRadius * sqrt(level[i]) * 0.9f

    /** How deep the water is under [surfacePoint] in [cell]: 0 (dry) .. 1 (full puddle centre). */
    fun depthAt(cell: Int, surfacePoint: Vec3): Float {
        val i = index[cell]
        if (i < 0 || level[i] < 0.02f) return 0f
        val r = radius(i)
        val d = (surfacePoint - maze.centers[cell]).length()
        if (d >= r) return 0f
        val edge = ((r - d) / (r * 0.5f)).coerceIn(0f, 1f)
        return level[i] * edge
    }

    fun puddleIndex(cell: Int) = index[cell]
}
