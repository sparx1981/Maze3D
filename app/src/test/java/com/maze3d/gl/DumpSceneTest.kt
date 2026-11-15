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

    @Test
    fun dump() {
        val out = System.getenv("MAZE_DUMP") ?: return
        val kind = ShapeKind.valueOf(System.getenv("MAZE_SHAPE") ?: "SPHERE")
        val pal = Palette.all[(System.getenv("MAZE_PAL") ?: "0").toInt()]
        val maze = Maze(kind.createMesh(), 11)
        val marble = Marble(maze)
        // let the marble roll a little so it is mid-maze
        repeat(1) { marble.step(com.maze3d.core.Vec3(0f, -3f, 0f), 1f / 60f) }
        val b = SceneBuilder(maze, pal)
        val hz = com.maze3d.core.Hazards(maze, java.util.Random(5), true, true)
        val sb = StringBuilder("{")
        sb.append("\"outer\":${kind.outerRadius},")
        sb.append("\"floor\":${mesh(b.floor())},\"walls\":${mesh(b.walls())},\"markers\":${mesh(b.markers(hz.doorEdges))},\"doors\":${mesh(b.doors(hz.doorEdges, hz.doorPhase))},\"holes\":${mesh(b.holes(hz.holeCells, hz.holePhase, hz.holeRadius))},\"thread\":${mesh(b.thread(maze.start))},")
        sb.append("\"marble\":{\"pos\":${arr(floatArrayOf(marble.position.x, marble.position.y, marble.position.z))},\"r\":${marble.radius}},")
        fun v(c: com.maze3d.core.Vec3) = arr(floatArrayOf(c.x, c.y, c.z))
        sb.append("\"pal\":{\"floor\":${v(pal.floor)},\"floorMat\":${pal.floorMat},\"floorRough\":${pal.floorRough},\"wall\":${v(pal.wall)},\"wallMat\":${pal.wallMat},\"wallRough\":${pal.wallRough},\"bgTop\":${v(pal.bgTop)},\"bgBottom\":${v(pal.bgBottom)},\"accent\":${v(pal.accent)}},")
        sb.append("\"sh\":{\"SCENE_VS\":${esc(Shaders.SCENE_VS)},\"SCENE_FS\":${esc(Shaders.SCENE_FS)},\"MARBLE_VS\":${esc(Shaders.MARBLE_VS)},\"MARBLE_FS\":${esc(Shaders.MARBLE_FS)},\"SHADOW_FS\":${esc(Shaders.SHADOW_FS)},\"BG_VS\":${esc(Shaders.BG_VS)},\"BG_FS\":${esc(Shaders.BG_FS)},\"HOLE_FS\":${esc(Shaders.HOLE_FS)},\"CLOUD_FS\":${esc(Shaders.CLOUD_FS)}}")
        sb.append("}")
        File(out).writeText(sb.toString())
    }
}
