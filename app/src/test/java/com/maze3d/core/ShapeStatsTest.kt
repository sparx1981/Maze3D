package com.maze3d.core

import org.junit.Test
import java.io.File

/** Prints per-shape cell statistics when SHAPE_STATS names an output file (developer aid). */
class ShapeStatsTest {
    @Test
    fun stats() {
        val out = System.getenv("SHAPE_STATS") ?: return
        val sb = StringBuilder()
        for (k in ShapeKind.values()) {
            val mesh = k.createMesh()
            val maze = Maze(mesh, 1)
            val e = ArrayList<Float>()
            for (i in 0 until maze.edgeCount) e.add((mesh.vertices[maze.edgeA[i]] - mesh.vertices[maze.edgeB[i]]).length())
            e.sort()
            fun pct(p: Float) = e[(p * (e.size - 1)).toInt()]
            var narrowOpen = 0; var minOpen = 9f
            for (i in 0 until maze.edgeCount) if (maze.open[i]) { val l = (mesh.vertices[maze.edgeA[i]] - mesh.vertices[maze.edgeB[i]]).length(); minOpen = minOf(minOpen, l); if (l < 2f * maze.marbleRadius * 1.15f + 2f * maze.wallHalfThickness) narrowOpen++ }
            var pos = 0; var neg = 0; var worstSkew = 0f
            for (c in 0 until maze.cellCount) {
                val v = mesh.vertices
                val e1 = ((v[mesh.cellVertex(c, 1)] - v[mesh.cellVertex(c, 0)]) + (v[mesh.cellVertex(c, 2)] - v[mesh.cellVertex(c, 3)])) * 0.5f
                val e2 = ((v[mesh.cellVertex(c, 3)] - v[mesh.cellVertex(c, 0)]) + (v[mesh.cellVertex(c, 2)] - v[mesh.cellVertex(c, 1)])) * 0.5f
                val n = mesh.surface.normal(maze.centers[c])
                if (((e1 cross e2) dot n) >= 0f) pos++ else neg++
                val sk = kotlin.math.abs(e1.normalized() dot e2.normalized()); if (sk > worstSkew) worstSkew = sk
            }
            sb.append("   orient+=$pos orient-=$neg worstSkew=%.2f ".format(worstSkew))
            sb.append("   minOpen=%.3f narrowOpen=%d ".format(minOpen, narrowOpen))
            sb.append("%-14s cells=%4d cs=%.3f min=%.3f p5=%.3f p50=%.3f p95=%.3f max=%.3f marble=%.4f\\n".format(k.label, maze.cellCount, maze.cellSize, e.first(), pct(0.05f), pct(0.5f), pct(0.95f), e.last(), maze.marbleRadius))
        }
        File(out).writeText(sb.toString())
    }
}
