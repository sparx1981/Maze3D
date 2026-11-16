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

    private fun holeC(maze: Maze, hz: com.maze3d.core.Hazards): String {
        val parts = ArrayList<String>()
        for (c in hz.holeCells.take(16)) { val p = maze.centers[c]; parts.add("${p.x},${p.y},${p.z},${hz.holeRadius * 1.03f}") }
        return parts.joinToString(",", "[", "]")
    }

    private fun trail(maze: Maze): String {
        val parts = ArrayList<String>()
        val path = maze.solution
        val r = maze.marbleRadius * 1.25f
        var t = 0f
        for (k in 0 until minOf(path.size - 1, 60)) {
            val a = maze.centers[path[k]]; val b = maze.centers[path[k + 1]]
            for (j in 0 until 14) {
                val p = maze.mesh.surface.project(com.maze3d.core.Vec3.lerp(a, b, j / 14f))
                val n = maze.mesh.surface.normal(p)
                parts.add("${p.x},${p.y},${p.z},${t},${n.x},${n.y},${n.z},${r}")
                t += 0.07f
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
        val out0 = System.getenv("MAZE_DUMP") ?: return
        val kinds = (System.getenv("MAZE_SHAPE") ?: "SPHERE").split(",").map { ShapeKind.valueOf(it.trim()) }
        val pals = (System.getenv("MAZE_PAL") ?: "0").split(",").map { it.trim().toInt() }
        for (k in kinds) for (pi in pals) {
            var out = out0
            if (kinds.size > 1) out = out.replace(".json", "_${k.name}.json")
            if (pals.size > 1) out = out.replace(".json", "_p$pi.json")
            dumpOne(k, pi, out)
        }
    }

    private fun dumpOne(kind: ShapeKind, palIndex: Int, out: String) {
        var pal = Palette.all[palIndex]
        val terrain = System.getenv("MAZE_TERRAIN") ?: ""
        if (terrain == "SAND") pal = pal.withFloor(if (pal.themeId == 7) com.maze3d.core.Vec3(0.86f, 0.92f, 1f) else com.maze3d.core.Vec3(0.84f, 0.72f, 0.50f), Mat.SAND, 0.96f)
        if (terrain == "MUD") pal = pal.withFloor(com.maze3d.core.Vec3(0.30f, 0.20f, 0.13f), Mat.MUD, 0.4f)
        val maze = Maze(kind.createMesh(), 11)
        val marble = Marble(maze)
        // let the marble roll a little so it is mid-maze
        repeat(1) { marble.step(com.maze3d.core.Vec3(0f, -3f, 0f), 1f / 60f) }
        val b = SceneBuilder(maze, pal)
        val rn = com.maze3d.core.Rain(maze, java.util.Random(4))
        for (i in rn.level.indices) rn.level[i] = 0.35f + 0.65f * ((i * 37) % 10) / 10f
        val hz = com.maze3d.core.Hazards(maze, java.util.Random(5), true, true)
        var sandJson: String? = null
        if (System.getenv("MAZE_FIELD") != null && terrain != "") {
            val f = com.maze3d.core.SandField(maze, terrain == "MUD", 22)
            val path = maze.solution
            val surf = maze.mesh.surface
            for (k in 0 until minOf(path.size - 1, 70)) {
                val a = maze.centers[path[k]]; val bb = maze.centers[path[k + 1]]
                val dir = (bb - a).normalized()
                for (j in 0 until 12) {
                    val pt = surf.project(com.maze3d.core.Vec3.lerp(a, bb, j / 12f))
                    val cell = if (j < 7) path[k] else path[k + 1]
                    f.stamp(cell, pt, dir, maze.marbleRadius * 1.1f, 1f)
                }
                if (k % 6 == 0) f.update(0.05f)
            }
            repeat(40) { f.update(0.05f) }
            val data = FloatArray(f.atlasW * f.atlasH)
            for (c in 0 until maze.cellCount) {
                val (ox, oy) = f.tileOrigin(c)
                for (j in 0 until f.tile) for (i in 0 until f.tile) data[(oy + j) * f.atlasW + ox + i] = f.h[f.idx(c, i, j)]
            }
            sandJson = "{\"t\":${f.t},\"tile\":${f.tile},\"perRow\":${f.perRow},\"w\":${f.atlasW},\"h\":${f.atlasH},\"data\":${arr(data)}}"
        }
        val sb = StringBuilder("{")
        run {
            val n0 = maze.mesh.surface.normal(marble.position)
            val to = maze.centers[maze.solution[1]] - maze.centers[maze.solution[0]]
            val f0 = (to - n0 * (to dot n0)).normalized()
            sb.append("\"chase\":{\"cs\":${maze.cellSize},\"n\":${arr(floatArrayOf(n0.x, n0.y, n0.z))},\"f\":${arr(floatArrayOf(f0.x, f0.y, f0.z))}},")
        }
        b.planProps(kind.outerRadius)
        val gal = System.getenv("MAZE_GALLERY") != null
        sb.append("\"props\":${mesh(if (gal) b.gallery() else b.props())},")
        val med = System.getenv("MAZE_MEDIUM")
        if (med != null) {
            val mk = com.maze3d.core.Medium.valueOf(med)
            val fld = com.maze3d.core.MediumField(maze, mk, java.util.Random(3), kind.outerRadius)
            fld.warmUp(20f)
            if (System.getenv("MAZE_FLOOD") != null) for (k in 0 until 14) fld.level[maze.solution[k]] = 0.8f
            var waveJson = ""
            if (mk == com.maze3d.core.Medium.WATER) {
                val wf = com.maze3d.core.WaveField(maze, 12)
                val rr = java.util.Random(9)
                repeat(220) {
                    val c = rr.nextInt(maze.cellCount)
                    wf.disturb(c, maze.centers[c], maze.cellSize * 0.07f, -0.012f)
                    wf.update(1f / 60f)
                }
                for (k in 0 until 40) { wf.disturb(maze.solution[k], maze.centers[maze.solution[k]], maze.marbleRadius * 1.2f, -0.02f); wf.update(1f / 30f) }
                val data = FloatArray(wf.atlasW * wf.atlasH)
                for (c in 0 until maze.cellCount) { val (ox, oy) = wf.tileOrigin(c); for (j in 0 until wf.tile) for (i in 0 until wf.tile) data[(oy + j) * wf.atlasW + ox + i] = wf.h[wf.idx(c, i, j)] }
                waveJson = ",\"wave\":{\"t\":${wf.t},\"tile\":${wf.tile},\"perRow\":${wf.perRow},\"w\":${wf.atlasW},\"h\":${wf.atlasH},\"data\":${arr(data)}}"
            }
            val mm = MediumMesh(maze, mk, b.wallHeight)
            sb.append("\"medium\":{\"kind\":${when (mk) { com.maze3d.core.Medium.WATER -> 1; com.maze3d.core.Medium.GAS -> 2; else -> 3 }},\"cs\":${maze.cellSize},\"v\":${arr(mm.update(fld.level))},\"i\":${arr(mm.indices)}$waveJson},")
        }
        if (sandJson != null) sb.append("\"sand\":$sandJson,")
        sb.append("\"outer\":${kind.outerRadius},")
        sb.append("\"floor\":${mesh(b.floor(sandJson != null))},\"walls\":${mesh(b.walls())},\"puddles\":${mesh(b.puddles(rn.cells, rn.maxRadius * 0.9f))},\"levels\":${arr(rn.level)},\"ripples\":${ripples(maze, rn)},\"trail\":${trail(maze)},\"markers\":${mesh(b.markers(hz.doorEdges))},\"doors\":${mesh(b.doors(hz.doorEdges, hz.doorPhase))},\"holes\":${mesh(b.pits(hz.holeCells, hz.holePhase, hz.holeRadius, maze.cellSize))},\"lids\":${mesh(b.lids(hz.holeCells, hz.holePhase, hz.holeRadius))},\"holeC\":${holeC(maze, hz)},\"thread\":${mesh(b.thread(maze.start))},")
        sb.append("\"marble\":{\"pos\":${arr(floatArrayOf(marble.position.x, marble.position.y, marble.position.z))},\"r\":${marble.radius}},")
        fun v(c: com.maze3d.core.Vec3) = arr(floatArrayOf(c.x, c.y, c.z))
        sb.append("\"pal\":{\"floor\":${v(pal.floor)},\"floor2\":${v(pal.floor2)},\"wallMetal\":${pal.wallMetal},\"backdrop\":${pal.backdrop},\"floorMat\":${pal.floorMat},\"floorRough\":${pal.floorRough},\"wall\":${v(pal.wall)},\"wallMat\":${pal.wallMat},\"wallRough\":${pal.wallRough},\"bgTop\":${v(pal.bgTop)},\"bgBottom\":${v(pal.bgBottom)},\"accent\":${v(pal.accent)}},")
        sb.append("\"sh\":{\"SCENE_VS\":${esc(Shaders.SCENE_VS)},\"SCENE_FS\":${esc(Shaders.SCENE_FS)},\"MARBLE_VS\":${esc(Shaders.MARBLE_VS)},\"MARBLE_FS\":${esc(Shaders.MARBLE_FS)},\"SHADOW_FS\":${esc(Shaders.SHADOW_FS)},\"BG_VS\":${esc(Shaders.BG_VS)},\"BG_FS\":${esc(Shaders.BG_FS)},\"HOLE_FS\":${esc(Shaders.PIT_FS)},\"LID_VS\":${esc(Shaders.LID_VS)},\"LID_FS\":${esc(Shaders.LID_FS)},\"CLOUD_FS\":${esc(Shaders.CLOUD_FS)},\"P_DOWN\":${esc(PostShaders.DOWN_FS)},\"P_BLUR\":${esc(PostShaders.BLUR_FS)},\"P_BRIGHT\":${esc(PostShaders.BRIGHT_FS)},\"P_SSAO\":${esc(PostShaders.SSAO_FS)},\"P_CLOUD\":${esc(PostShaders.CLOUD_FS)},\"P_COMP\":${esc(PostShaders.COMPOSITE_FS)},\"P_FXAA\":${esc(PostShaders.FXAA_FS)},\"MEDIUM_FS\":${esc(Shaders.MEDIUM_FS)},\"PUDDLE_FS\":${esc(Shaders.PUDDLE_FS)},\"RAIN_VS\":${esc(Shaders.RAIN_VS)},\"RAIN_FS\":${esc(Shaders.RAIN_FS)},\"TRAIL_VS\":${esc(Shaders.TRAIL_VS)},\"TRAIL_FS\":${esc(Shaders.TRAIL_FS)},\"SPARK_VS\":${esc(Shaders.SPARK_VS)},\"SMOKE_FS\":${esc(PostShaders.SMOKE_FS)}}")
        sb.append("}")
        File(out).writeText(sb.toString())
    }
}
