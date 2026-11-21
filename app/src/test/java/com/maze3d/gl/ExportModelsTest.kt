package com.maze3d.gl

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.IdentityHashMap

/**
 * Writes every procedural 3D model in the game (theme props, their animated parts, flying creatures and hazards) as
 * Wavefront OBJ files with per-vertex colours ("v x y z r g b"), for use in other 3D tools. Off unless MAZE_EXPORT_DIR is set:
 *   MAZE_EXPORT_DIR=/some/dir gradle :app:testDebugUnitTest --offline --tests '*ExportModelsTest*'
 * Units are prop units (a prop is about 1 across); z is up. Colours above 1 (glowing parts) are clamped to 1.
 */
class ExportModelsTest {
    private val themes = listOf("candy", "day-of-the-dead", "motorsport", "football", "space", "fast-food", "science", "winter", "egypt", "neon-city",
        "jungle", "glow", "fall", "toy-world", "craft", "prehistoric", "tool-shop", "lava", "jelly", "medieval")

    private fun write(dir: File, name: String, m: PropMesh) {
        dir.mkdirs()
        val sb = StringBuilder("# $name  (${m.vertexCount} vertices, ${m.idx.size / 3} triangles)\n")
        for (i in 0 until m.vertexCount) {
            fun c(k: Int) = "%.3f".format(java.util.Locale.ROOT, m.col[i * 3 + k].coerceIn(0f, 1f))
            sb.append("v %.4f %.4f %.4f %s %s %s\n".format(java.util.Locale.ROOT, m.pos[i * 3], m.pos[i * 3 + 1], m.pos[i * 3 + 2], c(0), c(1), c(2)))
        }
        for (i in 0 until m.vertexCount) sb.append("vn %.4f %.4f %.4f\n".format(java.util.Locale.ROOT, m.nrm[i * 3], m.nrm[i * 3 + 1], m.nrm[i * 3 + 2]))
        var q = 0
        while (q < m.idx.size) { val a = m.idx[q] + 1; val b = m.idx[q + 1] + 1; val c = m.idx[q + 2] + 1; sb.append("f $a//$a $b//$b $c//$c\n"); q += 3 }
        File(dir, "$name.obj").writeText(sb.toString())
    }

    @Test fun exportAll() {
        val out = System.getenv("MAZE_EXPORT_DIR")
        assumeTrue("set MAZE_EXPORT_DIR to export", out != null)
        val root = File(out!!)
        var n = 0
        for ((id, theme) in themes.withIndex()) {
            val lib = PropLibrary.forTheme(id) ?: continue
            val dir = File(root, "props/%02d-%s".format(id, theme))
            val seen = IdentityHashMap<PropMesh, String>()
            fun put(name: String, m: PropMesh?) { if (m == null || seen.containsKey(m)) return; seen[m] = name; write(dir, name, m); n++ }
            put("junction", lib.junction); put("dead-end-a", lib.deadA); put("dead-end-b", lib.deadB); put("landmark", lib.landmark)
            put("dead-end-c", lib.deadC); put("junction-2", lib.junction2)
            lib.more.forEachIndexed { i, m -> put("extra-$i", m) }
            lib.junctionMore.forEachIndexed { i, m -> put("junction-extra-$i", m) }
            lib.floor.forEachIndexed { i, m -> put("floor-$i", m) }
            put("ring-grandstand", lib.ring)
            for ((host, sp) in lib.anims) { val hn = seen[host] ?: "prop"; put("$hn-moving-part", sp.part) }
            listOf(lib.bird, lib.birds2, lib.birds3).forEachIndexed { i, b -> if (b != null) { put("flyer-$i-body", b.body); put("flyer-$i-wing-left", b.wingL); put("flyer-$i-wing-right", b.wingR) } }
        }
        val hz = File(root, "hazards")
        val list = listOf(
            "bridge-frame" to HazardModels.bridgeFrame(0.35f), "bridge-board" to HazardModels.bridgeBoard(0.35f),
            "hammer-gantry" to HazardModels.gantry(0.5f), "hammer" to HazardModels.hammer(),
            "swing-gantry" to HazardModels.swingGantry(0.56f), "swing-seat" to HazardModels.swingSeat(0.5f),
            "key" to HazardModels.keyModel(255, 200, 40), "spring-pad" to HazardModels.springPad(),
            "conveyor-belt" to HazardModels.belt(), "conveyor-chevron" to HazardModels.chevron(),
            "crusher-frame" to HazardModels.crusherFrame(), "crusher-ram" to HazardModels.crusherRam(),
            "sweeper" to HazardModels.sweeper(), "seesaw-base" to HazardModels.seesawBase(), "seesaw-plank" to HazardModels.seesawPlank(),
            "drawbridge-deck" to HazardModels.drawbridgeDeck(), "gatehouse" to HazardModels.gatehouse(), "moat-water" to HazardModels.moatWater())
        for ((name, m) in list) { write(hz, name, m); n++ }
        println("exported $n models to $root")
    }
}
