package com.maze3d.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Per-level moving hazards: sliding doors on open passages and floor holes that open and close.
 * The timing formulas are mirrored in the shaders (SCENE_VS for doors, HOLE_FS for holes).
 */
class Hazards(private val maze: Maze, rnd: Random, withDoors: Boolean, withHoles: Boolean) {
    val doorEdges: IntArray
    val doorPhase: FloatArray
    val holeCells: IntArray
    val holePhase: FloatArray
    private val holeIndex = IntArray(maze.cellCount) { -1 }
    val doorSet = HashSet<Int>()
    val holeRadius = maze.cellSize * 0.40f

    init {
        val protectedCells = HashSet<Int>()
        for (c in listOf(maze.start, maze.goal)) {
            protectedCells.add(c)
            for (o in maze.links[c]) protectedCells.add(o)
        }
        val path = maze.solution
        val pathEdges = ArrayList<Int>()
        for (k in 0 until path.size - 1) {
            for (j in 0..3) {
                val e = maze.cellEdges[path[k] * 4 + j]
                val o = if (maze.edgeCellL[e] == path[k]) maze.edgeCellR[e] else maze.edgeCellL[e]
                if (o == path[k + 1] && maze.open[e]) { pathEdges.add(e); break }
            }
        }

        val doors = ArrayList<Int>()
        if (withDoors) {
            val ok = { e: Int -> maze.edgeCellL[e] !in protectedCells && maze.edgeCellR[e] !in protectedCells }
            val target = (maze.cellCount / 70).coerceIn(4, 10)
            pathEdges.filter(ok).shuffled(rnd).take(3).forEach { doors.add(it) }
            val others = (0 until maze.edgeCount).filter { maze.open[it] && ok(it) && it !in doors }.shuffled(rnd)
            for (e in others) { if (doors.size >= target) break; doors.add(e) }
        }
        doorEdges = doors.toIntArray()
        doorPhase = FloatArray(doorEdges.size) { rnd.nextFloat() }
        doorSet.addAll(doors)

        val holes = ArrayList<Int>()
        if (withHoles) {
            val target = (maze.cellCount / 60).coerceIn(5, 12)
            val doorCells = HashSet<Int>()
            for (e in doors) { doorCells.add(maze.edgeCellL[e]); doorCells.add(maze.edgeCellR[e]) }
            val ok = { c: Int -> c !in protectedCells && c !in doorCells }
            path.filter(ok).shuffled(rnd).take(3).forEach { holes.add(it) }
            val others = (0 until maze.cellCount).filter { ok(it) && it !in holes }.shuffled(rnd)
            for (c in others) {
                if (holes.size >= target) break
                // keep holes apart so they never wall off a whole area
                if (holes.none { (maze.centers[it] - maze.centers[c]).length() < maze.cellSize * 2.2f }) holes.add(c)
            }
        }
        holeCells = holes.toIntArray()
        holePhase = FloatArray(holeCells.size) { rnd.nextFloat() }
        holeCells.forEachIndexed { i, c -> holeIndex[c] = i }
    }

    val hasDoors get() = doorEdges.isNotEmpty()
    val hasHoles get() = holeCells.isNotEmpty()

    fun doorHeight(i: Int, t: Float): Float = doorG(t, doorPhase[i])
    fun doorClosed(i: Int, t: Float) = doorHeight(i, t) > 0.5f

    /** 0 (shut) .. 1 (fully open). */
    fun holeOpen(i: Int, t: Float): Float = smooth(0f, 0.6f, sin(HOLE_W * t + holePhase[i] * 2 * PI.toFloat()))

    /** True when [surfacePoint] is over an open hole in [cell]. */
    fun fellInto(cell: Int, surfacePoint: Vec3, t: Float): Int {
        val i = holeIndex[cell]
        if (i < 0) return -1
        val a = holeOpen(i, t)
        if (a < 0.6f) return -1
        return if ((surfacePoint - maze.centers[cell]).length() < holeRadius * a * 0.85f) i else -1
    }

    companion object {
        const val DOOR_W = 1.15f
        const val HOLE_W = 0.9f
        private fun smooth(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }
        fun doorG(t: Float, phase: Float): Float =
            smooth(0.4f, 0.8f, 0.5f + 0.5f * sin(DOOR_W * t + phase * 2 * PI.toFloat()))
    }
}
