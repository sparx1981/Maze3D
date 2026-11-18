package com.maze3d.core

import kotlin.math.exp
import kotlin.math.min

/** The follow camera's pose maths, kept free of GL so it can be tested: it sits behind and above the ball and looks down the track. */
class ChaseCam {
    var f = Vec3(0f, 0f, 1f)          // heading along the surface
    var init = false
    var eyeS = Vec3.ZERO
    var tgtS = Vec3.ZERO
    var upS = Vec3(0f, 1f, 0f)
    var speed = 0f                    // 0..1

    fun update(pos: Vec3, n: Vec3, vel: Vec3, cs: Float, zoomV: Float, dt: Float, yaw: Float, aspect: Float = 9f / 19.5f) {
        var h0 = f - n * (f dot n)
        h0 = if (h0.lengthSq() < 1e-8f) (Vec3(1f, 0f, 0f) - n * n.x).normalized() else h0.normalized()
        if (yaw != 0f) h0 = Quat.fromAxisAngle(n, yaw).rotate(h0)
        val vt = vel - n * (vel dot n)
        val sp = vt.length()
        if (sp > cs * 0.25f && dt > 0f) {                          // swing round behind the direction of travel
            val a = 1f - exp(-3.2f * dt * min(1f, sp / (cs * 1.5f)))
            h0 = (h0 * (1f - a) + (vt / sp) * a).normalized()
        }
        f = h0
        speed = (sp / (cs * 4f)).coerceIn(0f, 1f)
        val zf = 2.4f + (0.35f - 2.4f) * zoomV
        val back = cs * 1.9f * zf + cs * 0.8f * speed
        val h = cs * 1.8f * zf
        val eyeL = pos + n * h - f * back
        val tgtL = pos + f * (cs * 1.15f) - n * (cs * 0.05f)
        if (!init) { eyeS = eyeL; tgtS = tgtL; upS = n; init = true }
        else if (dt > 0f) {
            val a = 1f - exp(-9f * dt)
            eyeS += (eyeL - eyeS) * a
            tgtS += (tgtL - tgtS) * a
            upS = (upS + (n - upS) * (1f - exp(-6f * dt))).normalized()
            keepInFrame(pos, aspect, dt)
        }
    }

    /**
     * Framing guard: a portrait screen is narrow, so a ball that slides sideways (or is knocked about) can leave the picture faster than the
     * heading follows. When it drifts toward the edge, the camera strafes after it (and tilts for the vertical edges) until it is back inside.
     */
    private fun keepInFrame(pos: Vec3, aspect: Float, dt: Float) {
        val th = kotlin.math.tan(Math.toRadians((72f + 12f * speed) / 2.0)).toFloat()
        val fwd = (tgtS - eyeS).normalized()
        val right = (fwd cross upS).normalized()
        val up = right cross fwd
        val rel = pos - eyeS
        val depth = rel dot fwd
        if (depth < 1e-4f) return
        val xn = (rel dot right) / (depth * th * aspect)
        val yn = (rel dot up) / (depth * th)
        val g = 1f - exp(-18f * dt)
        val limX = 0.42f; val limY = 0.55f
        if (kotlin.math.abs(xn) > limX) {
            val shift = right * ((xn - kotlin.math.sign(xn) * limX) * depth * th * aspect * g)
            eyeS += shift; tgtS += shift
        }
        if (kotlin.math.abs(yn) > limY) tgtS += up * ((yn - kotlin.math.sign(yn) * limY) * depth * th * g)
    }
}
