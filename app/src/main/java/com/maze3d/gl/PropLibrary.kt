package com.maze3d.gl

import com.maze3d.core.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural, theme-specific models. Each is built about 1 unit tall, standing on z = 0, with a footprint
 * radius of at most 0.5, so the placer can scale them to fit a wall joint, a dead end or a landmark slot.
 */
object PropLibrary {
    private fun c(r: Int, g: Int, b: Int) = PM.rgb(r, g, b)
    private fun glow(v: Vec3, k: Float) = v * k

    class ThemeProps(val junction: PropMesh, val deadA: PropMesh, val deadB: PropMesh, val landmark: PropMesh,
                     val deadC: PropMesh? = null, val junction2: PropMesh? = null)

    private val cache = HashMap<Int, ThemeProps>()
    fun forTheme(id: Int): ThemeProps? = if (id < 0) null else cache.getOrPut(id) { build(id) }

    private fun build(id: Int): ThemeProps = when (id) {
        0 -> ThemeProps(gumdrop(), cupcake(), lollipop(), candyCane(), allsortsPile(), allsortLayered(0.5f))
        1 -> ThemeProps(marigold(), sugarSkull(), candleVotive(), candelabra())
        2 -> ThemeProps(trafficCone(), tyreStack(), trophy(), flagPole())
        3 -> ThemeProps(cornerFlag(), football(), trophy(), floodlight())
        4 -> ThemeProps(beacon(), dish(), canister(), rocket())
        5 -> ThemeProps(ketchup(), burger(), sodaCup(), friesBox())
        6 -> ThemeProps(flask(), beaker(), testTubes(), atom())
        7 -> ThemeProps(lampPost(), snowman(), snowPine(), snowPine(), iceCrystals())
        8 -> ThemeProps(obelisk(), canopicJar(), sphinx(), pyramidProp())
        9 -> ThemeProps(neonSign(), terminal(), hologram(), cityTower(), neonRing(), neonStar())
        10 -> ThemeProps(fern(), idol(), jungleStump(), jungleTree())
        11 -> ThemeProps(glowBell(), glowCluster(), glowParasol(), glowGiant(), sporePod(), glowBell())
        else -> ThemeProps(pumpkin(), leafPile(), hayBale(), autumnTree(), autumnTree(), pumpkin())
    }

