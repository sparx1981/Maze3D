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

    /** The swinging platform: a square striped deck (side 2 * half) hung by four chains from the pivot at the origin (it hangs along -z). */
    fun swingSeat(half: Float) = PM().apply {
        val chain = c(150, 156, 170)
        col = chain
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1))
            tube(listOf(Vec3(0f, 0f, 0f), Vec3(sx * half * 0.85f, sy * half * 0.85f, -SWING_LENGTH + 0.04f)), 0.014f, 5)
        push(); translate(0f, 0f, -SWING_LENGTH)
        col = c(88, 94, 108); push(); translate(0f, 0f, 0.0f); box(half * 2f, half * 1.96f, 0.06f); pop()
        for (i in 0 until 8) { col = if (i % 2 == 0) c(250, 200, 30) else c(40, 40, 46)
            for (sy in intArrayOf(-1, 1)) { push(); translate(-half + (i + 0.5f) * half * 2f / 8f, sy * half * 0.96f, 0.0f); box(half * 2f / 8f, half * 0.1f, 0.075f); pop() } }
        col = c(255, 196, 40); push(); translate(0f, 0f, 0.032f); torus(half * 0.5f, 0.012f, 28, 5); pop()
        col = c(255, 60, 40) * 1.6f; push(); translate(0f, 0f, 0.032f); cylinder(half * 0.12f, 0.02f, 16); pop()
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
        col = c(46, 48, 56); push(); translate(0f, 0f, 0.015f); box(1.0f, 0.62f, 0.03f); pop()
        col = c(255, 196, 40)
        for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.32f, 0.03f); box(1.0f, 0.04f, 0.05f); pop() }
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
