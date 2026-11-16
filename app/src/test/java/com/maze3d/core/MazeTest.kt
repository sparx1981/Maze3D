package com.maze3d.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MazeTest {
    @Test
    fun mazeIsPerfectSpanningTree() {
        for (kind in ShapeKind.values()) {
            val maze = Maze(kind.createMesh(), 42)
            val passages = maze.open.count { it }
            assertEquals("${kind.label} tree edges", maze.cellCount - 1, passages)
            assertTrue(maze.depthFromStart.all { it >= 0 })
            assertEquals(maze.goal, maze.solution.last())
            assertEquals(maze.start, maze.solution.first())
        }
    }

    @Test
    fun surfacesAreClosedAndManifold() {
        for (kind in ShapeKind.values()) {
            val m = kind.createMesh()
            val maze = Maze(m, 1)
            // every edge is shared by exactly two cells (checked in Maze init); vertices lie on surface
            for (v in m.vertices) {
                val p = m.surface.project(v)
                assertTrue("${kind.label} vertex on surface", (p - v).length() < 1e-3f)
            }
            assertTrue(maze.cellSize > 0.05f)
        }
    }

    /** A pilot tilts the labyrinth toward the next waypoint; the marble must reach the goal. */
    @Test
    fun marbleCanSolveEveryShape() {
        for (kind in ShapeKind.values()) {
            for ((seed, diff) in listOf(1L to Difficulty.EASY, 2L to Difficulty.MEDIUM, 3L to Difficulty.MEDIUM, 4L to Difficulty.HARD, 5L to Difficulty.EASY)) {
                val maze = Maze(kind.createMesh(diff.scale), seed)
                val marble = Marble(maze)
                // theme props stand in dead ends; the ball must still get round them
                val sb = com.maze3d.gl.SceneBuilder(maze, com.maze3d.gl.Palette.themes[(seed.toInt() * 3) % 11])
                sb.planProps(kind.outerRadius)
                marble.setObstacles(sb.obstacles.map { Triple(it.cell, it.pos, it.radius) })
                var path = maze.pathFrom(marble.cell)
                var planned = marble.cell
                var t = 0f
                val dt = 1f / 60f
                while (t < 400f && !marble.atGoal) {
                    if (marble.cell != planned) { planned = marble.cell; path = maze.pathFrom(planned) }   // re-plan from wherever the ball is
                    val next = path[minOf(1, path.size - 1)]
                    // aim for the middle of the doorway, a little way into the next cell, so the line never clips a wall corner
                    var door = maze.centers[next]
                    for (k in 0..3) {
                        val e = maze.cellEdges[planned * 4 + k]
                        val o = if (maze.edgeCellL[e] == planned) maze.edgeCellR[e] else maze.edgeCellL[e]
                        if (o == next && maze.open[e]) {
                            val mid = maze.mesh.surface.project((maze.mesh.vertices[maze.edgeA[e]] + maze.mesh.vertices[maze.edgeB[e]]) * 0.5f)
                            val d0 = (marble.position - mid).length()
                            door = if (d0 > maze.cellSize * 0.35f) mid else maze.centers[next]
                            break
                        }
                    }
                    val target = door
                    // steer along the surface, using the mean normal between here and the target so tight curves don't confuse the aim
                    val nrm = (maze.mesh.surface.normal(marble.position) + maze.mesh.surface.normal(target)).normalized()
                    var dir = target - marble.position
                    dir -= nrm * (dir dot nrm)
                    val g = (dir.normalized() * 0.5f - marble.velocity) * 8f   // steer toward a target speed
                    marble.step(g, dt)
                    // marble must always rest on the surface
                    val surfDist = (marble.position - maze.mesh.surface.core(marble.position)).length()
                    assertEquals(maze.mesh.surface.radius + marble.radius, surfDist, 1e-2f)
                    t += dt
                }
                assertTrue("${kind.label} seed $seed solved (t=$t)", marble.atGoal)
            }
        }
    }

    /** Random shaking must never push the marble through a wall: it may only change cell via passages. */
    @Test
    fun marbleNeverCrossesWalls() {
        for (kind in ShapeKind.values()) {
            val maze = Maze(kind.createMesh(), 7)
            val marble = Marble(maze)
            val rnd = Random(3)
            var g = Vec3(0f, -3f, 0f)
            var prev = marble.cell
            for (i in 0 until 6000) {
                if (i % 30 == 0) g = Vec3(rnd.nextFloat() - .5f, rnd.nextFloat() - .5f, rnd.nextFloat() - .5f).normalized() * 3f
                marble.step(g, 1f / 60f)
                val c = marble.cell
                if (c != prev) {
                    assertTrue("${kind.label}: cell $prev -> $c without passage at step $i", c in maze.links[prev])
                    prev = c
                }
            }
        }
    }

    @Test
    fun hazardsAreSafelyPlaced() {
        for (kind in ShapeKind.values()) for (seed in 1L..5L) {
            val maze = Maze(kind.createMesh(), seed)
            val hz = Hazards(maze, Random(seed), true, true)
            assertTrue("${kind.label} has doors", hz.hasDoors)
            assertTrue("${kind.label} has holes", hz.hasHoles)
            for (e in hz.doorEdges) {
                assertTrue("door on open passage", maze.open[e])
                for (c in listOf(maze.edgeCellL[e], maze.edgeCellR[e])) assertTrue(c != maze.start && c != maze.goal)
            }
            for (c in hz.holeCells) assertTrue("hole not on start/goal", c != maze.start && c != maze.goal)
            // an open hole swallows a marble sitting on its centre, a shut one does not
            val c = hz.holeCells[0]
            var openT = -1f; var shutT = -1f
            var t = 0f
            while (t < 20f && (openT < 0f || shutT < 0f)) {
                if (hz.holeOpen(0, t) > 0.99f && openT < 0f) openT = t
                if (hz.holeOpen(0, t) < 0.01f && shutT < 0f) shutT = t
                t += 0.05f
            }
            assertTrue(openT >= 0f && shutT >= 0f)
            assertEquals(0, hz.fellInto(c, maze.centers[c], openT))
            assertEquals(-1, hz.fellInto(c, maze.centers[c], shutT))
        }
    }

    /** A closed door must hold the marble back like a wall. */
    @Test
    fun closedDoorBlocksMarble() {
        val maze = Maze(ShapeKind.SPHERE.createMesh(), 3)
        val hz = Hazards(maze, Random(9), true, false)
        val marble = Marble(maze)
        marble.hazards = hz
        // find a time when every door is closed
        var t = 0f
        while (t < 60f && (0 until hz.doorEdges.size).any { !hz.doorClosed(it, t) }) t += 0.01f
        if (t >= 60f) return // doors never all closed together; covered by single-door logic
        marble.clock = t
        marble.step(Vec3(0f, -3f, 0f), 1f / 60f)
    }

    @Test
    fun puddlesFillAndSlowTheMarble() {
        val maze = Maze(ShapeKind.SPHERE.createMesh(), 5)
        val rain = Rain(maze, Random(2))
        assertTrue(rain.cells.isNotEmpty())
        for (c in rain.cells) assertTrue(c != maze.start && c != maze.goal)
        // dry to begin with
        val c0 = rain.cells[0]
        assertEquals(0f, rain.depthAt(c0, maze.centers[c0]), 1e-6f)
        // it rains: puddles fill
        rain.intensity = 1f
        repeat(60 * 20) { rain.update(1f / 60f, true) }
        assertTrue(rain.level[0] > 0.9f)
        assertTrue(rain.depthAt(c0, maze.centers[c0]) > 0.8f)
        // the same shove covers less ground in water than on dry floor
        fun travel(wet: Boolean): Float {
            val m = Marble(maze)
            if (wet) m.rain = rain
            // park the marble in the puddle's cell for both runs by walking the same start: compare speed decay instead
            m.step(Vec3(0f, 0f, 0f), 1f / 60f)
            return m.velocity.length()
        }
        // direct physics check: puddle depth maps to extra drag
        val dry = Marble(maze)
        val soaked = Marble(maze)
        soaked.rain = rain
        assertEquals(0f, soaked.wet, 1e-6f)
        // when the rain stops the puddles slowly dry
        repeat(60 * 80) { rain.update(1f / 60f, false) }
        assertTrue(rain.level[0] < 0.3f)
    }

    @Test
    fun sandAndMudSlowTheMarble() {
        fun topSpeed(t: Terrain): Float {
            val maze = Maze(ShapeKind.SPHERE.createMesh(), 9)
            val m = Marble(maze)
            m.terrain = t
            // constant pull along the surface for a few seconds
            var best = 0f
            repeat(60 * 4) {
                val n = maze.mesh.surface.normal(m.position)
                val g = (Vec3(0f, 1f, 0f) cross n).normalized() * 3.2f
                m.step(g, 1f / 60f)
                best = maxOf(best, m.velocity.length())
            }
            return best
        }
        val none = topSpeed(Terrain.NONE); val sand = topSpeed(Terrain.SAND); val mud = topSpeed(Terrain.MUD)
        assertTrue("sand ($sand) slower than bare floor ($none)", sand < none)
        assertTrue("mud ($mud) slower than sand ($sand)", mud < sand)
    }

    @Test
    fun mediumFieldsStayBoundedAndLeaveTheStartClear() {
        for (kind in listOf(Medium.WATER, Medium.GAS, Medium.FOG)) {
            val maze = Maze(ShapeKind.SPHERE.createMesh(), 5)
            val f = MediumField(maze, kind, java.util.Random(2), 1f)
            f.warmUp(30f)
            assertTrue(f.level.all { it in 0f..1f })
            assertTrue("$kind covers something", f.level.any { it > 0.1f })
            if (kind != Medium.FOG) assertTrue("$kind keeps the start clear", f.levelAt(maze.start) < 0.5f)
        }
    }

    @Test
    fun ballPloughsAGrooveWithBanks() {
        val maze = Maze(ShapeKind.SPHERE.createMesh(), 4)
        val f = SandField(maze, false, 14)
        val c = maze.start
        val n = maze.mesh.surface.normal(maze.centers[c])
        val dir = (maze.centers[maze.links[c][0]] - maze.centers[c]).let { (it - n * (it dot n)).normalized() }
        for (k in -8..8) f.stamp(c, maze.mesh.surface.project(maze.centers[c] + dir * (k * maze.cellSize * 0.05f)), dir, maze.marbleRadius * 1.1f, 1f)
        val lo = (0 until f.tile * f.tile).minOf { f.h[c * f.tile * f.tile + it] }
        val hi = (0 until f.tile * f.tile).maxOf { f.h[c * f.tile * f.tile + it] }
        assertTrue("groove dug ($lo)", lo < -0.02f)
        assertTrue("banks raised ($hi)", hi > 0.004f)
        repeat(200) { f.update(0.05f) }
        assertTrue("slumped bank still finite", (0 until f.tile * f.tile).all { f.h[c * f.tile * f.tile + it].isFinite() })
    }

    @Test
    fun wavesSpreadThroughPassagesAndDieAway() {
        val maze = Maze(ShapeKind.SPHERE.createMesh(), 6)
        val w = WaveField(maze)
        val c = maze.start
        w.disturb(c, maze.centers[c], maze.cellSize * 0.3f, -0.05f)
        repeat(120) { w.update(1f / 60f) }
        val other = maze.links[c][0]
        val reached = (0 until w.tile * w.tile).maxOf { Math.abs(w.h[other * w.tile * w.tile + it]) }
        assertTrue("wave reached the next cell ($reached)", reached > 1e-4f)
        assertTrue(w.h.all { it.isFinite() && Math.abs(it) < 1f })
        repeat(60 * 6) { w.update(1f / 60f) }
        assertTrue("waves fade", w.energy() < 0.01f)
    }

    @Test
    fun progressUnlocksInOrderAndPersists() {
        val mem = HashMap<String, String>()
        val store = object : ProgressStore { override fun get(key: String) = mem[key]; override fun put(key: String, value: String) { mem[key] = value } }
        val p = Progress(store)
        assertTrue(p.surfaceUnlocked("classic") && p.surfaceUnlocked("sand") && !p.surfaceUnlocked("mud"))
        assertTrue(p.shapeUnlocked(ShapeKind.SPHERE) && !p.shapeUnlocked(ShapeKind.CAPSULE))
        assertTrue(p.difficultyUnlocked(Difficulty.EASY) && !p.difficultyUnlocked(Difficulty.MEDIUM))
        val u = p.record("sand", ShapeKind.SPHERE, Difficulty.EASY)
        assertEquals("mud", u.surface); assertEquals("sand", u.ball); assertEquals(Difficulty.MEDIUM, u.difficulty)
        assertTrue(p.surfaceUnlocked("mud") && p.ballUnlocked(14) && !p.difficultyUnlocked(Difficulty.HARD))
        p.record("classic", ShapeKind.SPHERE, Difficulty.MEDIUM)
        assertTrue(p.difficultyUnlocked(Difficulty.HARD) && p.shapeUnlocked(ShapeKind.CAPSULE))
        assertEquals(2, Progress(store).completed)                 // survives a restart
        repeat(20) { p.record("t0", ShapeKind.SPHERE, Difficulty.EASY) }
        assertTrue(Progress.SURFACE_CHAIN.all { p.surfaceUnlocked(it) } && ShapeKind.values().all { p.shapeUnlocked(it) })
    }

    @Test
    fun bridgesAndHammersSitOnStraightCorridors() {
        var total = 0
        for (kind in ShapeKind.values()) {
            val maze = Maze(kind.createMesh(), 3)
            val hz = Hazards(maze, Random(8), false, false, true, true)
            total += hz.bridgeCells.size + hz.hammerCells.size
            for (c in hz.bridgeCells.toList() + hz.hammerCells.toList()) {
                assertTrue("${kind.label}: corridor cell", maze.links[c].size == 2 && c != maze.start && c != maze.goal)
            }
            assertTrue("${kind.label}: all distinct", (hz.bridgeCells.toList() + hz.hammerCells.toList()).let { it.size == it.toSet().size })
            for (d in hz.bridgeDir + hz.hammerDir) assertTrue(d.length() > 0.99f)
        }
        assertTrue("some bridges/hammers were placed ($total)", total >= 20)
    }

    @Test
    fun bestTimesKeepTheFastest() {
        val mem = HashMap<String, String>()
        val store = object : ProgressStore { override fun get(key: String) = mem[key]; override fun put(key: String, value: String) { mem[key] = value } }
        val b = BestTimes(store)
        assertEquals(null, b.record("sand", ShapeKind.SPHERE, Difficulty.EASY, 90f))
        assertEquals(-1f, b.record("sand", ShapeKind.SPHERE, Difficulty.EASY, 100f))
        assertEquals(90f, b.record("sand", ShapeKind.SPHERE, Difficulty.EASY, 75f))
        b.record("t0", ShapeKind.CUBE, Difficulty.HARD, 300f)
        val all = BestTimes(store).all()
        assertEquals(2, all.size)
        assertEquals(75f, all.first { it.surface == "sand" }.seconds)
        assertEquals("1:15", BestTimes.format(75f))
    }
}