    // ---------------------------------------------------------------- candy
    private fun gumdrop() = PM().apply {
        val cols = c(255, 70, 110)
        col = cols
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.5f, 0f), floatArrayOf(0.5f, 0.1f), floatArrayOf(0.44f, 0.4f),
            floatArrayOf(0.3f, 0.75f), floatArrayOf(0.12f, 0.96f), floatArrayOf(0f, 1f)), 20) { u, v -> if ((u * 24f).toInt() % 2 == 0 && v > 0.15f) c(255, 235, 240) * (0.85f + 0.15f * v) else cols }
    }.build()

    private fun cupcake() = PM().apply {
        col = c(240, 150, 190)
        // paper case with pleats
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.30f, 0f), floatArrayOf(0.44f, 0.38f), floatArrayOf(0f, 0.38f)), 24, false) { u, _ ->
            if ((u * 16f).toInt() % 2 == 0) c(250, 200, 220) else c(236, 120, 170) }
        // sponge dome
        col = c(204, 148, 92); at(z = 0.36f) { sphere(0.44f, 20, 8) }
        // frosting swirl: three stacked tapering rings
        col = c(255, 236, 244)
        for (i in 0 until 3) at(z = 0.50f + i * 0.14f) { torus(0.26f - i * 0.07f, 0.15f - i * 0.025f, 20, 8) }
        // cherry and sprinkles
        col = c(220, 20, 50); at(z = 0.95f) { sphere(0.11f, 12, 8) }
        for (i in 0 until 14) {
            val a = i * 2.4f; val r = 0.1f + 0.2f * ((i * 7) % 5) / 5f
            col = listOf(c(80, 200, 255), c(255, 220, 60), c(120, 220, 120), c(255, 90, 90))[i % 4]
            push(); translate(cos(a) * r, sin(a) * r, 0.58f + (0.3f - r) * 1.2f); rotZ(a); rotY(1.1f); scale(0.12f, 0.025f, 0.025f); sphere(0.5f, 6, 4); pop()
        }
    }.build()

    private fun lollipop() = PM().apply {
        col = c(250, 250, 250); cylinder(0.025f, 0.62f, 8)
        // swirl disc: concentric coloured rings give the spiral read
        push(); translate(0f, 0f, 0.8f); rotX((PI / 2).toFloat())
        val ringCols = listOf(c(255, 60, 130), c(255, 240, 240), c(60, 190, 255), c(255, 240, 240), c(255, 200, 40), c(255, 240, 240))
        for (i in 0 until 6) { col = ringCols[i]; push(); translate(0f, 0f, 0.0f); scale(1f, 1f, 0.35f); torus(0.04f + i * 0.06f, 0.035f, 20, 6); pop() }
        pop()
        col = c(255, 60, 130); at(z = 0.8f) { push(); rotX((PI / 2).toFloat()); scale(1f, 1f, 0.35f); sphere(0.12f, 10, 6); pop() }
    }.build()

    private fun candyCane() = PM().apply {
        val path = ArrayList<Vec3>()
        for (i in 0..8) path.add(Vec3(0f, 0f, i * 0.09f))
        for (i in 1..14) { val a = PI.toFloat() * i / 14f; path.add(Vec3(0.2f - 0.2f * cos(a), 0f, 0.72f + 0.2f * sin(a))) }
        tube(path, 0.065f, 10, false, { t -> if ((t * 22f).toInt() % 2 == 0) c(235, 40, 60) else c(255, 250, 250) })
    }.build()

    // ---------------------------------------------------------------- liquorice allsorts
    private val LIQ = c(22, 16, 20)
    private fun PM.layerBox(w: Float, d: Float, layers: List<Vec3>, lh: Float, z0: Float = 0f) {
        for ((i, lc) in layers.withIndex()) { col = lc; push(); translate(0f, 0f, z0 + lh * (i + 0.5f)); scale(1f - 0.012f * (i % 2), 1f - 0.012f * (i % 2), 1f); box(w, d, lh * 0.97f); pop() }
    }
    private fun allsortLayered(scale: Float) = PM().apply {
        // the classic sandwich: liquorice and coloured coconut/aniseed layers
        scale(1f / 0.9f * scale * 1.6f)
        layerBox(0.5f, 0.3f, listOf(LIQ, c(250, 120, 170), LIQ, c(245, 245, 238), LIQ, c(250, 120, 170), LIQ), 0.12f)
    }.build()

    private fun allsortsPile() = PM().apply {
        // layered cuboid
        push(); translate(-0.14f, 0.06f, 0f); rotZ(0.35f); layerBox(0.34f, 0.22f, listOf(LIQ, c(255, 220, 70), LIQ, c(245, 245, 238), LIQ, c(250, 120, 170), LIQ), 0.1f); pop()
        // Catherine wheel: spiral of liquorice and sugar, standing on edge
        push(); translate(0.2f, -0.12f, 0.2f); rotX((PI / 2).toFloat()); rotZ(0.4f)
        val spiral = listOf(LIQ, c(255, 140, 190), LIQ, c(245, 245, 238), LIQ, c(255, 140, 190), LIQ)
        for ((i, sc) in spiral.withIndex()) { col = sc; push(); translate(0f, 0f, 0f); scale(1f, 1f, 1f); torus(0.025f + i * 0.026f, 0.015f, 20, 6); pop() }
        col = LIQ; cylinder(0.205f, 0.09f, 28); pop()
        // bobble: liquorice core rolled in blue sugar beads
        push(); translate(-0.02f, -0.24f, 0f)
        col = LIQ; cylinder(0.11f, 0.26f, 14)
        for (k in 0 until 40) { val a = k * 2.399f; val z = 0.02f + 0.22f * ((k * 7) % 11) / 11f
            col = if (k % 3 == 0) c(250, 120, 170) else c(90, 170, 255); push(); translate(cos(a) * 0.115f, sin(a) * 0.115f, z); sphere(0.026f, 6, 4); pop() }
        pop()
        // pink coconut roll with liquorice centre
        push(); translate(0.12f, 0.2f, 0.11f); rotY((PI / 2).toFloat()); rotZ(0.5f)
        col = c(250, 125, 175); cylinder(0.11f, 0.3f, 18); col = LIQ; cylinder(0.05f, 0.302f, 12); pop()
        // a loose blue-and-pink jelly bead or two
        col = c(90, 170, 255); push(); translate(-0.26f, -0.18f, 0.04f); sphere(0.045f, 8, 6); pop()
        col = c(255, 120, 180); push(); translate(0.3f, 0.04f, 0.04f); sphere(0.04f, 8, 6); pop()
    }.build()

    // ---------------------------------------------------------------- day of the dead
    private fun marigold() = PM().apply {
        col = c(60, 130, 50); cylinder(0.04f, 0.35f, 8)
        for (layer in 0 until 3) for (i in 0 until 12) {
            val a = (i + layer * 0.5f) * (2 * PI / 12).toFloat()
            col = Vec3.lerp(c(255, 170, 30), c(255, 110, 10), layer / 2f)
            push(); translate(0f, 0f, 0.42f + layer * 0.05f); rotZ(a); rotY(0.5f - layer * 0.2f); translate(0.18f - layer * 0.04f, 0f, 0f); scale(0.3f - layer * 0.05f, 0.1f, 0.03f); sphere(0.5f, 8, 4); pop()
        }
        col = c(150, 60, 10); at(z = 0.5f) { sphere(0.08f, 8, 6) }
    }.build()

    private fun sugarSkull() = PM().apply {
        col = c(250, 246, 236)
        at(z = 0.55f) { push(); scale(0.9f, 0.8f, 1f); sphere(0.4f, 24, 14); pop() }          // cranium
        at(z = 0.18f) { push(); scale(0.9f, 0.7f, 0.6f); sphere(0.3f, 18, 10); pop() }         // jaw
        for (s in intArrayOf(-1, 1)) {
            col = c(40, 20, 60); push(); translate(s * 0.15f, -0.31f, 0.6f); scale(1f, 0.5f, 1f); sphere(0.1f, 12, 8); pop()   // eye sockets
            col = c(255, 120, 30); push(); translate(s * 0.15f, -0.34f, 0.6f); scale(1f, 0.3f, 1f); torus(0.1f, 0.02f, 14, 6); pop() // marigold ring
            for (i in 0 until 8) { col = if (i % 2 == 0) c(240, 70, 120) else c(40, 200, 190)
                val a = i * 0.785f; push(); translate(s * 0.15f + cos(a) * 0.12f, -0.34f, 0.6f + sin(a) * 0.12f); sphere(0.025f, 6, 4); pop() }
        }
        col = c(60, 30, 60); push(); translate(0f, -0.33f, 0.46f); scale(0.6f, 0.4f, 1.1f); cone(0.05f, 0.1f, 4); pop()          // nose
        col = c(50, 30, 50); for (i in -3..3) { push(); translate(i * 0.05f, -0.26f, 0.2f); scale(0.016f, 0.016f, 0.1f); box(1f, 1f, 1f); pop() }   // teeth
        col = c(255, 170, 30); push(); translate(0f, -0.1f, 0.82f); sphere(0.07f, 8, 6); pop()
    }.build()

    private fun candleVotive() = PM().apply {
        col = c(255, 214, 120); cylinder(0.2f, 0.55f, 20, 0.2f)
        col = c(255, 160, 40) * 1.6f; at(z = 0.55f) { cone(0.045f, 0.2f, 8) }
        col = c(240, 90, 60); at(z = 0.2f) { torus(0.2f, 0.012f, 20, 4) }
        col = c(60, 30, 20); cylinder(0.24f, 0.06f, 20)
    }.build()

    private fun candelabra() = PM().apply {
        col = c(214, 168, 70); cylinder(0.3f, 0.06f, 16, 0.2f); cylinder(0.04f, 0.8f, 10)
        at(z = 0.4f) { torus(0.06f, 0.03f, 10, 6) }
        for (s in -1..1) {
            val x = s * 0.3f
            val path = listOf(Vec3(0f, 0f, 0.6f), Vec3(x * 0.4f, 0f, 0.58f), Vec3(x * 0.9f, 0f, 0.65f), Vec3(x, 0f, 0.78f))
            col = c(214, 168, 70); tube(path, 0.025f, 8)
            at(x, 0f, 0.78f) { col = c(214, 168, 70); cylinder(0.06f, 0.03f, 10); col = c(250, 240, 220); cylinder(0.035f, 0.16f, 10)
                col = c(255, 170, 40) * 1.7f; at(z = 0.16f) { cone(0.028f, 0.1f, 8) } }
        }
    }.build()

    // ---------------------------------------------------------------- motorsport
    private fun trafficCone() = PM().apply {
        col = c(30, 30, 34); at { box(0.9f, 0.9f, 0.08f); translate(0f, 0f, 0.04f) }
        push(); translate(0f, 0f, 0.08f)
        lathe(listOf(floatArrayOf(0.36f, 0f), floatArrayOf(0.08f, 0.9f), floatArrayOf(0.06f, 0.92f), floatArrayOf(0f, 0.92f)), 24, false) { _, v ->
            if (v in 0.25f..0.45f || v in 0.6f..0.75f) c(250, 250, 250) else c(255, 100, 14) }
        pop()
    }.build()

    private fun tyreStack() = PM().apply {
        for (i in 0 until 3) {
            push(); translate(0f, 0f, 0.15f + i * 0.28f)
            col = c(24, 24, 26); torus(0.26f, 0.14f, 28, 12)
            col = c(110, 112, 118); cylinder(0.14f, 0.02f, 18)
            if (i == 2) { col = c(250, 250, 250); translate(0f, 0f, 0.12f); torus(0.22f, 0.012f, 28, 4) }
            pop()
        }
        col = c(210, 30, 36); at(z = 0.0f) { cylinder(0.42f, 0.02f, 24) }
    }.build()

    private fun trophy() = PM().apply {
        col = c(255, 206, 70)
        lathe(listOf(floatArrayOf(0f, 0.0f), floatArrayOf(0.3f, 0f), floatArrayOf(0.3f, 0.08f), floatArrayOf(0.12f, 0.12f), floatArrayOf(0.05f, 0.2f),
            floatArrayOf(0.05f, 0.45f), floatArrayOf(0.12f, 0.5f), floatArrayOf(0.2f, 0.65f), floatArrayOf(0.34f, 0.92f), floatArrayOf(0.3f, 0.98f), floatArrayOf(0.28f, 0.93f)), 24)
        for (s in intArrayOf(-1, 1)) {
            val p = ArrayList<Vec3>(); for (i in 0..10) { val a = PI.toFloat() * i / 10f; p.add(Vec3(s * (0.3f + 0.12f * sin(a)), 0f, 0.8f - 0.25f * (1f - cos(a)) * 0.5f - 0.1f * i / 10f)) }
            tube(p, 0.025f, 8)
        }
        col = c(50, 40, 30); cylinder(0.32f, 0.04f, 4)
    }.build()

    private fun flagPole() = PM().apply {
        col = c(220, 222, 228); cylinder(0.02f, 1f, 8)
        col = c(255, 214, 80); at(z = 1f) { sphere(0.035f, 8, 6) }
        for (i in 0 until 4) for (j in 0 until 3) {
            col = if ((i + j) % 2 == 0) c(20, 20, 20) else c(250, 250, 250)
            val x0 = 0.02f + i * 0.1f; val z0 = 0.62f + j * 0.1f; val w0 = sin(x0 * 9f) * 0.02f; val w1 = sin((x0 + 0.1f) * 9f) * 0.02f
            flat(Vec3(x0, w0, z0), Vec3(x0 + 0.1f, w1, z0), Vec3(x0 + 0.1f, w1, z0 + 0.1f), Vec3(x0, w0, z0 + 0.1f))
        }
    }.build()

    // ---------------------------------------------------------------- football
    private fun cornerFlag() = PM().apply {
        col = c(250, 250, 250); cylinder(0.02f, 0.9f, 8)
        col = c(255, 214, 40); flat(Vec3(0.02f, 0f, 0.9f), Vec3(0.34f, 0f, 0.82f), Vec3(0.02f, 0f, 0.7f), Vec3(0.02f, 0f, 0.7f))
        col = c(30, 30, 30); cylinder(0.12f, 0.03f, 14)
    }.build()

    private fun football() = PM().apply {
        // white ball with dark patches placed on the vertices of an icosahedron (pentagon centres)
        val phi = ((1f + 5f.let { kotlin.math.sqrt(it) }) / 2f)
        val dirs = ArrayList<Vec3>()
        for (a in intArrayOf(-1, 1)) for (b in intArrayOf(-1, 1)) { dirs.add(Vec3(0f, a.toFloat(), b * phi).normalized()); dirs.add(Vec3(a.toFloat(), b * phi, 0f).normalized()); dirs.add(Vec3(b * phi, 0f, a.toFloat()).normalized()) }
        at(z = 0.5f) { sphere(0.5f, 28, 18) { u, v ->
            val th = u * 2f * PI.toFloat(); val ph = v * PI.toFloat()
            val p = Vec3(sin(ph) * cos(th), sin(ph) * sin(th), -cos(ph))
            if (dirs.any { (it dot p) > 0.86f }) c(24, 24, 26) else c(246, 246, 246) } }
    }.build()

    private fun floodlight() = PM().apply {
        col = c(120, 124, 132); cylinder(0.04f, 0.95f, 10, 0.025f)
        at(z = 0.95f) { box(0.5f, 0.1f, 0.3f)
            for (i in -2..2) for (j in -1..0) { col = c(255, 250, 220) * 1.8f; push(); translate(i * 0.09f, -0.055f, j * 0.12f + 0.06f); sphere(0.035f, 8, 6); pop() }
            col = c(120, 124, 132) }
        col = c(70, 74, 80); cylinder(0.14f, 0.04f, 12)
    }.build()

    // ---------------------------------------------------------------- space
    private fun beacon() = PM().apply {
        col = c(160, 168, 182); cylinder(0.3f, 0.1f, 16, 0.22f)
        col = c(120, 128, 142); cylinder(0.025f, 0.8f, 8)
        col = c(255, 80, 80) * 2.0f; at(z = 0.85f) { sphere(0.07f, 10, 8) }
        col = c(210, 214, 224); at(z = 0.55f) { torus(0.1f, 0.012f, 14, 4) }
    }.build()

    private fun dish() = PM().apply {
        col = c(170, 176, 190); cylinder(0.3f, 0.08f, 16, 0.2f); cylinder(0.04f, 0.35f, 8)
        push(); translate(0f, 0f, 0.45f); rotX(-0.9f)
        col = c(230, 232, 240); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.2f, 0.03f), floatArrayOf(0.4f, 0.12f), floatArrayOf(0.45f, 0.16f)), 26, true)
        col = c(120, 128, 142); cylinder(0.012f, 0.3f, 6); at(z = 0.3f) { col = c(255, 90, 160) * 1.8f; sphere(0.03f, 8, 6) }
        pop()
    }.build()

    private fun canister() = PM().apply {
        col = c(210, 216, 226); cylinder(0.28f, 0.7f, 20)
        col = c(255, 150, 40); at(z = 0.2f) { cylinder(0.285f, 0.1f, 20) }
        col = c(84, 224, 255) * 1.8f; at(z = 0.45f) { push(); translate(0f, -0.275f, 0f); scale(1f, 0.1f, 1f); box(0.2f, 1f, 0.08f); pop() }
        col = c(140, 148, 162); at(z = 0.7f) { cylinder(0.22f, 0.06f, 16, 0.12f) }
    }.build()

    private fun rocket() = PM().apply {
        col = c(240, 242, 248)
        lathe(listOf(floatArrayOf(0f, 0.1f), floatArrayOf(0.14f, 0.1f), floatArrayOf(0.15f, 0.6f), floatArrayOf(0.1f, 0.85f), floatArrayOf(0f, 1f)), 24, true) { _, v -> if (v > 0.78f) c(230, 50, 50) else c(240, 242, 248) }
        col = c(60, 160, 255); at(0f, -0.145f, 0.55f) { sphere(0.06f, 10, 8) }
        col = c(230, 50, 50)
        for (i in 0 until 3) { push(); rotZ(i * 2.094f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.0125f)
            prism(listOf(floatArrayOf(0.12f, 0.08f), floatArrayOf(0.34f, 0.04f), floatArrayOf(0.12f, 0.46f)), 0.025f); pop() }
        col = c(60, 66, 78); at(z = 0.02f) { cylinder(0.08f, 0.1f, 14, 0.12f) }
        col = c(255, 160, 50) * 2f; at(z = -0.0f) { cone(0.06f, 0.1f, 10) }
    }.build()

    // ---------------------------------------------------------------- fast food
    private fun ketchup() = PM().apply {
        col = c(215, 28, 28)
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.4f, 0.04f), floatArrayOf(0.34f, 0.24f), floatArrayOf(0.2f, 0.5f), floatArrayOf(0.1f, 0.8f), floatArrayOf(0f, 1f)), 20)
    }.build()

    private fun burger() = PM().apply {
        col = c(224, 160, 80)
        at(z = 0.0f) { lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.46f, 0f), floatArrayOf(0.5f, 0.06f), floatArrayOf(0.42f, 0.14f), floatArrayOf(0f, 0.14f)), 28) }
        col = c(250, 190, 40); at(z = 0.15f) { push(); rotZ(0.4f); box(0.9f, 0.9f, 0.025f); pop() }               // cheese
        col = c(96, 52, 30); at(z = 0.16f) { cylinder(0.46f, 0.12f, 28, 0.44f) }                             // patty
        col = c(70, 170, 50); at(z = 0.27f) { torus(0.42f, 0.04f, 22, 6) }                                    // lettuce
        col = c(220, 50, 40); at(z = 0.31f) { cylinder(0.4f, 0.04f, 20) }                                     // tomato
        col = c(228, 164, 84)                                                                                   // top bun
        at(z = 0.34f) { lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.5f, 0f), floatArrayOf(0.52f, 0.12f), floatArrayOf(0.42f, 0.34f), floatArrayOf(0.2f, 0.45f), floatArrayOf(0f, 0.47f)), 28) }
        col = c(255, 244, 220)
        for (i in 0 until 14) { val a = i * 2.4f; val r = 0.1f + 0.3f * ((i * 5) % 7) / 7f
            push(); translate(cos(a) * r, sin(a) * r, 0.34f + 0.43f - r * 0.62f); rotZ(a); scale(0.07f, 0.035f, 0.02f); sphere(0.5f, 6, 4); pop() }
    }.build()

    private fun sodaCup() = PM().apply {
        col = c(250, 250, 250)
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.22f, 0f), floatArrayOf(0.3f, 0.8f), floatArrayOf(0.32f, 0.8f)), 24, false) { _, v -> if (v in 0.3f..0.65f) c(220, 30, 40) else c(250, 250, 250) }
        col = c(60, 60, 64); at(z = 0.8f) { cylinder(0.34f, 0.05f, 24, 0.33f) }
        col = c(250, 250, 250); at(z = 0.85f) { lathe(listOf(floatArrayOf(0.33f, 0f), floatArrayOf(0.2f, 0.07f), floatArrayOf(0f, 0.09f)), 24) }
        col = c(230, 40, 50); tube(listOf(Vec3(0.03f, 0f, 0.8f), Vec3(0.06f, 0f, 1.0f), Vec3(0.14f, 0f, 1.12f)), 0.022f, 8)
    }.build()

    private fun friesBox() = PM().apply {
        val bo = 0.2f; val to = 0.32f; val h = 0.5f
        fun side(k: Int) {
            val a = k * (PI / 2).toFloat()
            val ca = cos(a); val sa = sin(a)
            fun pt(u: Float, r: Float, z: Float) = Vec3(ca * r - sa * u, sa * r + ca * u, z)
            col = c(220, 36, 36)
            flat(pt(-bo, bo, 0f), pt(bo, bo, 0f), pt(to, to, h), pt(-to, to, h))
            col = c(255, 235, 140)
            flat(pt(-bo * 0.7f, bo + 0.002f, 0.09f), pt(bo * 0.7f, bo + 0.002f, 0.09f), pt(to * 0.72f, to * 0.78f, h * 0.72f), pt(-to * 0.72f, to * 0.78f, h * 0.72f))
        }
        for (k in 0 until 4) side(k)
        col = c(250, 205, 70)
        for (i in 0 until 22) {
            val a = i * 2.4f; val r = 0.04f + 0.2f * ((i * 3) % 7) / 7f
            push(); translate(cos(a) * r, sin(a) * r, h + 0.1f + 0.14f * ((i * 7) % 4)); rotZ(a); rotX(0.1f * (i % 3 - 1)); rotY(0.1f * (i % 4 - 1.5f)); box(0.07f, 0.07f, 0.6f); pop()
        }
    }.build()

    // ---------------------------------------------------------------- science
    private fun flask() = PM().apply {
        val glass = c(200, 236, 250)
        col = glass
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.36f, 0f), floatArrayOf(0.38f, 0.05f), floatArrayOf(0.3f, 0.3f), floatArrayOf(0.1f, 0.62f), floatArrayOf(0.1f, 0.86f), floatArrayOf(0.13f, 0.9f), floatArrayOf(0.13f, 0.94f)), 24, true) { _, v ->
            if (v < 0.4f) c(110, 255, 150) * 1.1f else glass }
        col = c(110, 255, 150) * 1.4f; at(z = 0.18f) { sphere(0.05f, 6, 4) }
    }.build()

    private fun beaker() = PM().apply {
        col = c(205, 235, 248); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.3f, 0f), floatArrayOf(0.3f, 0.7f), floatArrayOf(0.33f, 0.72f)), 24, false) { _, v -> if (v < 0.55f) c(255, 120, 150) else c(205, 235, 248) }
        col = c(30, 40, 60); for (i in 1..4) at(0f, -0.3f, 0.12f * i) { box(if (i % 2 == 0) 0.16f else 0.09f, 0.02f, 0.012f) }
    }.build()

    private fun testTubes() = PM().apply {
        col = c(180, 140, 90); box(0.8f, 0.3f, 0.06f); at(z = 0.02f) { box(0.8f, 0.3f, 0.06f) }
        val cols = listOf(c(255, 90, 90), c(90, 220, 255), c(255, 230, 90), c(140, 255, 130))
        for (i in 0 until 4) { at(-0.27f + i * 0.18f, 0f, 0.1f) {
            col = c(210, 236, 248); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.055f, 0.03f), floatArrayOf(0.055f, 0.7f)), 12, true) { _, v -> if (v < 0.5f) cols[i] * 1.2f else c(210, 236, 248) } } }
    }.build()

    private fun atom() = PM().apply {
        col = c(255, 100, 90); at(z = 0.5f) { sphere(0.1f, 12, 8) }
        for (k in 0 until 3) { push(); translate(0f, 0f, 0.5f); rotZ(k * 1.047f); rotX(1.1f); col = c(150, 220, 255) * 1.3f; torus(0.38f, 0.012f, 36, 6)
            col = c(255, 240, 120) * 1.6f; translate(0.38f, 0f, 0f); sphere(0.05f, 8, 6); pop() }
        col = c(120, 128, 140); cylinder(0.04f, 0.4f, 8); cylinder(0.18f, 0.05f, 14)
    }.build()

    // ---------------------------------------------------------------- ice
    private fun iceCrystals() = PM().apply {
        for (i in 0 until 5) { val a = i * 1.26f; val h = 0.5f + 0.5f * ((i * 3) % 5) / 4f
            push(); translate(cos(a) * 0.12f * (i > 0).compareTo(false), sin(a) * 0.12f * (i > 0).compareTo(false), 0f); rotZ(a); rotX(0.18f * (i % 3 - 1) * 1.5f)
            col = Vec3.lerp(c(220, 246, 255), c(120, 190, 240), 0.5f) * (if (i % 2 == 0) 1.0f else 1.25f); cylinder(0.1f, h * 0.7f, 6, 0.09f); at(z = h * 0.7f) { cone(0.09f, h * 0.35f, 6) }; pop() }
    }.build()

    private fun snowman() = PM().apply {
        col = c(250, 252, 255)
        at(z = 0.2f) { sphere(0.26f, 20, 12) }; at(z = 0.58f) { sphere(0.2f, 18, 10) }; at(z = 0.86f) { sphere(0.14f, 16, 10) }
        col = c(30, 30, 34); for (s in intArrayOf(-1, 1)) at(s * 0.05f, -0.125f, 0.9f) { sphere(0.02f, 6, 4) }
        for (i in 0 until 3) at(0f, -0.19f, 0.52f + i * 0.08f) { sphere(0.022f, 6, 4) }
        col = c(255, 130, 30); push(); translate(0f, -0.13f, 0.86f); rotX(PI.toFloat() / 2); cone(0.025f, 0.14f, 8); pop()
        col = c(210, 40, 50); at(z = 0.74f) { torus(0.12f, 0.035f, 16, 6) }
        col = c(40, 40, 46); at(z = 0.98f) { cylinder(0.14f, 0.02f, 14); cylinder(0.08f, 0.14f, 14) }
        col = c(120, 80, 40); for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(s * 0.18f, 0f, 0.6f), Vec3(s * 0.34f, 0f, 0.68f), Vec3(s * 0.42f, 0f, 0.8f)), 0.012f, 6)
    }.build()

    private fun lampPost() = PM().apply {
        col = c(26, 34, 46); cylinder(0.05f, 0.82f, 8, 0.035f); cylinder(0.14f, 0.05f, 10)
        col = c(40, 52, 70); at(z = 0.82f) { cylinder(0.11f, 0.04f, 10, 0.09f) }
        col = c(120, 190, 255) * 3.0f; at(z = 0.96f) { sphere(0.1f, 12, 8) }
        col = c(40, 52, 70); at(z = 1.07f) { cone(0.12f, 0.08f, 10) }
        col = c(240, 248, 255); at(z = 1.1f) { push(); scale(1f, 1f, 0.5f); sphere(0.1f, 10, 5); pop() }
    }.build()

    private fun snowPine() = PM().apply {
        col = c(100, 70, 40); cylinder(0.05f, 0.2f, 8)
        for (i in 0 until 4) { col = c(40, 100, 70); at(z = 0.15f + i * 0.2f) { cone(0.38f - i * 0.08f, 0.4f, 12) }
            col = c(248, 252, 255); at(z = 0.27f + i * 0.2f) { cone(0.3f - i * 0.075f, 0.28f, 12) } }
    }.build()

    // ---------------------------------------------------------------- egypt
    private fun obelisk() = PM().apply {
        col = c(214, 176, 112)
        lathe(listOf(floatArrayOf(0.2f, 0f), floatArrayOf(0.12f, 0.85f)), 4, false)
        push(); rotZ((PI / 4).toFloat()); at { lathe(listOf(floatArrayOf(0.16f, 0f), floatArrayOf(0.1f, 0.8f), floatArrayOf(0f, 1f)), 4, false) }; pop()
        col = c(255, 206, 70) * 1.2f; at(z = 0.8f) { push(); rotZ((PI / 4).toFloat()); cone(0.1f, 0.2f, 4); pop() }
    }.build()

    private fun canopicJar() = PM().apply {
        col = c(196, 160, 100)
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.22f, 0f), floatArrayOf(0.3f, 0.2f), floatArrayOf(0.3f, 0.5f), floatArrayOf(0.2f, 0.68f), floatArrayOf(0.16f, 0.72f), floatArrayOf(0.2f, 0.76f)), 22)
        col = c(40, 150, 190); at(z = 0.4f) { torus(0.3f, 0.02f, 24, 6) }
        col = c(255, 206, 70); at(z = 0.76f) { push(); scale(0.9f, 0.8f, 1.1f); sphere(0.2f, 16, 10); pop() }               // jackal-head lid
        col = c(40, 40, 50); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.1f, -0.15f, 0.84f); sphere(0.03f, 6, 4); pop(); push(); translate(s * 0.1f, 0.02f, 1.02f); rotZ(0f); scale(0.5f, 0.3f, 1.3f); cone(0.1f, 0.2f, 4); pop() }
    }.build()

    private fun sphinx() = PM().apply {
        col = c(214, 176, 116)
        at(0f, 0f, 0.0f) { push(); translate(0f, 0.05f, 0.12f); box(0.42f, 0.9f, 0.24f); pop() }                                     // body
        push(); translate(0f, -0.36f, 0.14f); box(0.3f, 0.3f, 0.2f); pop()                                                             // paws
        push(); translate(0f, -0.22f, 0.5f); scale(0.9f, 0.8f, 1f); sphere(0.2f, 18, 12); pop()                                        // head
        col = c(40, 130, 190)                                                                                                         // nemes headdress stripes
        for (i in 0 until 5) { val z = 0.34f + i * 0.06f; at(0f, -0.22f, z) { torus(0.2f - 0.012f * i, 0.012f, 22, 4) } }
        col = c(255, 206, 70); push(); translate(0f, -0.4f, 0.56f); sphere(0.04f, 8, 6); pop()
    }.build()

    private fun pyramidProp() = PM().apply {
        col = c(224, 188, 122); push(); rotZ((PI / 4).toFloat()); cone(0.52f, 0.85f, 4); pop()
        col = c(255, 206, 70); at(z = 0.82f) { push(); rotZ((PI / 4).toFloat()); cone(0.05f, 0.1f, 4); pop() }
        col = c(210, 170, 110)
        for (i in 0 until 5) { val r = 0.5f * (1f - i * 0.18f); at(z = i * 0.16f) { push(); rotZ((PI / 4).toFloat()); cylinder(r * 1.03f, 0.012f, 4); pop() } }
    }.build()

    // ---------------------------------------------------------------- neon
    private fun neonStar() = PM().apply {
        val pts = ArrayList<Vec3>()
        for (i in 0..10) { val r = if (i % 2 == 0) 0.4f else 0.17f; val a = (PI.toFloat() * i / 5f) + PI.toFloat() / 2f; pts.add(Vec3(cos(a) * r, 0f, 0.5f + sin(a) * r)) }
        pts.add(pts[0])
        col = c(255, 230, 80) * 2.4f; tube(pts, 0.025f, 8)
        col = c(40, 40, 60); cylinder(0.02f, 0.12f, 8)
    }.build()

    private fun arcadeStick() = PM().apply {
        col = c(30, 20, 60); box(0.8f, 0.5f, 0.4f).also { }
        push(); translate(0f, 0f, 0.2f); col = c(30, 20, 60); box(0.8f, 0.5f, 0.4f); pop()
        col = c(255, 60, 200) * 1.6f; push(); translate(0f, -0.26f, 0.24f); box(0.78f, 0.02f, 0.03f); pop()
        col = c(200, 200, 220); at(-0.15f, 0f, 0.4f) { cylinder(0.015f, 0.2f, 8) }
        col = c(255, 60, 60); at(-0.15f, 0f, 0.6f) { sphere(0.07f, 12, 8) }
        for (i in 0 until 3) { col = listOf(c(60, 255, 222), c(255, 230, 60), c(255, 60, 200))[i] * 1.5f; at(0.1f + i * 0.12f, 0f, 0.4f) { cylinder(0.04f, 0.03f, 12) } }
    }.build()

    private fun neonRing() = PM().apply {
        push(); translate(0f, 0f, 0.5f); rotX((PI / 2).toFloat()); col = c(60, 255, 222) * 2.4f; torus(0.32f, 0.025f, 36, 8)
        col = c(255, 60, 200) * 2.4f; torus(0.2f, 0.02f, 32, 8); pop()
        col = c(40, 40, 60); cylinder(0.12f, 0.06f, 14)
    }.build()

    private fun neonPalm() = PM().apply {
        col = c(255, 140, 40) * 2.0f; tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.05f, 0f, 0.4f), Vec3(0.02f, 0f, 0.75f)), 0.03f, 8)
        for (i in 0 until 6) { val a = i * 1.047f; val p = ArrayList<Vec3>(); for (k in 0..6) { val t = k / 6f; p.add(Vec3(0.02f + cos(a) * 0.38f * t, sin(a) * 0.38f * t, 0.75f + 0.18f * sin(t * 2.2f) - 0.2f * t * t)) }
            col = c(60, 255, 150) * 2.2f; tube(p, 0.02f, 6, false, null) { 1f - it * 0.5f } }
    }.build()

    // ---------------------------------------------------------------- neon city (cyberpunk)
    private val PINK = c(255, 28, 170)
    private val NCYAN = c(0, 238, 255)
    private val NYEL = c(252, 238, 10)

    private fun neonSign() = PM().apply {
        col = c(20, 22, 30); cylinder(0.035f, 0.5f, 8)
        col = c(14, 14, 22); push(); translate(0f, 0f, 0.78f); box(0.46f, 0.05f, 0.62f); pop()
        col = PINK * 3.2f; push(); translate(0f, -0.03f, 0.95f); box(0.4f, 0.02f, 0.05f); pop()
        col = NCYAN * 3.2f; push(); translate(0f, -0.03f, 0.8f); box(0.3f, 0.02f, 0.05f); pop()
        col = NYEL * 3.0f; push(); translate(0f, -0.03f, 0.65f); box(0.36f, 0.02f, 0.05f); pop()
        col = NCYAN * 3.2f; push(); translate(-0.19f, -0.03f, 0.8f); box(0.04f, 0.02f, 0.5f); pop()
        col = PINK * 3.2f; push(); translate(0.19f, -0.03f, 0.78f); box(0.04f, 0.02f, 0.4f); pop()
    }.build()

    private fun terminal() = PM().apply {
        col = c(24, 26, 38); push(); translate(0f, 0f, 0.42f); box(0.5f, 0.34f, 0.84f); pop()
        col = NCYAN * 2.4f; push(); translate(0f, -0.175f, 0.62f); box(0.36f, 0.012f, 0.22f); pop()
        col = PINK * 2.8f; push(); translate(-0.16f, -0.175f, 0.36f); box(0.05f, 0.012f, 0.18f); pop()
        col = NYEL * 2.8f; push(); translate(0f, -0.175f, 0.36f); box(0.18f, 0.012f, 0.04f); pop()
        col = c(0, 255, 160) * 2.6f; push(); translate(0.16f, -0.175f, 0.36f); box(0.05f, 0.012f, 0.18f); pop()
        col = NCYAN * 3f; push(); translate(0f, -0.175f, 0.82f); box(0.5f, 0.02f, 0.02f); pop()
        col = PINK * 3f; push(); translate(0f, -0.175f, 0.04f); box(0.5f, 0.02f, 0.02f); pop()
    }.build()

    private fun hologram() = PM().apply {
        col = c(22, 24, 36); cylinder(0.3f, 0.07f, 20, 0.26f); at(z = 0.07f) { cylinder(0.12f, 0.05f, 16) }
        val apex = Vec3(0f, 0f, 0.8f); val b = listOf(Vec3(0.2f, 0.2f, 0.2f), Vec3(-0.2f, 0.2f, 0.2f), Vec3(-0.2f, -0.2f, 0.2f), Vec3(0.2f, -0.2f, 0.2f))
        col = NCYAN * 3.2f
        for (i in 0 until 4) { tube(listOf(b[i], apex), 0.012f, 5); tube(listOf(b[i], b[(i + 1) % 4]), 0.012f, 5) }
        col = PINK * 2.6f; at(z = 0.46f) { torus(0.1f, 0.012f, 20, 5) }
        col = NYEL * 3f; at(z = 0.45f) { sphere(0.035f, 8, 6) }
    }.build()

    private fun cityTower() = PM().apply {
        col = c(16, 16, 26)
        val tiers = listOf(floatArrayOf(0.42f, 0.9f), floatArrayOf(0.32f, 0.7f), floatArrayOf(0.22f, 0.5f))
        var z = 0f
        for ((ti, t) in tiers.withIndex()) {
            val w = t[0]; val h = t[1]
            col = c(16, 16, 26); push(); translate(0f, 0f, z + h * 0.5f * 0.5f); box(w, w, h * 0.5f); pop()
            val rows = 6; val cols = 4
            for (r in 0 until rows) for (k in 0 until cols) for (face in 0 until 2) {
                val lit = ((r * 7 + k * 3 + ti * 5 + face) % 5) != 0
                if (!lit) continue
                col = (when ((r + k + ti) % 3) { 0 -> NYEL; 1 -> NCYAN; else -> PINK }) * 2.4f
                val wx = -w / 2 + (k + 0.5f) * w / cols; val wz = z + (r + 0.5f) * h * 0.5f / rows
                push(); translate(wx, if (face == 0) -w / 2 - 0.004f else w / 2 + 0.004f, wz); box(w / cols * 0.5f, 0.012f, h * 0.5f / rows * 0.5f); pop()
            }
            col = (if (ti % 2 == 0) NCYAN else PINK) * 3f; push(); translate(0f, 0f, z + h * 0.5f); box(w + 0.02f, w + 0.02f, 0.02f); pop()
            z += h * 0.5f
        }
        col = c(60, 64, 80); cylinder(0.012f, 0.35f, 6)
        push(); translate(0f, 0f, z); cylinder(0.012f, 0.35f, 6); pop()
        col = c(255, 40, 60) * 3.4f; at(z = z + 0.36f) { sphere(0.025f, 8, 6) }
    }.build()

    // ---------------------------------------------------------------- jungle
    private fun fern() = PM().apply {
        for (i in 0 until 7) { val a = i * 0.9f; val p = ArrayList<Vec3>(); for (k in 0..8) { val t = k / 8f; p.add(Vec3(cos(a) * 0.4f * t, sin(a) * 0.4f * t, 0.1f + 0.5f * sin(t * 1.6f) - 0.12f * t)) }
            col = Vec3.lerp(c(52, 140, 50), c(150, 220, 80), (i % 3) / 2f); tube(p, 0.016f, 5)
            for (k in 1..7) { val q = p[k]; val d = (p[minOf(k + 1, 8)] - p[k - 1]); val sd = Vec3(-d.y, d.x, 0f).normalized() * (0.07f * (1f - k / 9f))
                flat(q, q + sd + d * 0.05f, q + d * 0.18f, q - sd + d * 0.05f)
                flat(q, q - sd + d * 0.05f, q + d * 0.18f, q + sd + d * 0.05f) } }
    }.build()

    private fun idol() = PM().apply {
        col = c(122, 124, 110)
        push(); translate(0f, 0f, 0.32f); box(0.5f, 0.4f, 0.64f); pop()
        push(); translate(0f, 0f, 0.8f); box(0.6f, 0.46f, 0.3f); pop()                                    // head
        col = c(80, 90, 70); push(); translate(0f, -0.24f, 0.8f); box(0.5f, 0.05f, 0.08f); pop()           // brow
        col = c(255, 190, 60) * 1.4f; for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.14f, -0.25f, 0.78f); sphere(0.035f, 8, 6); pop() }
        col = c(60, 66, 54); push(); translate(0f, -0.24f, 0.7f); box(0.24f, 0.04f, 0.04f); pop()
        col = c(58, 120, 46); for (i in 0 until 4) { push(); translate(-0.2f + i * 0.13f, -0.2f, 0.62f); box(0.02f, 0.02f, 0.3f); pop() }
        col = c(100, 104, 92); push(); translate(0f, 0f, 1.0f); box(0.7f, 0.52f, 0.06f); pop()
    }.build()

    private fun jungleStump() = PM().apply {
        col = c(104, 76, 48); cylinder(0.3f, 0.45f, 16, 0.24f)
        col = c(70, 130, 50); at(z = 0.44f) { push(); scale(1f, 1f, 0.3f); sphere(0.26f, 14, 6); pop() }
        col = c(250, 70, 140); for (i in 0 until 5) { val a = i * 1.256f; at(cos(a) * 0.14f, sin(a) * 0.14f, 0.5f) { sphere(0.035f, 6, 4) } }
        col = c(60, 150, 50); for (i in 0 until 4) { push(); translate(cos(i * 1.6f) * 0.22f, sin(i * 1.6f) * 0.22f, 0.4f); rotZ(i * 1.6f); rotY(0.7f); scale(0.3f, 0.12f, 0.02f); sphere(0.5f, 8, 4); pop() }
    }.build()

    private fun jungleTree() = PM().apply {
        col = c(94, 70, 44); tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.03f, 0f, 0.3f), Vec3(-0.02f, 0f, 0.6f), Vec3(0f, 0f, 0.85f)), 0.07f, 10, false, null) { 1f - 0.4f * it }
        for (i in 0 until 4) { col = Vec3.lerp(c(40, 120, 46), c(110, 190, 70), i / 3f)
            at(cos(i * 1.7f) * 0.18f, sin(i * 1.7f) * 0.18f, 0.8f + 0.06f * i) { push(); scale(1f, 1f, 0.6f); sphere(0.3f - 0.03f * i, 14, 8); pop() } }
        col = c(60, 150, 50); for (i in 0 until 3) tube(listOf(Vec3(0.2f * cos(i * 2.1f), 0.2f * sin(i * 2.1f), 0.82f), Vec3(0.26f * cos(i * 2.1f), 0.26f * sin(i * 2.1f), 0.55f), Vec3(0.24f * cos(i * 2.1f), 0.24f * sin(i * 2.1f), 0.3f)), 0.012f, 5)
    }.build()

    // ---------------------------------------------------------------- glow (bioluminescent forest)
    private val FOX = c(140, 255, 92)
    private val TEAL = c(77, 255, 192)
    private val CYAN = c(63, 224, 255)
    private val LIME = c(216, 255, 90)

    /** A mushroom: pale stem, glowing cap and gills. Colours above 1.0 glow. */
    private fun PM.shroom(stem: Float, capR: Float, glowCol: Vec3, k: Float = 2.4f, x: Float = 0f, y: Float = 0f) {
        push(); translate(x, y, 0f)
        col = c(176, 190, 170); cylinder(stem * 0.14f / 0.5f * 0.5f + 0.02f, stem, 10, 0.03f)
        // cap: a bell lathe that glows brighter toward the rim, with a bright underside ring (gills)
        val prof = listOf(floatArrayOf(0.02f, stem * 0.92f), floatArrayOf(capR * 0.55f, stem * 0.9f), floatArrayOf(capR, stem * 1.02f), floatArrayOf(capR * 0.9f, stem * 1.25f), floatArrayOf(capR * 0.5f, stem * 1.45f), floatArrayOf(0.01f, stem * 1.52f))
        lathe(prof, 18, true) { _, v -> glowCol * (k * (0.55 + 0.7 * (1f - v)).toFloat()) }
        col = glowCol * (k * 1.3f); at(z = stem * 0.9f) { torus(capR * 0.82f, 0.018f, 20, 6) }
        for (i in 0 until 5) { col = c(235, 255, 225) * 1.2f; val a = i * 1.257f; push(); translate(cos(a) * capR * 0.5f, sin(a) * capR * 0.5f, stem * 1.33f); sphere(0.025f, 6, 4); pop() }
        pop()
    }

    private fun glowBell() = PM().apply { shroom(0.38f, 0.3f, FOX, 1.8f) }.build()

    private fun glowCluster() = PM().apply {
        shroom(0.5f, 0.2f, TEAL, 2.4f, -0.22f, 0.08f)
        shroom(0.34f, 0.16f, FOX, 2.6f, 0.16f, 0.16f)
        shroom(0.62f, 0.24f, CYAN, 2.4f, 0.04f, -0.2f)
        shroom(0.22f, 0.11f, LIME, 2.8f, 0.3f, -0.12f)
        shroom(0.28f, 0.12f, TEAL, 2.6f, -0.28f, -0.2f)
    }.build()

    private fun glowParasol() = PM().apply { shroom(0.8f, 0.42f, c(192, 112, 255), 2.2f) }.build()

    private fun glowGiant() = PM().apply {
        shroom(0.62f, 0.46f, FOX, 2.0f, 0f, 0f)
        shroom(0.36f, 0.2f, TEAL, 2.4f, 0.34f, 0.1f)
        shroom(0.28f, 0.16f, CYAN, 2.4f, -0.3f, 0.22f)
    }.build()

    private fun sporePod() = PM().apply {
        col = c(60, 90, 70); cylinder(0.04f, 0.4f, 8, 0.02f)
        col = FOX * 2.6f; at(z = 0.46f) { sphere(0.12f, 12, 8) }
        for (i in 0 until 8) { val a = i * 0.785f; col = LIME * 3f; at(cos(a) * 0.2f, sin(a) * 0.2f, 0.5f + 0.1f * ((i * 5) % 3)) { sphere(0.03f, 6, 4) } }
    }.build()

    // ---------------------------------------------------------------- fall (autumn)
    private fun pumpkin() = PM().apply {
        val orange = c(238, 120, 28)
        col = orange
        lathe((0..12).map { val ph = (PI * it / 12).toFloat(); floatArrayOf(sin(ph) * 0.42f, 0.28f - cos(ph) * 0.26f) }, 20, true) { u, _ -> if ((u * 12f).toInt() % 2 == 0) orange else orange * 0.84f }
        col = c(92, 112, 40); cylinder(0.05f, 0.18f, 8, 0.035f).also { }
        push(); translate(0f, 0f, 0.5f); col = c(96, 120, 44); cylinder(0.05f, 0.16f, 8, 0.03f); pop()
        col = c(70, 140, 50); tube(listOf(Vec3(0.04f, 0f, 0.6f), Vec3(0.16f, 0.04f, 0.62f), Vec3(0.2f, 0.12f, 0.56f)), 0.012f, 5)
    }.build()

    private fun leafPile() = PM().apply {
        val cols = listOf(c(240, 180, 40), c(232, 100, 24), c(190, 52, 24), c(210, 140, 40))
        for (i in 0 until 28) {
            val a = i * 2.4f; val r = 0.34f * ((i * 7) % 11) / 11f
            col = cols[i % 4]
            push(); translate(cos(a) * r, sin(a) * r, 0.04f + 0.17f * (1f - r / 0.34f) + 0.01f * (i % 3)); rotZ(a * 1.7f); rotX(0.25f * ((i % 5) - 2)); scale(0.2f, 0.12f, 0.02f); sphere(0.5f, 8, 4); pop()
        }
        col = c(238, 120, 28); push(); translate(0.1f, 0.06f, 0.3f); scale(0.5f, 0.5f, 0.42f); sphere(0.3f, 14, 10); pop()
        col = c(92, 112, 40); push(); translate(0.1f, 0.06f, 0.5f); cylinder(0.025f, 0.08f, 6); pop()
    }.build()

    private fun hayBale() = PM().apply {
        col = c(196, 144, 52)
        push(); translate(0f, 0.25f, 0.3f); rotX(-(PI / 2).toFloat()); cylinder(0.3f, 0.5f, 20); pop()
        col = c(170, 124, 44)
        for (k in 0 until 3) { push(); translate(0f, -0.25f + k * 0.25f, 0.2f); rotX((PI / 2).toFloat()); torus(0.3f, 0.012f, 20, 4); pop() }
    }.build()

    private fun autumnTree() = PM().apply {
        col = c(94, 62, 36); tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.03f, 0f, 0.3f), Vec3(-0.02f, 0f, 0.55f), Vec3(0f, 0f, 0.7f)), 0.07f, 10, false, null) { 1f - 0.4f * it }
        val cols = listOf(c(240, 180, 40), c(232, 100, 24), c(210, 140, 40), c(190, 60, 24))
        for (i in 0 until 7) {
            val a = i * 0.9f; val r = if (i == 0) 0f else 0.22f
            col = cols[i % 4]
            at(cos(a) * r, sin(a) * r, 0.72f + 0.07f * (i % 3)) { push(); scale(1f, 1f, 0.75f); sphere(0.26f - 0.012f * i, 12, 8); pop() }
        }
    }.build()
}
