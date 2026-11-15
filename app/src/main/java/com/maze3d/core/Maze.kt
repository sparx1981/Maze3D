package com.maze3d.core

import java.util.Random

/**
 * A perfect maze (spanning tree) over the cells of a [CellMesh]. Walls stand on every edge
 * between two cells that are not joined by a passage.
 */
class Maze(val mesh: CellMesh, seed: Long) {
    val cellCount = mesh.cellCount

    // --- edges -------------------------------------------------------------------------
    val edgeA: IntArray
    val edgeB: IntArray
    val edgeCellL: IntArray
    val edgeCellR: IntArray
    val edgeCount: Int

    /** For each cell, its 4 edges (indices into the edge arrays), edge k joins corner k and k+1. */
    val cellEdges: IntArray

    /** Open passage flag per edge. */
    val open: BooleanArray

    val centers: Array<Vec3>
    /** Per cell: (p - centre) dotted with these gives cell-local coordinates in -0.5..0.5 when inside. */
    val axisU: Array<Vec3>
    val axisV: Array<Vec3>
    val cellSize: Float
    /** Marble and wall sizes, limited by the narrowest cell so it can never jam. */
    val marbleRadius: Float
    val wallHalfThickness: Float

    // --- solution ----------------------------------------------------------------------
    val start: Int
    val goal: Int
    val depthFromStart: IntArray
    val solution: IntArray
    val maxDepth: Int

    /** Adjacent cells through open passages. */
    val links: Array<IntArray>

