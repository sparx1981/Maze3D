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

    /** How a placed prop (or a part of it) moves. Parts are drawn at each offset in [at], in the prop's own units. */
    object Anim { const val SPIN = 0; const val BOB = 1; const val SWAY = 2; const val FLICKER = 3; const val WOBBLE = 4; const val NOD = 5; const val SCREW = 6; const val CROWD = 7 }
    class AnimSpec(val part: PropMesh, val type: Int, val speed: Float = 1f, val amp: Float = 1f, val whole: Boolean = false,
                   val at: List<Vec3> = listOf(Vec3.ZERO), val pivotZ: Float = 0f)
    /** A small flock circling above the maze: body plus two flapping wings (left wing along +y, right along -y). */
    class Bird(val body: PropMesh, val wingL: PropMesh, val wingR: PropMesh, val count: Int, val size: Float, val height: Float, val flap: Float, val glide: Boolean = false)
    /** Extra flocks beyond the first (e.g. toucans as well as parrots). */

    class ThemeProps(val junction: PropMesh, val deadA: PropMesh, val deadB: PropMesh, val landmark: PropMesh,
                     val deadC: PropMesh? = null, val junction2: PropMesh? = null,
                     val anims: Map<PropMesh, AnimSpec> = emptyMap(), val bird: Bird? = null, val more: List<PropMesh> = emptyList(),
                     val junctionMore: List<PropMesh> = emptyList(), val floor: List<PropMesh> = emptyList(), val birds2: Bird? = null, val junctionShare: Float = 0.2f, val birds3: Bird? = null, val ring: PropMesh? = null, val ringCount: Int = 0)

    private fun whole(m: PropMesh, type: Int, speed: Float = 1f, amp: Float = 1f, pivotZ: Float = 0f) = m to AnimSpec(m, type, speed, amp, true, pivotZ = pivotZ)
    private fun part(host: PropMesh, part: PropMesh, type: Int, at: List<Vec3>, speed: Float = 1f, amp: Float = 1f) = host to AnimSpec(part, type, speed, amp, false, at)


    private val cache = HashMap<Int, ThemeProps>()
    fun forTheme(id: Int): ThemeProps? = if (id < 0) null else cache.getOrPut(id) { build(id) }

    private fun build(id: Int): ThemeProps = when (id) {
        0 -> { val lp = lollipop(); val cc = candyCane()
            val fp = fruitPastilles(); val ws = wrappedSweet(); val lw = liquoriceWheel(); val cn = candyCorn()
            ThemeProps(gumdrop(), cupcake(), lp, cc, allsortsPile(), allsortLayered(0.5f),
                mapOf(whole(lp, Anim.SPIN, 0.6f), whole(cc, Anim.SPIN, 0.25f), whole(lw, Anim.SPIN, 0.5f), whole(ws, Anim.WOBBLE, 1f, 0.6f)), butterfly(c(255, 120, 200), c(120, 220, 255)),
                listOf(fp, ws, lw, cn), junctionMore = listOf(fp, cn, ws)) }
        1 -> { val mg = marigold(); val cv = candleVotive(); val cb = candelabra()
            ThemeProps(mg, sugarSkull(), cv, cb, null, null,
                mapOf(whole(mg, Anim.SWAY, 1f, 1.2f), part(cv, flame(0.05f, 0.22f), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.55f))),
                    part(cb, flame(0.032f, 0.13f), Anim.FLICKER, listOf(Vec3(-0.3f, 0f, 0.94f), Vec3(0f, 0f, 0.94f), Vec3(0.3f, 0f, 0.94f)))),
                butterfly(c(255, 130, 20), c(40, 20, 10))) }
        2 -> { val tr = trophy(); val fp = flagPole()
            val gs = grandstand(c(220, 30, 30), c(250, 250, 250), c(30, 30, 34))
            ThemeProps(trafficCone(), tyreStack(), tr, fp, raceCar(), null, mapOf(whole(tr, Anim.SPIN, 0.5f), whole(fp, Anim.SWAY, 1.6f, 1.4f), crowdSpec(gs)), null, listOf(gs, gs, gs), ring = gs, ringCount = 18) }
        3 -> { val cf = cornerFlag(); val fb = football(); val tr = trophy()
            val gs = grandstand(c(40, 90, 220), c(250, 214, 40), c(40, 140, 60))
            ThemeProps(cf, fb, tr, floodlight(), goalNet(), null, mapOf(whole(cf, Anim.SWAY, 1.8f, 1.6f), whole(fb, Anim.BOB, 1f, 1f), whole(tr, Anim.SPIN, 0.5f), crowdSpec(gs)),
                bird(c(250, 250, 250), c(200, 200, 205), 5, 0.5f, 3.2f, 7f), listOf(gs, gs)) }
        4 -> { val ds = dish(); val rk = rocket()
            val mr = moonRover()
            ThemeProps(beacon(), ds, canister(), rk, mr, null,
                mapOf(whole(ds, Anim.SPIN, 0.35f), whole(mr, Anim.NOD, 0.4f, 0.5f), part(rk, flame(0.07f, 0.22f, c(120, 200, 255), c(255, 255, 255)), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.02f)), 1.5f, -1f))) }
        5 -> { val bg = burger()
            ThemeProps(ketchup(), bg, sodaCup(), friesBox(), null, null, mapOf(whole(bg, Anim.WOBBLE, 1f, 0.7f)),
                bird(c(250, 250, 250), c(220, 220, 220), 4, 0.55f, 3f, 6f)) }
        6 -> { val at = atom(); val bk = beaker()
            val tc = teslaCoil()
            ThemeProps(flask(), bk, testTubes(), at, microscope(), tc, mapOf(whole(at, Anim.SPIN, 1.2f), part(bk, bubbles(), Anim.BOB, listOf(Vec3(0f, 0f, 0.15f)), 2f, 1f),
                part(tc, teslaSparks(), Anim.SPIN, listOf(Vec3.ZERO), 4f))) }
        7 -> { val sp = snowPine(); val sm = snowman()
            val pg = penguin()
            ThemeProps(lampPost(), sm, sp, sp, iceCrystals(), null, mapOf(whole(sp, Anim.SWAY, 0.7f, 0.8f), whole(sm, Anim.NOD, 0.6f, 0.6f), whole(pg, Anim.BOB, 1.4f, 0.8f)),
                bird(c(200, 40, 30), c(110, 70, 50), 4, 0.35f, 2.2f, 9f), listOf(pg)) }
        8 -> { val sx = sphinx()
            val tq = torch(); val pt = palmTree()
            ThemeProps(obelisk(), canopicJar(), sx, pyramidProp(), pt, tq, mapOf(whole(pt, Anim.SWAY, 0.8f, 1f), part(tq, flame(0.06f, 0.2f), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.78f)))), falcon(), listOf(mummy())) }
        9 -> { val hg = hologram(); val nr = neonRing(); val ns = neonStar()
            ThemeProps(neonSign(), terminal(), hg, cityTower(), nr, ns, mapOf(whole(hg, Anim.SPIN, 1.1f), whole(nr, Anim.SPIN, 0.8f), whole(ns, Anim.SPIN, 0.5f)), drone()) }
        10 -> { val fn = fern(); val jt = bigJungleTree(); val mk = monkey(); val bn = bananaPlant(); val br = bromeliad(); val bb = bamboo(); val ms = monstera(); val hl = heliconia()
            val sn = snake(); val jt2 = jungleTree()
            ThemeProps(fn, idol(), jungleStump(), jt, mk, br,
                mapOf(whole(fn, Anim.SWAY, 1.1f, 1.4f), whole(mk, Anim.BOB, 1.2f, 1f), whole(bn, Anim.SWAY, 0.9f, 1.3f), whole(bb, Anim.SWAY, 0.7f, 1.2f),
                    whole(ms, Anim.SWAY, 1f, 1.2f), whole(hl, Anim.SWAY, 1.1f, 1.3f), whole(br, Anim.SWAY, 1.2f, 0.8f), whole(jt2, Anim.SWAY, 0.7f, 0.8f),
                    part(jt, sn, Anim.SWAY, listOf(Vec3(0.26f * cos(0.4f), 0.26f * sin(0.4f), 0.76f)), 0.8f, 2.5f)),
                parrot(), listOf(bn, bb, ms, hl, jt2), junctionMore = listOf(bn, ms, hl, bb), floor = listOf(fn, br, ms, hl, bb, jt2, fn, br), birds2 = toucan(), junctionShare = 0.85f, birds3 = butterfly(c(60, 140, 255), c(20, 20, 40))) }
        11 -> { val gb = glowBell(); val gc = glowCluster(); val gp = glowParasol(); val gg = glowGiant(); val bn = bonnets(); val ik = inkcap()
            ThemeProps(gb, gc, gp, gg, bn, ik, mapOf(whole(gb, Anim.SWAY, 0.5f, 0.6f), whole(gc, Anim.SWAY, 0.6f, 0.5f), whole(gp, Anim.SWAY, 0.45f, 0.7f),
                whole(bn, Anim.SWAY, 0.7f, 0.6f), whole(ik, Anim.SWAY, 0.55f, 0.6f))) }
        12 -> { val at = autumnTree(); val pk = pumpkin()
            val sc = scarecrow()
            ThemeProps(pk, leafPile(), hayBale(), at, sc, pk, mapOf(whole(at, Anim.SWAY, 0.8f, 0.9f), whole(sc, Anim.SWAY, 1.1f, 1.3f)), crow()) }
        13 -> { val dk = duck(); val tr = toyRocket(); val sb = starBlock()
            val jb = jackInBox(); val st = spinningTop()
            ThemeProps(legoBrick(), dk, toyCar(), tr, blockStack(), sb, mapOf(whole(dk, Anim.BOB, 1f, 1f), whole(tr, Anim.BOB, 0.5f, 0.8f), whole(sb, Anim.SPIN, 0.6f),
                whole(jb, Anim.BOB, 1.6f, 1.4f), whole(st, Anim.SPIN, 6f)), paperPlane(), listOf(jb, st)) }
        14 -> { val sp = spool(); val pp = pomPom(); val ft = feltTree(); val yb = yarnBall()
            ThemeProps(buttonProp(), sp, pp, ft, heartPatch(), yb, mapOf(whole(sp, Anim.SPIN, 0.7f), whole(pp, Anim.WOBBLE, 1.2f, 1f), whole(ft, Anim.SWAY, 0.8f, 1f), whole(yb, Anim.SPIN, 0.4f)),
                butterfly(c(255, 150, 190), c(250, 230, 200))) }
        15 -> { val en = eggNest(); val fn = fern(); val jt = jungleTree(); val dn = dino(); val rp = raptor(); val sk = dinoSkeleton(); val eg = bigEggs()
            val bn = bananaPlant(); val bb = bamboo(); val ms = monstera(); val br = bromeliad()
            ThemeProps(boneProp(), en, fn, volcanoProp(), dn, jt,
                mapOf(whole(en, Anim.WOBBLE, 0.8f, 0.6f), whole(fn, Anim.SWAY, 1f, 1.3f), whole(jt, Anim.SWAY, 0.6f, 0.8f), whole(dn, Anim.NOD, 0.9f, 1.2f),
                    whole(rp, Anim.NOD, 1.3f, 1.4f), whole(eg, Anim.WOBBLE, 0.7f, 0.5f), whole(bn, Anim.SWAY, 0.9f, 1.3f), whole(ms, Anim.SWAY, 1f, 1.2f)),
                pterodactyl(), listOf(rp, sk, eg, dn, sk), junctionMore = listOf(bn, ms, bb, fn), floor = listOf(fn, ms, br, bb), junctionShare = 0.55f) }
        16 -> { val dr = drill(); val gp = gearProp(); val b1 = boltProp(); val b2 = boltProp(); val nut = hexNut()
            ThemeProps(b1, toolbox(), wrench(), hammerProp(), gp, b2, mapOf(whole(gp, Anim.SPIN, 0.9f),
                part(b1, nut, Anim.SCREW, listOf(Vec3(0f, 0f, 0.16f)), 0.8f, 0.34f), part(b2, nut, Anim.SCREW, listOf(Vec3(0f, 0f, 0.16f)), 0.6f, 0.34f),
                dr to AnimSpec(drillBit(), Anim.SPIN, 9f, 1f, false, listOf(Vec3(0.06f, 0f, 0.84f)))), null, listOf(dr)) }
        17 -> { val bz = brazier()
            val lp = lavaPool()
            ThemeProps(emberSpire(), lavaRocks(), obsidianCrystal(), bz, volcanoProp(), emberSpire(),
                mapOf(part(bz, flame(0.16f, 0.42f, c(255, 90, 20), c(255, 210, 60)), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.48f)), 1.2f), whole(lp, Anim.WOBBLE, 0.7f, 0.5f)), null, listOf(lp)) }
        18 -> { val gb = gummyBear(); val pd = pudding(); val ch = cherryProp()
            ThemeProps(gb, pd, jellyBean(), jellyDrop(), ch, gb, mapOf(whole(gb, Anim.WOBBLE, 1.1f, 1f), whole(pd, Anim.WOBBLE, 0.9f, 1.3f), whole(ch, Anim.SWAY, 1.2f, 1.2f)),
                butterfly(c(120, 255, 160), c(255, 240, 120))) }
        else -> medieval()
    }

    private fun medieval(): ThemeProps {
        val cf = campfire(); val bn = banner(); val ca = cage(); val sw = swordInStone(); val sh = shieldStand(); val cs = castle()
        return ThemeProps(bn, sw, sh, cs, ca, bn,
            mapOf(part(cf, flame(0.12f, 0.36f), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.08f)), 1.1f), whole(bn, Anim.SWAY, 1.4f, 1.2f), whole(ca, Anim.NOD, 0.7f, 0.5f)),
            dragon(), listOf(cf, cf, ca, sw), birds2 = bird(c(40, 40, 46), c(30, 30, 36), 4, 0.45f, 2.6f, 6f))
    }

    // ---------------------------------------------------------------- shared animated parts
    /** A flame standing on z = 0: a hot core inside a softer outer tongue (colours above 1 glow and light the scene). */
    fun flame(r: Float, h: Float, outer: Vec3 = c(255, 120, 30), inner: Vec3 = c(255, 220, 90)) = PM().apply {
        col = outer * 2.2f; lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(r, h * 0.18f), floatArrayOf(r * 0.8f, h * 0.45f), floatArrayOf(r * 0.3f, h * 0.8f), floatArrayOf(0f, h)), 12)
        col = inner * 2.8f; lathe(listOf(floatArrayOf(0f, h * 0.02f), floatArrayOf(r * 0.55f, h * 0.18f), floatArrayOf(r * 0.4f, h * 0.45f), floatArrayOf(0f, h * 0.7f)), 10)
    }.build()

    private fun bubbles() = PM().apply {
        col = c(170, 255, 200) * 1.3f
        for (i in 0 until 6) { val a = i * 2.3f; at(cos(a) * 0.08f, sin(a) * 0.08f, i * 0.07f) { sphere(0.025f + 0.008f * (i % 3), 8, 6) } }
    }.build()

    // ---------------------------------------------------------------- flying creatures (body along +x, wings along ±y)
    private fun wingPair(span: Float, chord: Float, cA: Vec3, cB: Vec3, tip: Float = 0.5f): Pair<PropMesh, PropMesh> {
        fun wing(sgn: Float) = PM().apply {
            val n = 5
            for (k in 0 until n) {
                val y0 = sgn * span * k / n; val y1 = sgn * span * (k + 1) / n
                val f0 = 1f - (1f - tip) * k / n; val f1 = 1f - (1f - tip) * (k + 1) / n
                col = if (k >= n - 2) cB else cA
                push(); translate(-chord * 0.15f, (y0 + y1) * 0.5f, 0f); box(chord * (f0 + f1) * 0.5f, kotlin.math.abs(y1 - y0) * 1.02f, 0.012f); pop()
            }
        }.build()
        return wing(1f) to wing(-1f)
    }

    private fun bird(cBody: Vec3, cWing: Vec3, count: Int, size: Float, height: Float, flap: Float): Bird {
        val body = PM().apply {
            col = cBody; at { scale(1f, 0.6f, 0.6f); sphere(0.12f, 12, 8) }
            at(0.13f, 0f, 0.03f) { sphere(0.06f, 10, 8) }
            col = c(250, 180, 40); at(0.19f, 0f, 0.03f) { rotY((PI / 2).toFloat()); cone(0.02f, 0.06f, 6) }
            col = cWing; at(-0.16f, 0f, 0f) { scale(1f, 1f, 0.2f); cone(0.06f, 0.12f, 6) }
        }.build()
        val (l, r) = wingPair(0.3f, 0.14f, cWing, cWing * 0.8f)
        return Bird(body, l, r, count, size, height, flap)
    }

    private fun parrot(): Bird {
        val body = PM().apply {
            col = c(230, 40, 40); at { scale(1f, 0.6f, 0.6f); sphere(0.13f, 14, 10) }
            at(0.14f, 0f, 0.04f) { sphere(0.065f, 12, 8) }
            col = c(255, 230, 120); at(0.2f, 0f, 0.02f) { rotY(2.0f); cone(0.025f, 0.07f, 8) }
            col = c(250, 250, 250); for (s in intArrayOf(-1, 1)) at(0.165f, s * 0.04f, 0.06f) { sphere(0.015f, 6, 4) }
            col = c(40, 100, 230); push(); translate(-0.13f, 0f, 0f); rotY((PI / 2).toFloat() + 0.2f); scale(0.4f, 1f, 1f); cylinder(0.06f, 0.26f, 8, 0.01f); pop()
        }.build()
        val (l, r) = wingPair(0.34f, 0.16f, c(230, 40, 40), c(40, 110, 230), 0.45f)
        return Bird(body, l, r, 5, 0.6f, 2.6f, 8f)
    }

    private fun crow(): Bird {
        val b = bird(c(30, 30, 36), c(24, 24, 30), 5, 0.55f, 2.8f, 6f)
        return Bird(b.body, b.wingL, b.wingR, 6, 0.55f, 2.8f, 6f, glide = true)
    }

    private fun falcon(): Bird {
        val b = bird(c(150, 110, 70), c(110, 80, 50), 3, 0.75f, 3.6f, 4f)
        return Bird(b.body, b.wingL, b.wingR, 3, 0.75f, 3.6f, 4f, glide = true)
    }

    private fun pterodactyl(): Bird {
        val body = PM().apply {
            col = c(150, 110, 80); at { scale(1f, 0.5f, 0.5f); sphere(0.12f, 12, 8) }
            at(0.12f, 0f, 0.05f) { sphere(0.05f, 10, 8) }
            push(); translate(0.15f, 0f, 0.06f); rotY((PI / 2).toFloat() + 0.25f); cone(0.03f, 0.2f, 8); pop()          // long beak
            col = c(200, 80, 50); push(); translate(0.08f, 0f, 0.09f); rotY(-0.9f); cone(0.025f, 0.14f, 6); pop()       // crest
        }.build()
        val (l, r) = wingPair(0.48f, 0.2f, c(170, 120, 90), c(120, 80, 60), 0.2f)
        return Bird(body, l, r, 3, 0.9f, 3.6f, 3f, glide = true)
    }

    private fun butterfly(cA: Vec3, cB: Vec3): Bird {
        val body = PM().apply { col = c(40, 30, 30); at { scale(1f, 0.3f, 0.3f); sphere(0.08f, 8, 6) } }.build()
        fun wing(sgn: Float) = PM().apply {
            col = cA * 1.1f; push(); translate(0.02f, sgn * 0.11f, 0f); scale(1f, 1f, 0.06f); sphere(0.1f, 12, 8); pop()
            col = cB; push(); translate(-0.07f, sgn * 0.08f, 0f); scale(1f, 1f, 0.06f); sphere(0.065f, 10, 6); pop()
        }.build()
        return Bird(body, wing(1f), wing(-1f), 6, 0.35f, 0.9f, 14f)
    }

    private fun paperPlane(): Bird {
        val body = PM().apply { col = c(250, 250, 250); box(0.3f, 0.02f, 0.05f) }.build()
        fun wing(sgn: Float) = PM().apply { col = c(240, 240, 245); prism(listOf(floatArrayOf(0.15f, 0f), floatArrayOf(-0.15f, sgn * 0.0f), floatArrayOf(-0.15f, sgn * 0.16f)).let { if (sgn < 0) it.reversed() else it }, 0.008f) }.build()
        return Bird(body, wing(1f), wing(-1f), 3, 0.6f, 2.2f, 0f, glide = true)
    }

    private fun drone(): Bird {
        val body = PM().apply {
            col = c(40, 44, 54); box(0.22f, 0.1f, 0.05f)
            col = c(255, 40, 160) * 2f; at(0.11f, 0f, 0f) { sphere(0.02f, 6, 4) }
            col = c(40, 220, 255) * 2f; at(-0.11f, 0f, 0f) { sphere(0.02f, 6, 4) }
        }.build()
        fun arm(sgn: Float) = PM().apply {
            col = c(60, 64, 74); at(0f, sgn * 0.1f, 0f) { box(0.03f, 0.12f, 0.02f) }
            col = c(160, 170, 190); for (x in floatArrayOf(-0.09f, 0.09f)) at(x, sgn * 0.16f, 0.015f) { cylinder(0.06f, 0.004f, 14) }
        }.build()
        return Bird(body, arm(1f), arm(-1f), 4, 0.6f, 2.4f, 0f, glide = true)
    }

    // ---------------------------------------------------------------- ground animals
    private fun monkey() = PM().apply {
        val fur = c(110, 70, 40); val face = c(220, 170, 130)
        col = fur; at(z = 0.35f) { scale(0.9f, 0.8f, 1.1f); sphere(0.22f, 16, 12) }
        at(z = 0.72f) { sphere(0.18f, 16, 12) }
        col = face; at(0.12f, 0f, 0.7f) { scale(0.6f, 1f, 0.9f); sphere(0.12f, 12, 8) }
        col = fur; for (s in intArrayOf(-1, 1)) { at(0f, s * 0.19f, 0.76f) { sphere(0.06f, 10, 8) } }
        col = face; for (s in intArrayOf(-1, 1)) { at(0f, s * 0.2f, 0.76f) { sphere(0.035f, 8, 6) } }
        col = c(20, 16, 14); for (s in intArrayOf(-1, 1)) { at(0.17f, s * 0.05f, 0.76f) { sphere(0.022f, 8, 6) } }
        col = fur
        for (s in intArrayOf(-1, 1)) {
            tube(listOf(Vec3(0f, s * 0.17f, 0.48f), Vec3(0.12f, s * 0.24f, 0.35f), Vec3(0.18f, s * 0.2f, 0.2f)), 0.045f, 8)
            tube(listOf(Vec3(0f, s * 0.1f, 0.2f), Vec3(0.12f, s * 0.14f, 0.12f), Vec3(0.14f, s * 0.12f, 0.02f)), 0.05f, 8)
        }
        tube(listOf(Vec3(-0.18f, 0f, 0.2f), Vec3(-0.34f, 0f, 0.22f), Vec3(-0.4f, 0f, 0.42f), Vec3(-0.32f, 0f, 0.55f)), 0.025f, 6)
        col = c(250, 220, 60); push(); translate(0.22f, 0.18f, 0.2f); rotX(0.5f); scale(0.35f, 0.35f, 1f); cylinder(0.08f, 0.24f, 10, 0.05f); pop()   // banana
    }.build()

    private fun dino() = PM().apply {
        // a friendly long-necked sauropod: smooth barrel body, tapering neck and tail as one curve, pillar legs with toenails,
        // a small head with eyes and nostrils, a lighter belly and darker blotches down the back
        val skin = c(96, 156, 84); val dark = c(60, 112, 58); val belly = c(206, 214, 150)
        val spineCurve = (0..20).map { k -> val v = k / 20f
            val x = -0.62f + 1.16f * v
            val z = 0.42f + (if (v > 0.62f) (v - 0.62f) * 1.25f else 0f) * (if (v > 0.62f) 1f else 0f) + 0.06f * kotlin.math.sin(v * 3.1f) - (if (v < 0.25f) (0.25f - v) * 0.9f else 0f)
            Vec3(x, 0f, z) }
        col = skin
        tube(spineCurve, 0.2f, 18, colorFn = { v -> if (((v * 23f).toInt() % 5) == 0 && v in 0.25f..0.7f) dark else skin }, taper = { v ->
            when { v < 0.25f -> 0.18f + 0.82f * (v / 0.25f); v < 0.62f -> 1f; else -> 1f - 0.72f * ((v - 0.62f) / 0.38f) } })
        col = belly; at(0.0f, 0f, 0.36f) { scale(1.5f, 0.75f, 0.55f); sphere(0.18f, 16, 10) }
        val head = spineCurve.last()
        col = skin; at(head.x + 0.05f, 0f, head.z + 0.03f) { scale(1.5f, 0.85f, 0.8f); sphere(0.075f, 14, 10) }
        col = c(30, 26, 22); for (s in intArrayOf(-1, 1)) { at(head.x + 0.07f, s * 0.05f, head.z + 0.07f) { sphere(0.014f, 8, 6) }; at(head.x + 0.15f, s * 0.02f, head.z + 0.05f) { sphere(0.007f, 5, 4) } }
        col = c(255, 255, 255); for (s in intArrayOf(-1, 1)) at(head.x + 0.078f, s * 0.054f, head.z + 0.075f) { sphere(0.005f, 4, 3) }
        col = dark; tube(listOf(Vec3(head.x + 0.02f, -0.03f, head.z - 0.01f), Vec3(head.x + 0.12f, 0f, head.z), Vec3(head.x + 0.02f, 0.03f, head.z - 0.01f)), 0.004f, 3)   // smile
        for (lx in floatArrayOf(-0.2f, 0.18f)) for (s in intArrayOf(-1, 1)) {
            col = skin; tube(listOf(Vec3(lx, s * 0.12f, 0.38f), Vec3(lx + 0.02f, s * 0.13f, 0.18f), Vec3(lx, s * 0.13f, 0.03f)), 0.07f, 12, taper = { 1f - 0.15f * it })
            col = belly; for (t in -1..1) at(lx + 0.06f, s * 0.13f + t * 0.03f, 0.02f) { scale(1f, 0.8f, 0.6f); sphere(0.022f, 6, 4) }
        }
        col = dark; for (k in 0 until 7) { val p = spineCurve[6 + k * 2]; at(p.x, 0f, p.z + 0.16f) { scale(1.3f, 0.5f, 0.6f); sphere(0.04f, 8, 6) } }   // back ridge
    }.build()

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
        // calavera: rounded cranium with temples, cheekbones and a jaw, deep eye sockets ringed with petals,
        // an upside-down heart nose, stitched smile and teeth, painted forehead flower and dotted scrollwork
        val bone = c(250, 246, 236)
        col = bone
        at(z = 0.58f) { scale(0.86f, 0.82f, 0.92f); sphere(0.4f, 28, 18) }                                  // cranium
        for (s in intArrayOf(-1, 1)) at(s * 0.2f, -0.2f, 0.38f) { scale(0.9f, 0.8f, 0.7f); sphere(0.14f, 14, 10) }   // cheekbones
        at(0f, -0.08f, 0.24f) { scale(0.78f, 0.7f, 0.55f); sphere(0.3f, 22, 12) }                         // jaw
        at(0f, -0.24f, 0.18f) { scale(1.2f, 0.6f, 0.5f); sphere(0.12f, 14, 8) }                            // chin
        for (s in intArrayOf(-1, 1)) {
            col = c(30, 14, 46); at(s * 0.14f, -0.3f, 0.52f) { scale(1f, 0.45f, 1.05f); sphere(0.105f, 16, 10) }        // socket
            col = c(255, 255, 255) * 1.3f; at(s * 0.12f, -0.345f, 0.55f) { sphere(0.018f, 6, 4) }                       // glint
            for (i in 0 until 10) {                                                                                        // petals around the eye
                val a = i * 0.628f
                col = if (i % 2 == 0) c(255, 130, 30) else c(255, 196, 40)
                push(); translate(s * 0.14f + cos(a) * 0.135f, -0.325f, 0.52f + sin(a) * 0.14f); rotY(a); scale(1.6f, 0.4f, 0.9f); sphere(0.028f, 8, 5); pop()
            }
            col = c(40, 200, 190); for (k in 0 until 5) at(s * (0.3f + 0.02f * k), -0.2f + 0.04f * k, 0.32f + 0.06f * k) { sphere(0.016f, 6, 4) }   // cheek dots
            col = c(240, 70, 120); at(s * 0.24f, -0.27f, 0.35f) { scale(1f, 0.4f, 1f); sphere(0.04f, 10, 6) }                                   // cheek rose
        }
        col = c(40, 16, 50); push(); translate(0f, -0.345f, 0.4f); rotY(PI.toFloat()); scale(1f, 0.4f, 1f)                 // heart-shaped nose
        for (s in intArrayOf(-1, 1)) at(s * 0.025f, 0f, -0.01f) { sphere(0.03f, 8, 6) }; at(z = 0.02f) { cone(0.045f, 0.06f, 8) }
        pop()
        col = c(40, 16, 50); tube((0..10).map { k -> val u = k / 10f - 0.5f; Vec3(u * 0.34f, -0.33f + 0.02f * (u * u * 4f), 0.29f + 0.03f * (u * u * 4f)) }, 0.008f, 4)    // smile
        col = bone * 1.02f; for (i in -4..4) at(i * 0.036f, -0.335f, 0.28f + 0.012f * (i * i) / 4f) { box(0.026f, 0.02f, 0.05f) }
        col = c(40, 16, 50); for (i in -4..4) at(i * 0.036f + 0.018f, -0.345f, 0.28f + 0.012f * (i * i) / 4f) { box(0.004f, 0.006f, 0.05f) }
        // forehead flower and scrollwork
        for (i in 0 until 6) { val a = i * 1.047f; col = if (i % 2 == 0) c(240, 70, 120) else c(150, 80, 220)
            push(); translate(cos(a) * 0.055f, -0.36f + 0.02f, 0.78f + sin(a) * 0.055f); scale(1f, 0.35f, 1f); sphere(0.04f, 10, 6); pop() }
        col = c(255, 214, 40); at(0f, -0.37f, 0.78f) { scale(1f, 0.4f, 1f); sphere(0.028f, 8, 6) }
        col = c(40, 200, 190); for (s in intArrayOf(-1, 1)) tube((0..8).map { k -> val v = k / 8f; Vec3(s * (0.1f + 0.14f * v), -0.34f + 0.1f * v * v, 0.7f + 0.12f * kotlin.math.sin(v * 3.2f)) }, 0.008f, 4)
        col = c(255, 214, 40); for (k in 0 until 7) at(-0.15f + k * 0.05f, -0.3f, 0.92f - 0.01f * (k - 3) * (k - 3)) { sphere(0.014f, 6, 4) }
    }.build()

    private fun candleVotive() = PM().apply {
        col = c(255, 214, 120); cylinder(0.2f, 0.55f, 20, 0.2f)
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
                col = c(60, 40, 30); at(z = 0.16f) { cylinder(0.006f, 0.02f, 4) } }
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
        // three racing tyres with tread grooves and rims, strapped with a sponsor band
        for (k in 0 until 3) {
            val z = 0.1f + k * 0.2f; val tw = k * 0.07f
            push(); translate(0.02f * sin(k * 2f), 0.02f * cos(k * 2f), z); rotZ(tw)
            col = c(26, 26, 30); torus(0.26f, 0.1f, 28, 12)
            col = c(44, 44, 50); for (g in 0 until 24) { push(); rotZ(g * 0.2618f); translate(0.355f, 0f, 0f); box(0.012f, 0.02f, 0.16f); pop() }
            col = c(250, 214, 40); torus(0.2f, 0.012f, 24, 4)
            pop()
        }
        col = c(230, 40, 40); push(); translate(0f, -0.365f, 0.3f); box(0.3f, 0.01f, 0.5f); pop()
        col = c(250, 250, 250); push(); translate(0f, -0.37f, 0.36f); box(0.22f, 0.006f, 0.08f); pop()
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
        // lattice mast with a cage and a bank of lamps that light the pitch
        val steel = c(150, 156, 166)
        col = c(70, 74, 80); cylinder(0.16f, 0.04f, 12)
        col = steel
        for (s in 0 until 3) { val a = s * 2.094f; tube(listOf(Vec3(cos(a) * 0.1f, sin(a) * 0.1f, 0.04f), Vec3(cos(a) * 0.04f, sin(a) * 0.04f, 0.92f)), 0.012f, 5) }
        for (k in 0 until 8) { val z0 = 0.06f + k * 0.11f; val r0 = 0.1f - 0.06f * z0; val a = k * 2.094f
            tube(listOf(Vec3(cos(a) * r0, sin(a) * r0, z0), Vec3(cos(a + 2.094f) * r0, sin(a + 2.094f) * r0, z0 + 0.11f)), 0.006f, 3) }
        at(0f, 0f, 0.98f) {
            col = c(60, 64, 72); box(0.54f, 0.1f, 0.32f)
            for (i in -2..2) for (j in -1..0) { col = c(255, 250, 225) * 2.2f; push(); translate(i * 0.1f, -0.055f, j * 0.13f + 0.065f); rotX((PI / 2).toFloat()); cylinder(0.04f, 0.01f, 10); pop() }
            col = c(60, 64, 72); for (i in -2..2) { push(); translate(i * 0.1f, -0.07f, 0.13f); box(0.08f, 0.04f, 0.012f); pop() }
        }
    }.build()

    // ---------------------------------------------------------------- space
    private fun beacon() = PM().apply {
        // antenna mast: tripod, lattice and a blinking warning light on top (lights its surroundings red)
        val steel = c(170, 178, 192)
        col = c(120, 128, 142); cylinder(0.26f, 0.06f, 14, 0.2f)
        col = steel
        for (s in 0 until 3) { val a = s * 2.094f; tube(listOf(Vec3(cos(a) * 0.18f, sin(a) * 0.18f, 0.05f), Vec3(cos(a) * 0.03f, sin(a) * 0.03f, 0.82f)), 0.012f, 5) }
        for (k in 0 until 6) at(z = 0.15f + k * 0.12f) { torus(0.16f - 0.18f * (0.15f + k * 0.12f), 0.006f, 12, 3) }
        col = c(210, 214, 224); at(z = 0.6f) { push(); rotX(1.2f); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.1f, 0.03f), floatArrayOf(0.12f, 0.05f)), 14, true); pop() }
        col = c(255, 60, 60) * 2.4f; at(z = 0.86f) { sphere(0.06f, 12, 8) }
        col = c(60, 64, 76); at(z = 0.82f) { cylinder(0.05f, 0.02f, 10) }
    }.build()

    private fun dish() = PM().apply {
        col = c(170, 176, 190); cylinder(0.3f, 0.08f, 16, 0.2f); cylinder(0.04f, 0.35f, 8)
        push(); translate(0f, 0f, 0.45f); rotX(-0.9f)
        col = c(230, 232, 240); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.2f, 0.03f), floatArrayOf(0.4f, 0.12f), floatArrayOf(0.45f, 0.16f)), 26, true)
        col = c(120, 128, 142); cylinder(0.012f, 0.3f, 6); at(z = 0.3f) { col = c(255, 90, 160) * 1.8f; sphere(0.03f, 8, 6) }
        pop()
    }.build()

    private fun canister() = PM().apply {
        // ribbed fuel tank with hazard band, valve wheel and carry handle
        val steel = c(206, 212, 222)
        col = steel; lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.26f, 0f), floatArrayOf(0.28f, 0.03f), floatArrayOf(0.28f, 0.62f), floatArrayOf(0.22f, 0.7f), floatArrayOf(0.08f, 0.74f), floatArrayOf(0f, 0.74f)), 24)
        col = c(170, 176, 188); for (k in 0 until 4) at(z = 0.1f + k * 0.15f) { torus(0.283f, 0.012f, 24, 4) }
        for (i in 0 until 8) { col = if (i % 2 == 0) c(250, 190, 30) else c(30, 30, 34); push(); rotZ(i * 0.785f); at(0f, 0f, 0.36f) { lathe(listOf(floatArrayOf(0.286f, 0f), floatArrayOf(0.286f, 0.08f)), 6, false) }; pop() }
        col = c(84, 224, 255) * 1.6f; at(0f, -0.28f, 0.52f) { box(0.12f, 0.01f, 0.05f) }
        col = c(90, 96, 108); at(z = 0.74f) { cylinder(0.03f, 0.1f, 10) }
        col = c(220, 40, 40); at(z = 0.84f) { torus(0.07f, 0.012f, 14, 5) }
        col = c(60, 64, 74); tube(listOf(Vec3(-0.16f, 0f, 0.68f), Vec3(-0.12f, 0f, 0.82f), Vec3(0.12f, 0f, 0.82f), Vec3(0.16f, 0f, 0.68f)), 0.015f, 6)
    }.build()

    private fun rocket() = PM().apply {
        col = c(240, 242, 248)
        lathe(listOf(floatArrayOf(0f, 0.1f), floatArrayOf(0.14f, 0.1f), floatArrayOf(0.15f, 0.6f), floatArrayOf(0.1f, 0.85f), floatArrayOf(0f, 1f)), 24, true) { _, v -> if (v > 0.78f) c(230, 50, 50) else c(240, 242, 248) }
        col = c(60, 160, 255); at(0f, -0.145f, 0.55f) { sphere(0.06f, 10, 8) }
        col = c(230, 50, 50)
        for (i in 0 until 3) { push(); rotZ(i * 2.094f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.0125f)
            prism(listOf(floatArrayOf(0.12f, 0.08f), floatArrayOf(0.34f, 0.04f), floatArrayOf(0.12f, 0.46f)), 0.025f); pop() }
        col = c(60, 66, 78); at(z = 0.02f) { cylinder(0.08f, 0.1f, 14, 0.12f) }
    }.build()

    // ---------------------------------------------------------------- fast food
    private fun ketchup() = PM().apply {
        // squeeze bottle: rounded body, white label, flip cap with a nozzle and a drip of sauce
        val red = c(205, 22, 24)
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.26f, 0f), floatArrayOf(0.3f, 0.04f), floatArrayOf(0.31f, 0.3f), floatArrayOf(0.3f, 0.52f),
            floatArrayOf(0.24f, 0.64f), floatArrayOf(0.14f, 0.7f), floatArrayOf(0.13f, 0.72f)), 26) { _, v -> if (v in 0.22f..0.6f) c(248, 244, 232) else red }
        col = c(40, 140, 50); for (k in 0 until 3) at(0f, 0f, 0.3f + k * 0.09f) { push(); translate(0f, -0.31f, 0f); box(0.22f - k * 0.05f, 0.012f, 0.03f); pop() }
        col = red * 0.8f; at(z = 0.42f) { push(); translate(0f, -0.312f, 0f); scale(1f, 0.1f, 1f); sphere(0.06f, 10, 6); pop() }      // tomato
        col = c(250, 250, 250); at(z = 0.72f) { cylinder(0.14f, 0.1f, 20, 0.12f) }
        at(z = 0.82f) { cylinder(0.05f, 0.14f, 12, 0.012f) }
        col = red; at(0.0f, 0.0f, 0.97f) { sphere(0.022f, 8, 6) }
        tube(listOf(Vec3(0.12f, 0f, 0.74f), Vec3(0.2f, 0f, 0.7f), Vec3(0.27f, 0f, 0.62f), Vec3(0.31f, 0f, 0.5f)), 0.025f, 8, taper = { 1f - 0.5f * it })
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
        // red carton with a curved front, a golden arch and a fan of fries
        val red = c(212, 30, 30)
        col = red
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.2f, 0f), floatArrayOf(0.3f, 0.5f), floatArrayOf(0.31f, 0.52f)), 4, false)
        col = c(255, 200, 40); at(0f, -0.23f, 0.25f) { rotX((PI / 2).toFloat()); torus(0.08f, 0.018f, 16, 5) }
        for (i in 0 until 16) {
            val a = i * 2.4f; val r = 0.04f + 0.17f * ((i * 7) % 9) / 9f
            col = if (i % 3 == 0) c(255, 210, 90) else c(244, 190, 70)
            push(); translate(cos(a) * r, sin(a) * r * 0.7f, 0.42f); rotX(sin(a) * 0.25f); rotY(cos(a) * 0.25f)
            translate(0f, 0f, 0.14f + 0.06f * (i % 4)); box(0.045f, 0.045f, 0.36f + 0.06f * (i % 3)); pop()
        }
    }.build()

    // ---------------------------------------------------------------- science
    private fun flask() = PM().apply {
        // conical lab flask with glowing liquid, a cork, a little clamp stand and vapour
        col = c(80, 255, 170) * 1.3f; lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.3f, 0.01f), floatArrayOf(0.22f, 0.22f), floatArrayOf(0f, 0.24f)), 22)
        col = c(214, 236, 248); lathe(listOf(floatArrayOf(0.22f, 0.22f), floatArrayOf(0.08f, 0.56f), floatArrayOf(0.07f, 0.72f), floatArrayOf(0.085f, 0.74f)), 22, false)
        at(z = 0f) { torus(0.29f, 0.015f, 22, 4) }
        col = c(170, 120, 70); at(z = 0.7f) { cylinder(0.065f, 0.1f, 12, 0.075f) }
        col = c(60, 64, 76); push(); translate(-0.36f, 0f, 0f); cylinder(0.12f, 0.03f, 12); cylinder(0.015f, 0.75f, 6); pop()
        tube(listOf(Vec3(-0.36f, 0f, 0.52f), Vec3(-0.08f, 0f, 0.52f)), 0.012f, 5)
        col = c(200, 255, 230) * 1.2f; for (k in 0 until 3) at(0.02f * k, 0f, 0.86f + k * 0.07f) { sphere(0.035f + 0.012f * k, 8, 6) }
    }.build()

    private fun beaker() = PM().apply {
        // glass beaker with a pouring lip, graduation marks and a glowing liquid
        val glass = c(200, 228, 240)
        col = c(90, 255, 160) * 1.25f; at(z = 0.02f) { cylinder(0.27f, 0.42f, 24) }
        col = c(150, 255, 200) * 1.4f; at(z = 0.44f) { cylinder(0.27f, 0.015f, 24) }
        col = glass
        lathe(listOf(floatArrayOf(0.3f, 0.46f), floatArrayOf(0.3f, 0.78f), floatArrayOf(0.33f, 0.8f)), 24, false)     // glass shows above the liquid only
        at(z = 0.0f) { torus(0.29f, 0.015f, 24, 5) }
        at(0f, -0.3f, 0.78f) { rotX(-0.6f); cone(0.06f, 0.08f, 8) }
        col = c(250, 250, 255)
        for (i in 1..5) at(0.3f, 0f, 0.12f * i) { box(0.012f, if (i % 2 == 0) 0.14f else 0.08f, 0.012f) }
    }.build()

    private fun testTubes() = PM().apply {
        // a wooden rack holding four tubes of coloured, glowing liquids
        col = c(170, 120, 70); push(); translate(0f, 0f, 0.03f); box(0.7f, 0.2f, 0.06f); pop()
        push(); translate(0f, 0f, 0.4f); box(0.7f, 0.2f, 0.04f); pop()
        for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.33f, 0f, 0.22f); box(0.04f, 0.2f, 0.4f); pop() }
        val liq = listOf(c(255, 80, 120), c(80, 220, 255), c(140, 255, 120), c(255, 200, 60))
        for (i in 0 until 4) {
            val x = -0.22f + i * 0.145f
            col = c(214, 236, 248); push(); translate(x, 0f, 0.07f); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.045f, 0.03f), floatArrayOf(0.045f, 0.62f), floatArrayOf(0.055f, 0.64f)), 12, false); pop()
            col = liq[i] * 1.3f; push(); translate(x, 0f, 0.075f); cylinder(0.04f, 0.2f + 0.08f * (i % 3), 12); pop()
        }
    }.build()

    private fun atom() = PM().apply {
        col = c(255, 100, 90); at(z = 0.5f) { sphere(0.1f, 12, 8) }
        for (k in 0 until 3) { push(); translate(0f, 0f, 0.5f); rotZ(k * 1.047f); rotX(1.1f); col = c(150, 220, 255) * 1.3f; torus(0.38f, 0.012f, 36, 6)
            col = c(255, 240, 120) * 1.6f; translate(0.38f, 0f, 0f); sphere(0.05f, 8, 6); pop() }
        col = c(120, 128, 140); cylinder(0.04f, 0.4f, 8); cylinder(0.18f, 0.05f, 14)
    }.build()

    // ---------------------------------------------------------------- ice
    private fun iceCrystals() = PM().apply {
        // a cluster of faceted ice shards, glowing faintly from inside
        col = c(180, 214, 240); at(z = 0.01f) { scale(1f, 1f, 0.25f); sphere(0.3f, 10, 6) }
        for (i in 0 until 7) {
            val a = i * 0.9f; val r = if (i == 0) 0f else 0.14f + 0.04f * (i % 2); val h = if (i == 0) 0.95f else 0.4f + 0.35f * ((i * 3) % 5) / 4f
            push(); translate(cos(a) * r, sin(a) * r, 0.02f); rotZ(a); rotX(if (i == 0) 0f else 0.35f * (if (i % 2 == 0) 1f else -0.7f))
            col = c(210, 240, 255) * (if (i % 2 == 0) 1.12f else 1.3f); cylinder(0.07f + 0.03f * (i == 0).compareTo(false), h * 0.72f, 6, 0.06f)
            col = c(235, 250, 255) * 1.4f; at(z = h * 0.72f) { cone(0.065f + 0.03f * (i == 0).compareTo(false), h * 0.3f, 6) }
            pop()
        }
    }.build()

    private fun snowman() = PM().apply {
        // three packed snowballs, coal buttons and eyes, carrot nose, stick arms, knitted scarf and a top hat
        val snow = c(246, 249, 255)
        col = snow; at(z = 0.2f) { scale(1f, 1f, 0.9f); sphere(0.24f, 20, 14) }; at(z = 0.5f) { sphere(0.17f, 18, 12) }; at(z = 0.74f) { sphere(0.12f, 16, 12) }
        col = c(20, 20, 24)
        for (k in 0 until 3) at(0.165f, 0f, 0.42f + k * 0.07f) { sphere(0.02f, 6, 4) }
        for (s in intArrayOf(-1, 1)) at(0.105f, s * 0.04f, 0.78f) { sphere(0.016f, 6, 4) }
        for (k in 0 until 5) { val a = -0.5f + k * 0.25f; at(0.11f * cos(a), 0.11f * sin(a), 0.7f - 0.012f * (2 - kotlin.math.abs(k - 2))) { sphere(0.01f, 5, 4) } }
        col = c(255, 130, 30); at(0.115f, 0f, 0.75f) { rotY((PI / 2).toFloat()); cone(0.025f, 0.13f, 8) }
        col = c(110, 70, 40)
        for (s in intArrayOf(-1, 1)) { tube(listOf(Vec3(0f, s * 0.15f, 0.55f), Vec3(0.02f, s * 0.28f, 0.66f), Vec3(0.0f, s * 0.36f, 0.74f)), 0.012f, 5)
            tube(listOf(Vec3(0.0f, s * 0.3f, 0.69f), Vec3(0.03f, s * 0.34f, 0.62f)), 0.008f, 4) }
        col = c(210, 40, 40); at(z = 0.64f) { torus(0.12f, 0.035f, 18, 6) }
        tube(listOf(Vec3(0.06f, 0.08f, 0.63f), Vec3(0.1f, 0.12f, 0.5f), Vec3(0.11f, 0.13f, 0.42f)), 0.03f, 6)
        col = c(250, 250, 250); for (k in 0 until 3) at(0.12f, 0.135f, 0.44f + k * 0.03f) { box(0.01f, 0.04f, 0.006f) }
        col = c(30, 30, 36); at(z = 0.84f) { cylinder(0.14f, 0.015f, 18) }; at(z = 0.855f) { cylinder(0.09f, 0.16f, 16) }
        col = c(200, 40, 40); at(z = 0.87f) { cylinder(0.092f, 0.025f, 16) }
    }.build()

    private fun lampPost() = PM().apply {
        // Victorian street lamp: fluted post, scroll arms, glowing glass lantern with a snowy cap
        val iron = c(26, 34, 46)
        col = iron; cylinder(0.14f, 0.06f, 12, 0.11f); at(z = 0.06f) { cylinder(0.08f, 0.1f, 10, 0.05f) }
        at(z = 0.16f) { cylinder(0.045f, 0.62f, 10, 0.035f) }
        at(z = 0.4f) { torus(0.045f, 0.012f, 10, 4) }
        for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(0f, 0f, 0.7f), Vec3(s * 0.09f, 0f, 0.74f), Vec3(s * 0.07f, 0f, 0.8f), Vec3(s * 0.02f, 0f, 0.78f)), 0.01f, 5)
        at(z = 0.8f) { cylinder(0.1f, 0.04f, 8, 0.12f) }
        col = c(255, 214, 140) * 2.6f; at(z = 0.84f) { cylinder(0.09f, 0.18f, 8, 0.11f) }
        col = iron; for (k in 0 until 4) { val a = k * 1.571f + 0.39f; tube(listOf(Vec3(cos(a) * 0.1f, sin(a) * 0.1f, 0.84f), Vec3(cos(a) * 0.12f, sin(a) * 0.12f, 1.02f)), 0.008f, 4) }
        at(z = 1.02f) { cone(0.16f, 0.1f, 8) }
        col = c(240, 248, 255); at(z = 1.06f) { push(); scale(1f, 1f, 0.45f); sphere(0.13f, 12, 6); pop() }
    }.build()

    private fun snowPine() = PM().apply {
        // layered fir with drooping boughs, snow lying on each tier, a trunk flare and a star of frost at the top
        col = c(96, 64, 40); cylinder(0.05f, 0.25f, 10, 0.04f)
        for (i in 0 until 5) {
            val z = 0.15f + i * 0.15f; val r = 0.4f - i * 0.07f
            col = Vec3.lerp(c(30, 90, 56), c(46, 120, 70), i / 4f)
            at(z = z) { lathe(listOf(floatArrayOf(0f, 0.22f), floatArrayOf(r * 0.4f, 0.12f), floatArrayOf(r, -0.02f), floatArrayOf(r * 0.94f, -0.05f), floatArrayOf(r * 0.3f, 0.0f)), 14) { u, _ -> if ((u * 14f).toInt() % 2 == 0) c(30, 96, 60) else c(40, 110, 66) } }
            col = c(244, 248, 255); at(z = z + 0.04f) { lathe(listOf(floatArrayOf(0f, 0.17f), floatArrayOf(r * 0.38f, 0.095f), floatArrayOf(r * 0.85f, 0.01f), floatArrayOf(r * 0.7f, 0f)), 14) }
        }
        col = c(230, 245, 255) * 1.4f; at(z = 0.98f) { sphere(0.035f, 8, 6) }
    }.build()

    // ---------------------------------------------------------------- egypt
    private fun obelisk() = PM().apply {
        // a tapering granite needle on a stepped base, carved with glyph rows and capped in gold
        col = c(190, 152, 96); push(); translate(0f, 0f, 0.03f); box(0.44f, 0.44f, 0.06f); pop(); push(); translate(0f, 0f, 0.08f); box(0.34f, 0.34f, 0.04f); pop()
        push(); translate(0f, 0f, 0.1f); rotZ((PI / 4).toFloat())
        lathe(listOf(floatArrayOf(0.19f, 0f), floatArrayOf(0.12f, 0.78f)), 4, false) { _, v -> if (((v * 24f).toInt() % 3) == 0) c(176, 138, 84) else c(214, 176, 112) }
        col = c(255, 206, 70) * 1.25f; at(z = 0.78f) { cone(0.12f, 0.16f, 4) }
        pop()
    }.build()

    private fun canopicJar() = PM().apply {
        // painted jar with a jackal-head lid, hieroglyph bands and gold rims
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.2f, 0f), floatArrayOf(0.27f, 0.2f), floatArrayOf(0.26f, 0.45f), floatArrayOf(0.18f, 0.58f), floatArrayOf(0.16f, 0.6f)), 24) { u, v ->
            when { v in 0.3f..0.36f -> c(40, 100, 180); v in 0.38f..0.44f -> if ((u * 16f).toInt() % 2 == 0) c(200, 50, 40) else c(240, 220, 170); else -> c(232, 214, 176) } }
        col = c(255, 206, 70); at(z = 0.0f) { torus(0.2f, 0.015f, 20, 4) }; at(z = 0.6f) { torus(0.165f, 0.015f, 20, 4) }
        col = c(30, 30, 36)
        at(z = 0.6f) { cylinder(0.15f, 0.06f, 18, 0.13f) }
        at(z = 0.72f) { scale(1f, 0.85f, 1f); sphere(0.12f, 14, 10) }
        at(0.13f, 0f, 0.7f) { rotY(1.4f); cone(0.06f, 0.14f, 10) }
        for (s in intArrayOf(-1, 1)) at(-0.02f, s * 0.06f, 0.82f) { cone(0.035f, 0.14f, 8) }
        col = c(255, 206, 70) * 1.2f; for (s in intArrayOf(-1, 1)) at(0.09f, s * 0.045f, 0.76f) { sphere(0.012f, 6, 4) }
    }.build()

    private fun sphinx() = PM().apply {
        // a recumbent lion with a human head in a striped nemes headdress
        val stone = c(214, 176, 116); val shade = c(186, 148, 96)
        col = stone
        at(0f, 0.12f, 0.2f) { scale(0.32f, 0.62f, 0.2f); sphere(1f, 20, 12) }                     // body
        at(0f, 0.5f, 0.24f) { scale(0.3f, 0.22f, 0.22f); sphere(1f, 16, 10) }                      // haunches
        for (s in intArrayOf(-1, 1)) {
            at(s * 0.16f, -0.32f, 0.07f) { scale(0.09f, 0.28f, 0.07f); sphere(1f, 12, 8) }        // fore legs
            col = shade; at(s * 0.16f, -0.58f, 0.06f) { sphere(0.07f, 10, 8) }; col = stone      // paws
        }
        at(0f, -0.2f, 0.42f) { scale(0.2f, 0.18f, 0.2f); sphere(1f, 14, 10) }                       // chest
        tube(listOf(Vec3(0f, 0.7f, 0.12f), Vec3(0.18f, 0.66f, 0.04f), Vec3(0.26f, 0.38f, 0.03f)), 0.03f, 6)   // tail
        // head and headdress
        at(0f, -0.26f, 0.64f) { scale(0.13f, 0.14f, 0.16f); sphere(1f, 16, 12) }
        col = c(40, 110, 190)
        for (i in 0 until 6) {
            col = if (i % 2 == 0) c(40, 110, 190) else c(236, 196, 90)
            for (s in intArrayOf(-1, 1)) at(s * (0.15f + i * 0.012f), -0.22f, 0.7f - i * 0.06f) { box(0.05f, 0.14f, 0.06f) }
        }
        col = c(236, 196, 90); at(0f, -0.25f, 0.78f) { scale(1.15f, 1f, 0.45f); sphere(0.15f, 14, 8) }
        col = c(255, 206, 70) * 1.2f; at(0f, -0.38f, 0.8f) { sphere(0.03f, 8, 6) }               // uraeus
        col = shade; for (s in intArrayOf(-1, 1)) at(s * 0.05f, -0.385f, 0.66f) { box(0.04f, 0.01f, 0.012f) }
        at(0f, -0.4f, 0.62f) { box(0.025f, 0.03f, 0.05f) }
        col = c(120, 90, 60); at(0f, -0.36f, 0.5f) { box(0.03f, 0.03f, 0.1f) }                    // beard
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
        // an Olmec-style colossal head on a stepped plinth: a banded helmet with carved glyph studs, heavy lidded eyes
        // that glow from deep sockets, a broad flat nose with flared nostrils, full lips, ear spools, cracks and draped moss
        val stone = c(132, 128, 112); val shade = c(96, 94, 82); val moss = c(74, 128, 52); val mossD = c(46, 96, 40)
        col = shade; push(); translate(0f, 0f, 0.04f); box(0.78f, 0.7f, 0.08f); pop(); col = stone; push(); translate(0f, 0f, 0.1f); box(0.66f, 0.6f, 0.06f); pop()
        col = stone
        at(0f, 0f, 0.5f) { scale(0.86f, 0.72f, 1.0f); sphere(0.36f, 26, 18) }                                     // head
        at(0f, -0.06f, 0.24f) { scale(0.95f, 0.75f, 0.6f); sphere(0.3f, 20, 12) }                                // jaw and cheeks
        // helmet: a band round the head with a raised rim and glyph studs, and a crest at the back
        col = shade; at(0f, 0f, 0.66f) { scale(0.9f, 0.76f, 0.5f); torus(0.32f, 0.05f, 28, 8) }
        col = stone; for (k in 0 until 9) { val a = -1.25f + k * 0.31f; at(sin(a) * 0.3f, -cos(a) * 0.27f, 0.68f) { scale(1f, 0.6f, 1f); sphere(0.03f, 8, 6) } }
        col = shade; at(0f, 0.14f, 0.82f) { scale(0.5f, 0.9f, 0.4f); sphere(0.2f, 14, 8) }
        // eyes: deep sockets, heavy lids, glowing irises
        for (s in intArrayOf(-1, 1)) {
            col = c(40, 38, 32); at(s * 0.12f, -0.24f, 0.5f) { scale(1.3f, 0.5f, 0.7f); sphere(0.065f, 12, 8) }
            col = stone; at(s * 0.12f, -0.255f, 0.545f) { scale(1.5f, 0.6f, 0.45f); sphere(0.06f, 12, 8) }                           // upper lid
            col = c(255, 190, 70) * 1.8f; at(s * 0.12f, -0.27f, 0.5f) { scale(1f, 0.5f, 0.8f); sphere(0.025f, 8, 6) }
            col = stone; at(s * 0.31f, -0.02f, 0.45f) { scale(0.4f, 1f, 1.3f); sphere(0.07f, 10, 8) }                               // ears
            col = shade; at(s * 0.34f, -0.02f, 0.42f) { rotY((PI / 2).toFloat()); torus(0.035f, 0.014f, 12, 5) }                  // ear spools
        }
        col = stone; at(0f, -0.29f, 0.4f) { scale(1.4f, 0.8f, 1f); sphere(0.07f, 12, 8) }; at(0f, -0.27f, 0.46f) { scale(0.6f, 0.6f, 1.4f); sphere(0.045f, 10, 8) }   // nose
        col = c(40, 38, 32); for (s in intArrayOf(-1, 1)) at(s * 0.045f, -0.33f, 0.38f) { sphere(0.016f, 6, 4) }                // nostrils
        col = stone; at(0f, -0.29f, 0.29f) { scale(1.7f, 0.7f, 0.45f); sphere(0.07f, 14, 8) }; at(0f, -0.28f, 0.24f) { scale(1.5f, 0.7f, 0.45f); sphere(0.065f, 14, 8) }   // lips
        col = c(40, 38, 32); at(0f, -0.32f, 0.265f) { box(0.18f, 0.01f, 0.012f) }
        // weathering: cracks and moss draped over the crown and down one side, with a few hanging tendrils
        col = c(60, 58, 50); tube(listOf(Vec3(0.14f, -0.2f, 0.78f), Vec3(0.2f, -0.24f, 0.62f), Vec3(0.24f, -0.22f, 0.5f)), 0.006f, 3)
        for (k in 0 until 14) { val a = k * 0.45f; col = if (k % 3 == 0) mossD else moss
            at(cos(a) * 0.17f * (0.4f + 0.6f * (k % 4) / 3f), sin(a) * 0.14f, 0.8f + 0.02f * (k % 3)) { scale(1f, 1f, 0.35f); sphere(0.07f, 8, 6) } }
        col = moss; for (k in 0 until 5) { val x = -0.25f + k * 0.03f; tube(listOf(Vec3(x, -0.05f, 0.78f), Vec3(x - 0.04f, -0.14f, 0.6f), Vec3(x - 0.05f, -0.12f, 0.4f)), 0.008f, 4) }
        col = c(80, 160, 60); for (k in 0 until 6) at(-0.3f, -0.12f + k * 0.01f, 0.62f - k * 0.05f) { scale(1f, 0.4f, 0.2f); sphere(0.03f, 6, 4) }
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

    /** A mushroom after the spore demo: pale stem, a cap that is only faintly lit on top, brightly glowing gills underneath, and dew beads. */
    private fun PM.shroom(stem: Float, capR: Float, glowCol: Vec3, k: Float = 2.4f, x: Float = 0f, y: Float = 0f) {
        push(); translate(x, y, 0f)
        col = c(184, 196, 176); cylinder(0.022f + capR * 0.07f, stem, 10, 0.02f + capR * 0.045f)
        val capTop = c(200, 255, 154)
        val prof = listOf(floatArrayOf(0.02f, stem * 0.9f), floatArrayOf(capR * 0.6f, stem * 0.88f), floatArrayOf(capR, stem * 1.0f), floatArrayOf(capR * 0.92f, stem * 1.22f), floatArrayOf(capR * 0.5f, stem * 1.42f), floatArrayOf(0.01f, stem * 1.5f))
        lathe(prof, 18, true) { _, v -> Vec3.lerp(capTop * 0.85f, glowCol * 1.1f, 1f - v) }
        col = glowCol * (k * 1.5f); at(z = stem * 0.9f) { cylinder(capR * 0.97f, 0.012f, 20) }           // the glowing gills under the cap
        col = glowCol * (k * 1.2f); at(z = stem * 0.9f) { torus(capR * 0.86f, 0.016f, 22, 6) }
        for (i in 0 until 7) { col = c(235, 255, 235); val a = i * 0.9f; val r = capR * (0.25f + 0.5f * ((i * 5) % 3) / 3f)
            push(); translate(cos(a) * r, sin(a) * r, stem * (1.28f - r / capR * 0.25f)); sphere(0.016f, 6, 4); pop() }   // dew
        pop()
    }

    private fun inkcap() = PM().apply {
        col = c(214, 222, 208); cylinder(0.035f, 0.78f, 10, 0.03f)
        col = Vec3.lerp(c(220, 235, 210), c(77, 255, 192), 0.4f) * 1.4f
        lathe(listOf(floatArrayOf(0.04f, 0.7f), floatArrayOf(0.14f, 0.68f), floatArrayOf(0.15f, 0.82f), floatArrayOf(0.1f, 1.0f), floatArrayOf(0.02f, 1.08f)), 16, true) { _, v -> c(77, 255, 192) * (0.5f + 2.0f * (1f - v)) }
    }.build()

    private fun bonnets() = PM().apply {
        for (i in 0 until 9) { val a = i * 2.4f; val r = 0.04f + 0.24f * ((i * 7) % 9) / 9f; shroom(0.12f + 0.1f * ((i * 3) % 4) / 4f, 0.05f + 0.025f * (i % 3), if (i % 2 == 0) c(63, 224, 255) else c(216, 255, 90), 2.8f, cos(a) * r, sin(a) * r) }
    }.build()

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
        // ribbed pumpkin with a curly stem and a leaf, plus a little gourd beside it
        col = c(236, 120, 30)
        for (k in 0 until 10) { val a = k * 0.628f; at(cos(a) * 0.13f, sin(a) * 0.13f, 0.2f) { rotZ(a); scale(1.1f, 0.8f, 0.85f); sphere(0.2f, 14, 10) } }
        col = c(200, 90, 20); at(z = 0.2f) { scale(1f, 1f, 0.9f); sphere(0.24f, 16, 10) }
        col = c(90, 110, 40); tube(listOf(Vec3(0f, 0f, 0.36f), Vec3(0.02f, 0f, 0.46f), Vec3(0.07f, 0.02f, 0.5f)), 0.03f, 8, taper = { 1f - 0.4f * it })
        tube(listOf(Vec3(0.02f, 0f, 0.44f), Vec3(-0.05f, 0.06f, 0.46f), Vec3(-0.08f, 0.02f, 0.5f), Vec3(-0.05f, -0.02f, 0.48f)), 0.006f, 4)
        col = c(70, 130, 40); push(); translate(-0.08f, -0.08f, 0.38f); rotZ(-0.8f); rotY(0.4f); scale(1f, 0.6f, 0.08f); sphere(0.09f, 10, 6); pop()
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
        // two rectangular bales stacked crosswise, with twine and loose straw
        val straw = c(214, 170, 72)
        for (b in 0..1) {
            push(); translate(0f, 0f, 0.17f + b * 0.32f); rotZ(b * 1.4f)
            col = straw; box(0.62f, 0.36f, 0.3f)
            col = c(232, 196, 104); for (k in 0 until 9) at(-0.28f + k * 0.07f, -0.181f, 0f) { box(0.012f, 0.006f, 0.28f) }
            col = c(150, 96, 40); for (s in intArrayOf(-1, 1)) at(s * 0.15f, 0f, 0f) { box(0.02f, 0.372f, 0.312f) }
            pop()
        }
        col = c(232, 196, 104)
        for (k in 0 until 10) { val a = k * 0.63f; tube(listOf(Vec3(cos(a) * 0.33f, sin(a) * 0.24f, 0.02f), Vec3(cos(a) * 0.42f, sin(a) * 0.3f, 0.06f + 0.03f * (k % 3))), 0.006f, 4) }
    }.build()

    private fun autumnTree() = PM().apply {
        // gnarled trunk splitting into boughs, clouds of red-gold foliage and a few bare twigs
        val bark = c(88, 60, 40)
        col = bark
        tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.03f, 0f, 0.2f), Vec3(-0.01f, 0.02f, 0.42f)), 0.07f, 10, taper = { 1f - 0.3f * it })
        for (i in 0 until 4) { val a = i * 1.57f + 0.3f
            tube(listOf(Vec3(-0.01f, 0.02f, 0.4f), Vec3(cos(a) * 0.12f, sin(a) * 0.12f, 0.55f), Vec3(cos(a) * 0.22f, sin(a) * 0.22f, 0.66f)), 0.035f, 7, taper = { 1f - 0.6f * it }) }
        for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(0f, 0f, 0.03f), Vec3(s * 0.12f, 0.04f, 0f)), 0.025f, 6)
        val cols = listOf(c(214, 70, 24), c(236, 140, 30), c(190, 40, 30), c(240, 180, 50), c(160, 70, 30))
        for (i in 0 until 11) {
            val a = i * 0.57f; val r = 0.08f + 0.22f * ((i * 3) % 7) / 6f; val z = 0.62f + 0.12f * ((i * 5) % 4) / 3f
            col = cols[i % cols.size]
            at(cos(a) * r, sin(a) * r, z) { scale(1f, 1f, 0.75f); sphere(0.14f + 0.03f * (i % 3), 12, 8) }
        }
        col = bark * 0.8f; for (k in 0 until 3) { val a = k * 2.1f; tube(listOf(Vec3(cos(a) * 0.22f, sin(a) * 0.22f, 0.66f), Vec3(cos(a) * 0.38f, sin(a) * 0.38f, 0.78f)), 0.008f, 4) }
    }.build()

    // ---------------------------------------------------------------- toy world
    private fun legoBrick() = PM().apply {
        // a 2x4 brick with rounded studs, embossed rims and the hollow tubes visible underneath
        val red = c(220, 30, 30)
        col = red; push(); translate(0f, 0f, 0.2f); box(0.8f, 0.4f, 0.4f); pop()
        for (i in 0 until 4) for (j in 0 until 2) at(-0.3f + i * 0.2f, -0.1f + j * 0.2f, 0.4f) {
            col = red; cylinder(0.07f, 0.07f, 16)
            col = c(240, 60, 60); at(z = 0.07f) { torus(0.06f, 0.01f, 16, 4) }
        }
        col = c(170, 20, 20); for (i in 0 until 3) at(-0.2f + i * 0.2f, 0f, 0f) { cylinder(0.06f, 0.006f, 14) }
        col = c(250, 250, 250); push(); translate(0f, -0.201f, 0.2f); box(0.2f, 0.003f, 0.06f); pop()
    }.build()

    private fun starBlock() = PM().apply {
        col = c(250, 200, 30); push(); translate(0f, 0f, 0.2f); box(0.5f, 0.5f, 0.4f); pop()
        col = c(255, 235, 120); at(z = 0.4f) { cylinder(0.12f, 0.08f, 5) }
    }.build()

    private fun duck() = PM().apply {
        // rubber duck: plump body with a raised tail, head, orange bill with a lip line, glossy eyes and wing bumps
        val y = c(255, 214, 30)
        col = y; at(z = 0.24f) { scale(1.25f, 0.95f, 0.75f); sphere(0.26f, 22, 14) }
        push(); translate(-0.3f, 0f, 0.36f); rotY(-0.8f); scale(0.6f, 0.5f, 1f); cone(0.1f, 0.16f, 12); pop()
        at(0.18f, 0f, 0.58f) { sphere(0.17f, 20, 14) }
        for (s in intArrayOf(-1, 1)) at(-0.04f, s * 0.22f, 0.3f) { scale(1.4f, 0.4f, 0.8f); sphere(0.11f, 12, 8) }
        col = c(255, 120, 20); at(0.34f, 0f, 0.55f) { scale(1.2f, 1f, 0.45f); sphere(0.08f, 12, 8) }
        col = c(220, 90, 10); at(0.36f, 0f, 0.54f) { box(0.1f, 0.12f, 0.006f) }
        col = c(20, 20, 24); for (s in intArrayOf(-1, 1)) at(0.29f, s * 0.08f, 0.64f) { sphere(0.025f, 8, 6) }
        col = c(255, 255, 255) * 1.1f; for (s in intArrayOf(-1, 1)) at(0.305f, s * 0.085f, 0.655f) { sphere(0.008f, 5, 4) }
    }.build()

    private fun toyCar() = PM().apply {
        // chunky wind-up toy car: rounded body, windows, big wheels with hubs, key on the back
        val paint = c(40, 130, 230)
        col = paint; push(); translate(0f, 0f, 0.22f); scale(0.28f, 0.44f, 0.12f); sphere(1f, 18, 10); pop()
        push(); translate(0f, 0.05f, 0.34f); scale(0.2f, 0.22f, 0.12f); sphere(1f, 14, 8); pop()
        col = c(200, 230, 255); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.17f, 0.05f, 0.36f); box(0.01f, 0.2f, 0.07f); pop() }
        push(); translate(0f, -0.13f, 0.37f); rotX(0.5f); box(0.26f, 0.01f, 0.08f); pop()
        col = c(255, 214, 40); for (s in intArrayOf(-1, 1)) at(s * 0.12f, -0.43f, 0.24f) { sphere(0.035f, 8, 6) }
        col = c(230, 50, 50); at(0f, 0.45f, 0.22f) { box(0.3f, 0.02f, 0.06f) }
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            col = c(24, 24, 28); push(); translate(sx * 0.24f, sy * 0.27f, 0.12f); rotY((PI / 2).toFloat()); translate(0f, 0f, -0.05f); cylinder(0.12f, 0.1f, 16); pop()
            col = c(250, 214, 40); push(); translate(sx * 0.295f, sy * 0.27f, 0.12f); rotY((PI / 2).toFloat()); cylinder(0.05f, 0.01f, 12); pop()
        }
        col = c(200, 200, 210); tube(listOf(Vec3(0f, 0.44f, 0.3f), Vec3(0f, 0.54f, 0.3f)), 0.015f, 6)
        push(); translate(0f, 0.56f, 0.3f); rotX((PI / 2).toFloat()); scale(1f, 0.25f, 1f); torus(0.05f, 0.015f, 12, 4); pop()
    }.build()

    private fun toyRocket() = PM().apply {
        col = c(235, 60, 50)
        lathe(listOf(floatArrayOf(0.02f, 0.2f), floatArrayOf(0.2f, 0.22f), floatArrayOf(0.2f, 0.7f), floatArrayOf(0.1f, 0.92f), floatArrayOf(0f, 1f)), 20, true) { _, v -> if (v > 0.75f) c(250, 200, 30) else c(235, 60, 50) }
        col = c(40, 130, 230); for (k in 0 until 3) { push(); rotZ(k * 2.094f); translate(0.2f, 0f, 0.28f); box(0.16f, 0.03f, 0.3f); pop() }
        col = c(210, 235, 255); at(0f, -0.195f, 0.55f) { sphere(0.06f, 10, 8) }
    }.build()

    private fun blockStack() = PM().apply {
        val cols = listOf(c(226, 48, 40), c(250, 200, 30), c(34, 120, 230))
        for (i in 0 until 3) { col = cols[i]; push(); translate(0.04f * (i % 2), 0f, 0.15f + i * 0.3f); rotZ(0.3f * i); box(0.34f, 0.34f, 0.3f); pop() }
        col = c(255, 255, 255); at(z = 0.92f) { sphere(0.12f, 10, 8) }
    }.build()

    // ---------------------------------------------------------------- craft
    private fun buttonProp() = PM().apply {
        col = c(236, 90, 120); cylinder(0.3f, 0.08f, 26)
        col = c(250, 130, 150); at(z = 0.06f) { torus(0.26f, 0.025f, 26, 6) }
        col = c(60, 40, 40); for (i in 0..1) for (j in 0..1) { at(-0.07f + i * 0.14f, -0.07f + j * 0.14f, 0.075f) { cylinder(0.03f, 0.02f, 8) } }
        col = c(250, 240, 220); tube(listOf(Vec3(-0.07f, -0.07f, 0.1f), Vec3(0.07f, 0.07f, 0.1f)), 0.012f, 5); tube(listOf(Vec3(0.07f, -0.07f, 0.1f), Vec3(-0.07f, 0.07f, 0.1f)), 0.012f, 5)
    }.build()

    private fun spool() = PM().apply {
        col = c(200, 150, 90); cylinder(0.3f, 0.07f, 24); at(z = 0.73f) { cylinder(0.3f, 0.07f, 24) }
        col = c(224, 60, 90); at(z = 0.07f) { cylinder(0.2f, 0.66f, 22) }
        col = c(240, 100, 130); for (k in 0 until 8) { at(z = 0.1f + k * 0.075f) { torus(0.2f, 0.012f, 22, 5) } }
        col = c(250, 240, 220); tube(listOf(Vec3(0.2f, 0f, 0.3f), Vec3(0.34f, 0.1f, 0.15f), Vec3(0.3f, 0.3f, 0.03f)), 0.012f, 5)
    }.build()

    private fun pomPom() = PM().apply {
        val cols = listOf(c(250, 140, 170), c(250, 200, 80), c(110, 200, 220))
        for (i in 0 until 40) { val a = i * 2.4f; val z = 0.1f + 0.5f * ((i * 7) % 13) / 13f; val r = 0.28f * kotlin.math.sqrt(1f - ((z - 0.35f) / 0.4f).let { it * it }.coerceAtMost(1f))
            col = cols[i % 3]; push(); translate(cos(a) * r, sin(a) * r, z); sphere(0.07f, 6, 4); pop() }
        col = c(250, 140, 170); at(z = 0.36f) { sphere(0.26f, 12, 8) }
    }.build()

    private fun heartPatch() = PM().apply {
        // a stuffed felt heart on a stand, edged with running stitches
        val felt = c(224, 60, 90)
        fun lobe(sgn: Float) = listOf(floatArrayOf(0f, -0.42f)) + (0..10).map { k ->
            val a = (PI * (1.15f - 1.15f * k / 10f)).toFloat()
            floatArrayOf(sgn * (0.2f + 0.2f * cos(a)) , 0.12f + 0.2f * sin(a)) }.let { if (sgn < 0) it else it.reversed() } + listOf(floatArrayOf(0f, 0.1f))
        push(); translate(0f, 0.05f, 0.5f); rotX((PI / 2).toFloat())
        col = felt; for (s in floatArrayOf(-1f, 1f)) { val pts = lobe(s); prism(if (s > 0) pts.reversed() else pts, 0.1f) }
        col = c(250, 240, 220)
        for (s in floatArrayOf(-1f, 1f)) for (k in 0 until 9) {
            val a0 = (PI * (1.1f - 1.1f * k / 9f)).toFloat(); val a1 = a0 - 0.08f
            tube(listOf(Vec3(s * (0.2f + 0.19f * cos(a0)), 0.12f + 0.19f * sin(a0), 0.105f), Vec3(s * (0.2f + 0.19f * cos(a1)), 0.12f + 0.19f * sin(a1), 0.105f)), 0.008f, 4)
        }
        pop()
        col = c(180, 140, 100); cylinder(0.025f, 0.12f, 8); cylinder(0.14f, 0.03f, 16)
    }.build()

    private fun yarnBall() = PM().apply {
        for (k in 0 until 14) { col = if (k % 2 == 0) c(100, 190, 210) else c(236, 100, 130); push(); translate(0f, 0f, 0.3f); rotZ(k * 0.5f); rotX(0.4f * k); torus(0.28f, 0.03f, 26, 6); pop() }
        col = c(236, 100, 130); at(0f, 0f, 0.3f) { sphere(0.27f, 14, 10) }
    }.build()

    private fun feltTree() = PM().apply {
        col = c(130, 90, 60); cylinder(0.06f, 0.25f, 8)
        for (i in 0 until 3) { col = if (i % 2 == 0) c(70, 150, 90) else c(100, 180, 100); at(z = 0.2f + i * 0.22f) { cone(0.34f - i * 0.08f, 0.4f, 14) } }
        col = c(250, 200, 60); at(z = 0.92f) { sphere(0.05f, 8, 6) }
    }.build()

    // ---------------------------------------------------------------- prehistoric
    private fun boneProp() = PM().apply {
        // a big dinosaur bone lying on a rock, knobbly at both ends
        col = c(120, 110, 96); at(z = 0.05f) { scale(1.3f, 1f, 0.4f); sphere(0.2f, 10, 6) }
        col = c(240, 232, 210)
        push(); translate(0f, 0f, 0.16f); rotY((PI / 2).toFloat()); translate(0f, 0f, -0.3f); cylinder(0.055f, 0.6f, 12, 0.06f); pop()
        for (s in intArrayOf(-1, 1)) { at(s * 0.32f, 0.05f, 0.16f) { sphere(0.085f, 12, 8) }; at(s * 0.32f, -0.05f, 0.16f) { sphere(0.08f, 12, 8) } }
        col = c(210, 200, 176); for (k in 0 until 3) at(-0.1f + 0.1f * k, 0f, 0.215f) { box(0.01f, 0.06f, 0.006f) }
    }.build()

    private fun eggNest() = PM().apply {
        // a twiggy nest holding three speckled eggs
        col = c(120, 86, 50)
        for (k in 0 until 18) { val a = k * 0.35f; val r = 0.3f
            tube(listOf(Vec3(cos(a) * r, sin(a) * r, 0.04f + 0.03f * (k % 3)), Vec3(cos(a + 0.9f) * (r + 0.03f), sin(a + 0.9f) * (r + 0.03f), 0.1f - 0.02f * (k % 2))), 0.018f, 4) }
        col = c(140, 100, 60); at(z = 0.02f) { scale(1f, 1f, 0.3f); sphere(0.3f, 16, 8) }
        for (i in 0 until 3) { val a = i * 2.1f
            push(); translate(cos(a) * 0.1f, sin(a) * 0.1f, 0.16f); rotY(0.3f * (i - 1)); scale(0.8f, 0.8f, 1.1f)
            sphere(0.12f, 16, 12) { u, v -> if (((u * 37f + v * 23f) * 7f % 1f) > 0.9f) c(150, 120, 90) else c(244, 236, 214) }
            pop() }
    }.build()

    private fun volcanoProp() = PM().apply {
        // rugged cone, glowing crater, lava rivulets down the flanks and a plume of smoke
        lathe(listOf(floatArrayOf(0.02f, 0f), floatArrayOf(0.48f, 0f), floatArrayOf(0.4f, 0.12f), floatArrayOf(0.26f, 0.48f), floatArrayOf(0.16f, 0.76f), floatArrayOf(0.13f, 0.8f), floatArrayOf(0.08f, 0.74f), floatArrayOf(0.02f, 0.72f)), 22, false) { u, v ->
            val n = sin(u * 37f) * sin(v * 23f + u * 11f)
            if (v > 0.9f) c(255, 120, 30) * 2f else c(84, 64, 54) * (0.8f + 0.25f * n) }
        col = c(255, 110, 20) * 2.4f; at(z = 0.74f) { cylinder(0.1f, 0.02f, 14) }
        for (i in 0 until 4) {
            val a = i * 1.57f + 0.4f
            col = c(255, 100, 20) * 2f
            tube((0..5).map { k -> val v = k / 5f; val r = 0.14f + 0.33f * v; Vec3(cos(a + 0.2f * sin(v * 6f)) * r, sin(a + 0.2f * sin(v * 6f)) * r, 0.78f - 0.74f * v) }, 0.016f, 5, taper = { 1f - 0.6f * it })
        }
        col = c(90, 86, 90)
        for (k in 0 until 5) at(0.04f * sin(k * 2f), 0.04f * cos(k * 1.3f), 0.86f + k * 0.07f) { sphere(0.07f + 0.025f * k, 10, 8) }
    }.build()

    private fun ammonite() = PM().apply {
        col = c(176, 150, 110)
        val path = ArrayList<Vec3>(); for (i in 0..30) { val a = i * 0.28f; val r = 0.04f + 0.012f * i; path.add(Vec3(cos(a) * r, 0f, 0.2f + sin(a) * r)) }
        tube(path, 0.05f, 8, false, null) { 0.5f + 0.9f * it }
        col = c(120, 100, 70); push(); translate(0f, 0f, 0.0f); cylinder(0.2f, 0.06f, 14); pop()
    }.build()

    // ---------------------------------------------------------------- tool shop
    private fun boltProp() = PM().apply {
        col = c(168, 172, 182); cylinder(0.3f, 0.12f, 6); at(z = 0.12f) { cylinder(0.22f, 0.04f, 6, 0.2f) }
        col = c(130, 134, 144); at(z = 0.1f) { cylinder(0.09f, 0.5f, 12) }
        col = c(100, 104, 114); for (k in 0 until 7) at(z = 0.14f + k * 0.06f) { torus(0.09f, 0.012f, 12, 4) }
    }.build()

    /** A hex nut centred on the bolt axis, sitting on z = 0 (it winds up and down the thread). */
    private fun hexNut() = PM().apply {
        col = c(200, 170, 70); cylinder(0.17f, 0.09f, 6)
        col = c(225, 196, 96); at(z = 0.09f) { cylinder(0.15f, 0.012f, 6, 0.13f) }
        col = c(60, 60, 66); at(z = 0.1f) { cylinder(0.092f, 0.004f, 14) }
    }.build()

    private fun toolbox() = PM().apply {
        // steel toolbox with a cantilever tray, latches, handle and a few tools poking out
        val red = c(214, 40, 36)
        col = red; push(); translate(0f, 0f, 0.18f); box(0.78f, 0.4f, 0.36f); pop()
        col = c(180, 30, 28); push(); translate(0f, 0f, 0.38f); box(0.8f, 0.42f, 0.04f); pop()
        col = c(150, 24, 22); for (k in 0 until 3) { push(); translate(0f, -0.201f, 0.08f + k * 0.1f); box(0.7f, 0.004f, 0.012f); pop() }
        col = c(200, 204, 214); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.28f, -0.21f, 0.33f); box(0.07f, 0.02f, 0.08f); pop() }
        tube(listOf(Vec3(-0.2f, 0f, 0.4f), Vec3(-0.18f, 0f, 0.56f), Vec3(0.18f, 0f, 0.56f), Vec3(0.2f, 0f, 0.4f)), 0.022f, 8)
        col = c(30, 30, 34); at(0f, 0f, 0.565f) { push(); rotY((PI / 2).toFloat()); translate(0f, 0f, -0.12f); cylinder(0.03f, 0.24f, 10); pop() }
        col = c(186, 190, 200); push(); translate(0.2f, 0.1f, 0.48f); rotY(0.3f); box(0.04f, 0.02f, 0.22f); pop()
        col = c(250, 214, 40); push(); translate(-0.22f, 0.1f, 0.46f); rotY(-0.4f); box(0.05f, 0.04f, 0.18f); pop()
        col = c(250, 214, 40); push(); translate(0f, -0.203f, 0.22f); box(0.16f, 0.004f, 0.06f); pop()
    }.build()

    private fun wrench() = PM().apply {
        // combination spanner standing on its ring end: tapered shaft, open jaw at the top
        val steel = c(186, 190, 200)
        col = steel
        push(); translate(0f, 0f, 0.48f); box(0.09f, 0.035f, 0.62f); pop()
        at(0f, 0f, 0.12f) { rotX((PI / 2).toFloat()); translate(0f, 0f, -0.02f); torus(0.09f, 0.035f, 18, 6) }
        for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.1f, 0f, 0.9f); rotY(-s * 0.25f); box(0.06f, 0.04f, 0.16f); pop() }
        push(); translate(0f, 0f, 0.8f); box(0.24f, 0.04f, 0.06f); pop()
        col = c(230, 40, 36); push(); translate(0f, 0f, 0.42f); box(0.1f, 0.045f, 0.3f); pop()
        col = c(60, 60, 66); for (k in 0 until 4) at(0f, -0.024f, 0.32f + k * 0.06f) { box(0.08f, 0.004f, 0.012f) }
    }.build()

    private fun hammerProp() = PM().apply {
        // claw hammer standing on its head: wooden handle with grip wraps, forged head with claw
        col = c(70, 74, 86)
        push(); translate(0f, 0f, 0.09f); box(0.14f, 0.16f, 0.18f); pop()
        push(); translate(0.13f, 0f, 0.09f); rotY((PI / 2).toFloat()); cylinder(0.075f, 0.06f, 14); pop()
        for (s in intArrayOf(-1, 1)) { push(); translate(-0.12f, s * 0.035f, 0.12f); rotY(-0.5f); box(0.16f, 0.03f, 0.06f); pop() }
        col = c(176, 120, 64); at(z = 0.18f) { cylinder(0.04f, 0.72f, 12, 0.032f) }
        col = c(30, 30, 34); for (k in 0 until 6) at(z = 0.62f + k * 0.04f) { torus(0.038f, 0.008f, 12, 4) }
    }.build()

    private fun gearProp() = PM().apply {
        // a thick cog with chamfered teeth, spokes and a hub, standing on its edge
        push(); translate(0f, 0f, 0.45f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.06f)
        col = c(176, 182, 194)
        lathe(listOf(floatArrayOf(0.3f, 0f), floatArrayOf(0.36f, 0f), floatArrayOf(0.36f, 0.12f), floatArrayOf(0.3f, 0.12f)), 32, false)
        lathe(listOf(floatArrayOf(0.3f, 0f), floatArrayOf(0.3f, 0.12f)), 32, false)
        for (k in 0 until 14) { push(); rotZ(k * 0.4488f); translate(0.4f, 0f, 0.06f); box(0.1f, 0.09f, 0.12f); pop() }
        for (k in 0 until 5) { push(); rotZ(k * 1.2566f); translate(0.18f, 0f, 0.06f); box(0.24f, 0.05f, 0.08f); pop() }
        col = c(120, 126, 140); cylinder(0.09f, 0.14f, 16)
        col = c(40, 44, 54); cylinder(0.04f, 0.15f, 12)
        pop()
        col = c(60, 64, 74); push(); translate(0f, 0f, 0.03f); box(0.3f, 0.2f, 0.06f); pop(); cylinder(0.03f, 0.45f, 8)
    }.build()

    // ---------------------------------------------------------------- lava
    private fun emberSpire() = PM().apply {
        // a cluster of basalt columns with molten seams
        val rock = c(36, 28, 32)
        val hs = floatArrayOf(0.95f, 0.7f, 0.55f, 0.8f, 0.45f)
        for (i in 0 until 5) {
            val a = i * 1.26f; val r = if (i == 0) 0f else 0.17f
            col = rock; push(); translate(cos(a) * r, sin(a) * r, 0f); rotZ(a); cylinder(0.11f, hs[i], 6, 0.1f)
            col = c(255, 100, 20) * 2.2f; at(z = hs[i]) { cylinder(0.075f, 0.012f, 6) }
            col = c(255, 120, 30) * 1.8f; at(0.1f, 0f, hs[i] * 0.4f) { box(0.012f, 0.02f, hs[i] * 0.8f) }
            pop()
        }
    }.build()

    private fun lavaRocks() = PM().apply {
        // rough boulders split by glowing cracks, with a small pool of lava between them
        col = c(255, 90, 20) * 2.1f; at(z = 0.005f) { cylinder(0.3f, 0.02f, 18) }
        for (i in 0 until 5) {
            val a = i * 1.3f; val r = 0.12f + 0.12f * (i % 2)
            col = c(42, 32, 34)
            push(); translate(cos(a) * r, sin(a) * r, 0.1f + 0.04f * (i % 3)); rotZ(a); rotX(0.3f * (i % 2)); scale(1.2f, 0.9f, 0.8f + 0.2f * (i % 3)); sphere(0.14f, 7, 5); pop()
            col = c(255, 110, 30) * 1.9f
            push(); translate(cos(a) * r, sin(a) * r, 0.12f); rotZ(a + 0.5f); box(0.2f, 0.012f, 0.012f); pop()
        }
    }.build()

    private fun obsidianCrystal() = PM().apply {
        col = c(24, 18, 28); cylinder(0.14f, 0.6f, 6, 0.1f); at(z = 0.6f) { cone(0.1f, 0.3f, 6) }
        col = c(255, 90, 30) * 1.7f; push(); translate(0.1f, 0f, 0.0f); box(0.02f, 0.02f, 0.6f); pop()
        col = c(40, 26, 34); push(); translate(0.22f, 0.1f, 0f); rotZ(0.6f); cylinder(0.08f, 0.38f, 6, 0.05f); pop()
    }.build()

    private fun brazier() = PM().apply {
        col = c(46, 38, 40); cylinder(0.04f, 0.4f, 8); at(z = 0.4f) { cylinder(0.24f, 0.1f, 14, 0.16f) }
        col = c(255, 110, 30) * 1.8f; at(z = 0.48f) { cylinder(0.18f, 0.03f, 14) }          // glowing coals
    }.build()

    // ---------------------------------------------------------------- jelly
    private fun gummyBear() = PM().apply {
        col = c(255, 60, 90) * 1.25f
        at(z = 0.3f) { scale(1f, 0.85f, 1.2f); sphere(0.3f, 20, 12) }
        at(z = 0.66f) { sphere(0.22f, 18, 12) }
        for (s in intArrayOf(-1, 1)) { at(s * 0.16f, 0f, 0.84f) { sphere(0.08f, 12, 8) }; at(s * 0.3f, 0f, 0.42f) { sphere(0.1f, 12, 8) }; at(s * 0.14f, 0f, 0.06f) { sphere(0.1f, 12, 8) } }
        col = c(255, 235, 240); for (s in intArrayOf(-1, 1)) at(s * 0.08f, 0.19f, 0.7f) { sphere(0.035f, 8, 6) }
    }.build()

    private fun pudding() = PM().apply {
        col = c(255, 200, 70) * 1.15f; cylinder(0.32f, 0.5f, 26, 0.2f)
        col = c(120, 56, 30); at(z = 0.5f) { cylinder(0.2f, 0.04f, 24) }
        col = c(235, 40, 70) * 1.3f; at(z = 0.62f) { sphere(0.1f, 14, 10) }
        col = c(250, 250, 250); at(0f, 0f, 0.0f) { cylinder(0.36f, 0.04f, 26) }
    }.build()

    private fun jellyBean() = PM().apply {
        col = c(70, 220, 90) * 1.2f; at(z = 0.22f) { scale(0.7f, 1.2f, 0.7f); sphere(0.26f, 18, 12) }
        col = c(255, 255, 255); at(0.1f, 0.14f, 0.34f) { scale(1f, 1.4f, 0.5f); sphere(0.05f, 8, 6) }
    }.build()

    private fun jellyDrop() = PM().apply {
        col = c(80, 200, 255) * 1.2f; cone(0.28f, 0.55f, 22)
        col = c(255, 255, 255); for (k in 0 until 10) { val a = k * 0.628f; at(0.22f * kotlin.math.cos(a) * (1f - k * 0.05f), 0.22f * kotlin.math.sin(a) * (1f - k * 0.05f), 0.04f + k * 0.045f) { sphere(0.03f, 6, 5) } }
    }.build()

    private fun cherryProp() = PM().apply {
        col = c(220, 30, 60) * 1.25f; at(0f, 0f, 0.22f) { sphere(0.22f, 18, 12) }
        col = c(60, 140, 50); tube(listOf(Vec3(0f, 0f, 0.42f), Vec3(0.06f, 0.02f, 0.62f), Vec3(0.2f, 0f, 0.78f)), 0.018f, 6)
        col = c(255, 230, 235); at(-0.08f, 0.14f, 0.3f) { sphere(0.04f, 8, 6) }
    }.build()

    // ---------------------------------------------------------------- extra props (second quality pass)
    private fun raceCar() = PM().apply {
        // open-wheel race car: tub, sidepods, wings, halo, four fat tyres
        val paint = c(220, 30, 30); val dark = c(30, 30, 34)
        col = paint
        push(); translate(0f, 0f, 0.14f); scale(0.42f, 0.11f, 0.07f); sphere(1f, 18, 10); pop()               // nose and tub
        for (s in intArrayOf(-1, 1)) { push(); translate(-0.08f, s * 0.12f, 0.13f); scale(0.2f, 0.06f, 0.06f); sphere(1f, 12, 8); pop() }
        col = c(250, 250, 250); push(); translate(0.06f, 0f, 0.2f); box(0.3f, 0.05f, 0.015f); pop()
        col = dark; push(); translate(0.42f, 0f, 0.08f); box(0.06f, 0.36f, 0.02f); pop()                         // front wing
        col = paint; push(); translate(-0.4f, 0f, 0.3f); box(0.1f, 0.3f, 0.02f); pop()                            // rear wing
        col = dark; for (s in intArrayOf(-1, 1)) { push(); translate(-0.38f, s * 0.13f, 0.22f); box(0.03f, 0.02f, 0.16f); pop() }
        tube(listOf(Vec3(0.02f, -0.06f, 0.2f), Vec3(-0.04f, 0f, 0.29f), Vec3(0.02f, 0.06f, 0.2f)), 0.012f, 6)       // halo
        col = c(250, 214, 40); at(-0.06f, 0f, 0.22f) { sphere(0.045f, 10, 8) }                                   // helmet
        for (x in floatArrayOf(0.28f, -0.3f)) for (s in intArrayOf(-1, 1)) {
            col = dark; push(); translate(x, s * 0.2f, 0.09f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.05f); cylinder(0.09f, 0.1f, 16); pop()
            col = c(200, 200, 210); push(); translate(x, s * 0.252f, 0.09f); rotX((PI / 2).toFloat()); cylinder(0.04f, 0.004f, 10); pop()
        }
    }.build()

    private fun goalNet() = PM().apply {
        // goal mouth: white posts and crossbar with a net stretched behind
        val post = c(250, 250, 250)
        col = post
        for (s in intArrayOf(-1, 1)) at(0f, s * 0.42f, 0f) { cylinder(0.025f, 0.62f, 8) }
        push(); translate(0f, -0.42f, 0.62f); rotX(-(PI / 2).toFloat()); cylinder(0.025f, 0.84f, 8); pop()
        col = c(220, 224, 230)
        for (k in 0..8) { val y = -0.42f + k * 0.105f; tube(listOf(Vec3(0f, y, 0.62f), Vec3(-0.18f, y, 0.55f), Vec3(-0.3f, y, 0f)), 0.005f, 3) }
        for (k in 0..5) { val z = k * 0.12f; val x = -0.3f + 0.3f * (z / 0.62f).coerceAtMost(1f) * 0.6f; tube(listOf(Vec3(x, -0.42f, z), Vec3(x, 0.42f, z)), 0.005f, 3) }
    }.build()

    private fun microscope() = PM().apply {
        val body = c(236, 238, 244); val dark = c(40, 44, 54)
        col = dark; push(); translate(0f, 0f, 0.03f); box(0.44f, 0.32f, 0.06f); pop()
        col = body; tube(listOf(Vec3(-0.14f, 0f, 0.05f), Vec3(-0.16f, 0f, 0.35f), Vec3(-0.05f, 0f, 0.62f)), 0.05f, 10)       // arm
        col = dark; push(); translate(0.02f, 0f, 0.3f); box(0.24f, 0.22f, 0.025f); pop()                                   // stage
        col = c(120, 200, 255) * 1.3f; push(); translate(0.04f, 0f, 0.318f); box(0.12f, 0.06f, 0.006f); pop()          // slide
        col = body; push(); translate(0.02f, 0f, 0.5f); rotY(-0.45f); cylinder(0.05f, 0.4f, 14); pop()                  // tube
        col = dark; push(); translate(0.02f, 0f, 0.5f); rotY(-0.45f); translate(0f, 0f, 0.38f); cylinder(0.035f, 0.1f, 12); pop()
        col = c(200, 180, 90); for (k in 0 until 3) { push(); translate(0.04f + 0.03f * (k - 1), 0f, 0.42f); rotY(0.4f * (k - 1)); cylinder(0.018f, 0.08f, 8); pop() }
        col = dark; for (s in intArrayOf(-1, 1)) at(-0.15f, s * 0.07f, 0.25f) { rotX((PI / 2).toFloat()); cylinder(0.04f, 0.03f, 12) }
    }.build()

    private fun teslaCoil() = PM().apply {
        col = c(40, 44, 54); cylinder(0.28f, 0.08f, 18)
        col = c(196, 120, 50); at(z = 0.08f) { cylinder(0.08f, 0.6f, 16) }
        col = c(230, 150, 70); for (k in 0 until 18) at(z = 0.1f + k * 0.032f) { torus(0.082f, 0.006f, 16, 3) }
        col = c(210, 214, 226); at(z = 0.76f) { scale(1f, 1f, 0.35f); torus(0.18f, 0.08f, 24, 10) }
    }.build()
    private fun teslaSparks() = PM().apply {
        col = c(180, 140, 255) * 2.4f
        for (k in 0 until 5) { val a = k * 1.26f; tube(listOf(Vec3(cos(a) * 0.2f, sin(a) * 0.2f, 0.78f), Vec3(cos(a + 0.4f) * 0.32f, sin(a + 0.4f) * 0.32f, 0.86f), Vec3(cos(a + 0.2f) * 0.45f, sin(a + 0.2f) * 0.45f, 0.8f)), 0.007f, 3) }
    }.build()

    private fun penguin() = PM().apply {
        val black = c(26, 28, 36); val white = c(244, 246, 250)
        col = black; at(z = 0.34f) { scale(0.8f, 0.7f, 1.1f); sphere(0.3f, 18, 12) }
        at(z = 0.72f) { sphere(0.17f, 16, 12) }
        col = white; at(0.1f, 0f, 0.32f) { scale(0.55f, 0.6f, 1f); sphere(0.28f, 16, 12) }
        at(0.12f, 0f, 0.72f) { scale(0.6f, 0.9f, 0.8f); sphere(0.12f, 12, 8) }
        col = black; for (s in intArrayOf(-1, 1)) at(0.2f, s * 0.05f, 0.76f) { sphere(0.022f, 6, 4) }
        col = c(255, 170, 30); at(0.2f, 0f, 0.7f) { rotY((PI / 2).toFloat()); cone(0.04f, 0.1f, 8) }
        for (s in intArrayOf(-1, 1)) at(0.06f, s * 0.1f, 0.02f) { scale(1.6f, 1f, 0.4f); sphere(0.06f, 8, 6) }
        col = black; for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.24f, 0.4f); rotX(s * 0.3f); scale(0.35f, 0.12f, 1f); sphere(0.2f, 10, 8); pop() }
        col = c(220, 40, 40); at(z = 0.6f) { torus(0.16f, 0.03f, 18, 6) }                                    // scarf
        tube(listOf(Vec3(-0.12f, 0.1f, 0.6f), Vec3(-0.2f, 0.12f, 0.45f)), 0.03f, 6)
    }.build()

    private fun palmTree() = PM().apply {
        col = c(150, 110, 70)
        tube((0..8).map { k -> val v = k / 8f; Vec3(0.12f * sin(v * 2.5f), 0f, v * 0.85f) }, 0.05f, 10, colorFn = { v -> if ((v * 14f).toInt() % 2 == 0) c(150, 110, 70) else c(120, 86, 54) }, taper = { 1f - 0.35f * it })
        col = c(60, 150, 50)
        for (k in 0 until 7) {
            val a = k * 0.9f
            tube((0..5).map { j -> val v = j / 5f; Vec3(0.12f * sin(2.5f) + cos(a) * v * 0.42f, sin(a) * v * 0.42f, 0.85f + 0.12f * v - 0.3f * v * v) }, 0.035f, 5, taper = { 1f - 0.8f * it })
        }
        col = c(120, 80, 40); for (k in 0 until 3) at(0.12f * sin(2.5f) + 0.05f * cos(k * 2.1f), 0.05f * sin(k * 2.1f), 0.8f) { sphere(0.04f, 8, 6) }
    }.build()

    private fun torch() = PM().apply {
        col = c(110, 80, 40); cylinder(0.035f, 0.7f, 10, 0.05f)
        col = c(214, 168, 70); at(z = 0.66f) { cylinder(0.09f, 0.1f, 12, 0.11f) }
        col = c(60, 40, 30); at(z = 0.76f) { cylinder(0.08f, 0.02f, 12) }
        col = c(214, 168, 70); cylinder(0.12f, 0.04f, 12)
    }.build()

    private fun scarecrow() = PM().apply {
        col = c(130, 96, 60); cylinder(0.03f, 0.9f, 8)
        push(); translate(0f, -0.35f, 0.62f); rotX(-(PI / 2).toFloat()); cylinder(0.025f, 0.7f, 8); pop()
        col = c(70, 110, 160); at(z = 0.5f) { scale(0.6f, 1f, 1.1f); sphere(0.16f, 12, 8) }                        // shirt
        col = c(170, 70, 40); for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.22f, 0.62f); rotX(s * (PI / 2).toFloat()); cylinder(0.05f, 0.14f, 8); pop() }
        col = c(230, 200, 140); at(z = 0.8f) { sphere(0.11f, 12, 8) }
        col = c(20, 20, 20); for (s in intArrayOf(-1, 1)) at(0.1f, s * 0.04f, 0.83f) { sphere(0.014f, 6, 4) }
        col = c(150, 100, 50); at(z = 0.88f) { cylinder(0.2f, 0.015f, 18) }; at(z = 0.89f) { cylinder(0.1f, 0.1f, 14, 0.08f) }
        col = c(230, 190, 80); for (k in 0 until 8) { val a = k * 0.8f; tube(listOf(Vec3(0f, 0f, 0.36f), Vec3(cos(a) * 0.08f, sin(a) * 0.08f, 0.28f)), 0.008f, 3) }
    }.build()

    private fun jackInBox() = PM().apply {
        col = c(60, 120, 230); push(); translate(0f, 0f, 0.2f); box(0.4f, 0.4f, 0.4f); pop()
        col = c(250, 214, 40); for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.201f, 0.2f); box(0.3f, 0.005f, 0.3f); pop() }
        col = c(230, 50, 50); push(); translate(0f, 0.22f, 0.42f); rotX(-1.2f); translate(0f, -0.2f, 0f); box(0.42f, 0.4f, 0.02f); pop()   // open lid
        col = c(200, 200, 210); for (k in 0 until 6) at(z = 0.42f + k * 0.05f) { torus(0.06f, 0.012f, 12, 4) }
        col = c(250, 220, 190); at(z = 0.78f) { sphere(0.12f, 14, 10) }
        col = c(230, 40, 40); at(0.11f, 0f, 0.78f) { sphere(0.03f, 8, 6) }
        col = c(120, 60, 200); at(z = 0.88f) { cone(0.1f, 0.18f, 12) }; col = c(250, 214, 40); at(z = 1.06f) { sphere(0.03f, 8, 6) }
    }.build()

    private fun spinningTop() = PM().apply {
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.1f, 0.08f), floatArrayOf(0.34f, 0.3f), floatArrayOf(0.32f, 0.38f), floatArrayOf(0.12f, 0.46f), floatArrayOf(0.04f, 0.5f), floatArrayOf(0.04f, 0.7f), floatArrayOf(0f, 0.72f)), 24) { u, v ->
            if (v > 0.6f) c(250, 214, 40) else if ((u * 8f).toInt() % 2 == 0) c(230, 50, 50) else c(250, 250, 250) }
    }.build()

    private fun drill() = PM().apply {
        // cordless drill standing on its battery
        col = c(30, 30, 34); push(); translate(0f, 0f, 0.07f); box(0.34f, 0.22f, 0.14f); pop()
        col = c(255, 160, 20); push(); translate(-0.02f, 0f, 0.36f); rotY(0.25f); box(0.12f, 0.12f, 0.46f); pop()     // grip
        col = c(255, 160, 20); push(); translate(0.06f, 0f, 0.66f); scale(1.6f, 0.9f, 0.9f); sphere(0.12f, 14, 10); pop()
        col = c(30, 30, 34); at(0.06f, 0f, 0.74f) { cylinder(0.055f, 0.1f, 14, 0.04f) }                                // chuck, pointing up
        col = c(200, 40, 40); push(); translate(0.04f, 0f, 0.48f); box(0.03f, 0.05f, 0.06f); pop()                        // trigger
    }.build()
    private fun drillBit() = PM().apply {
        col = c(190, 194, 204)
        cylinder(0.022f, 0.24f, 8, 0.008f)
        col = c(150, 154, 166); for (k in 0 until 6) at(z = 0.03f + k * 0.032f) { rotX(0.6f); torus(0.024f, 0.006f, 8, 3) }
    }.build()

    private fun lavaPool() = PM().apply {
        col = c(40, 30, 32); lathe(listOf(floatArrayOf(0.2f, 0f), floatArrayOf(0.44f, 0f), floatArrayOf(0.42f, 0.08f), floatArrayOf(0.32f, 0.1f), floatArrayOf(0.3f, 0.06f)), 18, false)
        col = c(255, 100, 20) * 2.3f; at(z = 0.05f) { cylinder(0.31f, 0.01f, 22) }
        col = c(255, 180, 50) * 2.6f; for (k in 0 until 4) at(cos(k * 1.7f) * 0.15f, sin(k * 1.7f) * 0.15f, 0.06f) { scale(1f, 1f, 0.5f); sphere(0.05f, 8, 6) }
    }.build()

    private fun moonRover() = PM().apply {
        col = c(220, 224, 232); push(); translate(0f, 0f, 0.22f); box(0.5f, 0.32f, 0.1f); pop()
        col = c(230, 180, 60); push(); translate(0f, 0f, 0.29f); box(0.46f, 0.28f, 0.03f); pop()
        col = c(60, 64, 76); for (x in floatArrayOf(-0.2f, 0f, 0.2f)) for (s in intArrayOf(-1, 1)) { push(); translate(x, s * 0.2f, 0.09f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.03f); cylinder(0.08f, 0.06f, 14); pop() }
        col = c(200, 204, 214); tube(listOf(Vec3(0.15f, 0f, 0.3f), Vec3(0.18f, 0f, 0.55f)), 0.012f, 6)
        push(); translate(0.18f, 0f, 0.58f); rotY(-0.6f); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.12f, 0.04f), floatArrayOf(0.14f, 0.06f)), 18, true); pop()
        col = c(80, 255, 140) * 1.8f; at(-0.15f, 0.1f, 0.33f) { sphere(0.025f, 6, 4) }
        col = c(40, 80, 160); push(); translate(-0.1f, 0f, 0.35f); rotY(0.2f); box(0.2f, 0.36f, 0.01f); pop()
    }.build()

    private fun mummy() = PM().apply {
        // a sarcophagus standing upright with a gold mask
        col = c(200, 160, 70)
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.18f, 0f), floatArrayOf(0.22f, 0.4f), floatArrayOf(0.2f, 0.7f), floatArrayOf(0.15f, 0.9f), floatArrayOf(0f, 0.95f)), 18) { _, v ->
            if (v in 0.3f..0.65f && ((v * 30f).toInt() % 2 == 0)) c(40, 100, 180) else c(200, 160, 70) }
        col = c(255, 214, 90) * 1.1f; at(0f, -0.15f, 0.78f) { scale(1f, 0.6f, 1.1f); sphere(0.1f, 12, 8) }
        col = c(20, 20, 30); for (s in intArrayOf(-1, 1)) at(s * 0.035f, -0.21f, 0.8f) { sphere(0.014f, 6, 4) }
        col = c(40, 100, 180); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.09f, -0.13f, 0.6f); rotY(s * 0.3f); box(0.06f, 0.04f, 0.26f); pop() }
    }.build()

    // ---------------------------------------------------------------- jungle (rich pass)
    /** Adds a long drooping leaf from (0,0,z0) heading out at angle [a]: a ribbon with a midrib, curving down at the tip. */
    private fun PM.leaf(len: Float, wid: Float, cA: Vec3, cB: Vec3, droop: Float, a: Float, z0: Float, rise: Float = 0.5f) {
        val n = 7
        val ca = cos(a); val sa = sin(a)
        val pts = (0..n).map { k -> val v = k / n.toFloat(); Vec3(ca * len * v, sa * len * v, z0 + rise * len * v - droop * len * v * v) }
        for (k in 0 until n) {
            val v0 = k / n.toFloat(); val v1 = (k + 1) / n.toFloat()
            val w0 = wid * sin(PI.toFloat() * (0.08f + 0.9f * v0)); val w1 = wid * sin(PI.toFloat() * (0.08f + 0.9f * v1))
            val side = Vec3(-sa, ca, 0f)
            val c0 = Vec3.lerp(cA, cB, v0); val c1 = Vec3.lerp(cA, cB, v1)
            flat(pts[k] - side * w0, pts[k + 1] - side * w1, pts[k + 1] + side * w1, pts[k] + side * w0, c0, c1, c1, c0)
        }
        col = cA * 0.7f; tube(pts, wid * 0.08f + 0.004f, 3)
    }

    private fun bananaPlant() = PM().apply {
        col = c(120, 150, 70); tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.01f, 0f, 0.35f), Vec3(0f, 0.01f, 0.55f)), 0.06f, 10, taper = { 1f - 0.4f * it })
        for (i in 0 until 7) leaf(0.55f + 0.1f * (i % 3), 0.11f, c(70, 150, 50), c(130, 200, 70), 1.1f, i * 0.9f, 0.5f + 0.03f * i, 0.6f)
        col = c(250, 214, 60); for (k in 0 until 6) at(0.08f, 0.02f * k - 0.05f, 0.4f - 0.03f * k) { push(); rotY(0.6f); scale(0.35f, 0.35f, 1f); cylinder(0.05f, 0.14f, 8, 0.03f); pop() }
        col = c(140, 40, 80); at(0.1f, 0f, 0.26f) { scale(0.8f, 0.8f, 1.2f); sphere(0.05f, 8, 6) }
    }.build()

    private fun bromeliad() = PM().apply {
        for (i in 0 until 12) leaf(0.32f + 0.05f * (i % 2), 0.05f, c(60, 130, 60), c(200, 60, 80), 0.6f, i * 0.524f, 0.02f, 1.1f)
        col = c(255, 70, 60) * 1.15f; at(z = 0.04f) { cone(0.07f, 0.28f, 10) }
        col = c(255, 200, 40); for (k in 0 until 5) at(cos(k * 1.26f) * 0.04f, sin(k * 1.26f) * 0.04f, 0.18f + 0.02f * k) { sphere(0.015f, 6, 4) }
    }.build()

    private fun bamboo() = PM().apply {
        for (s in 0 until 5) {
            val a = s * 1.26f; val r = if (s == 0) 0f else 0.12f; val h = 0.75f + 0.3f * ((s * 7) % 4) / 3f
            val x = cos(a) * r; val y = sin(a) * r
            col = c(140, 180, 70)
            val segs = 6
            for (k in 0 until segs) {
                val z0 = h * k / segs; val z1 = h * (k + 1) / segs
                col = if (k % 2 == 0) c(140, 180, 70) else c(150, 190, 80)
                at(x + 0.02f * k * cos(a), y + 0.02f * k * sin(a), z0) { cylinder(0.035f, z1 - z0, 10) }
                col = c(110, 140, 50); at(x + 0.02f * (k + 1) * cos(a), y + 0.02f * (k + 1) * sin(a), z1) { torus(0.037f, 0.008f, 10, 3) }
            }
            for (k in 0 until 3) leaf(0.2f, 0.035f, c(80, 160, 60), c(150, 210, 90), 0.6f, a + k * 2.1f, h * (0.55f + 0.15f * k), 0.2f)
        }
    }.build()

    private fun monstera() = PM().apply {
        // big split heart-shaped leaves on long stalks
        for (i in 0 until 5) {
            val a = i * 1.26f + 0.3f; val hgt = 0.35f + 0.1f * (i % 3)
            col = c(70, 130, 50); tube(listOf(Vec3(0f, 0f, 0f), Vec3(cos(a) * 0.1f, sin(a) * 0.1f, hgt * 0.7f), Vec3(cos(a) * 0.2f, sin(a) * 0.2f, hgt)), 0.012f, 5)
            push(); translate(cos(a) * 0.2f, sin(a) * 0.2f, hgt); rotZ(a); rotY(0.5f)
            for (k in 0 until 5) {                           // the leaf, cut into lobes by the gaps between them
                val b = -1.1f + k * 0.55f
                col = Vec3.lerp(c(30, 110, 40), c(70, 160, 60), k / 4f)
                push(); rotZ(b * 0.5f); translate(0.12f, 0f, 0f); scale(1f, 0.32f, 0.04f); sphere(0.13f, 10, 6); pop()
            }
            pop()
        }
    }.build()

    private fun heliconia() = PM().apply {
        col = c(80, 140, 60); cylinder(0.02f, 0.7f, 8)
        for (k in 0 until 7) {
            val z = 0.35f + k * 0.055f; val s = if (k % 2 == 0) 1f else -1f
            col = if (k % 2 == 0) c(240, 50, 40) * 1.1f else c(250, 190, 40) * 1.1f
            push(); translate(s * 0.04f, 0f, z); rotY(s * 1.0f); scale(0.3f, 0.6f, 1f); cone(0.06f, 0.16f, 8); pop()
        }
        for (i in 0 until 3) leaf(0.4f, 0.08f, c(50, 130, 50), c(110, 190, 70), 0.9f, i * 2.1f + 0.4f, 0.15f + 0.08f * i, 0.8f)
    }.build()

    /** A green tree python hanging from a branch: built downward from the origin so it can sway like a pendulum. */
    private fun snake() = PM().apply {
        val body = (0..16).map { k -> val v = k / 16f
            if (v < 0.35f) { val a = v / 0.35f * 4.4f; Vec3(cos(a) * 0.06f, sin(a) * 0.06f, -0.02f - v * 0.1f) }     // coiled round the branch
            else Vec3(0.05f * sin(v * 9f), 0.04f * cos(v * 7f), -0.06f - (v - 0.35f) * 0.75f) }
        tube(body, 0.026f, 8, colorFn = { v -> if (((v * 22f).toInt()) % 3 == 0) c(250, 230, 70) else c(60, 170, 60) }, taper = { if (it < 0.85f) 1f else 1f - (it - 0.85f) * 3f })
        val head = body.last()
        col = c(70, 180, 60); at(head.x, head.y, head.z - 0.03f) { scale(1.4f, 1f, 0.8f); sphere(0.035f, 10, 8) }
        col = c(20, 20, 20); for (s in intArrayOf(-1, 1)) at(head.x + 0.03f, head.y + s * 0.02f, head.z - 0.02f) { sphere(0.008f, 5, 4) }
        col = c(220, 40, 40); tube(listOf(Vec3(head.x + 0.045f, head.y, head.z - 0.04f), Vec3(head.x + 0.085f, head.y, head.z - 0.05f)), 0.003f, 3)
    }.build()

    private fun bigJungleTree() = PM().apply {
        // buttress roots, a leaning banded trunk, branches with layered leafy canopy and hanging vines
        val bark = c(96, 72, 48)
        for (i in 0 until 5) { val a = i * 1.26f; col = bark * 0.9f; tube(listOf(Vec3(cos(a) * 0.22f, sin(a) * 0.22f, 0f), Vec3(cos(a) * 0.1f, sin(a) * 0.1f, 0.12f), Vec3(cos(a) * 0.04f, sin(a) * 0.04f, 0.26f)), 0.03f, 6, taper = { 1f - 0.5f * it }) }
        tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.04f, 0f, 0.25f), Vec3(-0.02f, 0.02f, 0.5f), Vec3(0.02f, 0f, 0.72f)), 0.075f, 12,
            colorFn = { v -> if (((v * 30f).toInt()) % 4 == 0) bark * 0.75f else bark }, taper = { 1f - 0.45f * it })
        for (b in 0 until 3) { val a = b * 2.1f + 0.4f; col = bark; tube(listOf(Vec3(0f, 0f, 0.62f), Vec3(cos(a) * 0.14f, sin(a) * 0.14f, 0.72f), Vec3(cos(a) * 0.26f, sin(a) * 0.26f, 0.76f)), 0.025f, 6, taper = { 1f - 0.5f * it }) }
        for (i in 0 until 9) {
            val a = i * 0.7f; val r = 0.08f + 0.2f * ((i * 5) % 7) / 6f; val z = 0.78f + 0.05f * (i % 4)
            col = Vec3.lerp(c(30, 100, 40), c(100, 180, 60), ((i * 3) % 5) / 4f)
            at(cos(a) * r, sin(a) * r, z) { push(); scale(1f, 1f, 0.45f); sphere(0.17f, 12, 8); pop() }
        }
        col = c(50, 130, 50)
        for (i in 0 until 6) { val a = i * 1.05f + 0.2f; val r = 0.24f
            tube((0..5).map { k -> val v = k / 5f; Vec3(cos(a) * r + 0.02f * sin(v * 9f), sin(a) * r, 0.78f - 0.5f * v) }, 0.007f, 3)
            col = c(80, 170, 60); for (k in 1..4) at(cos(a) * r, sin(a) * r, 0.78f - 0.12f * k) { scale(1f, 0.5f, 0.2f); sphere(0.022f, 6, 4) }
            col = c(50, 130, 50) }
        col = c(250, 70, 140); for (k in 0 until 4) at(cos(k * 1.6f) * 0.12f, sin(k * 1.6f) * 0.12f, 0.98f) { sphere(0.02f, 6, 4) }
    }.build()

    private fun toucan(): Bird {
        val body = PM().apply {
            col = c(24, 24, 28); at { scale(1f, 0.6f, 0.6f); sphere(0.12f, 12, 8) }
            col = c(250, 240, 200); at(0.11f, 0f, 0.0f) { scale(0.6f, 0.7f, 0.7f); sphere(0.07f, 10, 8) }
            col = c(24, 24, 28); at(0.13f, 0f, 0.04f) { sphere(0.055f, 10, 8) }
            col = c(255, 150, 20); push(); translate(0.17f, 0f, 0.04f); rotY((PI / 2).toFloat() + 0.15f); scale(0.7f, 0.9f, 1f); cone(0.045f, 0.2f, 10); pop()
            col = c(230, 40, 30); at(0.35f, 0f, 0.02f) { sphere(0.012f, 6, 4) }
            col = c(24, 24, 28); push(); translate(-0.15f, 0f, 0f); rotY((PI / 2).toFloat() + 0.25f); scale(0.5f, 1f, 1f); cylinder(0.05f, 0.16f, 8, 0.02f); pop()
        }.build()
        val (l, r) = wingPair(0.28f, 0.15f, c(24, 24, 28), c(30, 30, 36), 0.5f)
        return Bird(body, l, r, 4, 0.6f, 2.0f, 7f, glide = true)
    }

    // ---------------------------------------------------------------- stadium
    /** Concrete stepped grandstand facing +y, with a roof on posts and advertising boards. The crowd is separate (animated rows). */
    private fun grandstand(seatA: Vec3, seatB: Vec3, board: Vec3) = PM().apply {
        val conc = c(170, 172, 180)
        for (r in 0 until 4) {                           // four tiers, each a step up and back
            val top = 0.1f + 0.16f * r
            col = conc; push(); translate(0f, 0.18f - r * 0.13f, top * 0.5f); box(1.0f, 0.13f, top); pop()
            col = if (r % 2 == 0) seatA else seatB
            for (k in 0 until 9) { push(); translate(-0.44f + k * 0.11f, 0.18f - r * 0.13f + 0.03f, top + 0.015f); box(0.09f, 0.05f, 0.03f); pop() }
        }
        col = conc * 0.8f; push(); translate(0f, -0.25f, 0.3f); box(1.0f, 0.06f, 0.6f); pop()                    // back wall
        col = c(80, 84, 94); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.49f, -0.25f, 0.42f); box(0.03f, 0.03f, 0.84f); pop() }
        col = c(230, 232, 238); push(); translate(0f, -0.06f, 0.86f); rotX(-0.18f); box(1.08f, 0.5f, 0.025f); pop()        // roof
        col = board; push(); translate(0f, 0.33f, 0.05f); box(1.0f, 0.02f, 0.1f); pop()
        col = c(250, 250, 250); for (k in 0 until 4) { push(); translate(-0.36f + k * 0.24f, 0.341f, 0.05f); box(0.12f, 0.004f, 0.04f); pop() }
    }.build()

    /** One row of spectators (heads and shoulders in mixed shirt colours), to sit on a tier; animated to cheer and do the wave. */
    private fun crowdRow() = PM().apply {
        val shirts = listOf(c(220, 40, 40), c(40, 90, 220), c(250, 214, 40), c(250, 250, 250), c(40, 160, 70), c(240, 120, 30))
        val skins = listOf(c(240, 200, 160), c(200, 150, 110), c(150, 100, 70), c(110, 70, 50))
        for (k in 0 until 9) {
            val x = -0.44f + k * 0.11f
            col = shirts[(k * 5 + 3) % shirts.size]; at(x, 0f, 0.04f) { scale(1f, 0.7f, 1.1f); sphere(0.04f, 8, 6) }
            col = skins[(k * 3) % skins.size]; at(x, 0f, 0.1f) { sphere(0.026f, 8, 6) }
            if (k % 3 == 0) { col = shirts[(k + 2) % shirts.size]; tube(listOf(Vec3(x + 0.03f, 0f, 0.07f), Vec3(x + 0.05f, 0f, 0.14f)), 0.008f, 4) }      // a raised arm / scarf
        }
    }.build()
    private fun crowdSpec(host: PropMesh) = part(host, crowdRow(), Anim.CROWD, (0 until 4).map { r -> Vec3(0f, 0.18f - r * 0.13f - 0.01f, 0.13f + 0.16f * r) }, 1f, 1f)

    // ---------------------------------------------------------------- sweets
    private fun fruitPastilles() = PM().apply {
        // a little stack of sugar-dusted fruit gums in different flavours
        val cols = listOf(c(230, 40, 50), c(250, 160, 30), c(130, 200, 50), c(250, 220, 60), c(150, 60, 170))
        for (i in 0 until 6) {
            val a = i * 1.1f; val r = if (i < 3) 0.12f else 0.06f; val z = if (i < 3) 0f else 0.11f + 0.11f * (i - 3) * 0.5f
            col = cols[i % cols.size] * 1.1f
            push(); translate(cos(a) * r, sin(a) * r, z); rotX(0.15f * (i % 2)); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.13f, 0.005f), floatArrayOf(0.15f, 0.05f), floatArrayOf(0.13f, 0.1f), floatArrayOf(0f, 0.105f)), 18) { u, v -> cols[i % cols.size] * (if (((u * 41f + v * 17f) * 13f % 1f) > 0.85f) 1.5f else 1.1f) }
            pop()
        }
    }.build()

    private fun wrappedSweet() = PM().apply {
        // a boiled sweet in a twisted foil wrapper
        col = c(250, 60, 120); push(); translate(0f, 0f, 0.16f); rotY((PI / 2).toFloat()); scale(1f, 1f, 1.4f); sphere(0.15f, 16, 10); pop()
        col = c(255, 255, 255); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.2f, 0f, 0.16f); rotY(s * (PI / 2).toFloat()); cone(0.04f, 0.04f, 10); pop()
            push(); translate(s * 0.24f, 0f, 0.16f); rotY(-s * (PI / 2).toFloat()); lathe(listOf(floatArrayOf(0.01f, 0f), floatArrayOf(0.14f, 0.12f), floatArrayOf(0.12f, 0.13f)), 10, false) { u, _ -> if ((u * 10f).toInt() % 2 == 0) c(255, 220, 240) else c(250, 60, 120) }; pop() }
        col = c(255, 230, 245); for (k in 0 until 4) at(0f, -0.13f, 0.12f + k * 0.03f) { box(0.18f, 0.006f, 0.008f) }
    }.build()

    private fun liquoriceWheel() = PM().apply {
        // a liquorice wheel with a pink sweet in the middle, standing on its edge
        push(); translate(0f, 0f, 0.3f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.06f)
        col = c(20, 18, 22); for (k in 0 until 8) torus(0.05f + k * 0.03f, 0.016f, 24, 5)
        col = c(255, 120, 180) * 1.1f; cylinder(0.05f, 0.12f, 14)
        pop()
        col = c(20, 18, 22); push(); translate(0f, 0f, 0.02f); box(0.2f, 0.14f, 0.04f); pop()
    }.build()

    private fun candyCorn() = PM().apply {
        for (i in 0 until 3) { val a = i * 2.1f
            push(); translate(cos(a) * 0.12f, sin(a) * 0.12f, 0f); rotZ(a)
            lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.12f, 0.0f), floatArrayOf(0.1f, 0.15f), floatArrayOf(0.05f, 0.32f), floatArrayOf(0f, 0.36f)), 10) { _, v -> if (v < 0.35f) c(255, 220, 60) else if (v < 0.7f) c(255, 140, 30) else c(255, 255, 250) }
            pop() }
    }.build()

    // ---------------------------------------------------------------- motorsport sponsor boards and more dinosaurs
    /** An advertising board standing at the foot of a wall (facing +x), in sponsor colours with a block-letter logo. */
    fun sponsorBoard(k: Int) = PM().apply {
        val bgs = listOf(c(220, 30, 30), c(30, 70, 200), c(250, 210, 30), c(20, 20, 24), c(30, 150, 70))
        val fgs = listOf(c(250, 250, 250), c(250, 250, 250), c(20, 20, 24), c(250, 200, 30), c(250, 250, 250))
        val i = k % bgs.size
        col = c(60, 64, 72); for (s in intArrayOf(-1, 1)) at(-0.02f, s * 0.4f, 0f) { box(0.03f, 0.03f, 0.3f) }
        col = bgs[i]; at(0f, 0f, 0.17f) { box(0.03f, 0.9f, 0.24f) }
        col = fgs[i]
        val pattern = (k * 7919 + 13) % 997
        for (j in 0 until 6) {
            val tall = ((pattern shr j) and 1) == 1
            at(0.017f, -0.3f + j * 0.12f, 0.17f) { box(0.004f, 0.08f, if (tall) 0.14f else 0.08f) }
        }
        col = fgs[i] * 0.9f; at(0.017f, 0f, 0.07f) { box(0.004f, 0.8f, 0.015f) }
    }.build()

    private fun raptor() = PM().apply {
        // a small two-legged raptor with a long tail, sickle claws and a crest of feathers
        val skin = c(170, 120, 70); val stripe = c(110, 70, 40)
        col = skin; at(0f, 0f, 0.42f) { scale(1.2f, 0.55f, 0.6f); sphere(0.18f, 14, 10) }
        tube(listOf(Vec3(0.16f, 0f, 0.48f), Vec3(0.26f, 0f, 0.62f), Vec3(0.3f, 0f, 0.7f)), 0.05f, 8, taper = { 1f - 0.3f * it })
        at(0.38f, 0f, 0.72f) { scale(1.6f, 0.7f, 0.7f); sphere(0.07f, 12, 8) }
        col = c(240, 236, 220); for (k in 0 until 4) at(0.44f + 0.015f * k, 0f, 0.68f) { cone(0.008f, 0.02f, 4) }
        col = skin; tube(listOf(Vec3(-0.18f, 0f, 0.42f), Vec3(-0.4f, 0f, 0.48f), Vec3(-0.6f, 0f, 0.5f)), 0.06f, 8, taper = { 1f - 0.9f * it })
        for (s in intArrayOf(-1, 1)) {
            tube(listOf(Vec3(0f, s * 0.07f, 0.36f), Vec3(0.06f, s * 0.08f, 0.2f), Vec3(-0.02f, s * 0.08f, 0.08f), Vec3(0.06f, s * 0.08f, 0.0f)), 0.03f, 6, taper = { 1f - 0.4f * it })
            tube(listOf(Vec3(0.18f, s * 0.06f, 0.46f), Vec3(0.24f, s * 0.08f, 0.38f)), 0.015f, 5)
            col = c(30, 26, 22); at(0.42f, s * 0.04f, 0.75f) { sphere(0.012f, 6, 4) }; col = skin
        }
        col = stripe; for (k in 0 until 5) at(-0.1f + k * 0.06f, 0f, 0.5f) { box(0.02f, 0.2f, 0.01f) }
        col = c(200, 70, 40); for (k in 0 until 5) at(0.1f - k * 0.08f, 0f, 0.54f) { rotY(-0.5f); cone(0.02f, 0.08f, 4) }
    }.build()

    private fun dinoSkeleton() = PM().apply {
        // a museum-style T. rex skeleton on a plinth: skull with jaw, teeth and eye holes; vertebrae shrinking down the tail;
        // curved ribs, pelvis, three-part legs with clawed feet, tiny arms and support rods
        val bone = c(236, 226, 200); val old = c(206, 190, 160)
        col = c(70, 66, 62); at(z = 0.02f) { box(1.05f, 0.42f, 0.04f) }
        col = c(150, 140, 120); at(z = 0.045f) { box(1.0f, 0.37f, 0.01f) }
        val spine = (0..22).map { k -> val v = k / 22f
            Vec3(-0.5f + 0.92f * v, 0f, 0.38f + 0.26f * kotlin.math.sin(v * 2.6f + 0.3f) - (if (v < 0.4f) (0.4f - v) * 0.5f else 0f)) }
        for ((k, p) in spine.withIndex()) { val r = 0.016f + 0.028f * (k / 22f).let { if (it < 0.75f) it else 0.75f }
            col = if (k % 2 == 0) bone else old; at(p.x, p.y, p.z) { scale(0.8f, 1f, 1.2f); sphere(r, 8, 6) }
            if (k in 8..18) { col = bone; at(p.x, 0f, p.z + r * 2f) { box(0.012f, 0.01f, r * 1.8f) } } }
        for (k in 9..15) { val p = spine[k]; val drop = 0.16f + 0.05f * (k - 9) * (15 - k) / 9f
            for (s in intArrayOf(-1, 1)) { col = bone; tube((0..5).map { j -> val a = j / 5f * 2.6f; Vec3(p.x - 0.01f * j, s * 0.13f * kotlin.math.sin(a), p.z - drop * (1f - kotlin.math.cos(a)) * 0.6f) }, 0.009f, 4) } }
        val pel = spine[7]
        col = bone; at(pel.x, 0f, pel.z - 0.04f) { scale(1.4f, 1f, 0.8f); sphere(0.06f, 10, 8) }
        for (s in intArrayOf(-1, 1)) {
            tube(listOf(Vec3(pel.x, s * 0.07f, pel.z - 0.04f), Vec3(pel.x + 0.06f, s * 0.08f, 0.32f), Vec3(pel.x - 0.02f, s * 0.08f, 0.17f), Vec3(pel.x + 0.03f, s * 0.08f, 0.07f)), 0.022f, 6, taper = { 1f - 0.35f * it })
            at(pel.x + 0.06f, s * 0.08f, 0.32f) { sphere(0.026f, 8, 6) }; at(pel.x - 0.02f, s * 0.08f, 0.17f) { sphere(0.02f, 8, 6) }
            for (t in -1..1) tube(listOf(Vec3(pel.x + 0.03f, s * 0.08f, 0.07f), Vec3(pel.x + 0.1f, s * 0.08f + t * 0.025f, 0.055f)), 0.007f, 3)
            val ch = spine[17]
            tube(listOf(Vec3(ch.x, s * 0.08f, ch.z - 0.1f), Vec3(ch.x + 0.05f, s * 0.09f, ch.z - 0.17f), Vec3(ch.x + 0.08f, s * 0.08f, ch.z - 0.15f)), 0.008f, 3)
        }
        col = c(60, 60, 66); at(pel.x, 0f, 0.05f) { cylinder(0.008f, pel.z - 0.1f, 6) }; at(spine[16].x, 0f, 0.05f) { cylinder(0.008f, spine[16].z - 0.1f, 6) }
        // skull
        val hd = spine.last()
        col = bone
        push(); translate(hd.x + 0.1f, 0f, hd.z + 0.01f)
        at(0f, 0f, 0.02f) { scale(1.7f, 0.75f, 0.85f); sphere(0.075f, 14, 10) }
        at(0.05f, 0f, -0.05f) { scale(1.8f, 0.6f, 0.35f); sphere(0.06f, 12, 8) }
        col = c(40, 34, 28); for (s in intArrayOf(-1, 1)) { at(-0.03f, s * 0.055f, 0.04f) { sphere(0.022f, 8, 6) }; at(0.06f, s * 0.04f, 0.02f) { scale(1.4f, 0.6f, 1f); sphere(0.018f, 6, 4) } }
        col = c(250, 246, 230); for (k in 0 until 7) { at(0.0f + k * 0.022f, -0.04f, -0.02f) { rotY(PI.toFloat()); cone(0.006f, 0.028f, 4) }; at(0.0f + k * 0.022f, 0.04f, -0.02f) { rotY(PI.toFloat()); cone(0.006f, 0.028f, 4) } }
        pop()
    }.build()

    private fun bigEggs() = PM().apply {
        col = c(130, 110, 80); at(z = 0.02f) { scale(1f, 1f, 0.25f); sphere(0.3f, 14, 8) }
        for (i in 0 until 3) { val a = i * 2.1f
            push(); translate(cos(a) * 0.13f, sin(a) * 0.13f, 0.18f); rotY(0.25f * (i - 1)); scale(0.75f, 0.75f, 1.15f)
            sphere(0.16f, 16, 12) { u, v -> if ((((u * 47f + v * 31f) * 9f) % 1f) > 0.88f) c(120, 140, 90) else c(214, 226, 190) }
            pop() }
        col = c(214, 226, 190); for (k in 0 until 4) at(0.25f + 0.04f * k, -0.1f, 0.02f) { rotZ(k * 0.9f); scale(1f, 0.6f, 0.3f); sphere(0.04f, 6, 4) }    // a hatched shell
    }.build()

    // ---------------------------------------------------------------- medieval
    private fun castle() = PM().apply {
        // a keep with four round corner towers, conical roofs, crenellations, a gatehouse and pennants
        val stone = c(160, 154, 142); val roof = c(60, 70, 120)
        col = stone; push(); translate(0f, 0f, 0.25f); box(0.56f, 0.56f, 0.5f); pop()
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            col = stone; at(sx * 0.3f, sy * 0.3f, 0f) { cylinder(0.11f, 0.68f, 16) }
            for (m in 0 until 6) { val a = m * 1.047f; at(sx * 0.3f + cos(a) * 0.1f, sy * 0.3f + sin(a) * 0.1f, 0.71f) { box(0.05f, 0.05f, 0.06f) } }
            col = roof; at(sx * 0.3f, sy * 0.3f, 0.74f) { cone(0.13f, 0.24f, 16) }
            col = c(220, 40, 40); tube(listOf(Vec3(sx * 0.3f, sy * 0.3f, 0.98f), Vec3(sx * 0.3f, sy * 0.3f, 1.06f)), 0.006f, 3)
            col = if ((sx + sy) == 0) c(250, 214, 40) else c(220, 40, 40); flat(Vec3(sx * 0.3f, sy * 0.3f, 1.06f), Vec3(sx * 0.3f + 0.08f, sy * 0.3f, 1.04f), Vec3(sx * 0.3f + 0.08f, sy * 0.3f, 1.04f), Vec3(sx * 0.3f, sy * 0.3f, 1.01f))
        }
        col = stone; for (k in 0 until 7) for (s in intArrayOf(-1, 1)) { at(-0.24f + k * 0.08f, s * 0.28f, 0.53f) { box(0.045f, 0.03f, 0.06f) }; at(s * 0.28f, -0.24f + k * 0.08f, 0.53f) { box(0.03f, 0.045f, 0.06f) } }
        col = c(40, 30, 24); push(); translate(0f, -0.285f, 0.12f); box(0.14f, 0.02f, 0.22f); pop()                  // gate
        col = c(80, 80, 86); for (k in 0 until 4) { push(); translate(-0.05f + k * 0.033f, -0.29f, 0.12f); box(0.008f, 0.01f, 0.22f); pop() }   // portcullis
        col = c(255, 200, 100) * 1.4f; for (sx in intArrayOf(-1, 1)) at(sx * 0.15f, -0.285f, 0.36f) { box(0.03f, 0.01f, 0.06f) }               // lit windows
        col = c(110, 106, 98); push(); translate(0f, 0f, 0.5f); box(0.3f, 0.3f, 0.3f); pop()
        col = roof; at(z = 0.65f) { cone(0.22f, 0.3f, 4) }
    }.build()

    private fun swordInStone() = PM().apply {
        col = c(120, 116, 108); at(z = 0.1f) { scale(1.2f, 1f, 0.6f); sphere(0.26f, 12, 8) }
        col = c(200, 206, 216); push(); translate(0f, 0f, 0.2f); box(0.05f, 0.012f, 0.5f); pop()                       // blade
        col = c(160, 166, 176); push(); translate(0f, 0f, 0.2f); box(0.01f, 0.016f, 0.5f); pop()                      // fuller
        col = c(214, 168, 70); push(); translate(0f, 0f, 0.47f); box(0.24f, 0.03f, 0.03f); pop()                      // crossguard
        col = c(90, 50, 30); at(z = 0.48f) { cylinder(0.018f, 0.14f, 8) }
        col = c(214, 168, 70); at(z = 0.64f) { sphere(0.032f, 10, 8) }
        col = c(255, 230, 140) * 1.3f; at(z = 0.64f) { sphere(0.012f, 6, 4) }                                          // a jewel glinting in the pommel
    }.build()

    private fun shieldStand() = PM().apply {
        // a heater shield with a heraldic lion-rampant-style device, on a wooden stand, and crossed spears behind
        col = c(110, 74, 44); tube(listOf(Vec3(0f, 0.05f, 0f), Vec3(0f, 0.08f, 0.55f)), 0.02f, 6)
        for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(s * 0.25f, 0.1f, 0f), Vec3(-s * 0.22f, 0.1f, 0.9f)), 0.012f, 5)
        col = c(200, 204, 214); for (s in intArrayOf(-1, 1)) at(-s * 0.22f, 0.1f, 0.9f) { rotY(s * 0.24f); cone(0.025f, 0.08f, 6) }
        push(); translate(0f, 0f, 0.48f); rotX((PI / 2).toFloat())
        val outline = listOf(floatArrayOf(-0.22f, 0.22f), floatArrayOf(0.22f, 0.22f), floatArrayOf(0.22f, -0.02f), floatArrayOf(0.12f, -0.2f), floatArrayOf(0f, -0.28f), floatArrayOf(-0.12f, -0.2f), floatArrayOf(-0.22f, -0.02f))
        col = c(214, 168, 70); prism(outline.map { floatArrayOf(it[0] * 1.06f, it[1] * 1.06f + 0.005f) }, 0.03f)
        col = c(30, 60, 160); push(); translate(0f, 0f, 0.0f); prism(outline.map { floatArrayOf(minOf(it[0], 0f), it[1]) }, 0.04f); pop()
        col = c(170, 30, 30); push(); prism(outline.map { floatArrayOf(maxOf(it[0], 0f), it[1]) }, 0.04f); pop()
        col = c(250, 214, 40)
        at(0f, 0.02f, 0.045f) { scale(1f, 1.3f, 0.3f); sphere(0.07f, 10, 8) }
        at(0.02f, 0.11f, 0.045f) { scale(1f, 1f, 0.3f); sphere(0.045f, 8, 6) }
        for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(s * 0.03f, -0.05f, 0.045f), Vec3(s * 0.06f, -0.14f, 0.045f)), 0.014f, 4)
        pop()
    }.build()

    private fun cage() = PM().apply {
        // an iron gibbet cage on a post, with a skull inside
        col = c(80, 64, 44); cylinder(0.035f, 0.95f, 8)
        tube(listOf(Vec3(0f, 0f, 0.92f), Vec3(0.24f, 0f, 0.95f), Vec3(0.28f, 0f, 0.9f)), 0.02f, 6)
        col = c(70, 72, 78)
        tube(listOf(Vec3(0.28f, 0f, 0.9f), Vec3(0.28f, 0f, 0.78f)), 0.006f, 3)
        push(); translate(0.28f, 0f, 0.38f)
        for (k in 0 until 10) { val a = k * 0.628f; tube(listOf(Vec3(cos(a) * 0.09f, sin(a) * 0.09f, 0.38f), Vec3(cos(a) * 0.15f, sin(a) * 0.15f, 0.2f), Vec3(cos(a) * 0.12f, sin(a) * 0.12f, 0f)), 0.006f, 3) }
        at(z = 0f) { torus(0.12f, 0.008f, 16, 3) }; at(z = 0.2f) { torus(0.15f, 0.008f, 16, 3) }; at(z = 0.38f) { torus(0.09f, 0.008f, 16, 3) }
        col = c(236, 228, 210); at(z = 0.06f) { sphere(0.05f, 10, 8) }
        col = c(30, 26, 22); for (s in intArrayOf(-1, 1)) at(0.04f, s * 0.018f, 0.07f) { sphere(0.012f, 5, 4) }
        pop()
    }.build()

    private fun campfire() = PM().apply {
        // a ring of stones, crossed logs and glowing embers (the flames are a flickering part with real light)
        for (k in 0 until 10) { val a = k * 0.628f; col = c(110, 106, 100) * (0.85f + 0.2f * (k % 3) / 2f); at(cos(a) * 0.3f, sin(a) * 0.3f, 0.04f) { scale(1.2f, 1f, 0.8f); sphere(0.07f, 8, 6) } }
        col = c(100, 66, 40)
        for (k in 0 until 4) { val a = k * 1.571f + 0.4f; tube(listOf(Vec3(cos(a) * 0.24f, sin(a) * 0.24f, 0.03f), Vec3(cos(a) * 0.02f, sin(a) * 0.02f, 0.2f)), 0.035f, 7) }
        col = c(255, 110, 30) * 1.9f; at(z = 0.03f) { scale(1f, 1f, 0.3f); sphere(0.16f, 12, 8) }
        col = c(60, 50, 44); at(-0.42f, 0.1f, 0.04f) { rotZ(0.4f); scale(1f, 0.5f, 0.5f); sphere(0.1f, 8, 6) }        // a log seat
    }.build()

    private fun banner() = PM().apply {
        col = c(80, 64, 44); cylinder(0.02f, 0.95f, 8)
        col = c(214, 168, 70); at(z = 0.95f) { sphere(0.03f, 8, 6) }
        push(); translate(0f, 0f, 0.86f); rotY((PI / 2).toFloat()); cylinder(0.008f, 0.3f, 4); pop()
        for (k in 0 until 6) { val z0 = 0.86f - k * 0.08f; val z1 = z0 - 0.08f
            col = if (k == 2) c(250, 214, 40) else c(150, 26, 30)
            flat(Vec3(0.01f, 0f, z0), Vec3(0.3f, 0.02f * kotlin.math.sin(k * 1f), z0), Vec3(0.3f, 0.02f * kotlin.math.sin(k + 1f), z1), Vec3(0.01f, 0f, z1)) }
        col = c(150, 26, 30); flat(Vec3(0.01f, 0f, 0.38f), Vec3(0.3f, 0f, 0.38f), Vec3(0.155f, 0f, 0.3f), Vec3(0.155f, 0f, 0.3f))
    }.build()

    private fun dragon(): Bird {
        // a red dragon: long body and tail, horned head, bat-like wings with fingers and membrane
        val body = PM().apply {
            col = c(170, 30, 26)
            tube((0..10).map { k -> val v = k / 10f; Vec3(0.3f - 0.9f * v, 0f, 0.04f * kotlin.math.sin(v * 6f)) }, 0.07f, 10, taper = { v -> if (v < 0.2f) 0.8f + v else 1.2f - 1.1f * v })
            tube(listOf(Vec3(0.3f, 0f, 0f), Vec3(0.4f, 0f, 0.06f), Vec3(0.46f, 0f, 0.08f)), 0.045f, 8)
            at(0.5f, 0f, 0.08f) { scale(1.5f, 0.8f, 0.7f); sphere(0.05f, 10, 8) }
            col = c(240, 220, 160); for (s in intArrayOf(-1, 1)) at(0.48f, s * 0.03f, 0.13f) { rotY(-0.9f); cone(0.012f, 0.08f, 5) }
            col = c(255, 200, 40) * 1.6f; for (s in intArrayOf(-1, 1)) at(0.53f, s * 0.03f, 0.1f) { sphere(0.01f, 5, 4) }
            col = c(120, 20, 18); at(-0.62f, 0f, 0f) { scale(1f, 1f, 0.3f); cone(0.06f, 0.1f, 4) }
            col = c(240, 200, 120); for (k in 0 until 5) at(0.2f - k * 0.12f, 0f, 0.075f) { cone(0.02f, 0.05f, 4) }
        }.build()
        fun wing(sgn: Float) = PM().apply {
            col = c(110, 20, 20)
            for (f in 0 until 4) tube(listOf(Vec3(0.02f, 0f, 0f), Vec3(0.05f - f * 0.08f, sgn * 0.45f, 0.02f)), 0.01f, 4)
            col = c(200, 60, 40)
            for (f in 0 until 3) flat(Vec3(0.02f, 0f, 0f), Vec3(0.05f - f * 0.08f, sgn * 0.45f, 0.02f), Vec3(0.05f - (f + 1) * 0.08f, sgn * 0.45f, 0.02f), Vec3(-0.1f, sgn * 0.05f, 0f))
        }.build()
        return Bird(body, wing(1f), wing(-1f), 2, 1.5f, 4.5f, 2.6f, glide = true)
    }

    // ---------------------------------------------------------------- racing bridges over the track (span along y, ±0.56 from the centre line)
    /** A tyre-maker's arch: a black tyre-tread arch with a white brand, on yellow footings. */
    fun tyreArch() = PM().apply {
        col = c(250, 200, 30); for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.56f, 0.06f); box(0.22f, 0.14f, 0.12f); pop() }
        push(); rotY((PI / 2).toFloat())                         // the ring stands up across the track; its lower half is under the floor
        torus(0.56f, 0.075f, 40, 10) { u, v ->
            val a = u * 2f * PI.toFloat()
            val onTop = cos(a) < 0f
            val face = cos(v * 2f * PI.toFloat())
            if (!onTop) c(20, 20, 22)
            else if (face < -0.2f || face > 0.95f) c(24, 24, 26) * (if (((u * 160f).toInt() % 2) == 0) 0.8f else 1.1f)
            else if (((u * 38f).toInt() % 3) != 0 && u in 0.36f..0.64f) c(245, 245, 245) else c(24, 24, 26)
        }
        pop()
        col = c(250, 200, 30); for (s in floatArrayOf(-1f, 1f)) { push(); translate(s * 0.08f, 0f, 0f); rotY((PI / 2).toFloat()); torus(0.56f, 0.025f, 40, 5); pop() }
    }.build()

    /** An energy-drink style arch: navy legs and curved deck, red-and-gold emblem in the middle, brand panels on the legs. */
    fun brandArch() = PM().apply {
        val navy = c(18, 26, 70)
        col = c(30, 30, 34); for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.56f, 0.03f); box(0.24f, 0.16f, 0.06f); pop() }
        for (s in intArrayOf(-1, 1)) {
            col = navy; tube(listOf(Vec3(0f, s * 0.56f, 0.05f), Vec3(0f, s * 0.56f, 0.45f), Vec3(0f, s * 0.48f, 0.72f), Vec3(0f, s * 0.3f, 0.86f)), 0.05f, 6)
            col = c(220, 30, 40); for (k in 0 until 4) { push(); translate(-0.051f, s * 0.56f, 0.18f + k * 0.07f); box(0.006f, 0.07f, 0.04f); pop() }
        }
        col = navy; push(); translate(0f, 0f, 0.88f); box(0.1f, 0.66f, 0.2f); pop()
        col = c(255, 214, 40) * 1.1f; push(); translate(-0.052f, 0f, 0.88f); rotY((PI / 2).toFloat()); cylinder(0.075f, 0.004f, 20); pop()
        col = c(220, 30, 40); for (s in intArrayOf(-1, 1)) { push(); translate(-0.056f, s * 0.12f, 0.87f); scale(0.2f, 1.6f, 0.9f); sphere(0.05f, 10, 6); pop()
            push(); translate(-0.056f, s * 0.05f, 0.92f); rotX(s * 0.8f); cone(0.012f, 0.06f, 5); pop() }
        col = c(250, 250, 250); for (s in intArrayOf(-1, 1)) for (k in 0 until 3) { push(); translate(-0.052f, s * (0.22f + k * 0.035f), 0.88f); box(0.004f, 0.025f, 0.05f); pop() }
    }.build()

    /** Start gantry: a lattice tower, an overhead beam with a black board and a grid of start lights (they glow and light the track). */
    fun startGantry() = PM().apply {
        val steel = c(70, 74, 82)
        for (s in intArrayOf(-1, 1)) {
            col = steel
            for (cx in intArrayOf(-1, 1)) for (cy in intArrayOf(-1, 1)) { push(); translate(cx * 0.05f, s * 0.56f + cy * 0.05f, 0.5f); box(0.02f, 0.02f, 1f); pop() }
            for (k in 0 until 8) { val z = 0.06f + k * 0.12f; tube(listOf(Vec3(-0.05f, s * 0.56f - 0.05f, z), Vec3(0.05f, s * 0.56f + 0.05f, z + 0.12f)), 0.006f, 3); tube(listOf(Vec3(0.05f, s * 0.56f - 0.05f, z), Vec3(-0.05f, s * 0.56f + 0.05f, z + 0.12f)), 0.006f, 3) }
        }
        col = steel; push(); translate(0f, 0f, 0.98f); box(0.06f, 1.24f, 0.05f); pop()
        col = c(16, 16, 18); push(); translate(-0.04f, -0.24f, 0.86f); box(0.03f, 0.5f, 0.2f); pop()
        col = c(245, 245, 245); for (k in 0 until 5) { push(); translate(-0.057f, -0.42f + k * 0.07f, 0.88f); box(0.004f, 0.045f, if (k % 2 == 0) 0.1f else 0.07f); pop() }
        col = c(16, 16, 18); push(); translate(-0.04f, 0.28f, 0.84f); box(0.03f, 0.5f, 0.26f); pop()
        for (cx in 0 until 5) for (cy in 0 until 3) {
            col = if (cy == 0) c(255, 150, 30) * 2f else c(255, 40, 30) * 2.2f
            push(); translate(-0.058f, 0.08f + cx * 0.1f, 0.76f + cy * 0.07f); rotY((PI / 2).toFloat()); cylinder(0.026f, 0.006f, 12); pop()
        }
    }.build()
}
