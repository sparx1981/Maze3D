package com.maze3d.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Per-level moving hazards: sliding doors on open passages and floor holes that open and close.
 * The timing formulas are mirrored in the shaders (SCENE_VS for doors, HOLE_FS for holes).
 */
class Hazards(private val maze: Maze, rnd: Random, withDoors: Boolean, withHoles: Boolean, withBridges: Boolean = false, withHammers: Boolean = false, scale: Float = 1f) {
    val doorEdges: IntArray
    val doorPhase: FloatArray
    val holeCells: IntArray
    val holePhase: FloatArray
    private val holeIndex = IntArray(maze.cellCount) { -1 }
    /** Wobbling plank bridges over open pits on straight stretches of the route. */
    val bridgeCells: IntArray
    val bridgeDir: Array<Vec3>
    val bridgePhase: FloatArray
    /** Pendulum hammers hanging over straight stretches; they swing along the corridor. */
    val hammerCells: IntArray
    val hammerDir: Array<Vec3>
    val hammerPhase: FloatArray
    private val bridgeIndex = IntArray(maze.cellCount) { -1 }
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

        // bridges and hammers sit in straight cells of the route, well apart from every other hazard and from each other
        val taken = HashSet<Int>(protectedCells)
        for (e in doors) { taken.add(maze.edgeCellL[e]); taken.add(maze.edgeCellR[e]) }
        holes.forEach { taken.add(it) }
        val straight = ArrayList<Int>()
        for (k in 1 until path.size - 1) {
            val a = maze.centers[path[k - 1]]; val c = maze.centers[path[k]]; val b = maze.centers[path[k + 1]]
            if (((c - a).normalized() dot (b - c).normalized()) > 0.93f && path[k] !in taken && maze.links[path[k]].size == 2 &&
                holes.none { (maze.centers[it] - c).length() < maze.cellSize * 3f }) straight.add(k)
        }
        fun pick(count: Int, usedIdx: MutableList<Int>): List<Int> {
            val out = ArrayList<Int>()
            for (k in straight.shuffled(rnd)) {
                if (out.size >= count) break
                if (usedIdx.any { kotlin.math.abs(it - k) < 4 }) continue
                out.add(k); usedIdx.add(k)
            }
            return out
        }
        val used = ArrayList<Int>()
        for (k in 0 until path.size step 1) if (path[k] in holes) used.add(k)
        val bks = if (withBridges) pick(((maze.cellCount / 90f) * scale).toInt().coerceIn(2, 7), used) else emptyList()
        val hks = if (withHammers) pick(((maze.cellCount / 110f) * scale).toInt().coerceIn(2, 6), used) else emptyList()
        fun dirAt(k: Int) = (maze.centers[path[k + 1]] - maze.centers[path[k - 1]]).normalized()
        bridgeCells = IntArray(bks.size) { path[bks[it]] }
        bridgeDir = Array(bks.size) { dirAt(bks[it]) }
        bridgePhase = FloatArray(bks.size) { rnd.nextFloat() }
        hammerCells = IntArray(hks.size) { path[hks[it]] }
        hammerDir = Array(hks.size) { dirAt(hks[it]) }
        hammerPhase = FloatArray(hks.size) { rnd.nextFloat() }
        bridgeCells.forEachIndexed { i, c -> bridgeIndex[c] = i }
    }

    val hasBridges get() = bridgeCells.isNotEmpty()
    val hasHammers get() = hammerCells.isNotEmpty()
    fun bridgeAt(cell: Int) = bridgeIndex[cell]

    /** Sideways offset of the plank of bridge [i] at time [t], in cell widths. */
    /** Size (1 = solid, 0 = gone) of plank [k] of bridge [i]: boards shrink away and return in a travelling wave, leaving gaps to time. */
    fun boardScale(i: Int, k: Int, t: Float): Float {
        val v = sin(BOARD_W * t + bridgePhase[i] * 2 * PI.toFloat() + k * 0.55f)
        return 1f - smooth(0.42f, 0.68f, v)
    }

    fun bridgeOffset(i: Int, t: Float) = BRIDGE_AMP * sin(BRIDGE_W * t + bridgePhase[i] * 2 * PI.toFloat())

    /** Swing angle of hammer [i] in radians (0 = hanging straight down). */
    fun hammerAngle(i: Int, t: Float) = HAMMER_MAX * sin(HAMMER_W * t + hammerPhase[i] * 2 * PI.toFloat())
    fun hammerRate(i: Int, t: Float) = HAMMER_MAX * HAMMER_W * kotlin.math.cos(HAMMER_W * t + hammerPhase[i] * 2 * PI.toFloat())

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
        const val BRIDGE_W = 1.1f; const val BRIDGE_AMP = 0.07f; const val BOARD_W = 1.5f; const val BOARDS = 7
        const val HAMMER_W = 1.7f; const val HAMMER_MAX = 1.2f
        private fun smooth(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }
        fun doorG(t: Float, phase: Float): Float =
            smooth(0.4f, 0.8f, 0.5f + 0.5f * sin(DOOR_W * t + phase * 2 * PI.toFloat()))
    }
}
