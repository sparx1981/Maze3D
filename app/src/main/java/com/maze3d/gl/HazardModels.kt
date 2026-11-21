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

    private val steelDk = c(52, 58, 70); private val steel = c(96, 104, 118); private val chrome = c(214, 220, 228)
    private val hazY = c(255, 196, 40); private val hazK = c(30, 30, 34)
    /** Steel I-beam standing on z = 0 at (x, y): two flanges and a web, [h] tall, flanges facing ±[alongX] ? x : y. */
    private fun PM.iBeam(x: Float, y: Float, h: Float, w: Float, alongX: Boolean) {
        col = steelDk
        val fx = if (alongX) w else w * 0.18f; val fy = if (alongX) w * 0.18f else w
        for (s in intArrayOf(-1, 1)) at(x + (if (alongX) 0f else s * w * 0.42f), y + (if (alongX) s * w * 0.42f else 0f), h * 0.5f) { box(fx, fy, h) }
        at(x, y, h * 0.5f) { box(if (alongX) w * 0.9f else w * 0.12f, if (alongX) w * 0.12f else w * 0.9f, h) }
    }
    /** Yellow-and-black hazard banding wrapped round a post of radius [r], from z0 to z1. */
    private fun PM.hazardBands(x: Float, y: Float, r: Float, z0: Float, z1: Float, n: Int = 4) {
        for (k in 0 until n) { col = if (k % 2 == 0) hazY else hazK; at(x, y, z0 + (z1 - z0) * k / n) { cylinder(r, (z1 - z0) / n, 12) } }
    }
    private fun PM.bolt(x: Float, y: Float, z: Float, r: Float = 0.012f) { col = c(150, 156, 168); at(x, y, z) { cylinder(r, r * 0.8f, 6) } }

    /** The rails of a plank bridge: stringers, ropes and posts. */
    fun bridgeFrame(halfWidth: Float) = PM().apply {
        // carved posts with caps at each end, sagging handrail ropes twisted in two tones, rope hangers down to the stringers,
        // knotted rope coils at the posts, and wooden stringers
        val wood = c(96, 64, 38); val woodLt = c(122, 84, 50)
        for (s in intArrayOf(-1, 1)) {
            col = wood; at(0f, s * (halfWidth - 0.03f), 0.0f) { box(1.04f, 0.045f, 0.035f) }
            col = woodLt; at(0f, s * (halfWidth - 0.03f), 0.019f) { box(1.0f, 0.03f, 0.004f) }
            val rail = (0..14).map { k -> val t = k / 14f; Vec3(-0.5f + t, s * halfWidth, 0.17f - 0.05f * kotlin.math.sin(t * PI.toFloat())) }
            col = c(210, 190, 150); tube(rail, 0.012f, 6, colorFn = { v -> if (((v * 42f).toInt()) % 2 == 0) c(214, 194, 152) else c(176, 154, 112) })
            col = c(190, 170, 130); for (k in 1 until 7) { val t = k / 7f; val top = rail[(t * 14f).toInt()]
                tube(listOf(top, Vec3(top.x, s * (halfWidth - 0.03f), 0.02f)), 0.005f, 4) }
            for (e in intArrayOf(-1, 1)) {
                col = wood; at(e * 0.5f, s * halfWidth, 0.0f) { cylinder(0.024f, 0.2f, 8, 0.02f) }
                col = woodLt; at(e * 0.5f, s * halfWidth, 0.2f) { cone(0.026f, 0.03f, 8) }
                col = c(200, 180, 140); for (k in 0 until 3) at(e * 0.5f, s * halfWidth, 0.15f + k * 0.012f) { torus(0.026f, 0.006f, 10, 4) }   // rope coils
            }
        }
    }.build()

    /** One board, centred on the origin (1 / 7 of the bridge long). */
    fun bridgeBoard(halfWidth: Float) = PM().apply {
        // a weathered plank with grain lines and a nail at each end over the stringers
        val w = 0.9f / 7f
        col = c(150, 104, 62); at(z = 0.016f) { box(w * 0.94f, halfWidth * 2f, 0.03f) }
        col = c(124, 84, 48); for (g in 0 until 3) at((g - 1) * w * 0.28f, 0f, 0.0315f) { box(0.004f, halfWidth * 1.96f, 0.002f) }
        col = c(60, 60, 66); for (sy in intArrayOf(-1, 1)) at(0f, sy * (halfWidth - 0.03f), 0.032f) { cylinder(0.008f, 0.003f, 6) }
    }.build()

    /** The frame the hammer hangs from: two posts against the walls and a beam across. */
    fun gantry(halfSpan: Float) = PM().apply {
        // I-beam posts on bolted foot plates with striped bases and knee braces, a truss beam with zigzag bracing, and a
        // bearing housing with an axle at the pivot
        val top = HAMMER_PIVOT + 0.03f
        for (s in intArrayOf(-1, 1)) {
            val y = s * halfSpan
            col = steel; at(0f, y, 0.008f) { box(0.14f, 0.12f, 0.016f) }
            for (bx in intArrayOf(-1, 1)) bolt(bx * 0.05f, y, 0.016f, 0.008f)
            iBeam(0f, y, top, 0.075f, true)
            for (k in 0 until 4) { col = if (k % 2 == 0) hazY else hazK; at(0f, y, 0.03f + k * 0.05f) { box(0.082f, 0.082f, 0.05f) } }
            col = steelDk; tube(listOf(Vec3(0f, y, top - 0.2f), Vec3(0f, y * 0.6f, top)), 0.012f, 6)
        }
        for (z in floatArrayOf(top, top + 0.08f)) { col = steelDk; push(); translate(0f, -halfSpan, z); rotX(-(PI / 2).toFloat()); cylinder(0.018f, halfSpan * 2f, 8); pop() }
        col = steel; for (k in 0 until 8) { val y0 = -halfSpan + k * halfSpan * 2f / 8f; val y1 = y0 + halfSpan * 2f / 8f
            tube(listOf(Vec3(0f, y0, if (k % 2 == 0) top else top + 0.08f), Vec3(0f, y1, if (k % 2 == 0) top + 0.08f else top)), 0.008f, 5) }
        col = c(70, 76, 88); at(0f, 0f, HAMMER_PIVOT) { box(0.1f, 0.1f, 0.08f) }
        col = chrome; push(); translate(-0.07f, 0f, HAMMER_PIVOT); rotY((PI / 2).toFloat()); cylinder(0.018f, 0.14f, 10); pop()
        for (sx in intArrayOf(-1, 1)) bolt(sx * 0.03f, 0.051f, HAMMER_PIVOT + 0.02f, 0.007f)
    }.build()

    /** Rod and mallet head with the pivot at the origin, hanging along -z. */
    fun hammer() = PM().apply {
        // a steel arm with clamp plates, and a padded barrel in red and white sections, each slightly bulged, with chrome end
        // plates bolted on
        col = steel; at(0f, 0f, -HAMMER_LENGTH * 0.5f) { box(0.03f, 0.05f, HAMMER_LENGTH - 0.04f) }
        col = steelDk; at(0f, 0f, -0.02f) { box(0.06f, 0.07f, 0.06f) }
        for (sy in intArrayOf(-1, 1)) { col = steelDk; at(0f, sy * 0.03f, -HAMMER_LENGTH + 0.17f) { box(0.07f, 0.012f, 0.1f) }
            for (z in floatArrayOf(-HAMMER_LENGTH + 0.14f, -HAMMER_LENGTH + 0.2f)) bolt(0f, sy * 0.037f, z, 0.008f) }
        push(); translate(0f, 0f, -HAMMER_LENGTH); rotX((PI / 2).toFloat())
        for (i in 0 until 6) {
            col = if (i % 2 == 0) c(225, 45, 40) else c(248, 236, 220)
            val z0 = -0.16f + i * 0.053f
            lathe(listOf(floatArrayOf(0f, z0), floatArrayOf(HAMMER_HEAD * 0.95f, z0), floatArrayOf(HAMMER_HEAD, z0 + 0.026f), floatArrayOf(HAMMER_HEAD * 0.95f, z0 + 0.053f), floatArrayOf(0f, z0 + 0.053f)), 22, true)
        }
        for (e in floatArrayOf(-0.185f, 0.165f)) { col = chrome; at(z = e) { cylinder(HAMMER_HEAD * 0.78f, 0.02f, 18) }
            for (k in 0 until 6) { val a = k * 1.047f; col = c(150, 156, 168); at(cos(a) * HAMMER_HEAD * 0.6f, sin(a) * HAMMER_HEAD * 0.6f, e + (if (e < 0) -0.004f else 0.02f)) { cylinder(0.01f, 0.006f, 6) } } }
        pop()
    }.build()

    const val SWING_LENGTH = 3.0f

    /** Tall frame the swing hangs from: two legs against the corridor walls and a beam across at the top. */
    fun swingGantry(halfSpan: Float) = PM().apply {
        // lattice tower legs (twin posts braced in a zigzag), a truss beam across the top and a bearing hub at the pivot
        for (s in intArrayOf(-1, 1)) {
            val y = s * halfSpan
            col = steel; at(0f, y, 0.008f) { box(0.2f, 0.14f, 0.016f) }
            for (bx in intArrayOf(-1, 1)) bolt(bx * 0.07f, y, 0.016f, 0.009f)
            col = steelDk; for (px in floatArrayOf(-0.05f, 0.05f)) at(px, y, 0f) { cylinder(0.02f, SWING_LENGTH + 0.06f, 8) }
            col = steel; val n = 12
            for (k in 0 until n) { val z0 = k * SWING_LENGTH / n; val z1 = (k + 1) * SWING_LENGTH / n
                tube(listOf(Vec3(if (k % 2 == 0) -0.05f else 0.05f, y, z0), Vec3(if (k % 2 == 0) 0.05f else -0.05f, y, z1)), 0.008f, 5) }
            for (k in 0 until 4) { col = if (k % 2 == 0) hazY else hazK; at(0f, y, 0.03f + k * 0.06f) { box(0.13f, 0.05f, 0.06f) } }
        }
        val top = SWING_LENGTH + 0.06f
        for (z in floatArrayOf(top - 0.05f, top + 0.05f)) { col = steelDk; push(); translate(0f, -halfSpan - 0.05f, z); rotX(-(PI / 2).toFloat()); cylinder(0.022f, halfSpan * 2f + 0.1f, 8); pop() }
        col = steel; for (k in 0 until 10) { val y0 = -halfSpan + k * halfSpan * 0.2f; val y1 = y0 + halfSpan * 0.2f
            tube(listOf(Vec3(0f, y0, if (k % 2 == 0) top - 0.05f else top + 0.05f), Vec3(0f, y1, if (k % 2 == 0) top + 0.05f else top - 0.05f)), 0.009f, 5) }
        col = c(70, 76, 88); at(0f, 0f, SWING_LENGTH) { box(0.14f, 0.14f, 0.1f) }
        col = chrome; push(); translate(-0.09f, 0f, SWING_LENGTH); rotY((PI / 2).toFloat()); cylinder(0.025f, 0.18f, 10); pop()
    }.build()

    /** The swinging platform: a square striped deck (side 2 * half) hung by four chains from the pivot at the origin (it hangs along -z). */
    fun swingSeat(half: Float) = PM().apply {
        // four real chains of links down to corner shackles, a diamond tread-plate deck with hazard-striped edges, and the
        // glowing target ring in the middle
        val linkLen = 0.07f
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val a = Vec3(0f, 0f, 0f); val b = Vec3(sx * half * 0.85f, sy * half * 0.85f, -SWING_LENGTH + 0.06f)
            val d = b - a; val len = d.length(); val dir = d / len; val n = (len / linkLen).toInt()
            val ang = kotlin.math.atan2(dir.y, dir.x); val tilt = kotlin.math.acos(-dir.z)
            for (k in 0 until n) {
                val p = a + dir * ((k + 0.5f) * linkLen)
                col = if (k % 2 == 0) c(160, 166, 178) else c(132, 138, 150)
                push(); translate(p.x, p.y, p.z); rotZ(ang); rotY(-tilt); rotZ(if (k % 2 == 0) 0f else (PI / 2).toFloat())
                rotX((PI / 2).toFloat()); scale(1f, 1.6f, 1f); torus(0.016f, 0.0045f, 8, 4); pop()
            }
            col = steelDk; at(b.x, b.y, b.z - 0.02f) { torus(0.02f, 0.006f, 10, 4) }                                  // shackle
        }
        push(); translate(0f, 0f, -SWING_LENGTH)
        col = c(96, 102, 116); box(half * 2f, half * 1.96f, 0.06f)
        col = c(130, 136, 150); val g = 7
        for (i in 0 until g) for (j in 0 until g) { val x = -half + (i + 0.5f) * half * 2f / g; val y = -half * 0.9f + (j + 0.5f) * half * 1.8f / g   // tread plate
            push(); translate(x, y, 0.031f); rotZ(if ((i + j) % 2 == 0) 0.785f else -0.785f); box(0.05f, 0.012f, 0.004f); pop() }
        for (i in 0 until 8) { col = if (i % 2 == 0) hazY else hazK
            for (sy in intArrayOf(-1, 1)) { push(); translate(-half + (i + 0.5f) * half * 2f / 8f, sy * half * 0.96f, 0.0f); box(half * 2f / 8f, half * 0.1f, 0.075f); pop() }
            for (sx in intArrayOf(-1, 1)) { push(); translate(sx * half * 0.96f, -half * 0.86f + (i + 0.5f) * half * 1.72f / 8f, 0.0f); box(half * 0.1f, half * 1.72f / 8f, 0.075f); pop() } }
        col = c(255, 196, 40); at(z = 0.034f) { torus(half * 0.5f, 0.012f, 28, 5) }
        col = c(255, 60, 40) * 1.6f; at(z = 0.034f) { cylinder(half * 0.12f, 0.02f, 16) }
        pop()
    }.build()

    /** A key in cell units, lying flat-ish: a ring bow, a shaft and two teeth. */
    fun keyModel(r: Int, g: Int, b: Int) = PM().apply {
        col = c(r, g, b) * 1.15f
        push(); translate(-0.17f, 0f, 0f); torus(0.1f, 0.03f, 22, 8); pop()
        push(); translate(0.07f, 0f, 0f); rotY((PI / 2).toFloat()); cylinder(0.028f, 0.3f, 10); pop()
        for (k in 0..1) { push(); translate(0.17f + k * 0.07f, 0f, -0.045f); box(0.04f, 0.03f, 0.09f); pop() }
        col = c(255, 255, 255); push(); translate(-0.17f, 0f, 0.0f); torus(0.1f, 0.012f, 22, 5); pop()
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
        // steel side channels with diagonal hazard stripes and bolts, end rollers with caps, a ribbed rubber belt and feet
        col = c(28, 28, 32); at(z = 0.03f) { box(0.98f, 0.6f, 0.012f) }                                              // belt surface
        col = c(40, 40, 46); for (k in 0 until 9) at(0f, -0.26f + k * 0.065f, 0.0365f) { box(0.98f, 0.006f, 0.002f) }   // lengthwise ribs
        for (s in intArrayOf(-1, 1)) {
            col = steelDk; at(0f, s * 0.325f, 0.035f) { box(1.0f, 0.04f, 0.07f) }
            col = steel; at(0f, s * 0.325f, 0.072f) { box(1.0f, 0.05f, 0.008f) }
            for (k in 0 until 10) { col = if (k % 2 == 0) hazY else hazK
                push(); translate(-0.45f + k * 0.1f, s * 0.346f, 0.035f); rotX(0f); rotY(0.6f); box(0.045f, 0.004f, 0.08f); pop() }
            for (k in 0 until 5) bolt(-0.4f + k * 0.2f, s * 0.325f, 0.076f, 0.007f)
            col = steelDk; for (e in intArrayOf(-1, 1)) at(e * 0.45f, s * 0.3f, 0.0f) { box(0.05f, 0.04f, 0.012f) }  // feet
        }
        for (e in intArrayOf(-1, 1)) {                                                                               // rollers
            col = steel; push(); translate(e * 0.48f, -0.3f, 0.03f); rotX(-(PI / 2).toFloat()); cylinder(0.03f, 0.6f, 14); pop()
            col = chrome; for (s in intArrayOf(-1, 1)) at(e * 0.48f, s * 0.35f, 0.03f) { push(); rotX((PI / 2).toFloat()); cylinder(0.018f, 0.01f, 10); pop() }
        }
    }.build()

    /** One chevron arrow pointing along +x. */
    fun chevron() = PM().apply {
        // a glowing arrow with a dark outline, pointing along +x
        for (s in intArrayOf(-1, 1)) {
            col = c(20, 20, 24); push(); translate(0f, 0f, 0.036f); rotZ(s * 0.7f); translate(-0.06f, 0f, 0f); box(0.22f, 0.065f, 0.008f); pop()
            col = c(255, 196, 40) * 1.35f; push(); translate(0f, 0f, 0.041f); rotZ(s * 0.7f); translate(-0.06f, 0f, 0f); box(0.2f, 0.04f, 0.006f); pop()
        }
    }.build()

    /** Crusher frame: four posts and a top plate. The ram is separate. */
    fun crusherFrame() = PM().apply {
        // four I-beam columns on bolted foot plates with hazard-striped bases, knee braces under a thick header with cross
        // beams, a hydraulic cylinder on top feeding hoses down one column, a pressure gauge, a warning beacon, and a striped
        // border on the floor marking the crush zone
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val x = sx * 0.42f; val y = sy * 0.42f
            col = steel; at(x, y, 0.008f) { box(0.14f, 0.14f, 0.016f) }
            for (bx in intArrayOf(-1, 1)) for (by in intArrayOf(-1, 1)) bolt(x + bx * 0.05f, y + by * 0.05f, 0.016f, 0.008f)
            iBeam(x, y, 1.05f, 0.085f, false)
            for (k in 0 until 5) { col = if (k % 2 == 0) hazY else hazK; at(x, y, 0.03f + k * 0.05f) { box(0.092f, 0.092f, 0.05f) } }
            col = steelDk; tube(listOf(Vec3(x, y, 0.85f), Vec3(x * 0.55f, y, 1.02f)), 0.014f, 6); tube(listOf(Vec3(x, y, 0.85f), Vec3(x, y * 0.55f, 1.02f)), 0.014f, 6)   // knee braces
        }
        col = steelDk; at(z = 1.07f) { box(0.98f, 0.98f, 0.07f) }
        col = steel; for (sx in intArrayOf(-1, 1)) at(sx * 0.2f, 0f, 1.12f) { box(0.06f, 0.96f, 0.05f) }; for (sy in intArrayOf(-1, 1)) at(0f, sy * 0.2f, 1.12f) { box(0.96f, 0.06f, 0.05f) }
        for (k in 0 until 8) { val a = k * 0.785f; bolt(cos(a) * 0.4f, sin(a) * 0.4f, 1.105f) }
        col = c(200, 50, 40); at(z = 1.12f) { cylinder(0.14f, 0.36f, 18) }                                      // hydraulic cylinder
        col = steelDk; at(z = 1.48f) { cylinder(0.16f, 0.04f, 18) }; at(z = 1.12f) { cylinder(0.16f, 0.04f, 18) }
        for (k in 0 until 6) { val a = k * 1.047f; col = chrome; at(cos(a) * 0.15f, sin(a) * 0.15f, 1.12f) { cylinder(0.008f, 0.4f, 5) } }   // tie rods
        col = c(24, 24, 26); tube(listOf(Vec3(0.12f, 0.05f, 1.4f), Vec3(0.3f, 0.2f, 1.3f), Vec3(0.42f, 0.42f, 1.1f), Vec3(0.44f, 0.44f, 0.5f)), 0.016f, 6)   // hoses
        tube(listOf(Vec3(0.12f, -0.05f, 1.25f), Vec3(0.3f, 0.15f, 1.2f), Vec3(0.43f, 0.4f, 1.0f), Vec3(0.45f, 0.42f, 0.45f)), 0.013f, 6)
        col = c(240, 240, 236); at(0.49f, 0f, 1.07f) { rotY((PI / 2).toFloat()); cylinder(0.045f, 0.01f, 14) }         // pressure gauge
        col = c(220, 40, 40); at(0.5f, 0f, 1.07f) { box(0.004f, 0.006f, 0.035f) }
        col = steelDk; at(0.36f, 0.36f, 1.105f) { cylinder(0.04f, 0.03f, 10) }
        col = c(255, 140, 20) * 2.2f; at(0.36f, 0.36f, 1.15f) { scale(1f, 1f, 1.2f); sphere(0.035f, 10, 8) }       // warning beacon
        for (k in 0 until 16) { col = if (k % 2 == 0) hazY else hazK                                                // crush zone border on the floor
            val t = -0.4f + (k + 0.5f) * 0.05f
            at(t, 0.4f, 0.003f) { box(0.05f, 0.03f, 0.004f) }; at(t, -0.4f, 0.003f) { box(0.05f, 0.03f, 0.004f) }
            at(0.4f, t, 0.003f) { box(0.03f, 0.05f, 0.004f) }; at(-0.4f, t, 0.003f) { box(0.03f, 0.05f, 0.004f) } }
    }.build()

    /** The ram, centred at the origin (hangs 0.14 below and a piston rises above it). */
    fun crusherRam() = PM().apply {
        // chrome piston rod, a heavy bevelled steel block with hazard-striped sides and a bolted top, guide sleeves reaching to
        // the columns, and hardened crushing ridges underneath
        col = chrome; at(z = 0.11f) { cylinder(0.05f, 0.92f, 16) }
        col = steelDk; at(z = 0.11f) { cylinder(0.09f, 0.05f, 16) }
        col = steel; box(0.72f, 0.72f, 0.18f)
        col = c(120, 128, 142); at(z = 0.1f) { box(0.66f, 0.66f, 0.03f) }
        for (k in 0 until 12) { val a = k * 0.5236f; bolt(cos(a) * 0.28f, sin(a) * 0.28f, 0.115f, 0.01f) }
        for (side in 0 until 4) for (k in 0 until 9) {                                                               // diagonal hazard stripes on all four sides
            col = if (k % 2 == 0) hazY else hazK
            val t = -0.32f + k * 0.08f
            push(); rotZ(side * (PI / 2).toFloat()); translate(0.362f, t, 0f); rotX(0.6f); box(0.006f, 0.045f, 0.2f); pop()
        }
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {                                                // guide sleeves to the columns
            col = steelDk; tube(listOf(Vec3(sx * 0.34f, sy * 0.34f, 0f), Vec3(sx * 0.4f, sy * 0.4f, 0f)), 0.025f, 6)
            at(sx * 0.42f, sy * 0.42f, -0.06f) { cylinder(0.065f, 0.12f, 12) }
        }
        col = c(70, 74, 82); for (k in 0 until 7) at(-0.3f + k * 0.1f, 0f, -0.1f) { box(0.05f, 0.68f, 0.04f) }        // crushing ridges
        col = c(40, 42, 48); at(z = -0.095f) { box(0.7f, 0.7f, 0.01f) }
    }.build()

    const val SWEEP_LEN = 0.42f
    /** Paddle sweeper: a hub post (static, drawn with the arm) and a long striped arm turned around z. */
    fun sweeper() = PM().apply {
        // a finned motor hub on a bolted base with an orange beacon, and a steel I-beam arm in a padded red-and-white sleeve
        // with rubber end balls
        col = steel; cylinder(0.11f, 0.015f, 18)
        for (k in 0 until 6) { val a = k * 1.047f; bolt(cos(a) * 0.09f, sin(a) * 0.09f, 0.015f, 0.008f) }
        col = steelDk; at(z = 0.015f) { cylinder(0.07f, 0.13f, 16) }
        col = c(70, 76, 88); for (k in 0 until 5) at(z = 0.03f + k * 0.022f) { torus(0.072f, 0.006f, 16, 4) }         // cooling fins
        // the arm: I-beam core with a padded sleeve
        col = steelDk; at(0f, 0f, 0.08f) { box(2f * SWEEP_LEN - 0.08f, 0.03f, 0.08f) }
        push(); translate(-SWEEP_LEN + 0.06f, 0f, 0.08f); rotY((PI / 2).toFloat())
        tube((0..20).map { k -> Vec3(0f, 0f, k * (2f * SWEEP_LEN - 0.12f) / 20f) }, 0.045f, 14, colorFn = { v -> if (((v * 8f).toInt()) % 2 == 0) c(225, 45, 40) else c(248, 240, 226) })
        pop()
        col = c(255, 196, 40); for (e in intArrayOf(-1, 1)) at(e * SWEEP_LEN, 0f, 0.08f) { sphere(0.055f, 14, 10) }
        col = c(255, 255, 255) * 1.4f; for (e in intArrayOf(-1, 1)) at(e * (SWEEP_LEN + 0.05f), 0f, 0.09f) { sphere(0.008f, 6, 4) }   // reflectors
        col = steelDk; at(z = 0.145f) { cylinder(0.05f, 0.02f, 14) }
        col = c(255, 140, 20) * 2.2f; at(z = 0.18f) { scale(1f, 1f, 1.1f); sphere(0.03f, 10, 8) }                  // beacon
    }.build()

    /** Seesaw fulcrum, static: a wedge-like cylinder across the corridor. */
    fun seesawBase() = PM().apply {
        // a steel trestle at each side of the corridor: A-frame legs on a foot plate, a bearing block and the axle across
        for (sy in floatArrayOf(-0.22f, 0.22f)) {
            col = steel; at(0f, sy, 0.006f) { box(0.22f, 0.06f, 0.012f) }
            col = steelDk; for (sx in intArrayOf(-1, 1)) tube(listOf(Vec3(sx * 0.09f, sy, 0.01f), Vec3(0f, sy, 0.065f)), 0.012f, 6)
            col = c(70, 74, 84); at(0f, sy, 0.068f) { box(0.05f, 0.05f, 0.03f) }
            bolt(0f, sy + 0.026f, 0.068f, 0.008f)
        }
        col = chrome; push(); translate(0f, -0.26f, 0.075f); rotX(-(PI / 2).toFloat()); cylinder(0.014f, 0.52f, 10); pop()
    }.build()

    /** The tilting plank centred on the fulcrum, with a stripe at each end. */
    fun seesawPlank() = PM().apply {
        // four boards on two steel beams, a bolted centre plate over the axle, side rails, and striped rubber bumpers at the ends
        for (k in 0 until 4) { col = if (k % 2 == 0) c(156, 108, 64) else c(140, 96, 56)
            at(0f, -0.195f + k * 0.13f, 0.078f) { box(0.88f, 0.122f, 0.026f) }
            col = c(120, 82, 48); for (g in 0 until 3) at(0f, -0.195f + k * 0.13f + (g - 1) * 0.03f, 0.0915f) { box(0.86f, 0.004f, 0.001f) } }   // grain
        col = steelDk; for (sy in floatArrayOf(-0.15f, 0.15f)) at(0f, sy, 0.06f) { box(0.9f, 0.03f, 0.018f) }
        col = steel; at(0f, 0f, 0.093f) { box(0.12f, 0.5f, 0.006f) }
        for (sy in floatArrayOf(-0.2f, -0.07f, 0.07f, 0.2f)) for (sx in floatArrayOf(-0.04f, 0.04f)) bolt(sx, sy, 0.096f, 0.007f)
        col = c(40, 40, 46); for (s in intArrayOf(-1, 1)) at(0f, s * 0.265f, 0.085f) { box(0.96f, 0.025f, 0.05f) }
        for (e in intArrayOf(-1, 1)) for (k in 0 until 6) {                                                          // rubber bumpers with diagonal stripes
            col = if (k % 2 == 0) hazY else hazK
            push(); translate(e * 0.465f, -0.22f + k * 0.088f, 0.08f); rotX(0.6f); box(0.04f, 0.05f, 0.05f); pop()
        }
    }.build()

    // ---- castle drawbridge (frame units: x along the path, y across, z up; one unit = one cell)
    /** Hinge of the drawbridge: on the far side of the moat. */
    const val DRAW_HINGE = 0.46f
    /** The bridge deck, hinged at the origin and lying along -x when lowered: oak planks on two iron-bound beams. */
    fun drawbridgeDeck() = PM().apply {
        val len = 0.92f
        for (k in 0 until 7) { col = if (k % 2 == 0) c(120, 84, 52) else c(104, 72, 44)
            at(-len * (k + 0.5f) / 7f, 0f, 0.015f) { box(len / 7f * 0.94f, 0.82f, 0.03f) } }
        col = c(70, 50, 34); for (sy in floatArrayOf(-0.3f, 0.3f)) at(-len / 2f, sy, -0.012f) { box(len, 0.06f, 0.035f) }
        col = c(60, 62, 68); for (sy in floatArrayOf(-0.3f, 0.3f)) for (x in floatArrayOf(-0.08f, -0.84f)) at(x, sy, 0.0f) { box(0.05f, 0.08f, 0.05f) }
        col = c(130, 132, 140); for (sy in floatArrayOf(-0.4f, 0.4f)) at(-len + 0.03f, sy, 0.03f) { torus(0.025f, 0.006f, 10, 4) }   // chain rings
    }.build()

    /** Gatehouse over the far bank: twin round drum towers on battered plinths with coursed masonry, corbels, battlements and
     *  slate cones; between them a pointed arch of dressed voussoirs under machicolations and a parapet; a spiked portcullis
     *  raised in its groove, oak gates folded back inside, a painted shield over the arch and torches in iron brackets.
     *  Its front faces -x; the bridge hinges at its foot (x = [DRAW_HINGE]). */
    fun gatehouse() = PM().apply {
        fun hsh(i: Int) = (((i * 2654435761L) ushr 9) % 1000).toInt() / 1000f
        // coursed stone: each course a slightly different tint, each block within it too, with darker mortar lines
        fun stone(u: Float, z: Float, seed: Int): Vec3 {
            val course = (z / 0.055f).toInt(); val fz = z / 0.055f - course
            val block = ((u * 22f) + (if (course % 2 == 0) 0f else 0.5f)).toInt()
            val mortar = fz < 0.1f || ((u * 22f + (if (course % 2 == 0) 0f else 0.5f)) % 1f) < 0.06f
            val t = 0.86f + 0.2f * hsh(course * 131 + block * 17 + seed)
            return if (mortar) c(112, 106, 96) else c(166, 158, 142) * t
        }
        val hx = DRAW_HINGE + 0.2f
        for (sy in floatArrayOf(-0.6f, 0.6f)) {
            push(); translate(hx, sy, 0f)
            // drum: a profile ring at every course with a thin mortar ring, so each course of stone shows as its own band
            val prof = ArrayList<FloatArray>(); val kind = ArrayList<Int>()            // 0 = stone, 1 = mortar, 2 = cap
            prof.add(floatArrayOf(0f, 0f)); kind.add(2)
            var z = 0f; var course = 0
            while (z < 1.1f) {
                val r = if (z < 0.3f) 0.25f - 0.05f * (z / 0.3f) else 0.2f
                prof.add(floatArrayOf(r, z)); kind.add(1)
                prof.add(floatArrayOf(r, z + 0.006f)); kind.add(course)
                val z2 = minOf(z + 0.055f, 1.1f); val r2 = if (z2 < 0.3f) 0.25f - 0.05f * (z2 / 0.3f) else 0.2f
                prof.add(floatArrayOf(r2, z2 - 0.001f)); kind.add(course)
                z = z2; course++
            }
            prof.add(floatArrayOf(0f, 1.1f)); kind.add(2)
            val n = prof.size
            lathe(prof, 40, false) { u, v ->
                val i = Math.round(v * (n - 1)).coerceIn(0, n - 1)
                when (val k = kind[i]) { 1 -> c(108, 102, 92); 2 -> c(150, 142, 128); else -> {
                    val block = ((u * 18f) + (if (k % 2 == 0) 0f else 0.5f)).toInt()
                    c(168, 160, 144) * (0.84f + 0.24f * hsh(k * 131 + block * 17 + (if (sy > 0) 7 else 3))) } }
            }
            for (k in 0 until 14) { val a = k * 6.2832f / 14f; col = c(150, 142, 128) * (0.9f + 0.15f * hsh(k + 40))
                at(cos(a) * 0.205f, sin(a) * 0.205f, 1.09f) { rotZ(a); box(0.05f, 0.04f, 0.05f) } }                       // corbels
            col = c(160, 152, 138); at(z = 1.12f) { cylinder(0.235f, 0.08f, 28) }                                      // projecting parapet
            for (k in 0 until 10) { val a = k * 6.2832f / 10f + 0.3f
                col = c(166, 158, 142) * (0.88f + 0.18f * hsh(k + 90)); at(cos(a) * 0.215f, sin(a) * 0.215f, 1.24f) { rotZ(a); box(0.04f, 0.085f, 0.1f) } }   // merlons
            col = c(70, 78, 92); at(z = 1.18f) { lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.2f, 0f), floatArrayOf(0.12f, 0.16f), floatArrayOf(0f, 0.38f)), 20, false) { u, v ->
                c(64, 72, 88) * (0.85f + 0.25f * (((u * 26f).toInt() + (v * 9f).toInt()) % 2)) } }                  // slate cone
            col = c(214, 168, 70); at(z = 1.56f) { sphere(0.014f, 6, 4) }
            col = c(26, 22, 20)                                                                                       // cross-shaped arrow slits facing the bridge
            for (z in floatArrayOf(0.5f, 0.85f)) { at(-0.198f, 0f, z) { box(0.012f, 0.016f, 0.13f) }; at(-0.198f, 0f, z + 0.02f) { box(0.012f, 0.06f, 0.014f) } }
            pop()
        }
        // the arch block between the towers, built as vertical slices whose underside traces a pointed arch
        val half = 0.4f; val spring = 0.46f; val rise = 0.3f
        fun archH(y: Float): Float { val q = (kotlin.math.abs(y) / half).coerceIn(0f, 1f); return spring + rise * Math.pow((1f - q).toDouble(), 0.6).toFloat() }   // pointed: upright at the springs, meeting in a point
        val n = 20
        for (i in 0 until n) {
            val y0 = -half + 2f * half * i / n; val y1 = -half + 2f * half * (i + 1) / n
            val hLow = archH((y0 + y1) * 0.5f); val top = 1.02f
            val tiles = ((top - hLow) / 0.055f).toInt().coerceAtLeast(1)
            for (k in 0 until tiles) { val z0 = hLow + (top - hLow) * k / tiles; val z1 = hLow + (top - hLow) * (k + 1) / tiles
                col = stone((y0 + 0.4f) / 0.8f, (z0 + z1) * 0.5f, 11); at(hx, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f) { box(0.36f, y1 - y0 + 0.002f, z1 - z0) } }
            col = c(188, 180, 164) * (0.9f + 0.15f * hsh(i + 300))                                                       // dressed voussoirs round the arch
            at(DRAW_HINGE - 0.005f, (y0 + y1) * 0.5f, hLow + 0.035f) { box(0.03f, y1 - y0 - 0.004f, 0.07f) }
        }
        for (sy in floatArrayOf(-1f, 1f)) { col = c(182, 174, 158); at(DRAW_HINGE - 0.005f, sy * (half - 0.02f), spring * 0.5f) { box(0.03f, 0.05f, spring) } }   // jambs
        for (k in 0 until 7) { col = c(150, 142, 128) * (0.9f + 0.15f * hsh(k + 500)); at(DRAW_HINGE - 0.03f, -0.36f + k * 0.12f, 1.0f) { box(0.07f, 0.06f, 0.06f) } }   // machicolation corbels
        col = c(160, 152, 138); at(DRAW_HINGE + 0.17f, 0f, 1.075f) { box(0.42f, 0.84f, 0.09f) }
        for (k in 0 until 6) { col = c(166, 158, 142) * (0.88f + 0.18f * hsh(k + 600)); at(DRAW_HINGE - 0.02f, -0.35f + k * 0.14f, 1.17f) { box(0.05f, 0.08f, 0.1f) } }   // parapet merlons
        // portcullis raised in its groove, its spiked foot just showing under the arch
        col = c(62, 60, 64)
        for (k in 0 until 9) { val y = -0.34f + k * 0.085f; at(DRAW_HINGE + 0.08f, y, 0.86f) { box(0.018f, 0.014f, 0.36f) }
            at(DRAW_HINGE + 0.08f, y, 0.68f) { rotY(PI.toFloat()); cone(0.011f, 0.04f, 4) } }
        for (z in floatArrayOf(0.74f, 0.86f, 0.98f)) at(DRAW_HINGE + 0.08f, 0f, z) { box(0.02f, 0.72f, 0.014f) }
        // oak gates folded back against the passage walls, with iron straps
        for (sy in floatArrayOf(-1f, 1f)) {
            col = c(96, 64, 40); at(DRAW_HINGE + 0.24f, sy * 0.37f, 0.24f) { box(0.26f, 0.025f, 0.46f) }
            col = c(50, 50, 54); for (z in floatArrayOf(0.08f, 0.24f, 0.4f)) at(DRAW_HINGE + 0.24f, sy * 0.355f, z) { box(0.26f, 0.006f, 0.02f) }
        }
        // a painted shield over the arch
        push(); translate(DRAW_HINGE - 0.02f, 0f, 0.86f); rotY(-(PI / 2).toFloat())
        val sh = listOf(floatArrayOf(-0.07f, 0.07f), floatArrayOf(0.07f, 0.07f), floatArrayOf(0.07f, -0.01f), floatArrayOf(0f, -0.09f), floatArrayOf(-0.07f, -0.01f))
        col = c(214, 168, 70); prism(sh.map { floatArrayOf(it[0] * 1.12f, it[1] * 1.12f) }, 0.012f)
        col = c(30, 60, 160); at(z = 0.004f) { prism(sh.map { floatArrayOf(minOf(it[0], 0f), it[1]) }, 0.012f) }
        col = c(170, 30, 30); at(z = 0.004f) { prism(sh.map { floatArrayOf(maxOf(it[0], 0f), it[1]) }, 0.012f) }
        pop()
        // torches in iron brackets on the tower fronts
        for (sy in floatArrayOf(-0.6f, 0.6f)) {
            col = c(40, 40, 44); tube(listOf(Vec3(hx - 0.2f, sy * 0.7f, 0.5f), Vec3(hx - 0.27f, sy * 0.7f, 0.52f), Vec3(hx - 0.28f, sy * 0.7f, 0.58f)), 0.006f, 4)
            col = c(70, 50, 32); at(hx - 0.28f, sy * 0.7f, 0.56f) { cylinder(0.012f, 0.07f, 6, 0.016f) }
            col = c(255, 150, 50) * 2.4f; at(hx - 0.28f, sy * 0.7f, 0.65f) { scale(1f, 1f, 1.7f); sphere(0.022f, 8, 6) }
            col = c(255, 230, 140) * 2.8f; at(hx - 0.28f, sy * 0.7f, 0.64f) { scale(1f, 1f, 1.5f); sphere(0.011f, 6, 4) }
        }
        col = c(20, 18, 18); for (sy in floatArrayOf(-0.4f, 0.4f)) at(DRAW_HINGE - 0.005f, sy, 0.86f) { box(0.012f, 0.03f, 0.03f) }   // chain holes
    }.build()

    /** Dark green moat water filling the pit, with a few lily pads. */
    fun moatWater() = PM().apply {
        col = c(30, 62, 58); at(0f, 0f, -0.16f) { box(0.98f, 1.0f, 0.01f) }
        col = c(60, 120, 60); for (k in 0 until 3) at(-0.25f + k * 0.22f, (k - 1) * 0.25f, -0.152f) { cylinder(0.05f, 0.004f, 10) }
    }.build()
}
