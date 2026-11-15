package com.maze3d.core

import org.junit.Test
import java.io.File

/** Developer aid: how often does the scripted pilot solve each shape? (SOLVE_SWEEP=<output file>) */
class SolveSweepTest {
    @Test
    fun sweep() {
        val out = System.getenv("SOLVE_SWEEP") ?: return
        val sb = StringBuilder()
        for (kind in ShapeKind.values()) {
            var ok = 0; val fails = ArrayList<Long>()
            for (seed in 1L..8L) {
                val maze = Maze(kind.createMesh(), seed)
                val marble = Marble(maze)
                var path = maze.pathFrom(marble.cell); var planned = marble.cell
                var t = 0f
                while (t < 300f && !marble.atGoal) {
                    if (marble.cell != planned) { planned = marble.cell; path = maze.pathFrom(planned) }
                    val next = path[minOf(1, path.size - 1)]
                    var door = maze.centers[next]
                    for (k in 0..3) {
                        val e = maze.cellEdges[planned * 4 + k]
                        val o = if (maze.edgeCellL[e] == planned) maze.edgeCellR[e] else maze.edgeCellL[e]
                        if (o == next && maze.open[e]) { val mid = maze.mesh.surface.project((maze.mesh.vertices[maze.edgeA[e]] + maze.mesh.vertices[maze.edgeB[e]]) * 0.5f); door = if ((marble.position - mid).length() > maze.cellSize * 0.35f) mid else maze.centers[next]; break }
                    }
                    val nrm = (maze.mesh.surface.normal(marble.position) + maze.mesh.surface.normal(door)).normalized()
                    var dir = door - marble.position; dir -= nrm * (dir dot nrm)
                    marble.step((dir.normalized() * 0.5f - marble.velocity) * 8f, 1f / 60f)
                    t += 1f / 60f
                }
                if (marble.atGoal) ok++ else fails.add(seed)
            }
            sb.append("${kind.label}: $ok/8 solved, failed seeds $fails\n")
        }
        File(out).writeText(sb.toString())
    }
}
