package com.maze3d.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class ChaseCamTest {
    /** Rolls a ball along the solution at a brisk pace and returns the share of frames where it is outside the portrait screen. */
    private fun lostShare(kind: ShapeKind, seed: Long, zoom: Float, speedCells: Float, report: Boolean = false): Float {
        val maze = Maze(kind.createMesh(), seed)
        val surf = maze.mesh.surface
        val cam = ChaseCam()
        val cs = maze.cellSize
        val path = maze.solution
        var frames = 0; var lost = 0
        var pos = maze.centers[path[0]]
        var seg = 1
        val dt = 1f / 60f
        var worst = 0f
        while (seg < path.size) {
            val target = maze.centers[path[seg]]
            val d = target - pos
            val dist = d.length()
            val step = speedCells * cs * dt
            val dir = if (dist > 1e-6f) d / dist else Vec3.ZERO
            pos = if (dist <= step) { seg++; target } else surf.project(pos + dir * step)
            val n = surf.normal(pos)
            val p = pos + n * maze.marbleRadius
            val vel = dir * (speedCells * cs)
            cam.update(p, n, vel, cs, zoom, dt, 0f)
            val fov = 72f + 12f * cam.speed
            val th = tan(Math.toRadians(fov / 2.0)).toFloat()
            val aspect = 9f / 19.5f
            val fwd = (cam.tgtS - cam.eyeS).normalized()
            val right = (fwd cross cam.upS).normalized()
            val up = right cross fwd
            val rel = p - cam.eyeS
            val depth = rel dot fwd
            val x = if (depth > 1e-4f) (rel dot right) / (depth * th * aspect) else 99f
            val y = if (depth > 1e-4f) (rel dot up) / (depth * th) else 99f
            frames++
            val out = kotlin.math.max(kotlin.math.abs(x), kotlin.math.abs(y))
            worst = kotlin.math.max(worst, out)
            if (out > 0.95f) lost++
        }
        if (report) println("CAMTEST ${kind.label} seed=$seed zoom=$zoom speed=$speedCells lost=${lost}/${frames} worst=$worst")
        return lost / frames.toFloat()
    }

    @Test
    fun ballStaysOnScreen() {
        var bad = 0
        for (kind in ShapeKind.values()) for (zoom in floatArrayOf(0.3f, 0.67f, 1f)) for (spd in floatArrayOf(2f, 5f)) {
            val l = lostShare(kind, 3, zoom, spd, true)
            if (l > 0.01f) bad++
        }
        assertTrue("some shapes lose the ball", bad == 0)
    }

    /** Same check, but with the real marble physics steered along the route like a player would (walls, bounces, overshoot). */
    private fun playLost(kind: ShapeKind, seed: Long, zoom: Float, report: Boolean, wild: Boolean = false): Float {
        val maze = Maze(kind.createMesh(), seed)
        val mb = Marble(maze)
        val cam = ChaseCam()
        val cs = maze.cellSize
        val path = maze.solution
        val idx = HashMap<Int, Int>(); path.forEachIndexed { i, c -> idx[c] = i }
        val dt = 1f / 60f
        var frames = 0; var lost = 0; var worst = 0f
        var furthest = 0
        var hidden = 0
        val rr = java.util.Random(seed * 31 + 7)
        var wildLeft = 0; var wildAng = 0f
        for (step in 0 until 60 * (if (wild) 120 else 240)) {
            val here = idx[mb.cell] ?: furthest
            furthest = maxOf(furthest, here)
            if (mb.atGoal) break
            val tgtCell = path[minOf(here + 1, path.size - 1)]
            val n0 = mb.surface.normal(mb.position)
            var d = maze.centers[tgtCell] - mb.position
            d -= n0 * (d dot n0)
            var g = if (d.lengthSq() > 1e-10f) d.normalized() * 3.2f else Vec3.ZERO
            if (wild) {
                if (wildLeft <= 0 && rr.nextFloat() < 0.01f) { wildLeft = 25 + rr.nextInt(40); wildAng = (rr.nextFloat() - 0.5f) * 6.28f }
                if (wildLeft > 0) { wildLeft--; g = Quat.fromAxisAngle(n0, wildAng).rotate(g) }
            }
            mb.step(g, dt)
            val n = mb.surface.normal(mb.position)
            cam.update(mb.position, n, mb.velocity, cs, zoom, dt, 0f)
            val fov = 72f + 12f * cam.speed
            val th = tan(Math.toRadians(fov / 2.0)).toFloat()
            val aspect = 9f / 19.5f
            val fwd = (cam.tgtS - cam.eyeS).normalized()
            val right = (fwd cross cam.upS).normalized()
            val up = right cross fwd
            val rel = mb.position - cam.eyeS
            val depth = rel dot fwd
            val x = if (depth > 1e-4f) (rel dot right) / (depth * th * aspect) else 99f
            val y = if (depth > 1e-4f) (rel dot up) / (depth * th) else 99f
            frames++
            val out = kotlin.math.max(kotlin.math.abs(x), kotlin.math.abs(y))
            worst = kotlin.math.max(worst, out)
            if (out > 0.95f) lost++
            val sf = mb.surface
            var blocked = false
            for (k in 0..24) { val q = cam.eyeS + (mb.position - cam.eyeS) * (k / 28f); if ((q - sf.core(q)).length() < sf.radius * 0.97f) blocked = true }
            if (blocked) hidden++
        }
        if (report) println("PLAYTEST hidden=$hidden ${kind.label} seed=$seed zoom=$zoom lost=$lost/$frames worst=$worst progress=$furthest/${path.size}")
        return lost / maxOf(1, frames).toFloat()
    }

    @Test
    fun playedBallStaysOnScreen() {
        for (kind in listOf(ShapeKind.TORUS, ShapeKind.SPHERE, ShapeKind.CUBE)) for (zoom in floatArrayOf(0.3f, 0.67f, 1f)) for (seed in 1L..3L) assertTrue("$kind zoom $zoom seed $seed lost the ball", playLost(kind, seed, zoom, false) == 0f)
    }

    @Test
    fun wildPlayStaysOnScreen() {
        for (kind in listOf(ShapeKind.TORUS, ShapeKind.SPHERE, ShapeKind.CUBE)) for (zoom in floatArrayOf(0.3f, 0.67f, 1f)) for (seed in 1L..3L) assertTrue("$kind zoom $zoom seed $seed lost the ball", playLost(kind, seed, zoom, false, true) == 0f)
    }
}
