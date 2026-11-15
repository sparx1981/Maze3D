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
}