    init {
        val rnd = Random(seed)
        val n = cellCount
        val map = HashMap<Long, Int>()
        val ea = ArrayList<Int>(); val eb = ArrayList<Int>()
        val el = ArrayList<Int>(); val er = ArrayList<Int>()
        cellEdges = IntArray(n * 4)
        for (c in 0 until n) for (k in 0..3) {
            val a = mesh.cellVertex(c, k)
            val b = mesh.cellVertex(c, (k + 1) % 4)
            val lo = minOf(a, b); val hi = maxOf(a, b)
            val key = lo.toLong() * 1_000_000L + hi
            val existing = map[key]
            if (existing == null) {
                map[key] = ea.size
                cellEdges[c * 4 + k] = ea.size
                ea.add(lo); eb.add(hi); el.add(c); er.add(-1)
            } else {
                cellEdges[c * 4 + k] = existing
                er[existing] = c
            }
        }
        edgeCount = ea.size
        edgeA = ea.toIntArray(); edgeB = eb.toIntArray()
        edgeCellL = el.toIntArray(); edgeCellR = er.toIntArray()
        check(edgeCellR.all { it >= 0 }) { "surface must be closed" }
        open = BooleanArray(edgeCount)

        centers = Array(n) { c ->
            var s = Vec3.ZERO
            for (k in 0..3) s += mesh.vertices[mesh.cellVertex(c, k)]
            mesh.surface.project(s / 4f)
        }
        // dual basis of the cell's two mid-edge vectors, so (p - centre) . axisU/axisV are exact cell-local coordinates even for sheared cells
        val au = ArrayList<Vec3>(n); val av = ArrayList<Vec3>(n)
        for (c in 0 until n) {
            val v = mesh.vertices
            val a = v[mesh.cellVertex(c, 0)]; val b = v[mesh.cellVertex(c, 1)]; val cc = v[mesh.cellVertex(c, 2)]; val d = v[mesh.cellVertex(c, 3)]
            val e1 = ((b - a) + (cc - d)) * 0.5f
            val e2 = ((d - a) + (cc - b)) * 0.5f
            val g11 = e1 dot e1; val g12 = e1 dot e2; val g22 = e2 dot e2
            val det = (g11 * g22 - g12 * g12).let { if (kotlin.math.abs(it) < 1e-12f) 1e-12f else it }
            au.add((e1 * g22 - e2 * g12) / det)
            av.add((e2 * g11 - e1 * g12) / det)
        }
        axisU = au.toTypedArray(); axisV = av.toTypedArray()
        var total = 0f
        for (e in 0 until edgeCount) total += (mesh.vertices[edgeA[e]] - mesh.vertices[edgeB[e]]).length()
        cellSize = total / edgeCount
        // size the marble from the narrow cells, ignoring a few extreme outliers
        val lens = FloatArray(edgeCount) { e -> (mesh.vertices[edgeA[e]] - mesh.vertices[edgeB[e]]).length() }
        val sortedLens = lens.sortedArray()
        val narrow = sortedLens[(edgeCount * 0.05f).toInt()]
        val r0 = minOf(cellSize * 0.235f, narrow * 0.27f)          // a slightly smaller ball and thinner walls leave ~30% more room to slide sideways
        val wh = minOf(cellSize * 0.065f, narrow * 0.075f)
        wallHalfThickness = wh
        // a passage must be wide enough for the marble between two walls; narrower ones are opened only as a last resort
        val minPassage = 2f * r0 * 1.15f + 2f * wh

        // Randomised depth-first search carve (iterative). Narrow edges are skipped; leftovers are bridged afterwards.
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        var remaining = n
        fun carveFrom(startCell: Int) {
            var sp = 0
            stack[sp++] = startCell
            if (!visited[startCell]) { visited[startCell] = true; remaining-- }
            while (sp > 0) {
                val c = stack[sp - 1]
                val choices = ArrayList<Int>(4)
                for (k in 0..3) {
                    val e = cellEdges[c * 4 + k]
                    val o = if (edgeCellL[e] == c) edgeCellR[e] else edgeCellL[e]
                    if (!visited[o] && lens[e] >= minPassage) choices.add(e)
                }
                if (choices.isEmpty()) { sp--; continue }
                val e = choices[rnd.nextInt(choices.size)]
                val o = if (edgeCellL[e] == c) edgeCellR[e] else edgeCellL[e]
                open[e] = true
                visited[o] = true; remaining--
                stack[sp++] = o
            }
        }
        carveFrom(rnd.nextInt(n))
        while (remaining > 0) {
            // connect an unvisited cell to the carved area through its widest available edge, then keep carving from there
            var bestE = -1; var bestLen = -1f
            for (e in 0 until edgeCount) {
                val l = edgeCellL[e]; val r = edgeCellR[e]
                if (visited[l] != visited[r] && lens[e] > bestLen) { bestLen = lens[e]; bestE = e }
            }
            check(bestE >= 0) { "maze graph is disconnected" }
            open[bestE] = true
            carveFrom(if (visited[edgeCellL[bestE]]) edgeCellR[bestE] else edgeCellL[bestE])
        }
        // marble: the narrowest open passage decides the largest size that still fits
        var minOpen = Float.MAX_VALUE
        for (e in 0 until edgeCount) if (open[e]) minOpen = minOf(minOpen, lens[e])
        marbleRadius = minOf(r0, maxOf(r0 * 0.62f, (minOpen - 2f * wh) * 0.5f / 1.12f))

        links = Array(n) { c ->
            val l = ArrayList<Int>(4)
            for (k in 0..3) {
                val e = cellEdges[c * 4 + k]
                if (open[e]) l.add(if (edgeCellL[e] == c) edgeCellR[e] else edgeCellL[e])
            }
            l.toIntArray()
        }

        // Start and goal are the two ends of the longest corridor (tree diameter).
        val d0 = bfs(0).first
        val far = d0.indices.maxByOrNull { d0[it] }!!
        val (dStart, _) = bfs(far)
        val goalCell = dStart.indices.maxByOrNull { dStart[it] }!!
        start = far
        goal = goalCell
        depthFromStart = dStart
        maxDepth = dStart.max().coerceAtLeast(1)
        solution = pathTo(goalCell, bfs(far).second)
    }

    private fun bfs(from: Int): Pair<IntArray, IntArray> {
        val dist = IntArray(cellCount) { -1 }
        val parent = IntArray(cellCount) { -1 }
        val q = ArrayDeque<Int>()
        dist[from] = 0; q.add(from)
        while (q.isNotEmpty()) {
            val c = q.removeFirst()
            for (o in links[c]) if (dist[o] < 0) { dist[o] = dist[c] + 1; parent[o] = c; q.add(o) }
        }
        return dist to parent
    }

    private fun pathTo(target: Int, parent: IntArray): IntArray {
        val p = ArrayList<Int>()
        var c = target
        while (c >= 0) { p.add(c); c = parent[c] }
        p.reverse()
        return p.toIntArray()
    }

    /** Shortest path (cell indices) from any cell to the goal, for the hint thread. */
    fun pathFrom(cell: Int): IntArray {
        val (_, parent) = bfs(goal)
        val p = ArrayList<Int>()
        var c = cell
        while (c >= 0) { p.add(c); c = parent[c] }
        return p.toIntArray()
    }

    fun isWall(edge: Int) = !open[edge]
}
