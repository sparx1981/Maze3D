package com.maze3d.gl

import com.maze3d.core.Vec3
import kotlin.math.PI

/** Models for bridges and hammers in cell units (1 = one cell): x runs along the corridor, y across it, z up. */
object HazardModels {
    private fun c(r: Int, g: Int, b: Int) = PM.rgb(r, g, b)
    const val HAMMER_LENGTH = 0.78f
    const val HAMMER_PIVOT = 0.98f
    const val HAMMER_HEAD = 0.17f

    /** A wooden plank bridge of the given half-width. */
    fun bridge(halfWidth: Float) = PM().apply {
        val boards = 7
        val len = 1.0f / boards
        for (i in 0 until boards) {
            val x = -0.5f + (i + 0.5f) * len
            val tint = 0.82f + 0.18f * ((i * 37) % 7) / 7f
            col = c((150 * tint).toInt(), (104 * tint).toInt(), (62 * tint).toInt())
            push(); translate(x, 0f, 0.016f); box(len * 0.9f, halfWidth * 2f, 0.03f); pop()
        }
        col = c(92, 62, 38)
        for (s in intArrayOf(-1, 1)) {                                                // stringers under the boards, ropes above
            push(); translate(0f, s * (halfWidth - 0.03f), 0.0f); box(1.04f, 0.04f, 0.03f); pop()
            col = c(210, 190, 150)
            tube(listOf(Vec3(-0.5f, s * halfWidth, 0.15f), Vec3(0f, s * halfWidth, 0.12f), Vec3(0.5f, s * halfWidth, 0.15f)), 0.012f, 6)
            col = c(92, 62, 38)
            for (e in intArrayOf(-1, 1)) { push(); translate(e * 0.5f, s * halfWidth, 0.075f); cylinder(0.02f, 0.09f, 8); pop() }
        }
    }.build()

    /** The frame the hammer hangs from: two posts against the walls and a beam across. */
    fun gantry(halfSpan: Float) = PM().apply {
        col = c(58, 64, 76)
        for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * halfSpan, 0f); cylinder(0.045f, HAMMER_PIVOT + 0.03f, 10); pop() }
        push(); translate(0f, 0f, HAMMER_PIVOT + 0.03f); rotX((PI / 2).toFloat()); cylinder(0.03f, halfSpan * 2f, 10); pop()
        col = c(255, 196, 40)
        for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * halfSpan, HAMMER_PIVOT - 0.05f); cylinder(0.052f, 0.06f, 10); pop() }
        col = c(30, 34, 42)
        push(); translate(0f, 0f, HAMMER_PIVOT); sphere(0.05f, 10, 8); pop()
    }.build()

    /** Rod and mallet head with the pivot at the origin, hanging along -z. */
    fun hammer() = PM().apply {
        col = c(120, 126, 140)
        push(); translate(0f, 0f, -HAMMER_LENGTH + 0.02f); cylinder(0.016f, HAMMER_LENGTH - 0.02f, 8); pop()
        push(); translate(0f, 0f, -HAMMER_LENGTH); rotX((PI / 2).toFloat())
        for (i in 0 until 6) {
            col = if (i % 2 == 0) c(230, 50, 40) else c(250, 232, 214)
            push(); translate(0f, 0f, -0.16f + i * 0.053f); cylinder(HAMMER_HEAD, 0.055f, 18); pop()
        }
        col = c(40, 44, 54); push(); translate(0f, 0f, -0.18f); cylinder(HAMMER_HEAD * 0.7f, 0.02f, 14); pop(); push(); translate(0f, 0f, 0.16f); cylinder(HAMMER_HEAD * 0.7f, 0.02f, 14); pop()
        pop()
    }.build()
}
