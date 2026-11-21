package com.maze3d.core

import kotlin.random.Random

/**
 * Lays out the ground shape of themed levels: racing hills with a few famous-corner features along the track, and the
 * medieval castle's open courtyards and turrets (a ramp up onto a turret top and a slide back down).
 * Everything is driven by the level seed so races and ghosts rebuild the same ground.
 */
object Landscape {
    /** A raised run of the maze path: [cells] in path order; [top] is the turret cell; [slide] are the cells of the way down. */
    class Turret(val cells: IntArray, val top: Int, val slide: IntArray, val height: Float)
    class Castle(val courtyards: List<IntArray>, val turrets: List<Turret>)

    /** Racing: rolling ground plus a crest-and-drop, a dip-into-a-climb and a plunging S along the track. */
    fun racing(maze: Maze, seed: Long): Elevation {
        val cs = maze.cellSize
        val rnd = Random(seed xor 0x4C41505L)
        val e = Elevation(cs)
        e.hillAmp = cs * 0.42f
        e.hillFreq = 1f / (cs * 5f)
        val path = maze.solution
        if (path.size > 12) {
            val n = path.size
            fun at(f: Float) = maze.centers[path[(f * (n - 1)).toInt().coerceIn(1, n - 2)]]
            val h = cs * 0.36f; val r = cs * 1.7f
            // a blind crest with a drop straight after it (think Paddock Hill)
            val f1 = 0.18f + 0.08f * rnd.nextFloat()
            e.bumps.add(Elevation.Bump(at(f1), r, h)); e.bumps.add(Elevation.Bump(at(f1 + 3f / n), r, -h * 0.9f))
            // a compression at the bottom of a dip, straight into a steep climb (think Eau Rouge)
            val f2 = 0.45f + 0.08f * rnd.nextFloat()
            e.bumps.add(Elevation.Bump(at(f2), r, -h)); e.bumps.add(Elevation.Bump(at(f2 + 3f / n), r * 1.2f, h * 1.2f))
            // a plunging left-right as the track falls away (think the Corkscrew)
            val f3 = 0.72f + 0.08f * rnd.nextFloat()
            e.bumps.add(Elevation.Bump(at(f3), r, h * 1.1f)); e.bumps.add(Elevation.Bump(at(f3 + 2f / n), r * 0.9f, -h * 0.4f)); e.bumps.add(Elevation.Bump(at(f3 + 4f / n), r, -h * 1.1f))
        }
        return e
    }

    /** Medieval: opens a few 3x3 courtyards (knocking down their inner walls) and raises turret runs along the path. */
    fun castle(maze: Maze, seed: Long): Pair<Elevation, Castle> {
        val cs = maze.cellSize
        val rnd = Random(seed xor 0xCA57L)
        val e = Elevation(cs)
        val m = maze.mesh
        val byVertex = HashMap<Int, ArrayList<Int>>()
        for (c in 0 until maze.cellCount) for (k in 0..3) byVertex.getOrPut(m.cellVertex(c, k)) { ArrayList() }.add(c)
        fun block(c: Int): Set<Int> { val s = HashSet<Int>(); for (k in 0..3) byVertex[m.cellVertex(c, k)]?.let { s.addAll(it) }; return s }
        val protect = hashSetOf(maze.start, maze.goal)
        // courtyards
        val yards = ArrayList<IntArray>()
        val taken = HashSet<Int>()
        val want = 2 + maze.cellCount / 500
        var tries = 0
        while (yards.size < want && tries < 400) {
            tries++
            val c = rnd.nextInt(maze.cellCount)
            val b = block(c)
            if (b.size != 9 || b.any { it in protect || it in taken }) continue
            if (taken.any { t -> b.any { (maze.centers[it] - maze.centers[t]).length() < cs * 2.5f } }) continue
            for (e2 in 0 until maze.edgeCount) if (maze.edgeCellL[e2] in b && maze.edgeCellR[e2] in b) maze.openEdge(e2)
            yards.add(b.toIntArray()); taken.addAll(b)
        }
        // turrets: runs of 8 corridor cells along the solution, kept apart from each other and from the courtyards
        val turrets = ArrayList<Turret>()
        val path = maze.solution
        val run = 8
        val wantT = if (path.size > 60) 3 else if (path.size > 30) 2 else 1
        val starts = (2 until path.size - run - 2).shuffled(rnd)
        val used = HashSet<Int>()
        for (i0 in starts) {
            if (turrets.size >= wantT) break
            val cells = IntArray(run) { path[i0 + it] }
            if (cells.any { it in protect || it in taken || it in used }) continue
            if ((1..6).any { maze.links[cells[it]].size != 2 }) continue      // no side turnings off the raised part: one way up, one slide down
            if (cells.any { c -> used.any { (maze.centers[it] - maze.centers[c]).length() < cs * 3f } }) continue
            // a run that folds back beside itself would share a wall between two heights
            if ((0 until run).any { a -> (a + 2 until run).any { b -> (maze.centers[cells[a]] - maze.centers[cells[b]]).length() < cs * 1.2f } }) continue
            val pts = cells.map { maze.centers[it] }
            val acc = FloatArray(run).also { for (k in 1 until run) it[k] = it[k - 1] + (pts[k] - pts[k - 1]).length() }
            fun mid(k: Int) = (acc[k] + acc[k + 1]) * 0.5f
            val hgt = cs * 1.15f
            // ramp up over cells 0-3, cell 4 flat on top, slide down over cells 5-6 to the ground in cell 7
            val s0 = acc[0] + (acc[1] - acc[0]) * 0.3f; val sTop0 = mid(3); val sTop1 = mid(4); val sEnd = acc[7] - (acc[7] - acc[6]) * 0.2f
            val fence = cells.flatMap { block(it) }.toSet().filter { it !in cells }.map { Triple(maze.centers[it], maze.axisU[it], maze.axisV[it]) }
            e.plateaus.add(Elevation.Plateau(pts, cells.map { maze.axisU[it] }, cells.map { maze.axisV[it] }, fence) { s ->
                hgt * Elevation.smooth((s - s0) / (sTop0 - s0)) * (1f - Elevation.smooth((s - sTop1) / (sEnd - sTop1))) })
            turrets.add(Turret(cells, cells[4], intArrayOf(cells[5], cells[6]), hgt))
            used.addAll(cells.toList())
        }
        return e to Castle(yards, turrets)
    }
}
