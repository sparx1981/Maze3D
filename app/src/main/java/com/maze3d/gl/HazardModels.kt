package com.maze3d.gl

import com.maze3d.core.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Models for bridges and hammers in cell units (1 = one cell): x runs along the corridor, y across it, z up. */
object HazardModels {
    private fun c(r: Int, g: Int, b: Int) = PM.rgb(r, g, b)
    const val HAMMER_LENGTH = 0.78f
    const val HAMMER_PIVOT = 0.98f
    const val HAMMER_HEAD = 0.17f

    /** The rails of a plank bridge: stringers, ropes and posts. */
    fun bridgeFrame(halfWidth: Float) = PM().apply {
        col = c(92, 62, 38)
        for (s in intArrayOf(-1, 1)) {
            push(); translate(0f, s * (halfWidth - 0.03f), 0.0f); box(1.04f, 0.04f, 0.03f); pop()
            col = c(210, 190, 150)
            tube(listOf(Vec3(-0.5f, s * halfWidth, 0.15f), Vec3(0f, s * halfWidth, 0.12f), Vec3(0.5f, s * halfWidth, 0.15f)), 0.012f, 6)
            col = c(92, 62, 38)
            for (e in intArrayOf(-1, 1)) { push(); translate(e * 0.5f, s * halfWidth, 0.075f); cylinder(0.02f, 0.09f, 8); pop() }
        }
    }.build()

