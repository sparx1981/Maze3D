package com.maze3d.core

import java.util.Random

/**
 * Keys & Gates: coloured gates close passages on the route to the exit, and each key lies in a side branch
 * that can be reached without crossing its own gate (or any later one), so the maze is always finishable.
 */
class KeyGates(private val maze: Maze, rnd: Random, count: Int, avoid: Set<Int>) {
    val gateEdges: IntArray
    val keyCells: IntArray
    val gateOpen: BooleanArray
    val keyTaken: BooleanArray
    private val gateOfEdge = HashMap<Int, Int>()

    init {
        val path = maze.solution
        val protectedCells = HashSet<Int>(avoid)
        protectedCells.add(maze.start); protectedCells.add(maze.goal)
        for (o in maze.links[maze.start]) protectedCells.add(o)
        for (o in maze.links[maze.goal]) protectedCells.add(o)
        fun edgeBetween(a: Int, b: Int): Int {
            for (j in 0..3) {
                val e = maze.cellEdges[a * 4 + j]
                val o = if (maze.edgeCellL[e] == a) maze.edgeCellR[e] else maze.edgeCellL[e]
                if (o == b && maze.open[e]) return e
            }
            return -1
        }
        val gates = ArrayList<Int>()
        val n = path.size
        for (i in 0 until count) {
            val base = ((i + 1f) / (count + 1f) * (n - 1)).toInt().coerceIn(2, n - 3)
            var chosen = -1
            for (d in 0..n) {
                for (k in intArrayOf(base + d, base - d)) {
                    if (k < 2 || k > n - 3) continue
                    val e = edgeBetween(path[k], path[k + 1])
                    if (e < 0 || path[k] in protectedCells || path[k + 1] in protectedCells) continue
                    if (gates.any { g -> val ga = maze.edgeCellL[g]; val gb = maze.edgeCellR[g]; ga == path[k] || gb == path[k] || ga == path[k + 1] || gb == path[k + 1] }) continue
                    if (gates.size > 0 && kotlin.math.abs(path.indexOf(maze.edgeCellL[gates.last()]) - k) < 3) continue
                    chosen = e; break
                }
                if (chosen >= 0) break
            }
            if (chosen >= 0) gates.add(chosen)
        }
        gateEdges = gates.toIntArray()
        gateEdges.forEachIndexed { i, e -> gateOfEdge[e] = i }
        gateOpen = BooleanArray(gateEdges.size)
        keyTaken = BooleanArray(gateEdges.size)
        val onPath = path.toHashSet()
        val keys = IntArray(gateEdges.size) { -1 }
        for (j in gateEdges.indices) {
            // flood from the start with gates j.. shut: the key must be somewhere in this region
            val dist = HashMap<Int, Int>(); val q = ArrayDeque<Int>()
            dist[maze.start] = 0; q.add(maze.start)
            while (q.isNotEmpty()) {
                val c = q.removeFirst()
                for (o in maze.links[c]) {
                    if (o in dist) continue
                    val e = edgeBetween(c, o)
                    val g = if (e >= 0) gateOfEdge[e] else null
                    if (g != null && g >= j) continue
                    dist[o] = dist[c]!! + 1; q.add(o)
                }
            }
            val pool = dist.keys.filter { it !in protectedCells && it !in keys && it != maze.start }
            val score = HashMap<Int, Int>()
            for (c in pool) score[c] = (if (c !in onPath) 1000 else 0) + (if (maze.links[c].size == 1) 500 else 0) + dist[c]!! + rnd.nextInt(3)
            val best = pool.maxByOrNull { score[it]!! }
            keys[j] = best ?: dist.keys.first { it != maze.start }
        }
        keyCells = keys
    }

    val count get() = gateEdges.size
    val collected get() = keyTaken.count { it }
    fun isShut(e: Int): Boolean { val g = gateOfEdge[e] ?: return false; return !gateOpen[g] }
    /** Picks up the key lying in [cell], if any; returns its index or -1. */
    fun take(cell: Int): Int {
        for (j in keyCells.indices) if (!keyTaken[j] && keyCells[j] == cell) { keyTaken[j] = true; gateOpen[j] = true; return j }
        return -1
    }
}
