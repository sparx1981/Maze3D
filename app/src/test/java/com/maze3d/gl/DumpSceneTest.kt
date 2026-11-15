package com.maze3d.gl

import com.maze3d.core.Marble
import com.maze3d.core.Maze
import com.maze3d.core.ShapeKind
import org.junit.Test
import java.io.File

/** Dumps a scene plus the shader sources as JSON so it can be rendered in a WebGL2 preview. Opt-in via MAZE_DUMP. */
class DumpSceneTest {
    private fun arr(f: FloatArray) = f.joinToString(",", "[", "]") { if (it == it.toInt().toFloat()) it.toInt().toString() else "%.5f".format(it) }
    private fun arr(f: IntArray) = f.joinToString(",", "[", "]")
    private fun mesh(mb: MeshBuilder) = "{\"v\":${arr(mb.vertices())},\"i\":${arr(mb.indices())}}"
    private fun esc(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun trail(maze: Maze): String {
        val parts = ArrayList<String>()
        val path = maze.solution
        val r = maze.marbleRadius * 1.15f
        var t = 0f
        for (k in 0 until minOf(path.size - 1, 60)) {
            val a = maze.centers[path[k]]; val b = maze.centers[path[k + 1]]
            for (j in 0 until 4) {
                val p = maze.mesh.surface.project(com.maze3d.core.Vec3.lerp(a, b, j / 4f))
                val n = maze.mesh.surface.normal(p)
                parts.add("${p.x},${p.y},${p.z},${t},${n.x},${n.y},${n.z},${r}")
                t += 0.25f
            }
        }
        return parts.joinToString(",", "[", "]")
    }

    private fun ripples(maze: Maze, rn: com.maze3d.core.Rain): String {
        val parts = ArrayList<String>()
        for (i in rn.cells.indices.take(10)) {
            val c = maze.centers[rn.cells[i]]
            val n = maze.mesh.surface.normal(c)
            parts.add("[${c.x + n.x * 0.002f},${c.y + n.y * 0.002f},${c.z + n.z * 0.002f},${0.15f + 0.12f * (i % 5)},${0.8f + 0.2f * (i % 3)}]")
        }
        return parts.joinToString(",", "[", "]")
    }

    @Test
    fun dump() {
        val out = System.getenv("MAZE_DUMP") ?: return
        val kind = ShapeKind.valueOf(System.getenv("MAZE_SHAPE") ?: "SPHERE")
        var pal = Palette.all[(System.getenv("MAZE_PAL") ?: "0").toInt()]
        val terrain = System.getenv("MAZE_TERRAIN") ?: ""
        if (terrain == "SAND") pal = pal.withFloor(com.maze3d.core.Vec3(0.84f, 0.72f, 0.50f), Mat.SAND, 0.96f)
        if (terrain == "MUD") pal = pal.withFloor(com.maze3d.core.Vec3(0.30f, 0.20f, 0.13f), Mat.MUD, 0.4f)
        val maze = Maze(kind.createMesh(), 11)
        val marble = Marble(maze)
        // let the marble roll a little so it is mid-maze
        repeat(1) { marble.step(com.maze3d.core.Vec3(0f, -3f, 0f), 1f / 60f) }
        val b = SceneBuilder(maze, pal)
        val rn = com.maze3d.core.Rain(maze, java.util.Random(4))
        for (i in rn.level.indices) rn.level[i] = 0.35f + 0.65f * ((i * 37) % 10) / 10f
        val hz = com.maze3d.core.Hazards(maze, java.util.Random(5), true, true)
        val sb = StringBuilder("{")
        sb.append("\"outer\":${kind.outerRadius},")
        sb.append("\"floor\":${mesh(b.floor())},\"walls\":${mesh(b.walls())},\"puddles\":${mesh(b.puddles(rn.cells, rn.maxRadius * 0.9f))},\"levels\":${arr(rn.level)},\"ripples\":${ripples(maze, rn)},\"trail\":${trail(maze)},\"markers\":${mesh(b.markers(hz.doorEdges))},\"doors\":${mesh(b.doors(hz.doorEdges, hz.doorPhase))},\"holes\":${mesh(b.holes(hz.holeCells, hz.holePhase, hz.holeRadius))},\"thread\":${mesh(b.thread(maze.start))},")
        sb.append("\"marble\":{\"pos\":${arr(floatArrayOf(marble.position.x, marble.position.y, marble.position.z))},\"r\":${marble.radius}},")
        fun v(c: com.maze3d.core.Vec3) = arr(floatArrayOf(c.x, c.y, c.z))
        sb.append("\"pal\":{\"floor\":${v(pal.floor)},\"floorMat\":${pal.floorMat},\"floorRough\":${pal.floorRough},\"wall\":${v(pal.wall)},\"wallMat\":${pal.wallMat},\"wallRough\":${pal.wallRough},\"bgTop\":${v(pal.bgTop)},\"bgBottom\":${v(pal.bgBottom)},\"accent\":${v(pal.accent)}},")
        sb.append("\"sh\":{\"SCENE_VS\":${esc(Shaders.SCENE_VS)},\"SCENE_FS\":${esc(Shaders.SCENE_FS)},\"MARBLE_VS\":${esc(Shaders.MARBLE_VS)},\"MARBLE_FS\":${esc(Shaders.MARBLE_FS)},\"SHADOW_FS\":${esc(Shaders.SHADOW_FS)},\"BG_VS\":${esc(Shaders.BG_VS)},\"BG_FS\":${esc(Shaders.BG_FS)},\"HOLE_FS\":${esc(Shaders.HOLE_FS)},\"CLOUD_FS\":${esc(Shaders.CLOUD_FS)},\"P_DOWN\":${esc(PostShaders.DOWN_FS)},\"P_BLUR\":${esc(PostShaders.BLUR_FS)},\"P_BRIGHT\":${esc(PostShaders.BRIGHT_FS)},\"P_SSAO\":${esc(PostShaders.SSAO_FS)},\"P_CLOUD\":${esc(PostShaders.CLOUD_FS)},\"P_COMP\":${esc(PostShaders.COMPOSITE_FS)},\"P_FXAA\":${esc(PostShaders.FXAA_FS)},\"PUDDLE_FS\":${esc(Shaders.PUDDLE_FS)},\"RAIN_VS\":${esc(Shaders.RAIN_VS)},\"RAIN_FS\":${esc(Shaders.RAIN_FS)},\"TRAIL_VS\":${esc(Shaders.TRAIL_VS)},\"TRAIL_FS\":${esc(Shaders.TRAIL_FS)},\"SPARK_VS\":${esc(Shaders.SPARK_VS)},\"SMOKE_FS\":${esc(PostShaders.SMOKE_FS)}}")
        sb.append("}")
        File(out).writeText(sb.toString())
    }
}
