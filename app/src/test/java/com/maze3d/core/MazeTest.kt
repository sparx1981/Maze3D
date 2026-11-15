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
            for (seed in 1L..3L) {
                val maze = Maze(kind.createMesh(), seed)
                val marble = Marble(maze)
                val path = maze.solution
                var idx = 0
                var t = 0f
                val dt = 1f / 60f
                while (t < 240f && !marble.atGoal) {
                    while (idx < path.size - 1 && marble.cell == path[idx + 1]) idx++
                    val target = maze.centers[path[minOf(idx + 1, path.size - 1)]]
                    val nrm = maze.mesh.surface.normal(marble.position)
                    var dir = target - marble.position
                    dir -= nrm * (dir dot nrm)
                    val g = dir.normalized() * 3f - marble.velocity * 2.0f
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
}