    /** One board, centred on the origin (1 / 7 of the bridge long). */
    fun bridgeBoard(halfWidth: Float) = PM().apply {
        col = c(150, 104, 62)
        push(); translate(0f, 0f, 0.016f); box(0.9f / 7f, halfWidth * 2f, 0.03f); pop()
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

    const val SWING_LENGTH = 3.0f

    /** Tall frame the swing hangs from: two legs against the corridor walls and a beam across at the top. */
    fun swingGantry(halfSpan: Float) = PM().apply {
        col = c(58, 64, 76)
        for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * halfSpan, 0f); cylinder(0.06f, SWING_LENGTH + 0.06f, 10, 0.05f); pop() }
        push(); translate(0f, 0f, SWING_LENGTH + 0.06f); rotX((PI / 2).toFloat()); cylinder(0.05f, halfSpan * 2f + 0.1f, 10); pop()
        col = c(255, 196, 40)
        for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * halfSpan, SWING_LENGTH - 0.1f); cylinder(0.075f, 0.08f, 10); pop() }
        col = c(30, 34, 42); push(); translate(0f, 0f, SWING_LENGTH); sphere(0.07f, 10, 8); pop()
    }.build()

    /** The swinging seat: a round platform with hazard stripes, hung by four chains from the pivot at the origin (it hangs along -z). */
    fun swingSeat(discR: Float) = PM().apply {
        val chain = c(150, 156, 170)
        col = chain
        for (k in 0 until 4) {
            val a = k * (PI / 2).toFloat() + (PI / 4).toFloat()
            tube(listOf(Vec3(0f, 0f, 0f), Vec3(cos(a) * discR * 0.9f, sin(a) * discR * 0.9f, -SWING_LENGTH + 0.04f)), 0.014f, 5)
        }
        push(); translate(0f, 0f, -SWING_LENGTH)
        for (i in 0 until 12) { col = if (i % 2 == 0) c(250, 200, 30) else c(40, 40, 46)
            push(); rotZ(i * (PI / 6).toFloat()); translate(discR * 0.93f, 0f, 0.0f); box(discR * 0.16f, discR * 0.5f, 0.07f); pop() }
        col = c(88, 94, 108); cylinder(discR, 0.06f, 32)
        col = c(255, 196, 40); push(); translate(0f, 0f, 0.058f); torus(discR * 0.55f, 0.012f, 28, 5); pop()
        col = c(255, 60, 40) * 1.6f; push(); translate(0f, 0f, 0.058f); cylinder(discR * 0.14f, 0.02f, 16); pop()
        pop()
    }.build()

    // ---- floor effects (cell units; x along the corridor, y across, z up) ----

    /** Round spring pad: a low plate with a red cushion and white rings. */
    fun springPad() = PM().apply {
        col = c(60, 64, 76); cylinder(0.3f, 0.03f, 28)
        col = c(235, 50, 50); push(); translate(0f, 0f, 0.03f); cylinder(0.25f, 0.035f, 28, 0.21f); pop()
        col = c(250, 240, 220); push(); translate(0f, 0f, 0.066f); torus(0.15f, 0.012f, 24, 5); pop()
        col = c(255, 196, 40); push(); translate(0f, 0f, 0.066f); torus(0.07f, 0.012f, 20, 5); pop()
    }.build()

    /** A glossy black puddle of oil: a few overlapping discs. */
    fun oil() = PM().apply {
        col = c(8, 8, 12)
        val blobs = floatArrayOf(0f, 0f, 0.30f, 0.17f, 0.08f, 0.22f, -0.14f, 0.13f, 0.2f, -0.05f, -0.19f, 0.21f, 0.2f, -0.1f, 0.16f)
        for (k in 0 until 5) { push(); translate(blobs[k * 3], blobs[k * 3 + 1], 0.002f * (k + 1)); cylinder(blobs[k * 3 + 2], 0.003f, 22); pop() }
    }.build()

    /** Conveyor belt bed with yellow side rails (the moving chevrons are separate). */
    fun belt() = PM().apply {
        col = c(46, 48, 56); push(); translate(0f, 0f, 0.015f); box(0.96f, 0.62f, 0.03f); pop()
        col = c(255, 196, 40)
        for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.32f, 0.03f); box(0.96f, 0.04f, 0.05f); pop() }
        col = c(120, 126, 140)
        for (e in intArrayOf(-1, 1)) { push(); translate(e * 0.48f, 0f, 0.03f); rotX((PI / 2).toFloat()); cylinder(0.03f, 0.62f, 10); pop() }
    }.build()

    /** One chevron arrow pointing along +x. */
    fun chevron() = PM().apply {
        col = c(255, 196, 40) * 1.2f
        for (s in intArrayOf(-1, 1)) { push(); translate(0f, 0f, 0.033f); rotZ(s * 0.7f); translate(-0.06f, 0f, 0f); box(0.2f, 0.045f, 0.012f); pop() }
    }.build()

    /** Crusher frame: four posts and a top plate. The ram is separate. */
    fun crusherFrame() = PM().apply {
        col = c(58, 64, 76)
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) { push(); translate(sx * 0.42f, sy * 0.42f, 0f); cylinder(0.04f, 1.05f, 10); pop() }
        push(); translate(0f, 0f, 1.06f); box(0.96f, 0.96f, 0.06f); pop()
        col = c(255, 196, 40)
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) { push(); translate(sx * 0.42f, sy * 0.42f, 0.95f); cylinder(0.05f, 0.05f, 10); pop() }
    }.build()

    /** The ram, centred at the origin (hangs 0.14 below and a piston rises above it). */
    fun crusherRam() = PM().apply {
        col = c(120, 126, 140); push(); translate(0f, 0f, 0.1f); cylinder(0.06f, 0.9f, 12); pop()
        for (i in 0 until 8) { col = if (i % 2 == 0) c(250, 200, 30) else c(40, 40, 46)
            push(); translate(-0.35f + i * 0.1f, 0f, -0.12f); box(0.1f, 0.7f, 0.04f); pop() }
        col = c(96, 102, 116); box(0.72f, 0.72f, 0.22f)
    }.build()

    const val SWEEP_LEN = 0.42f
    /** Paddle sweeper: a hub post (static, drawn with the arm) and a long striped arm turned around z. */
    fun sweeper() = PM().apply {
        col = c(58, 64, 76); cylinder(0.07f, 0.16f, 14)
        for (i in 0 until 8) { col = if (i % 2 == 0) c(235, 50, 50) else c(250, 240, 224)
            push(); translate(-SWEEP_LEN + 0.05f + i * (2f * SWEEP_LEN - 0.1f) / 7f, 0f, 0.08f); box((2f * SWEEP_LEN - 0.1f) / 7f, 0.07f, 0.1f); pop() }
        col = c(255, 196, 40); for (e in intArrayOf(-1, 1)) { push(); translate(e * SWEEP_LEN, 0f, 0.08f); sphere(0.055f, 10, 8); pop() }
        col = c(30, 34, 42); push(); translate(0f, 0f, 0.16f); cylinder(0.05f, 0.03f, 12); pop()
    }.build()

    /** Seesaw fulcrum, static: a wedge-like cylinder across the corridor. */
    fun seesawBase() = PM().apply {
        col = c(58, 64, 76); push(); translate(0f, 0f, 0.05f); rotX((PI / 2).toFloat()); cylinder(0.05f, 0.5f, 12); pop()
    }.build()

    /** The tilting plank centred on the fulcrum, with a stripe at each end. */
    fun seesawPlank() = PM().apply {
        col = c(150, 104, 62); push(); translate(0f, 0f, 0.075f); box(0.96f, 0.52f, 0.03f); pop()
        col = c(250, 200, 30)
        for (e in intArrayOf(-1, 1)) { push(); translate(e * 0.44f, 0f, 0.092f); box(0.08f, 0.52f, 0.012f); pop() }
        col = c(40, 40, 46)
        for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.265f, 0.085f); box(0.96f, 0.025f, 0.05f); pop() }
    }.build()
}
