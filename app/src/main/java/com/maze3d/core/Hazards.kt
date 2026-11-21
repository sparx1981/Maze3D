package com.maze3d.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Per-level moving hazards: sliding doors on open passages and floor holes that open and close.
 * The timing formulas are mirrored in the shaders (SCENE_VS for doors, HOLE_FS for holes).
 */
class Hazards(private val maze: Maze, rnd: Random, withDoors: Boolean, withHoles: Boolean, withBridges: Boolean = false, withHammers: Boolean = false, scale: Float = 1f, withSwings: Boolean = false, fxRnd: Random? = null, fxKinds: List<Int>? = null, fxWant: Int = -1,
              avoid: Set<Int> = emptySet(), drawRnd: Random? = null) {
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
    /** Swings over wide pits: a round platform rides back and forth along three straight cells. */
    val swingCells: IntArray
    val swingDir: Array<Vec3>
    val swingPhase: FloatArray
    private val swingIndex = IntArray(maze.cellCount) { -1 }
    /** Castle drawbridges over moats cut across straight stretches of the route; they raise and lower on a timer. */
    val drawCells: IntArray
    val drawDir: Array<Vec3>
    val drawPhase: FloatArray
    /** Floor effects: spring pads, oil slicks, conveyor belts, crushers, paddle sweepers and seesaws (kinds FX_*). */
    val fxKind: IntArray
    val fxCell: IntArray
    val fxDir: Array<Vec3>
    val fxPhase: FloatArray
    val doorSet = HashSet<Int>()
    val holeRadius = maze.cellSize * 0.40f

    init {
        val protectedCells = HashSet<Int>(avoid)                  // e.g. castle turrets and courtyards: no hazards on them
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
        // swings need three straight corridor cells in a row
        val swingSpots = ArrayList<Int>()
        if (withSwings) for (k in 2 until path.size - 2) {
            val tri = listOf(path[k - 1], path[k], path[k + 1])
            if (tri.any { it in taken || maze.links[it].size != 2 } || holes.any { h -> tri.any { (maze.centers[h] - maze.centers[it]).length() < maze.cellSize * 3f } }) continue
            val a = maze.centers[path[k - 1]]; val c = maze.centers[path[k]]; val b = maze.centers[path[k + 1]]
            if (((c - a).normalized() dot (b - c).normalized()) > 0.97f) swingSpots.add(k)
        }
        val sks = ArrayList<Int>()
        for (k in swingSpots.shuffled(rnd)) {
            if (sks.size >= ((maze.cellCount / 150f) * scale).toInt().coerceIn(1, 4)) break
            if (used.any { kotlin.math.abs(it - k) < 5 } || sks.any { kotlin.math.abs(it - k) < 6 }) continue
            sks.add(k); used.add(k)
        }
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
        swingCells = IntArray(sks.size) { path[sks[it]] }
        swingDir = Array(sks.size) { dirAt(sks[it]) }
        swingPhase = FloatArray(sks.size) { rnd.nextFloat() }
        swingCells.forEachIndexed { i, c -> swingIndex[c] = i }

        // drawbridges: like swings they need three straight corridor cells; the moat is cut across the middle one
        val dks = ArrayList<Int>()
        if (drawRnd != null) {
            val spots = ArrayList<Int>()
            for (k in 2 until path.size - 2) {
                val tri = listOf(path[k - 1], path[k], path[k + 1])
                if (tri.any { it in taken || maze.links[it].size != 2 } || holes.any { h -> tri.any { (maze.centers[h] - maze.centers[it]).length() < maze.cellSize * 3f } }) continue
                val a = maze.centers[path[k - 1]]; val c = maze.centers[path[k]]; val b = maze.centers[path[k + 1]]
                if (((c - a).normalized() dot (b - c).normalized()) > 0.97f) spots.add(k)
            }
            val want = ((maze.cellCount / 160f) * scale).toInt().coerceIn(2, 4)
            for (k in spots.shuffled(drawRnd)) {
                if (dks.size >= want) break
                if (used.any { kotlin.math.abs(it - k) < 5 } || dks.any { kotlin.math.abs(it - k) < 8 }) continue
                dks.add(k); used.add(k)
            }
        }
        drawCells = IntArray(dks.size) { path[dks[it]] }
        drawDir = Array(dks.size) { dirAt(dks[it]) }
        drawPhase = FloatArray(dks.size) { (drawRnd ?: rnd).nextFloat() }

        val fk = ArrayList<Int>(); val fc = ArrayList<Int>(); val fd = ArrayList<Vec3>()
        if (fxRnd != null) {
            val busy = ArrayList<Vec3>()
            for (c in taken) busy.add(maze.centers[c])
            for (c in bridgeCells) busy.add(maze.centers[c]); for (c in hammerCells) busy.add(maze.centers[c])
            for (c in swingCells) { busy.add(maze.centers[c]); for (o in maze.links[c]) busy.add(maze.centers[o]) }
            for (c in drawCells) { busy.add(maze.centers[c]); for (o in maze.links[c]) busy.add(maze.centers[o]) }
            val want = if (fxWant > 0) fxWant else ((maze.cellCount / 55f) * scale).toInt().coerceIn(3, 9)
            val placed = ArrayList<Vec3>()
            val order = ArrayList<Int>().apply { repeat(if (fxKinds != null) 14 else 2) { addAll((fxKinds ?: (0 until FX_KINDS).filter { it != FX_OIL }).shuffled(fxRnd)) } }   // oil slicks are retired
            val cells = (0 until maze.cellCount).shuffled(fxRnd)
            for (kind in order) {
                if (fk.size >= want) break
                for (c in cells) {
                    if (c in protectedCells || c in fc) continue
                    val p = maze.centers[c]
                    if (busy.any { (it - p).length() < maze.cellSize * 1.7f } || placed.any { (it - p).length() < maze.cellSize * 2.3f }) continue
                    var dir = Vec3(1f, 0f, 0f)
                    if (kind == FX_CONVEYOR || kind == FX_CRUSHER || kind == FX_SEESAW) {
                        if (maze.links[c].size != 2) continue
                        val a = maze.centers[maze.links[c][0]]; val b = maze.centers[maze.links[c][1]]
                        if (((p - a).normalized() dot (b - p).normalized()) < 0.9f) continue
                        dir = (b - a).normalized()
                        if (kind == FX_CONVEYOR && fxRnd.nextBoolean()) dir = dir * -1f
                    } else if (kind == FX_SWEEPER && maze.links[c].size < 3 && fxRnd.nextInt(3) != 0) continue
                    if (kind == FX_CONVEYOR) {                          // a long belt: three straight cells in a row, all running the same way
                        val ls = maze.links[c]
                        val strip = listOf(ls[0], c, ls[1])
                        if (strip.any { it in protectedCells || it in fc || maze.links[it].size != 2 } ||
                            strip.any { q -> busy.any { (it - maze.centers[q]).length() < maze.cellSize * 1.2f } }) continue
                        if (((maze.centers[ls[0]] - maze.centers[ls[1]]).normalized() dot dir.normalized()).let { kotlin.math.abs(it) } < 0.9f) continue
                        for (q in strip) { fk.add(kind); fc.add(q); fd.add(dir) }
                        placed.add(p)
                        break
                    }
                    fk.add(kind); fc.add(c); fd.add(dir); placed.add(p)
                    break
                }
            }
        }
        fxKind = fk.toIntArray(); fxCell = fc.toIntArray(); fxDir = fd.toTypedArray()
        fxPhase = FloatArray(fxKind.size) { (fxRnd ?: rnd).nextFloat() }
    }

    val hasFx get() = fxKind.isNotEmpty()
    private fun fxPh(i: Int, w: Float, t: Float) = w * t + fxPhase[i] * 2 * PI.toFloat()
    /** 0 (raised) .. 1 (slammed down) for crusher [i]. */
    fun crushH(i: Int, t: Float) = smooth(0.5f, 0.8f, sin(fxPh(i, CRUSH_W, t)))
    fun sweepAngle(i: Int, t: Float) = fxPh(i, SWEEP_W, t)
    fun seesawAngle(i: Int, t: Float) = SEESAW_MAX * sin(fxPh(i, SEESAW_W, t))

    val hasSwings get() = swingCells.isNotEmpty()
    val hasDrawbridges get() = drawCells.isNotEmpty()
    /** 1 = drawbridge [i] lowered across the moat, 0 = fully raised. Down most of the time, with a short raise and pause. */
    fun drawDown(i: Int, t: Float): Float {
        val p = ((t / DRAW_PERIOD + drawPhase[i]) % 1f + 1f) % 1f
        return when {
            p < 0.55f -> 1f
            p < 0.66f -> 1f - smooth(0f, 1f, (p - 0.55f) / 0.11f)
            p < 0.84f -> 0f
            p < 0.95f -> smooth(0f, 1f, (p - 0.84f) / 0.11f)
            else -> 1f
        }
    }
    fun swingAt(cell: Int) = swingIndex[cell]
    /** Platform position along the corridor in cells, 0 = centre of the pit. */
    fun swingX(i: Int, t: Float) = SWING_AMP * sin(SWING_W * t + swingPhase[i] * 2 * PI.toFloat())
    fun swingV(i: Int, t: Float) = SWING_AMP * SWING_W * kotlin.math.cos(SWING_W * t + swingPhase[i] * 2 * PI.toFloat())

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
        const val FX_BUMPER = 0; const val FX_OIL = 1; const val FX_CONVEYOR = 2; const val FX_CRUSHER = 3; const val FX_SWEEPER = 4; const val FX_SEESAW = 5; const val FX_KINDS = 6
        const val CRUSH_W = 1.9f; const val SWEEP_W = 1.35f; const val SWEEP_REACH = 0.44f; const val SEESAW_W = 1.0f; const val SEESAW_MAX = 0.22f
        const val DRAW_PERIOD = 7f; const val MOAT_HALF = 0.4f
        const val SWING_AMP = 0.5f; const val SWING_W = 0.9f; const val SWING_HALF = 0.5f; const val SWING_HALFPIT = 0.5f
        private fun smooth(a: Float, b: Float, x: Float): Float {
            val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
            return t * t * (3 - 2 * t)
        }
        fun doorG(t: Float, phase: Float): Float =
            smooth(0.4f, 0.8f, 0.5f + 0.5f * sin(DOOR_W * t + phase * 2 * PI.toFloat()))
    }
}
