package com.maze3d.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos

class LandscapeTest {
    private fun slopeDeg(s: Surface, p: Vec3) = Math.toDegrees(acos((s.normal(p) dot s.baseNormal(p)).coerceIn(-1f, 1f).toDouble()))

    @Test fun racingHillsAreFair() {
        for (kind in listOf(ShapeKind.SPHERE, ShapeKind.CUBE, ShapeKind.TORUS)) {
            val m = Maze(kind.createMesh(1f), 7)
            m.mesh.surface.lift = Landscape.racing(m, 7)
            m.applyLift()
            var worst = 0.0
            for (c in m.solution) worst = maxOf(worst, slopeDeg(m.mesh.surface, m.centers[c]))
            println("racing ${kind.name}: steepest slope on the track ${"%.1f".format(worst)} deg")
            assertTrue("${kind.name} too steep: $worst", worst < 30.0)
            assertTrue("${kind.name} has no hills: $worst", worst > 6.0)
        }
    }

    /** Push the marble along the path the way a player would (full tilt toward the next path cell) and check it gets over each turret. */
    @Test fun turretsCanBeClimbedAndSlideBackDown() {
        val m = Maze(ShapeKind.SPHERE.createMesh(1.4f), 21)
        val (el, castle) = Landscape.castle(m, 21)
        m.mesh.surface.lift = el
        m.applyLift()
        assertTrue("courtyards were made", castle.courtyards.isNotEmpty())
        assertTrue("turrets were made", castle.turrets.isNotEmpty())
        for (t in castle.turrets) {
            println("  run heights: " + t.cells.joinToString { "%.2f".format(el.at(m.mesh.surface.baseProject(m.centers[it])) / m.cellSize) } + "  links: " + t.cells.joinToString { m.links[it].size.toString() })
            val mb = Marble(m); mb.slopeG = 3.2f * 0.9f
            mb.reset(t.cells[0])
            var k = 1; var time = 0f; var topSpeed = 0f; var maxH = 0f
            while (k < t.cells.size && time < 30f) {
                val target = m.centers[t.cells[k]]
                val n = mb.surface.baseNormal(mb.position)
                var d = target - mb.position; d -= n * (d dot n)
                mb.step(d.normalized() * 3.2f, 1f / 60f); time += 1f / 60f
                maxH = maxOf(maxH, el.at(mb.surface.baseProject(mb.position)))
                if (mb.cell == t.cells[k]) k++
                if (mb.cell in t.slide) topSpeed = maxOf(topSpeed, mb.velocity.length() / m.cellSize)
            }
            println("turret: reached ${k}/${t.cells.size} cells in ${"%.1f".format(time)} s, peak height ${"%.2f".format(maxH / m.cellSize)} cells, slide speed ${"%.1f".format(topSpeed)} cells/s")
            assertTrue("marble got over the turret", k == t.cells.size)
            assertTrue("marble went up onto the turret top", maxH > t.height * 0.8f)
        }
    }

    @Test fun drawbridgesSpanTheRouteAndCycle() {
        val m = Maze(ShapeKind.SPHERE.createMesh(1.4f), 21)
        val (el, castle) = Landscape.castle(m, 21)
        m.mesh.surface.lift = el; m.applyLift()
        val avoid = (castle.courtyards.flatMap { it.toList() } + castle.turrets.flatMap { it.cells.toList() }).toSet()
        val hz = Hazards(m, java.util.Random(5), false, false, avoid = avoid, drawRnd = java.util.Random(9))
        println("drawbridges at ${hz.drawCells.toList()}")
        assertTrue("some drawbridges", hz.drawCells.size >= 2)
        for (c in hz.drawCells) {
            assertTrue("on the route", c in m.solution.toList())
            assertTrue("not on a turret or in a courtyard", c !in avoid)
            assertTrue("a plain corridor", m.links[c].size == 2)
        }
        var down = 0; var up = 0
        for (k in 0 until 700) { val d = hz.drawDown(0, k * 0.01f); if (d > 0.99f) down++; if (d < 0.01f) up++ }
        assertTrue("mostly lowered ($down/700)", down > 350)
        assertTrue("but it does raise ($up/700)", up > 80)
    }
}
